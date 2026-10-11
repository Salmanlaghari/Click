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

    suspend fun enqueue(context: Context, url: String, fileName: String) {
        val location = try { DownloadSettings.location(context) }
        catch (_: Exception) { DownloadSettings.LOCATION_PHONE }
        when (location) {
            DownloadSettings.LOCATION_APP ->
                enqueueViaDownloadManager(context, url, fileName, appPrivate = true)
            DownloadSettings.LOCATION_CUSTOM -> {
                val ok = downloadToCustomFolder(context, url, fileName)
                if (!ok) enqueueViaDownloadManager(context, url, fileName, appPrivate = false)
            }
            else -> enqueueViaDownloadManager(context, url, fileName, appPrivate = false)
        }
    }

    private fun enqueueViaDownloadManager(
        context: Context,
        url: String,
        fileName: String,
        appPrivate: Boolean
    ) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle("Click Browser Download")
                .setDescription(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
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
        fileName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val treeUri = try { DownloadSettings.customUri(context) } catch (_: Exception) { null }
                ?: return@withContext false
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext false
            if (!tree.canWrite()) return@withContext false
            tree.findFile(fileName)?.delete()
            val doc = tree.createFile(guessMime(fileName), fileName) ?: return@withContext false
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val req = okhttp3.Request.Builder().url(url).get()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
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
