package com.click.browser.engine

import android.app.DownloadManager
import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.io.DEFAULT_BUFFER_SIZE

/**
 * Download entry point honoring the user's download-location setting
 * (Settings → Browser → Downloads):
 *
 * - Phone storage (default): public Downloads/ClickBrowser via DownloadManager.
 * - App private: app-external Downloads via DownloadManager
 *   (invisible to other apps, removed on uninstall).
 * - Custom folder: SAF tree picked by the user — scheduled as a durable
 *   profile-scoped transfer (DownloadManager can't write to SAF URIs).
 *   Falls back to public Downloads only if the selected tree is unavailable.
 */
data class SavedDownload(val fileName: String, val location: String)

private data class SavedLocationWrite(
    val location: String,
    val usedPublicFallback: Boolean = false
)

object DownloadHelper {
    private const val MIN_FREE_SPACE_AFTER_STAGING_BYTES = 64L * 1024L * 1024L
    private const val SPACE_CHECK_INTERVAL_BYTES = 1024L * 1024L
    private val responseCallbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Consume GeckoView's one-shot response body outside Compose's cancellable scope.
     * The app-process scope survives recomposition and Activity recreation while the stream is copied.
     */
    fun enqueueResponseInApplicationScope(
        context: Context,
        responseBody: InputStream,
        url: String,
        fileName: String,
        mimeType: String?,
        mode: BrowserMode,
        totalBytes: Long,
        onComplete: suspend (SavedDownload?) -> Unit
    ): Job = responseCallbackScope.launch {
        val appContext = context.applicationContext
        val saved = try {
            enqueueResponse(
                context = appContext,
                responseBody = responseBody,
                url = url,
                fileName = fileName,
                mimeType = mimeType,
                mode = mode,
                totalBytes = totalBytes
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            withContext(Dispatchers.Main.immediate) {
                Toast.makeText(appContext, "Download failed.", Toast.LENGTH_LONG).show()
            }
            null
        } finally {
            runCatching { responseBody.close() }
        }
        withContext(Dispatchers.Main.immediate) { onComplete(saved) }
    }

    suspend fun enqueue(
        context: Context,
        url: String,
        fileName: String,
        locationOverride: String? = null,
        userAgent: String? = null,
        mode: BrowserMode = V9Engine.bootMode,
        mimeType: String? = null
    ): SavedDownload? {
        val scheme = try { Uri.parse(url).scheme?.lowercase() } catch (_: Exception) { null }
        if (scheme != "http" && scheme != "https") {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Only HTTP and HTTPS downloads are supported.", Toast.LENGTH_SHORT).show()
            }
            return null
        }
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(180).ifBlank { "download" }
        val actualMimeType = normalizeMimeType(mimeType, safeName)
        val location = locationOverride?.let(DownloadSettings::normalize) ?: try {
            DownloadSettings.location(context)
        } catch (_: Exception) {
            DownloadSettings.LOCATION_PHONE
        }
        return when (location) {
            DownloadSettings.LOCATION_APP -> {
                // Click's own engine (pause/resume + Click notifications);
                // DownloadManager remains only as a failure fallback.
                if (enqueueViaClickEngine(
                        context, url, safeName, actualMimeType, DownloadSettings.LOCATION_APP, userAgent, mode
                    )
                ) {
                    null // engine records the file itself when the transfer completes
                } else if (enqueueViaDownloadManager(
                        context, url, safeName, appPrivate = true, userAgent = userAgent, mimeType = actualMimeType
                    )
                ) {
                    SavedDownload(safeName, appPrivateDownloadPath(context, safeName))
                } else null
            }
            DownloadSettings.LOCATION_CUSTOM -> {
                val treeUri = try { DownloadSettings.customUri(context) } catch (_: Exception) { null }
                val cookieHeader = if (treeUri == null) null else
                    runCatching { android.webkit.CookieManager.getInstance().getCookie(url) }.getOrNull()
                val encryptedCookieHeader = cookieHeader?.takeIf { it.isNotBlank() }?.let { raw ->
                    runCatching {
                        withContext(Dispatchers.IO) { DownloadCookieVault.encrypt(raw, "${mode.name}\n$url") }
                    }.getOrNull()
                }
                if (!cookieHeader.isNullOrBlank() && encryptedCookieHeader == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Click couldn't securely queue this signed-in download. Try again or use another destination.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return null
                }
                val transfer = treeUri?.let {
                    CustomDownloadScheduler.enqueue(
                        context = context,
                        url = url,
                        fileName = safeName,
                        mimeType = actualMimeType,
                        userAgent = userAgent,
                        mode = mode,
                        destinationTreeUri = it.toString(),
                        encryptedCookieHeader = encryptedCookieHeader
                    )
                }
                if (transfer == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Custom folder unavailable — saving to Downloads instead.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    if (enqueueViaDownloadManager(
                            context, url, safeName, appPrivate = false, userAgent = userAgent, mimeType = actualMimeType
                        )
                    ) {
                        SavedDownload(safeName, publicDownloadPath(safeName))
                    } else null
                } else {
                    val message = when (transfer.state) {
                        com.click.browser.data.CustomDownloadState.FAILED ->
                            "Could not start the custom-folder download. Retry from Downloads."
                        com.click.browser.data.CustomDownloadState.CANCELED -> "Download canceled."
                        com.click.browser.data.CustomDownloadState.COMPLETED -> "Download saved."
                        else -> "Downloading to your custom folder…"
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                    null
                }
            }
            else -> {
                if (enqueueViaClickEngine(
                        context, url, safeName, actualMimeType, DownloadSettings.LOCATION_PHONE, userAgent, mode
                    )
                ) {
                    null // engine records the file itself when the transfer completes
                } else if (enqueueViaDownloadManager(
                        context, url, safeName, appPrivate = false, userAgent = userAgent, mimeType = actualMimeType
                    )
                ) {
                    SavedDownload(safeName, publicDownloadPath(safeName))
                } else null
            }
        }
    }

    /**
     * Starts a download in Click's own engine (progress notification with
     * Pause/Resume/Cancel, real resume, durable record). Returns false when
     * the engine could not even queue the record so the caller can fall back
     * to DownloadManager. The engine adds the saved-file record on completion,
     * so callers must NOT record anything for engine-routed downloads.
     */
    private suspend fun enqueueViaClickEngine(
        context: Context,
        url: String,
        safeName: String,
        mimeType: String,
        destination: String,
        userAgent: String?,
        mode: BrowserMode
    ): Boolean {
        return try {
            ClickDownloadEngine.enqueue(
                context = context,
                url = url,
                fileName = safeName,
                mimeType = mimeType,
                mode = mode,
                destination = destination,
                userAgent = userAgent
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download started — $safeName", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Absolute path DownloadManager writes to for the public Downloads route. */
    fun publicDownloadPath(fileName: String): String =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ClickBrowser/$fileName"
        ).absolutePath

    /** Absolute path DownloadManager writes to for the app-private route. */
    fun appPrivateDownloadPath(context: Context, fileName: String): String {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, Environment.DIRECTORY_DOWNLOADS)
        return File(directory, fileName).absolutePath
    }

    suspend fun retryCustomFolder(context: Context, item: com.click.browser.data.CustomDownloadItem): Boolean {
        if (!item.canRetry) return false
        if (item.sourceFilePath != null) {
            return CustomDownloadScheduler.retry(context, item.copy(encryptedCookieHeader = null)) != null
        }
        val cookieHeader = runCatching {
            android.webkit.CookieManager.getInstance().getCookie(item.url)
        }.getOrNull()
        val encryptedCookieHeader = cookieHeader?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching {
                withContext(Dispatchers.IO) { DownloadCookieVault.encrypt(raw, "${item.mode.name}\n${item.url}") }
            }.getOrNull()
        }
        if (!cookieHeader.isNullOrBlank() && encryptedCookieHeader == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Click couldn't securely prepare this retry.", Toast.LENGTH_LONG).show()
            }
            return false
        }
        return CustomDownloadScheduler.retry(
            context,
            item.copy(encryptedCookieHeader = encryptedCookieHeader)
        ) != null
    }

