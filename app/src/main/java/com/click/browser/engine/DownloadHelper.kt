package com.click.browser.engine

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    ) {
        val scheme = try { Uri.parse(url).scheme?.lowercase() } catch (_: Exception) { null }
        if (scheme != "http" && scheme != "https") {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Only HTTP and HTTPS downloads are supported.", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(180).ifBlank { "download" }
        val location = locationOverride?.let(DownloadSettings::normalize) ?: try {
            DownloadSettings.location(context)
        } catch (_: Exception) {
            DownloadSettings.LOCATION_PHONE
        }
        when (location) {
            DownloadSettings.LOCATION_APP ->
                enqueueViaDownloadManager(context, url, safeName, appPrivate = true, userAgent = userAgent)
            DownloadSettings.LOCATION_CUSTOM -> {
                val ok = downloadToCustomFolder(context, url, safeName, userAgent)
                if (!ok) enqueueViaDownloadManager(context, url, safeName, appPrivate = false, userAgent = userAgent)
            }
            else -> enqueueViaDownloadManager(context, url, safeName, appPrivate = false, userAgent = userAgent)
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
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val treeUri = try { DownloadSettings.customUri(context) } catch (_: Exception) { null }
                ?: return@withContext false
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext false
            if (!tree.canWrite()) return@withContext false
            tree.findFile(fileName)?.delete()
            val doc = tree.createFile(guessMime(fileName), fileName) ?: return@withContext false
            val requestBuilder = okhttp3.Request.Builder().url(url).get()
                .header(
                    "User-Agent",
                    userAgent?.takeIf { it.isNotBlank() }
                        ?: android.webkit.WebSettings.getDefaultUserAgent(context.applicationContext)
                )
            val cookie = try { android.webkit.CookieManager.getInstance().getCookie(url) } catch (_: Exception) { null }
            if (!cookie.isNullOrBlank()) requestBuilder.header("Cookie", cookie)
            val req = requestBuilder.build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val body = resp.body ?: return@withContext false
                context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                    body.byteStream().copyTo(out)
                } ?: return@withContext false
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context, "Saved to your folder: $fileName", Toast.LENGTH_LONG
                ).show()
            }
            true
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context, "Custom folder unavailable — using Downloads instead.",
                    Toast.LENGTH_LONG
                ).show()
            }
            false
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
