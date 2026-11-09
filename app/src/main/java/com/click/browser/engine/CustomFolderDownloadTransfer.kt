package com.click.browser.engine

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.documentfile.provider.DocumentFile
import com.click.browser.data.CustomDownloadItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal data class CustomTransferResult(
    val fileName: String,
    val location: String,
    val bytesDownloaded: Long,
    val totalBytes: Long
)

internal class CustomTransferFailure(val userMessage: String) : IOException(userMessage)

/** Network or staged-response transfer to SAF, finalized only after the stream succeeds. */
internal object CustomFolderDownloadTransfer {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    suspend fun download(
        context: Context,
        item: CustomDownloadItem,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit
    ): CustomTransferResult = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(item.destinationTreeUri))
            ?: throw CustomTransferFailure("The selected custom folder is unavailable.")
        if (!tree.canWrite()) {
            throw CustomTransferFailure("Access to the custom folder was revoked. Choose it again in Download Settings.")
        }
        val stagedSource = item.sourceFilePath?.let { resolveStagedSource(context, it) }
        val cookie = if (stagedSource == null) {
            try {
                item.encryptedCookieHeader?.let {
                    DownloadCookieVault.decrypt(it, "${item.mode.name}\n${item.url}")
                }
            } catch (_: Exception) {
                throw CustomTransferFailure("The saved sign-in snapshot is unavailable. Open the page and retry.")
            }
        } else {
            null
        }

        val partName = partName(item)
        tree.findFile(partName)?.delete()
        val part = tree.createFile(
            item.mimeType?.takeIf { it.isNotBlank() } ?: mimeFor(item.fileName),
            partName
        )
            ?: throw CustomTransferFailure("The custom folder could not create a temporary file.")
        val activeCall = AtomicReference<Call?>(null)
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) activeCall.get()?.cancel()
        }
        try {
            if (stagedSource != null) {
                copyAndFinalize(
                    context = context,
                    tree = tree,
                    part = part,
                    item = item,
                    source = stagedSource.inputStream(),
                    totalBytes = stagedSource.length(),
                    onProgress = onProgress
                )
            } else {
                var requestUrl = item.url.toHttpUrlOrNull()
                    ?: throw CustomTransferFailure("This download link is not a valid HTTP or HTTPS URL.")
                var requestCookie = cookie
                var redirectCount = 0
                val userAgent = item.userAgent?.takeIf { it.isNotBlank() }
                    ?: android.webkit.WebSettings.getDefaultUserAgent(context.applicationContext)
                var pendingResponse: okhttp3.Response? = null
                while (pendingResponse == null) {
                    val requestBuilder = Request.Builder()
                        .url(requestUrl)
                        .get()
                        .header("User-Agent", userAgent)
                    if (!requestCookie.isNullOrBlank()) requestBuilder.header("Cookie", requestCookie)
                    val call = client.newCall(requestBuilder.build())
                    activeCall.set(call)
                    val candidate = call.execute()
                    if (candidate.code !in 300..399) {
                        pendingResponse = candidate
                        break
                    }
                    val nextUrl = candidate.header("Location")?.let { candidate.request.url.resolve(it) }
                    candidate.close()
                    activeCall.compareAndSet(call, null)
                    if (nextUrl == null || ++redirectCount > MAX_REDIRECTS) {
                        throw CustomTransferFailure("The server redirected this download too many times.")
                    }
                    if (requestUrl.isHttps && !nextUrl.isHttps) {
                        throw CustomTransferFailure("An insecure redirect was blocked.")
                    }
                    if (requestUrl.scheme != nextUrl.scheme || requestUrl.host != nextUrl.host ||
                        requestUrl.port != nextUrl.port || requestUrl.encodedPath != nextUrl.encodedPath
                    ) {
                        requestCookie = null
                    }
                    requestUrl = nextUrl
                }
                val response = pendingResponse
                    ?: throw CustomTransferFailure("The server could not start this download.")
                response.use { finalResponse ->
                    val body = finalResponse.body
                    if (!finalResponse.isSuccessful || body == null) {
                        val message = when (finalResponse.code) {
                            401, 403 -> "The server denied this download or the sign-in has expired."
                            else -> "The server returned HTTP ${finalResponse.code}."
                        }
                        throw CustomTransferFailure(message)
                    }
                    copyAndFinalize(
                        context = context,
                        tree = tree,
                        part = part,
                        item = item,
                        source = body.byteStream(),
                        totalBytes = body.contentLength(),
                        onProgress = onProgress
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            runCatching { part.delete() }
            throw cancelled
        } catch (failure: CustomTransferFailure) {
            runCatching { part.delete() }
            throw failure
        } catch (error: Exception) {
            runCatching { part.delete() }
            throw CustomTransferFailure(
                when (error) {
                    is SecurityException -> "Access to the custom folder was revoked. Choose it again in Download Settings."
                    else -> "The download could not be saved. Check the connection and custom folder, then retry."
                }
            )
        } finally {
            activeCall.set(null)
            cancellationHandle.dispose()
        }
    }

    private suspend fun copyAndFinalize(
        context: Context,
        tree: DocumentFile,
        part: DocumentFile,
        item: CustomDownloadItem,
        source: InputStream,
        totalBytes: Long,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit
    ): CustomTransferResult {
        val total = totalBytes.takeIf { it > 0L } ?: -1L
        var downloaded = 0L
        var lastReportedBytes = 0L
        var lastReportedAt = SystemClock.elapsedRealtime()
        val output = context.contentResolver.openOutputStream(part.uri, "w")
            ?: throw CustomTransferFailure("The custom folder refused the output stream.")
        output.use { destination ->
            source.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    destination.write(buffer, 0, count)
                    downloaded += count
                    val now = SystemClock.elapsedRealtime()
                    if (downloaded - lastReportedBytes >= PROGRESS_BYTES_STEP ||
                        now - lastReportedAt >= PROGRESS_INTERVAL_MS
                    ) {
                        onProgress(downloaded, total)
                        lastReportedBytes = downloaded
                        lastReportedAt = now
                    }
                }
            }
        }
        onProgress(downloaded, total)
        currentCoroutineContext().ensureActive()
        val finalName = uniqueName(tree, item.fileName, item.id, item.attemptId)
        if (part.renameTo(finalName)) {
            val saved = tree.findFile(finalName)
            return CustomTransferResult(
                fileName = finalName,
                location = (saved?.uri ?: part.uri).toString(),
                bytesDownloaded = downloaded,
                totalBytes = total
            )
        }

        // Some document providers cannot rename. Copy into a fresh final
        // entry instead of deleting or overwriting the user's existing file.
        val finalDocument = tree.createFile(
            item.mimeType?.takeIf { it.isNotBlank() } ?: mimeFor(item.fileName),
            finalName
        ) ?: throw CustomTransferFailure("The custom folder could not finalize the downloaded file.")
        try {
            val input = context.contentResolver.openInputStream(part.uri)
                ?: throw CustomTransferFailure("The temporary download could not be reopened.")
            val finalOutput = context.contentResolver.openOutputStream(finalDocument.uri, "w")
                ?: throw CustomTransferFailure("The custom folder refused the final file.")
            input.use { source -> finalOutput.use { destination -> source.copyTo(destination) } }
            runCatching { part.delete() }
            return CustomTransferResult(
                fileName = finalName,
                location = finalDocument.uri.toString(),
                bytesDownloaded = downloaded,
                totalBytes = total
            )
        } catch (error: Exception) {
            runCatching { finalDocument.delete() }
            throw error
        }
    }

    private fun resolveStagedSource(context: Context, path: String): File {
        return try {
            val stagingDirectory = File(context.noBackupFilesDir, STAGING_DIRECTORY).canonicalFile
            val source = File(path).canonicalFile
            if (!source.isFile || !source.path.startsWith(stagingDirectory.path + File.separator)) {
                throw CustomTransferFailure("The staged download source is unavailable. Retry from the page.")
            }
            source
        } catch (failure: CustomTransferFailure) {
            throw failure
        } catch (_: Exception) {
            throw CustomTransferFailure("The staged download source is unavailable. Retry from the page.")
        }
    }

    fun deletePartial(context: Context, item: CustomDownloadItem) {
        runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(item.destinationTreeUri))
                ?.findFile(partName(item))
                ?.delete()
        }
    }

    private fun partName(item: CustomDownloadItem): String =
        ".click-${item.id.take(16)}-${item.attemptId?.take(10) ?: "noattempt"}.part"

    private fun uniqueName(tree: DocumentFile, requested: String, id: String, attemptId: String?): String {
        val safe = requested.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim().take(180).ifBlank { "download" }
        val dot = safe.lastIndexOf('.')
        val stem = if (dot > 0) safe.substring(0, dot) else safe
        val extension = if (dot > 0) safe.substring(dot) else ""
        if (tree.findFile(safe) == null) return safe
        var index = 0
        while (true) {
            val suffix = if (index == 0) "${id.take(8)}-${attemptId?.take(6) ?: "attempt"}"
                else "${id.take(8)}-${attemptId?.take(6) ?: "attempt"}-$index"
            val candidate = "$stem ($suffix)$extension"
            if (tree.findFile(candidate) == null) return candidate
            index += 1
        }
    }

    private fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
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

    const val STAGING_DIRECTORY = "custom_download_staging"
    private const val PROGRESS_BYTES_STEP = 256L * 1024L
    private const val PROGRESS_INTERVAL_MS = 400L
    private const val MAX_REDIRECTS = 10
}