    /**
     * Saves GeckoView's already-authenticated response body immediately after it is handed off.
     * Custom-folder responses are durably staged; other destinations stream directly without re-fetching the URL.
     */
    suspend fun enqueueResponse(
        context: Context,
        responseBody: InputStream,
        url: String,
        fileName: String,
        mimeType: String?,
        locationOverride: String? = null,
        mode: BrowserMode = V9Engine.bootMode,
        totalBytes: Long = -1L
    ): SavedDownload? {
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim().take(180).ifBlank { "download" }
        val actualMimeType = normalizeMimeType(mimeType, safeName)
        val location = locationOverride?.let(DownloadSettings::normalize) ?: try {
            DownloadSettings.location(context)
        } catch (_: Exception) {
            DownloadSettings.LOCATION_PHONE
        }
        // Response-body downloads are streamed by Click itself, never by
        // DownloadManager, so without this registration they were invisible:
        // no progress and no Downloads-screen entry while the file copied.
        val destinationLabel = when (location) {
            DownloadSettings.LOCATION_APP -> "App private storage"
            DownloadSettings.LOCATION_CUSTOM -> "Custom folder"
            else -> "Downloads/ClickBrowser"
        }
        val registryId = ActiveResponseDownloads.begin(safeName, destinationLabel, totalBytes)
        return try {
            val saved = enqueueResponseInternal(
                context, responseBody, url, safeName, actualMimeType, location, mode
            ) { bytes -> ActiveResponseDownloads.updateProgress(registryId, bytes) }
            if (saved != null) {
                // Response downloads stream outside the engine; still give the
                // user the completion alert in the notification bar.
                ClickDownloadNotifications.showResponseSaved(context, safeName)
            }
            saved
        } finally {
            ActiveResponseDownloads.end(registryId)
        }
    }

