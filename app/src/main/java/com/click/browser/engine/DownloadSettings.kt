package com.click.browser.engine

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Download location settings.
 *
 * Default is the phone's PUBLIC Downloads folder (visible in the file
 * manager, survives uninstall) — NOT app-private storage. The user can
 * switch to app-private or pick a custom folder (SAF tree URI).
 */
object DownloadSettings {

    const val LOCATION_PHONE = "phone"
    const val LOCATION_APP = "app"
    const val LOCATION_CUSTOM = "custom"

    private const val TAG = "DownloadSettings"

    fun normalize(value: String?): String = when (value) {
        LOCATION_APP, LOCATION_CUSTOM -> value
        else -> LOCATION_PHONE // default
    }

    fun displayName(value: String): String = when (normalize(value)) {
        LOCATION_APP -> "App private storage"
        LOCATION_CUSTOM -> "Custom folder"
        else -> "Phone storage (Downloads)"
    }

    suspend fun location(context: Context): String = try {
        normalize(context.dataStore.data.map { it[AppSettings.DOWNLOAD_LOCATION] }.first())
    } catch (e: Exception) {
        Log.w(TAG, "read failed", e); LOCATION_PHONE
    }

    suspend fun setLocation(context: Context, value: String) {
        try {
            context.dataStore.edit { it[AppSettings.DOWNLOAD_LOCATION] = normalize(value) }
        } catch (e: Exception) {
            Log.w(TAG, "write failed", e)
        }
    }

    suspend fun customUri(context: Context): Uri? = try {
        context.dataStore.data.map { it[AppSettings.DOWNLOAD_CUSTOM_URI] }.first()
            ?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
    } catch (e: Exception) {
        Log.w(TAG, "custom uri read failed", e); null
    }

    suspend fun setCustomUri(context: Context, uri: Uri?) {
        try {
            context.dataStore.edit {
                if (uri == null) it.remove(AppSettings.DOWNLOAD_CUSTOM_URI)
                else it[AppSettings.DOWNLOAD_CUSTOM_URI] = uri.toString()
            }
        } catch (e: Exception) {
            Log.w(TAG, "custom uri write failed", e)
        }
    }

    /**
     * Resolves the download directory for the current setting.
     * Returns null when the custom SAF tree is in use (caller must use
     * DocumentFile via [customUri]) or when it is gone (caller falls back).
     */
    suspend fun resolveDir(context: Context): File? {
        return when (location(context)) {
            LOCATION_APP -> context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads").apply { mkdirs() }
            LOCATION_CUSTOM -> {
                // SAF trees cannot be represented as java.io.File — the
                // downloader uses DocumentFile.fromTreeUri(customUri()).
                // Return null so callers take the DocumentFile path.
                null
            }
            else -> publicDownloadsDir()
        }
    }

    fun publicDownloadsDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .apply { mkdirs() }

    /** Human-readable path for the settings UI. */
    suspend fun displayPath(context: Context): String {
        return when (location(context)) {
            LOCATION_APP -> "App storage / Downloads (private)"
            LOCATION_CUSTOM -> customUri(context)?.toString()?.take(60) ?: "Custom folder (not set)"
            else -> "/Download (phone storage)"
        }
    }
}
