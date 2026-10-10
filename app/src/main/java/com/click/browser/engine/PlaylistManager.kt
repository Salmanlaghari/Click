package com.click.browser.engine

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Playlist — "save media for later", Play-policy-safe.
 *
 * Honest scope (do NOT widen without a policy review):
 * - ONLY direct media file URLs the user explicitly adds (mp3/mp4/m4a/…).
 * - This is NOT a downloader and NEVER a YouTube downloader: YouTube URLs
 *   are refused with an honest "not supported" message, and no UI string
 *   may mention downloading or saving YouTube videos.
 * - Files are STREAMED, never downloaded to storage.
 *
 * Storage follows the app's per-mode isolation: the playlist lives in the
 * current mode's [profileDataStore], like bookmarks/history.
 */
data class PlaylistItem(
    val id: String,
    val title: String,
    val url: String,
    val addedAt: Long
)

/** Outcome of [PlaylistManager.addItem], so the UI can show honest messages. */
sealed interface PlaylistAddResult {
    data object Added : PlaylistAddResult
    data object Duplicate : PlaylistAddResult
    data object InvalidUrl : PlaylistAddResult
    /** YouTube (and other streaming-service) URLs are refused by policy. */
    data object UnsupportedService : PlaylistAddResult
}

class PlaylistManager(private val context: Context) {

    companion object {
        private val PLAYLIST_KEY = stringPreferencesKey("playlist_items")

        /** Direct media extensions we treat as playable stream URLs. */
        private val MEDIA_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac",
            "mp4", "m4v", "webm", "mkv", "mov", "3gp"
        )

        /** Hosts whose URLs are streaming services — never presented as savable. */
        private val UNSUPPORTED_HOSTS = listOf(
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
            "youtu.be", "youtube-nocookie.com", "www.youtube-nocookie.com"
        )

        /** True for http(s) URLs pointing at a streaming service we must not touch. */
        fun isUnsupportedServiceUrl(rawUrl: String): Boolean {
            val host = try {
                java.net.URI(rawUrl.trim()).host?.lowercase() ?: return false
            } catch (_: Exception) {
                return false
            }
            return UNSUPPORTED_HOSTS.any { host == it || host.endsWith(".$it") }
        }

        /** True when the URL looks like a direct media file (by extension). */
        fun looksLikeDirectMedia(rawUrl: String): Boolean {
            val path = try {
                java.net.URI(rawUrl.trim()).path?.lowercase() ?: return false
            } catch (_: Exception) {
                return false
            }
            val ext = path.substringAfterLast('.', "").substringBefore('?')
            return ext in MEDIA_EXTENSIONS
        }

        fun isValidHttpUrl(rawUrl: String): Boolean {
            val t = rawUrl.trim()
            return (t.startsWith("http://") || t.startsWith("https://")) && t.length > 12
        }

        /** Human title fallback: file name from the URL path. */
        fun titleFromUrl(rawUrl: String): String {
            return try {
                val path = java.net.URI(rawUrl.trim()).path ?: ""
                val name = path.substringAfterLast('/').substringBefore('?')
                name.ifBlank { rawUrl.trim() }
            } catch (_: Exception) {
                rawUrl.trim()
            }
        }
    }

    val itemsFlow: Flow<List<PlaylistItem>> = context.profileDataStore.data.map { prefs ->
        readList(prefs[PLAYLIST_KEY])
    }

    suspend fun addItem(rawUrl: String, title: String? = null): PlaylistAddResult {
        val url = rawUrl.trim()
        if (!isValidHttpUrl(url)) return PlaylistAddResult.InvalidUrl
        if (isUnsupportedServiceUrl(url)) return PlaylistAddResult.UnsupportedService
        var result: PlaylistAddResult = PlaylistAddResult.Added
        context.profileDataStore.edit { prefs ->
            val list = readList(prefs[PLAYLIST_KEY]).toMutableList()
            if (list.any { it.url == url }) {
                result = PlaylistAddResult.Duplicate
                return@edit
            }
            list.add(
                0,
                PlaylistItem(
                    id = UUID.randomUUID().toString(),
                    title = title?.trim().takeIf { !it.isNullOrBlank() } ?: titleFromUrl(url),
                    url = url,
                    addedAt = System.currentTimeMillis()
                )
            )
            prefs[PLAYLIST_KEY] = writeList(list)
        }
        return result
    }

    suspend fun removeItem(id: String) {
        context.profileDataStore.edit { prefs ->
            val list = readList(prefs[PLAYLIST_KEY]).filter { it.id != id }
            prefs[PLAYLIST_KEY] = writeList(list)
        }
    }

    suspend fun clearAll() {
        context.profileDataStore.edit { prefs ->
            prefs[PLAYLIST_KEY] = "[]"
        }
    }

    private fun readList(jsonStr: String?): List<PlaylistItem> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                PlaylistItem(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    url = o.optString("url"),
                    addedAt = o.optLong("addedAt")
                ).takeIf { it.id.isNotBlank() && it.url.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeList(list: List<PlaylistItem>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("url", it.url)
                    .put("addedAt", it.addedAt)
            )
        }
        return arr.toString()
    }
}