    private suspend fun enqueueResponseInternal(
        context: Context,
        responseBody: InputStream,
        url: String,
        safeName: String,
        mimeType: String?,
        location: String,
        mode: BrowserMode,
        onProgress: (Long) -> Unit
    ): SavedDownload? {
        if (location != DownloadSettings.LOCATION_CUSTOM) {
            val savedWrite = withContext(Dispatchers.IO) {
                when (location) {
                    DownloadSettings.LOCATION_APP -> copyResponseBodyToAppStorage(context, responseBody, safeName, onProgress)
                        ?.let { SavedLocationWrite(it) }
                    else -> copyResponseBodyToPublicDownloads(context, responseBody, safeName, mimeType, onProgress)
                        ?.let { SavedLocationWrite(it) }
                }
            }
            val outcome = if (savedWrite == null) DownloadSaveOutcome.FAILED
                else DownloadSaveOutcome.SAVED_TO_SELECTED_LOCATION
            withContext(Dispatchers.Main) {
                Toast.makeText(context, downloadSaveStatusMessage(safeName, outcome), Toast.LENGTH_LONG).show()
            }
            return savedWrite?.let { SavedDownload(safeName, it.location) }
        }

        val staged = try {
            stageResponseBody(context, responseBody, onProgress)
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "The custom-folder response could not be staged safely.", Toast.LENGTH_LONG).show()
            }
            return null
        }

        if (location == DownloadSettings.LOCATION_CUSTOM) {
            val treeUri = runCatching { DownloadSettings.customUri(context) }.getOrNull()
            val tree = treeUri?.let { DocumentFile.fromTreeUri(context, it) }
            if (treeUri != null && tree?.canWrite() == true) {
                val transfer = try {
                    CustomDownloadScheduler.enqueueStagedResponse(
                        context = context,
                        url = url,
                        fileName = safeName,
                        mimeType = mimeType,
                        mode = mode,
                        destinationTreeUri = treeUri.toString(),
                        sourceFilePath = staged.absolutePath
                    )
                } catch (_: Exception) {
                    null
                }
                if (transfer != null) {
                    val message = when (transfer.state) {
                        com.click.browser.data.CustomDownloadState.FAILED ->
                            "Could not start the custom-folder download. Retry from Downloads."
                        com.click.browser.data.CustomDownloadState.CANCELED -> "Download canceled."
                        com.click.browser.data.CustomDownloadState.COMPLETED -> "Download saved."
                        else -> "Downloading to your custom folder…"
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                    return null
                }
            }
        }

        val savedWrite = withContext(Dispatchers.IO) {
            try {
                copyToPublicDownloads(context, staged, safeName, mimeType, onProgress)
                    ?.let { SavedLocationWrite(it, usedPublicFallback = true) }
            } catch (_: Exception) {
                null
            } finally {
                runCatching { staged.delete() }
            }
        }
        val outcome = when {
            savedWrite == null -> DownloadSaveOutcome.FAILED
            savedWrite.usedPublicFallback -> DownloadSaveOutcome.FALLBACK_TO_PUBLIC_DOWNLOADS
            else -> DownloadSaveOutcome.SAVED_TO_SELECTED_LOCATION
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                downloadSaveStatusMessage(safeName, outcome),
                Toast.LENGTH_LONG
            ).show()
        }
        return savedWrite?.let { SavedDownload(safeName, it.location) }
    }

    private suspend fun stageResponseBody(
        context: Context,
        responseBody: InputStream,
        onProgress: ((Long) -> Unit)? = null
    ): File =
        withContext(Dispatchers.IO) {
            val directory = File(context.noBackupFilesDir, CustomFolderDownloadTransfer.STAGING_DIRECTORY)
            if (!directory.exists() && !directory.mkdirs()) {
                responseBody.close()
                throw IllegalStateException("Could not create the private staging directory")
            }
            val id = java.util.UUID.randomUUID().toString()
            val temporary = File(directory, "$id.part")
            val ready = File(directory, "$id.ready")
            try {
                responseBody.use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var stagedBytes = 0L
                        var bytesSinceSpaceCheck = 0L
                        var availableBudget = (android.os.StatFs(directory.absolutePath).availableBytes -
                            MIN_FREE_SPACE_AFTER_STAGING_BYTES).coerceAtLeast(0L)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (stagedBytes > Long.MAX_VALUE - count) {
                                throw IllegalStateException("Staged response size exceeded the supported range")
                            }
                            if (bytesSinceSpaceCheck >= SPACE_CHECK_INTERVAL_BYTES) {
                                availableBudget = (android.os.StatFs(directory.absolutePath).availableBytes -
                                    MIN_FREE_SPACE_AFTER_STAGING_BYTES).coerceAtLeast(0L)
                                bytesSinceSpaceCheck = 0L
                                onProgress?.invoke(stagedBytes)
                            }
                            if (availableBudget < count) {
                                throw IllegalStateException("Not enough free storage to stage this response safely")
                            }
                            output.write(buffer, 0, count)
                            stagedBytes += count
                            bytesSinceSpaceCheck += count
                            availableBudget -= count
                        }
                        output.fd.sync()
                    }
                }
                if (!temporary.renameTo(ready)) throw IllegalStateException("Could not finalize staged response")
                ready
            } catch (error: Exception) {
                runCatching { responseBody.close() }
                runCatching { temporary.delete() }
                runCatching { ready.delete() }
                throw error
            }
        }

    private fun copyResponseBodyToAppStorage(
        context: Context,
        responseBody: InputStream,
        fileName: String,
        onProgress: ((Long) -> Unit)? = null
    ): String? {
        var destination: File? = null
        return try {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, Environment.DIRECTORY_DOWNLOADS)
            if (!directory.exists() && !directory.mkdirs()) return null
            val outputFile = File(directory, fileName)
            destination = outputFile
            outputFile.outputStream().use { output ->
                responseBody.use { copyWithProgress(it, output, onProgress) }
            }
            outputFile.absolutePath
        } catch (_: Exception) {
            runCatching { destination?.delete() }
            null
        } finally {
            runCatching { responseBody.close() }
        }
    }

    private fun copyResponseBodyToPublicDownloads(
        context: Context,
        responseBody: InputStream,
        fileName: String,
        mimeType: String?,
        onProgress: ((Long) -> Unit)? = null
    ): String? {
        val type = normalizeMimeType(mimeType, fileName)
        var pendingUri: Uri? = null
        return try {
            // MediaStore.RELATIVE_PATH is available from API 29 (Q).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, type)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ClickBrowser")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                pendingUri = uri
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Download output stream unavailable")
                output.use { destination ->
                    responseBody.use { copyWithProgress(it, destination, onProgress) }
                }
                val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                if (context.contentResolver.update(uri, ready, null, null) <= 0) {
                    throw IllegalStateException("Download could not be finalized")
                }
                pendingUri = null
                uri.toString()
            } else {
                if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) return null
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "ClickBrowser"
                )
                if (!directory.exists() && !directory.mkdirs()) return null
                val outputFile = File(directory, fileName)
                pendingUri = Uri.fromFile(outputFile)
                outputFile.outputStream().use { output ->
                    responseBody.use { copyWithProgress(it, output, onProgress) }
                }
                pendingUri = null
                outputFile.absolutePath
            }
        } catch (_: Exception) {
            pendingUri?.let { uri ->
                if (uri.scheme == "file") runCatching { File(uri.path.orEmpty()).delete() }
                else runCatching { context.contentResolver.delete(uri, null, null) }
            }
            null
        } finally {
            runCatching { responseBody.close() }
        }
    }

    /** Streams [input] to [output], reporting cumulative bytes at most every ~256 KB. */
    private fun copyWithProgress(
        input: InputStream,
        output: java.io.OutputStream,
        onProgress: ((Long) -> Unit)?
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        var lastReported = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            total += count
            if (onProgress != null && total - lastReported >= 256L * 1024L) {
                lastReported = total
                onProgress(total)
            }
        }
        onProgress?.invoke(total)
    }

    private fun copyToPublicDownloads(
        context: Context,
        source: File,
        fileName: String,
        mimeType: String?,
        onProgress: ((Long) -> Unit)? = null
    ): String? {
        val type = normalizeMimeType(mimeType, fileName)
        // MediaStore.RELATIVE_PATH is available from API 29 (Q).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, type)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ClickBrowser")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            return try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { copyWithProgress(it, output, onProgress) }
                } ?: throw IllegalStateException("Download output stream unavailable")
                val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                context.contentResolver.update(uri, ready, null, null)
                uri.toString()
            } catch (_: Exception) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                null
            }
        }

        if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return null
        return try {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "ClickBrowser"
            )
            if (!directory.exists() && !directory.mkdirs()) return null
            val destination = File(directory, fileName)
            destination.outputStream().use { output ->
                source.inputStream().use { copyWithProgress(it, output, onProgress) }
            }
            destination.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun enqueueViaDownloadManager(
        context: Context,
        url: String,
        fileName: String,
        appPrivate: Boolean,
        userAgent: String?,
        mimeType: String
    ): Boolean {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mimeType)
                .setTitle("Click Browser Download")
                .setDescription(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            val actualUserAgent = userAgent?.takeIf { it.isNotBlank() }
            if (actualUserAgent != null) req.addRequestHeader("User-Agent", actualUserAgent)
            val cookie = try { android.webkit.CookieManager.getInstance().getCookie(url) } catch (_: Exception) { null }
            if (!cookie.isNullOrBlank()) req.addRequestHeader("Cookie", cookie)
            if (appPrivate) {
                req.setDestinationInExternalFilesDir(
                    context, Environment.DIRECTORY_DOWNLOADS, fileName
                )
            } else {
                req.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, "ClickBrowser/$fileName"
                )
            }
            dm.enqueue(req)
            Toast.makeText(context, "Downloading $fileName…", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed.", Toast.LENGTH_SHORT).show()
            false
        }
    }

    internal fun normalizeMimeType(rawMimeType: String?, fileName: String): String =
        rawMimeType?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
            ?: guessMime(fileName)

    private fun guessMime(fileName: String): String =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
}
