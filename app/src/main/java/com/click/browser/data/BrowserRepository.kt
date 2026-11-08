package com.click.browser.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.SiteSettings
import com.click.browser.engine.profileDataStore
import com.click.browser.engine.profileDataStoreFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(val title: String, val url: String)
data class HistoryItem(val title: String, val url: String, val timestamp: Long)
data class DownloadItem(val fileName: String, val url: String, val path: String, val timestamp: Long)

class BrowserRepository(private val context: Context) {

    companion object {
        private val BOOKMARKS_KEY = stringPreferencesKey("bookmarks")
        private val HISTORY_KEY = stringPreferencesKey("history")
        private val DOWNLOADS_KEY = stringPreferencesKey("downloads")
        private val DESKTOP_HOSTS_KEY = stringPreferencesKey("desktop_hosts")
        private val SITE_SETTINGS_KEY = stringPreferencesKey("site_settings")
    }

    // --- Bookmarks ---
    val bookmarksFlow: Flow<List<Bookmark>> = context.profileDataStore.data.map { preferences ->
        val jsonStr = preferences[BOOKMARKS_KEY] ?: "[]"
        val list = mutableListOf<Bookmark>()
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(Bookmark(obj.getString("title"), obj.getString("url")))
        }
        list
    }

    suspend fun addBookmark(bookmark: Bookmark) {
        context.profileDataStore.edit { preferences ->
            val jsonStr = preferences[BOOKMARKS_KEY] ?: "[]"
            val array = JSONArray(jsonStr)

            // Avoid duplicate URLs
            var duplicate = false
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.getString("url") == bookmark.url) {
                    duplicate = true
                    break
                }
            }
            if (!duplicate) {
                val newObj = JSONObject()
                newObj.put("title", bookmark.title)
                newObj.put("url", bookmark.url)
                array.put(newObj)
                preferences[BOOKMARKS_KEY] = array.toString()
            }
        }
    }

    /** Atomically edits one bookmark in the current profile without changing its list position. */
    internal suspend fun updateBookmark(existingUrl: String, replacement: Bookmark): BookmarkEditResult {
        var result = BookmarkEditResult.NOT_FOUND
        context.profileDataStore.edit { preferences ->
            val source = JSONArray(preferences[BOOKMARKS_KEY] ?: "[]")
            val current = List(source.length()) { index ->
                val obj = source.getJSONObject(index)
                Bookmark(obj.getString("title"), obj.getString("url"))
            }
            val decision = applyBookmarkEdit(current, existingUrl, replacement)
            result = decision.result
            if (result == BookmarkEditResult.UPDATED) {
                val updated = JSONArray()
                decision.bookmarks.forEach { bookmark ->
                    updated.put(JSONObject().apply {
                        put("title", bookmark.title)
                        put("url", bookmark.url)
                    })
                }
                preferences[BOOKMARKS_KEY] = updated.toString()
            }
        }
        return result
    }

    suspend fun deleteBookmark(url: String) {
        context.profileDataStore.edit { preferences ->
            val jsonStr = preferences[BOOKMARKS_KEY] ?: "[]"
            val array = JSONArray(jsonStr)
            val newArray = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.getString("url") != url) {
                    newArray.put(obj)
                }
            }
            preferences[BOOKMARKS_KEY] = newArray.toString()
        }
    }

    data class ImportResult(val added: Int, val skippedExisting: Int)

    /**
     * Bulk-imports bookmarks in a single atomic DataStore edit. Bookmarks whose
     * URL already exists in the user's bookmarks are skipped gracefully.
     */
    suspend fun importBookmarks(newBookmarks: List<Bookmark>): ImportResult {
        var added = 0
        var skippedExisting = 0
        context.profileDataStore.edit { preferences ->
            val jsonStr = preferences[BOOKMARKS_KEY] ?: "[]"
            val array = JSONArray(jsonStr)

            val existingUrls = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                existingUrls.add(array.getJSONObject(i).optString("url"))
            }

            for (bookmark in newBookmarks) {
                if (!existingUrls.add(bookmark.url)) {
                    skippedExisting++
                    continue
                }
                val newObj = JSONObject()
                newObj.put("title", bookmark.title)
                newObj.put("url", bookmark.url)
                array.put(newObj)
                added++
            }
            preferences[BOOKMARKS_KEY] = array.toString()
        }
        return ImportResult(added = added, skippedExisting = skippedExisting)
    }

    // --- History ---
    val historyFlow: Flow<List<HistoryItem>> = context.profileDataStore.data.map { preferences ->
        val jsonStr = preferences[HISTORY_KEY] ?: "[]"
        val list = mutableListOf<HistoryItem>()
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(HistoryItem(obj.getString("title"), obj.getString("url"), obj.getLong("timestamp")))
        }
        list.sortedByDescending { it.timestamp }
    }

    suspend fun addHistoryItem(item: HistoryItem) {
        context.profileDataStore.edit { preferences ->
            val jsonStr = preferences[HISTORY_KEY] ?: "[]"
            val array = JSONArray(jsonStr)

            // Limit history size to 100
            if (array.length() >= 100) {
                val newArray = JSONArray()
                for (i in 1 until array.length()) {
                    newArray.put(array.get(i))
                }
                newArray.put(JSONObject().apply {
                    put("title", item.title)
                    put("url", item.url)
                    put("timestamp", item.timestamp)
                })
                preferences[HISTORY_KEY] = newArray.toString()
            } else {
                val newObj = JSONObject().apply {
                    put("title", item.title)
                    put("url", item.url)
                    put("timestamp", item.timestamp)
                }
                array.put(newObj)
                preferences[HISTORY_KEY] = array.toString()
            }
        }
    }

    suspend fun clearHistory() {
        context.profileDataStore.edit { preferences ->
            preferences[HISTORY_KEY] = "[]"
        }
    }

    /**
     * Clears only visits whose URL has [host]'s exact normalized hostname from the current
     * profile store. Simple, Developer, and Power share one history store; Advanced has a
     * separate store selected by [context.profileDataStore]. Unparseable URLs are preserved.
     */
    suspend fun clearHistoryForHost(host: String): Int {
        val normalizedHost = requireNotNull(HistoryHostFilter.normalizeHost(host)) {
            "Invalid history host"
        }
        var removedCount = 0
        context.profileDataStore.edit { preferences ->
            val source = JSONArray(preferences[HISTORY_KEY] ?: "[]")
            val entries = List(source.length()) { index -> source.getJSONObject(index) }
            val retained = HistoryHostFilter.retainOtherHosts(
                entries = entries,
                normalizedHost = normalizedHost,
                urlOf = { it.optString("url") }
            )
            removedCount = entries.size - retained.size
            if (removedCount > 0) {
                val updated = JSONArray()
                retained.forEach { updated.put(it) }
                preferences[HISTORY_KEY] = updated.toString()
            }
        }
        return removedCount
    }

    /**
     * Clears the history of ANY mode's isolated profile store — no mode
     * switch or app restart needed (history lives in DataStore, not the
     * WebView data directory). Respects 4-mode isolation: only the chosen
     * mode's history is touched.
     */
    suspend fun clearHistoryFor(mode: BrowserMode) {
        context.profileDataStoreFor(mode).edit { preferences ->
            preferences[HISTORY_KEY] = "[]"
        }
    }

    // --- Downloads ---
    val downloadsFlow: Flow<List<DownloadItem>> = context.profileDataStore.data.map { preferences ->
        val jsonStr = preferences[DOWNLOADS_KEY] ?: "[]"
        val list = mutableListOf<DownloadItem>()
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(DownloadItem(obj.getString("fileName"), obj.getString("url"), obj.getString("path"), obj.getLong("timestamp")))
        }
        list.sortedByDescending { it.timestamp }
    }

    suspend fun addDownloadItem(item: DownloadItem) {
        context.profileDataStore.edit { preferences ->
            val jsonStr = preferences[DOWNLOADS_KEY] ?: "[]"
            val array = JSONArray(jsonStr)
            val newObj = JSONObject().apply {
                put("fileName", item.fileName)
                put("url", item.url)
                put("path", item.path)
                put("timestamp", item.timestamp)
            }
            array.put(newObj)
            preferences[DOWNLOADS_KEY] = array.toString()
        }
    }

    // --- Per-site wide-layout preference (host -> explicit true or false) ---
    //
    // DELIBERATE: stored in the per-profile DataStore, so each engine keeps
    // its own layout choices. Click Advance is a fresh isolated space —
    // Prince's requirement is that nothing from Simple / Developer / Power
    // carries over, and per-site layout preferences are browsing-profile
    // state (like bookmarks and history), not device configuration. The
    // device-level settings that intentionally stay shared are listed in
    // ProfileData.kt's KDoc (mode selector, AI API key, V9 Shield/VPN toggle,
    // DoH provider).
    val desktopOverridesFlow: Flow<Map<String, Boolean>> = context.profileDataStore.data.map { preferences ->
        val jsonStr = preferences[DESKTOP_HOSTS_KEY] ?: "{}"
        val obj = JSONObject(jsonStr)
        val overrides = mutableMapOf<String, Boolean>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val host = keys.next()
            overrides[host] = obj.optBoolean(host, false)
        }
        overrides
    }

    /** Compatibility view for screens that only display opted-in wide layouts. */
    val desktopHostsFlow: Flow<Set<String>> = desktopOverridesFlow.map { overrides ->
        overrides.filterValues { it }.keys
    }

    suspend fun setDesktopHost(host: String, desktop: Boolean) {
        context.profileDataStore.edit { preferences ->
            val jsonStr = preferences[DESKTOP_HOSTS_KEY] ?: "{}"
            val obj = JSONObject(jsonStr)
            if (desktop) obj.put(host, true) else obj.remove(host)
            preferences[DESKTOP_HOSTS_KEY] = obj.toString()
        }
    }

    suspend fun setDesktopOverride(host: String, desktop: Boolean) {
        context.profileDataStore.edit { preferences ->
            val jsonStr = preferences[DESKTOP_HOSTS_KEY] ?: "{}"
            val obj = JSONObject(jsonStr)
            // False is retained as an explicit per-site mobile-layout override;
            // the legacy setDesktopHost(host, false) remains a reset operation.
            obj.put(host, desktop)
            preferences[DESKTOP_HOSTS_KEY] = obj.toString()
        }
    }

    suspend fun clearDesktopHost(host: String) {
        context.profileDataStore.edit { preferences ->
            val jsonStr = preferences[DESKTOP_HOSTS_KEY] ?: "{}"
            val obj = JSONObject(jsonStr)
            obj.remove(host)
            preferences[DESKTOP_HOSTS_KEY] = obj.toString()
        }
    }

    // --- Per-site settings (host -> SiteSettings JSON) ---
    //
    // Stored in the per-profile DataStore, same as desktop_hosts: each engine
    // mode keeps its own per-site choices (per-mode isolation). Empty
    // settings are pruned so the map never grows with no-op entries.
    val siteSettingsFlow: Flow<Map<String, SiteSettings>> =
        context.profileDataStore.data.map { preferences ->
            val jsonStr = preferences[SITE_SETTINGS_KEY] ?: "{}"
            SiteSettings.mapFromJson(jsonStr)
        }

    suspend fun setSiteSettings(host: String, settings: SiteSettings) {
        context.profileDataStore.edit { preferences ->
            val jsonStr = preferences[SITE_SETTINGS_KEY] ?: "{}"
            val map = SiteSettings.mapFromJson(jsonStr).toMutableMap()
            if (settings.isEmpty()) map.remove(host) else map[host] = settings
            preferences[SITE_SETTINGS_KEY] = SiteSettings.mapToJson(map)
        }
    }

    suspend fun clearSiteSettings(host: String) {
        context.profileDataStore.edit { preferences ->
            val jsonStr = preferences[SITE_SETTINGS_KEY] ?: "{}"
            val map = SiteSettings.mapFromJson(jsonStr).toMutableMap()
            map.remove(host)
            preferences[SITE_SETTINGS_KEY] = SiteSettings.mapToJson(map)
        }
    }

}
