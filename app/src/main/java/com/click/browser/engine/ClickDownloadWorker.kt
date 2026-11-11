package com.click.browser.engine

import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.click.browser.data.BrowserRepository
import com.click.browser.data.ClickDownloadStore
import com.click.browser.data.DownloadItem
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Transfer loop for Click's own download engine. Downloads into an
 * app-private partial file with OkHttp, polling the durable record between
 * chunks so Pause/Cancel from the notification or Downloads screen stop the
 * transfer cleanly at a chunk boundary. Resume re-enters here and continues
 * with a Range + If-Range request against the kept partial file.
 */
class ClickDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val store = ClickDownloadStore(appContext)

    override suspend fun doWork(): Result {
        val recordId = inputData.getString(ClickDownloadEngine.EXTRA_RECORD_ID) ?: return Result.success()
        val mode = runCatching {
            BrowserMode.valueOf(inputData.getString(ClickDownloadEngine.EXTRA_MODE).orEmpty())
        }.getOrNull() ?: return Result.success()
        var record = store.getFor(mode, recordId) ?: return Result.success()

        // A stale request firing after the user paused must not restart it.
        if (record.state == ClickDownloadState.PAUSED ||
            record.state == ClickDownloadState.COMPLETED ||
            record.state == ClickDownloadState.CANCELED
        ) {
            return Result.success()
        }
        record = store.update(mode, recordId) { current ->
            current.copy(state = ClickDownloadState.RUNNING, updatedAt = System.currentTimeMillis())
        } ?: return Result.success()

        runCatching {
            if (ClickDownloadNotifications.canPost(applicationContext)) {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ForegroundInfo(
                        ClickDownloadNotifications.notificationId(recordId),
                        ClickDownloadNotifications.progressNotification(applicationContext, record),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    ForegroundInfo(
                        ClickDownloadNotifications.notificationId(recordId),
                        ClickDownloadNotifications.progressNotification(applicationContext, record)
                    )
                }
                setForeground(info)
            }
        }
        ClickDownloadNotifications.showProgress(applicationContext, record)

        val partial = ClickDownloadEngine.partialFile(applicationContext, recordId)
        return try {
            transfer(mode, record, partial)
        } catch (cancelled: CancellationException) {
            // WorkManager cancellation (pause/cancel backstop or system):
            // reconcile the record with whatever the user actually asked for.
            val current = store.getFor(mode, recordId)
            when (current?.state) {
                ClickDownloadState.PAUSED -> {
                    store.update(mode, recordId) { it.copy(bytesDownloaded = partialLength(partial)) }
                    ClickDownloadNotifications.showProgress(
                        applicationContext,
                        store.getFor(mode, recordId) ?: record
                    )
                }
                ClickDownloadState.CANCELED -> runCatching { partial.delete() }
                ClickDownloadState.RUNNING -> {
                    val paused = store.update(mode, recordId) { latest ->
                        latest.copy(
                            state = ClickDownloadState.PAUSED,
                            bytesDownloaded = partialLength(partial),
                            errorMessage = "Interrupted — tap Resume to continue.",
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    if (paused != null) ClickDownloadNotifications.showProgress(applicationContext, paused)
                }
                else -> Unit
            }
            throw cancelled
        }
    }

    private fun partialLength(partial: File): Long = if (partial.isFile) partial.length() else 0L

    private suspend fun transfer(
        mode: BrowserMode,
        startRecord: ClickDownloadRecord,
        partial: File
    ): Result {
        var record = startRecord
        val partialBytes = partialLength(partial)
        val requestBuilder = Request.Builder()
            .url(record.url)
            .header(
                "User-Agent",
                record.userAgent ?: System.getProperty("http.agent") ?: "ClickBrowser/Android"
            )
        val cookie = runCatching {
            android.webkit.CookieManager.getInstance().getCookie(record.url)
        }.getOrNull()
        if (!cookie.isNullOrBlank()) requestBuilder.header("Cookie", cookie)
        if (partialBytes > 0L) {
            requestBuilder.header("Range", "bytes=$partialBytes-")
            val validator = record.etag ?: record.lastModified
            if (!validator.isNullOrBlank()) requestBuilder.header("If-Range", validator)
        }

        val response = try {
            client.newCall(requestBuilder.build()).execute()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            return fail(mode, record, "Network error — partial file kept, tap Resume to continue.")
        }

        response.use { resp ->
            val action = resumeActionForResponse(resp.code, partialBytes, record.totalBytes)
            if (action == ResumeAction.ERROR) {
                return fail(mode, record, "Server error (HTTP ${resp.code}). Tap Resume to try again.")
            }
            if (action == ResumeAction.ALREADY_COMPLETE) {
                return finalizeDownload(mode, record, partial)
            }
            val append = action == ResumeAction.APPEND
            val body = resp.body ?: return fail(mode, record, "Empty server response. Tap Resume to try again.")
            val totalBytes = if (append) {
                totalFromContentRange(resp.header("Content-Range"), resp.body?.contentLength() ?: -1L, partialBytes)
            } else {
                body.contentLength()
            }
            record = store.update(mode, record.id) { current ->
                current.copy(
                    totalBytes = if (totalBytes > 0L) totalBytes else current.totalBytes,
                    etag = resp.header("ETag") ?: current.etag,
                    lastModified = resp.header("Last-Modified") ?: current.lastModified,
                    bytesDownloaded = if (append) partialBytes else 0L,
                    updatedAt = System.currentTimeMillis()
                )
            } ?: return Result.success()

            var written = if (append) partialBytes else 0L
            var sinceStateCheck = 0L
            var lastNotifyAt = 0L
            try {
                body.byteStream().use { input ->
                    FileOutputStream(partial, append).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            written += count
                            sinceStateCheck += count
                            if (sinceStateCheck >= STATE_CHECK_INTERVAL_BYTES) {
                                sinceStateCheck = 0L
                                when (store.getFor(mode, record.id)?.state) {
                                    ClickDownloadState.PAUSED -> {
                                        output.flush()
                                        val paused = store.update(mode, record.id) { current ->
                                            current.copy(
                                                bytesDownloaded = written,
                                                updatedAt = System.currentTimeMillis()
                                            )
                                        }
                                        if (paused != null) {
                                            ClickDownloadNotifications.showProgress(applicationContext, paused)
                                        }
                                        return Result.success()
                                    }
                                    ClickDownloadState.CANCELED -> {
                                        runCatching { partial.delete() }
                                        return Result.success()
                                    }
                                    null -> return Result.success()
                                    else -> Unit
                                }
                                val current = store.update(mode, record.id) { latest ->
                                    latest.copy(
                                        bytesDownloaded = written,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                }
                                val now = System.currentTimeMillis()
                                if (current != null && now - lastNotifyAt >= NOTIFY_INTERVAL_MS) {
                                    lastNotifyAt = now
                                    ClickDownloadNotifications.showProgress(applicationContext, current)
                                }
                            }
                        }
                        output.flush()
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                store.update(mode, record.id) { current ->
                    current.copy(bytesDownloaded = written, updatedAt = System.currentTimeMillis())
                }
                return fail(mode, record, "Network error — partial file kept, tap Resume to continue.")
            }

            record = store.update(mode, record.id) { current ->
                current.copy(bytesDownloaded = written, updatedAt = System.currentTimeMillis())
            } ?: return Result.success()
            return finalizeDownload(mode, record, partial)
        }
    }

    /** Moves the completed partial file to the record's destination. */
    private suspend fun finalizeDownload(
        mode: BrowserMode,
        record: ClickDownloadRecord,
        partial: File
    ): Result {
        val finalLocation = try {
            when (record.destination) {
                DownloadSettings.LOCATION_APP -> copyToAppStorage(partial, record.fileName)
                else -> copyToPublicDownloads(partial, record.fileName, record.mimeType)
            }
        } catch (_: Exception) {
            null
        } ?: return fail(mode, record, "Couldn't save the finished file. Tap Resume to try again.")

        val size = partialLength(partial)
        val completed = store.update(mode, record.id) { current ->
            current.copy(
                state = ClickDownloadState.COMPLETED,
                bytesDownloaded = size,
                totalBytes = if (current.totalBytes > 0L) current.totalBytes else size,
                finalLocation = finalLocation,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
        } ?: return Result.success()
        runCatching {
            BrowserRepository(applicationContext).addDownloadItemFor(
                mode,
                DownloadItem(
                    fileName = record.fileName,
                    url = record.url,
                    path = finalLocation,
                    timestamp = System.currentTimeMillis(),
                    transferId = record.id
                )
            )
        }
        runCatching { partial.delete() }
        ClickDownloadNotifications.showCompletion(applicationContext, completed)
        return Result.success()
    }

    private fun copyToAppStorage(partial: File, fileName: String): String? {
        val directory = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(applicationContext.filesDir, Environment.DIRECTORY_DOWNLOADS)
        if (!directory.exists() && !directory.mkdirs()) return null
        val target = uniqueTargetFile(directory, fileName)
        partial.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target.absolutePath
    }

    private fun copyToPublicDownloads(partial: File, fileName: String, mimeType: String?): String? {
        val type = mimeType?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
            ?: "application/octet-stream"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, type)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ClickBrowser")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri: Uri = applicationContext.contentResolver
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                val output = applicationContext.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Download output stream unavailable")
                output.use { destination ->
                    partial.inputStream().use { input -> input.copyTo(destination) }
                }
                val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                if (applicationContext.contentResolver.update(uri, ready, null, null) <= 0) {
                    throw IllegalStateException("Download could not be finalized")
                }
                return uri.toString()
            } catch (error: Exception) {
                runCatching { applicationContext.contentResolver.delete(uri, null, null) }
                throw error
            }
        }
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ClickBrowser"
        )
        if (!directory.exists() && !directory.mkdirs()) return null
        val target = uniqueTargetFile(directory, fileName)
        partial.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target.absolutePath
    }

    private fun uniqueTargetFile(directory: File, fileName: String): File {
        val direct = File(directory, fileName)
        if (!direct.exists()) return direct
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val extension = if (dot > 0) fileName.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = File(directory, "$base ($index)$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private suspend fun fail(
        mode: BrowserMode,
        record: ClickDownloadRecord,
        message: String
    ): Result {
        val failed = store.update(mode, record.id) { current ->
            // A user pause/cancel racing the failure wins: never resurrect it.
            if (current.state == ClickDownloadState.PAUSED ||
                current.state == ClickDownloadState.CANCELED ||
                current.state == ClickDownloadState.COMPLETED
            ) {
                current
            } else {
                current.copy(
                    state = ClickDownloadState.FAILED,
                    errorMessage = message,
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
        if (failed != null && failed.state == ClickDownloadState.FAILED) {
            ClickDownloadNotifications.showFailure(applicationContext, failed)
        } else if (failed != null) {
            ClickDownloadNotifications.cancelProgress(applicationContext, record.id)
        }
        return Result.success()
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val STATE_CHECK_INTERVAL_BYTES = 256L * 1024L
        const val NOTIFY_INTERVAL_MS = 1_000L

        val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
