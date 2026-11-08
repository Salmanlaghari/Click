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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Download entry point honoring the user's download-location setting
 * (Settings → Browser → Downloads):
 *
 * - Phone storage (default): public Downloads/ClickBrowser via DownloadManager.
 * - App private: app-external Downloads via DownloadManager
 *   (invisible to other apps, removed on uninstall).
 * - Custom folder: SAF tree picked by the user — downloaded manually with
 *   OkHttp into the tree (DownloadManager can't write to SAF URIs).
 *   Falls back to public Downloads if the tree is gone.
 */
data class SavedDownload(val fileName: String, val location: String)

private data class SavedLocationWrite(
    val location: String,
    val usedPublicFallback: Boolean = false
)

object DownloadHelper {

    /** Shared OkHttpClient — reused across downloads (connection pooling). */
    private val httpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    suspend fun enqueue(
        context: Context,
        url: String,
        fileName: String,
        locationOverride: String? = null,
        userAgent: String? = null
    ): SavedDownload? {
        val scheme = try { Uri.parse(url).scheme?.lowercase() } catch (_: Exception) { null }
        if (scheme != "http" && scheme != "https") {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Only HTTP and HTTPS downloads are supported.", Toast.LENGTH_SHORT).show()
            }
            return null
        }
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(180).ifBlank { "download" }
        val location = locationOverride?.let(DownloadSettings::normalize) ?: try {
            DownloadSettings.location(context)
        } catch (_: Exception) {
            DownloadSettings.LOCATION_PHONE
        }
        return when (location) {
            DownloadSettings.LOCATION_APP -> {
                enqueueViaDownloadManager(context, url, safeName, appPrivate = true, userAgent = userAgent)
                null
            }
            DownloadSettings.LOCATION_CUSTOM -> {
                val savedLocation = downloadToCustomFolder(context, url, safeName, userAgent)
                if (savedLocation != null) {
                    SavedDownload(safeName, savedLocation)
                } else {
                    enqueueViaDownloadManager(context, url, safeName, appPrivate = false, userAgent = userAgent)
                    null
                }
            }
            else -> {
                enqueueViaDownloadManager(context, url, safeName, appPrivate = false, userAgent = userAgent)
                null
            }
        }
    }

    /**
     * Saves GeckoView's already-authenticated response body after Click's normal
     * confirmation dialog. The stream is staged only after the user confirms,
     * then copied to the selected destination without re-fetching the URL.
     */
    suspend fun enqueueResponse(
        context: Context,
        responseBody: InputStream,
        fileName: String,
        mimeType: String?,
        locationOverride: String? = null
    ): SavedDownload? {
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim().take(180).ifBlank { "download" }
        val location = locationOverride?.let(DownloadSettings::normalize) ?: try {
            DownloadSettings.location(context)
        } catch (_: Exception) {
            DownloadSettings.LOCATION_PHONE
        }
        val savedWrite = withContext(Dispatchers.IO) {
            val staged = File.createTempFile("click-gecko-download-", ".tmp", context.cacheDir)
            try {
                responseBody.use { input ->
                    FileOutputStream(staged).use { output -> input.copyTo(output) }
                }
                when (location) {
                    DownloadSettings.LOCATION_APP -> {
                        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                            ?: File(context.filesDir, Environment.DIRECTORY_DOWNLOADS)
                        directory.mkdirs()
                        val destination = File(directory, safeName)
                        destination.outputStream().use { output ->
                            staged.inputStream().use { input -> input.copyTo(output) }
                        }
                        SavedLocationWrite(destination.absolutePath)
                    }
                    DownloadSettings.LOCATION_CUSTOM -> {
                        val customLocation = copyToCustomFolder(context, staged, safeName, mimeType)
                        if (customLocation != null) {
                            SavedLocationWrite(customLocation)
                        } else {
                            copyToPublicDownloads(context, staged, safeName, mimeType)
                                ?.let { SavedLocationWrite(it, usedPublicFallback = true) }
                        }
                    }
                    else -> copyToPublicDownloads(context, staged, safeName, mimeType)
                        ?.let { SavedLocationWrite(it) }
                }
            } catch (_: Exception) {
                runCatching { responseBody.close() }
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

    private suspend fun copyToCustomFolder(
        context: Context,
        source: File,
        fileName: String,
        mimeType: String?
    ): String? {
        val treeUri = DownloadSettings.customUri(context) ?: return null
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        if (!tree.canWrite()) return null
        tree.findFile(fileName)?.delete()
        val document = tree.createFile(mimeType?.takeIf { it.isNotBlank() } ?: guessMime(fileName), fileName)
            ?: return null
        return try {
            val output = context.contentResolver.openOutputStream(document.uri)
            if (output == null) {
                runCatching { document.delete() }
                null
            } else {
                output.use { destination ->
                    source.inputStream().use { input -> input.copyTo(destination) }
                }
                document.uri.toString()
            }
        } catch (_: Exception) {
            runCatching { document.delete() }
            null
        }
    }

    private fun copyToPublicDownloads(
        context: Context,
        source: File,
        fileName: String,
        mimeType: String?
    ): String? {
        val type = mimeType?.takeIf { it.isNotBlank() } ?: guessMime(fileName)
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
                    source.inputStream().use { it.copyTo(output) }
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
                source.inputStream().use { it.copyTo(output) }
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
        userAgent: String?
    ) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url))
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
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed.", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun downloadToCustomFolder(
        context: Context,
        url: String,
        fileName: String,
        userAgent: String?
    ): String? = withContext(Dispatchers.IO) {
        var createdDocument: DocumentFile? = null
        try {
            val treeUri = try { DownloadSettings.customUri(context) } catch (_: Exception) { null }
                ?: return@withContext null
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext null
            if (!tree.canWrite()) return@withContext null
            tree.findFile(fileName)?.delete()
            val doc = tree.createFile(guessMime(fileName), fileName) ?: return@withContext null
            createdDocument = doc
            val requestBuilder = okhttp3.Request.Builder().url(url).get()
                .header(
                    "User-Agent",
                    userAgent?.takeIf { it.isNotBlank() }
                        ?: android.webkit.WebSettings.getDefaultUserAgent(context.applicationContext)
                )
            val cookie = try { android.webkit.CookieManager.getInstance().getCookie(url) } catch (_: Exception) { null }
            if (!cookie.isNullOrBlank()) requestBuilder.header("Cookie", cookie)
            val req = requestBuilder.build()
            val savedLocation = httpClient.newCall(req).execute().use { resp ->
                val body = resp.body
                if (!resp.isSuccessful || body == null) {
                    null
                } else {
                    val output = context.contentResolver.openOutputStream(doc.uri)
                    if (output == null) {
                        null
                    } else {
                        output.use { out -> body.byteStream().use { input -> input.copyTo(out) } }
                        doc.uri.toString()
                    }
                }
            }
            if (savedLocation == null) {
                runCatching { doc.delete() }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, "Could not save to custom folder — using Downloads instead.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to your folder: $fileName", Toast.LENGTH_LONG).show()
                }
            }
            savedLocation
        } catch (_: Exception) {
            runCatching { createdDocument?.delete() }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context, "Could not save to custom folder — using Downloads instead.",
                    Toast.LENGTH_LONG
                ).show()
            }
            null
        }
    }

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
