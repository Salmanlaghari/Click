package com.click.browser.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.SiteSettings
import com.click.browser.engine.canonicalWebsiteMicrophoneOrigin
import com.click.browser.engine.profileDataStore
import com.click.browser.engine.profileDataStoreFor
import com.click.browser.engine.websiteMicrophoneHostKey
import com.click.browser.engine.websiteMicrophoneOriginHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Bookmark(val title: String, val url: String, val folder: String? = null)
data class HistoryItem(val title: String, val url: String, val timestamp: Long)
data class DownloadItem(
    val fileName: String,
    val url: String,
    val path: String,
    val timestamp: Long,
    val transferId: String? = null
)

class BrowserRepository(private val context: Context) {

    companion object {
        private const val TAG = "BrowserRepository"
        private val BOOKMARKS_KEY = stringPreferencesKey("bookmarks")
        private val HISTORY_KEY = stringPreferencesKey("history")
        private val DOWNLOADS_KEY = stringPreferencesKey("downloads")
        private val CUSTOM_DOWNLOADS_KEY = stringPreferencesKey("custom_downloads_v1")
        private val DESKTOP_HOSTS_KEY = stringPreferencesKey("desktop_hosts")
        private val SITE_SETTINGS_KEY = stringPreferencesKey("site_settings")
        private val WEBSITE_MICROPHONE_PERMISSIONS_KEY =
            stringPreferencesKey("website_microphone_permissions")
        private const val MAX_CUSTOM_DOWNLOAD_RECORDS = 100
    }

    // --- Bookmarks ---
    val bookmarksFlow: Flow<List<Bookmark>> = context.profileDataStore.data.map { preferences ->
        val jsonStr = preferences[BOOKMARKS_KEY] ?: "[]"
        val list = mutableListOf<Bookmark>()
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                Bookmark(
                    obj.getString("title"),
                    obj.getString("url"),
                    obj.optString("folder").takeIf { it.isNotBlank() }
                )
            )
        }
        list
    }

    suspend fun addBookmark(bookmark: Bookmark): Boolean {
        val normalizedBookmark = BookmarkAddPolicy.prepare(bookmark.title, bookmark.url, bookmark.folder) ?: return false
        var added = false
        context.profileDataStore.edit { preferences ->
            val jsonStr = preferences[BOOKMARKS_KEY] ?: "[]"
            val array = JSONArray(jsonStr)

            val existingUrls = List(array.length()) { index ->
                array.getJSONObject(index).getString("url")
            }
            if (BookmarkAddPolicy.shouldAdd(existingUrls, normalizedBookmark)) {
                val newObj = JSONObject().apply {
                    put("title", normalizedBookmark.title)
                    put("url", normalizedBookmark.url)
                    normalizedBookmark.folder?.let { put("folder", it) }
                }
                array.put(newObj)
                preferences[BOOKMARKS_KEY] = array.toString()
                added = true
            }
        }
        return added
    }

    /** Atomically edits one bookmark in the current profile without changing its list position. */
    internal suspend fun updateBookmark(existingUrl: String, replacement: Bookmark): BookmarkEditResult {
        var result = BookmarkEditResult.NOT_FOUND
        context.profileDataStore.edit { preferences ->
            val source = JSONArray(preferences[BOOKMARKS_KEY] ?: "[]")
            val current = List(source.length()) { index ->
                val obj = source.getJSONObject(index)
                Bookmark(
                    obj.getString("title"),
                    obj.getString("url"),
                    obj.optString("folder").takeIf { it.isNotBlank() }
                )
            }
            val decision = applyBookmarkEdit(current, existingUrl, replacement)
            result = decision.result
            if (result == BookmarkEditResult.UPDATED) {
                val updated = JSONArray()
                decision.bookmarks.forEach { bookmark ->
                    updated.put(JSONObject().apply {
                        put("title", bookmark.title)
                        put("url", bookmark.url)
                        bookmark.folder?.let { put("folder", it) }
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

    /** Assigns or clears a bookmark folder atomically in the current profile. */
    internal suspend fun moveBookmarkToFolder(url: String, folderName: String?): BookmarkOrganizationResult {
        var result = BookmarkOrganizationResult.NOT_FOUND
        context.profileDataStore.edit { preferences ->
            val source = JSONArray(preferences[BOOKMARKS_KEY] ?: "[]")
            val current = List(source.length()) { index ->
                val obj = source.getJSONObject(index)
                Bookmark(
                    obj.getString("title"),
                    obj.getString("url"),
                    obj.optString("folder").takeIf { it.isNotBlank() }
                )
            }
            val decision = BookmarkOrganizationPolicy.move(current, url, folderName)
            result = decision.result
            if (result == BookmarkOrganizationResult.UPDATED) {
                val updated = JSONArray()
                decision.bookmarks.forEach { bookmark ->
                    updated.put(JSONObject().apply {
                        put("title", bookmark.title)
                        put("url", bookmark.url)
                        bookmark.folder?.let { put("folder", it) }
                    })
                }
                preferences[BOOKMARKS_KEY] = updated.toString()
            }
        }
        return result
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
                val normalizedBookmark = BookmarkOrganizationPolicy.normalizeImportedBookmark(bookmark)
                if (!existingUrls.add(normalizedBookmark.url)) {
                    skippedExisting++
                    continue
                }
                val newObj = JSONObject()
                newObj.put("title", normalizedBookmark.title)
                newObj.put("url", normalizedBookmark.url)
                normalizedBookmark.folder?.let { newObj.put("folder", it) }
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

    suspend fun addHistoryItem(item: HistoryItem, isIncognitoTab: Boolean) {
        if (!HistoryRecordingPolicy.shouldRecord(isIncognitoTab, item.url)) return

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
     * Clears the history store selected for [mode] without a mode switch or app restart.
     * Simple, Developer, and Power share one store; Advance uses its isolated store.
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
            list.add(
                DownloadItem(
                    obj.getString("fileName"),
                    obj.getString("url"),
                    obj.getString("path"),
                    obj.getLong("timestamp"),
                    obj.optString("transferId").takeUnless { it.isBlank() || it == "null" }
                )
            )
        }
        list.sortedByDescending { it.timestamp }
    }

    /** Downloads saved in the requested profile; Simple/Developer/Power share their store. */
    fun downloadsFlowFor(mode: BrowserMode): Flow<List<DownloadItem>> =
        context.profileDataStoreFor(mode).data.map { preferences ->
            parseDownloadItems(preferences[DOWNLOADS_KEY])
        }

    /** Transfer lifecycle is kept beside saved files in the originating profile's store. */
    fun customDownloadsFlowFor(mode: BrowserMode): Flow<List<CustomDownloadItem>> =
        context.profileDataStoreFor(mode).data.map { preferences ->
            parseCustomDownloadItems(preferences[CUSTOM_DOWNLOADS_KEY])
        }

    suspend fun getCustomDownloadFor(mode: BrowserMode, id: String): CustomDownloadItem? =
        customDownloadsFlowFor(mode).first().firstOrNull { it.id == id }

    /** Inserts a new queued row, or accepts only the same still-queued retry attempt. */
    suspend fun ensureCustomDownloadQueuedFor(item: CustomDownloadItem): CustomDownloadItem? {
        if (item.state != CustomDownloadState.QUEUED || item.attemptId.isNullOrBlank()) return null
        var result: CustomDownloadItem? = null
        context.profileDataStoreFor(item.mode).edit { preferences ->
            val items = parseCustomDownloadItems(preferences[CUSTOM_DOWNLOADS_KEY])
            val existing = items.firstOrNull { it.id == item.id }
            if (existing != null) {
                if (existing.state == CustomDownloadState.QUEUED && existing.attemptId == item.attemptId) {
                    result = existing
                }
                return@edit
            }
            val queued = item.copy(state = CustomDownloadState.QUEUED, updatedAt = System.currentTimeMillis())
            preferences[CUSTOM_DOWNLOADS_KEY] = encodeCustomDownloadItems(
                trimCustomDownloadRecords(items.plus(queued), MAX_CUSTOM_DOWNLOAD_RECORDS)
            )
            result = queued
        }
        return result
    }

    /** Assigns a backend only while the same attempt is still queued. */
    suspend fun assignCustomDownloadBackendFor(
        mode: BrowserMode,
        id: String,
        attemptId: String,
        backend: CustomDownloadBackend,
        backendId: String
    ): CustomDownloadItem? = mutateCustomDownloadFor(mode, id) { current ->
        if (current.state != CustomDownloadState.QUEUED || current.attemptId != attemptId) return@mutateCustomDownloadFor null
        current.copy(
            backend = backend,
            backendId = backendId,
            updatedAt = System.currentTimeMillis()
        )
    }

    /** Atomically claims a queued attempt so stale or duplicate backends cannot start it. */
    suspend fun claimCustomDownloadFor(
        mode: BrowserMode,
        id: String,
        attemptId: String
    ): CustomDownloadItem? = mutateCustomDownloadFor(mode, id) { current ->
        if (current.state != CustomDownloadState.QUEUED || current.attemptId != attemptId) return@mutateCustomDownloadFor null
        current.copy(
            state = CustomDownloadState.RUNNING,
            updatedAt = System.currentTimeMillis(),
            errorMessage = null
        )
    }

    /** Updates progress only for the currently running attempt. */
    suspend fun updateCustomDownloadProgressFor(
        mode: BrowserMode,
        id: String,
        attemptId: String,
        downloaded: Long,
        total: Long
    ): Boolean = mutateCustomDownloadFor(mode, id) { current ->
        if (current.state != CustomDownloadState.RUNNING || current.attemptId != attemptId) return@mutateCustomDownloadFor null
        current.copy(
            bytesDownloaded = downloaded.coerceAtLeast(0L),
            totalBytes = total,
            updatedAt = System.currentTimeMillis()
        )
    } != null

    /** Records a backend stop only while it still owns the running attempt. */
    suspend fun stopCustomDownloadAttemptFor(
        mode: BrowserMode,
        id: String,
        attemptId: String,
        userCancelled: Boolean
    ): Boolean = mutateCustomDownloadFor(mode, id) { current ->
        if (current.state != CustomDownloadState.RUNNING || current.attemptId != attemptId) return@mutateCustomDownloadFor null
        current.copy(
            state = if (userCancelled) CustomDownloadState.CANCELED else CustomDownloadState.QUEUED,
            updatedAt = System.currentTimeMillis(),
            errorMessage = if (userCancelled) "Canceled by you." else null,
            encryptedCookieHeader = if (userCancelled) null else current.encryptedCookieHeader
        )
    } != null

    suspend fun failCustomDownloadAttemptFor(
        mode: BrowserMode,
        id: String,
        attemptId: String,
        message: String
    ): Boolean = mutateCustomDownloadFor(mode, id) { current ->
        if ((current.state != CustomDownloadState.RUNNING && current.state != CustomDownloadState.QUEUED) ||
            current.attemptId != attemptId
        ) return@mutateCustomDownloadFor null
        current.copy(
            state = CustomDownloadState.FAILED,
            updatedAt = System.currentTimeMillis(),
            errorMessage = message,
            encryptedCookieHeader = null
        )
    } != null

    /** Fails an orphan only if its state, attempt, backend, and timestamp still match the scanned snapshot. */
    suspend fun failOrphanedCustomDownloadFor(
        mode: BrowserMode,
        snapshot: CustomDownloadItem,
        message: String
    ): Boolean = mutateCustomDownloadFor(mode, snapshot.id) { current ->
        if ((current.state != CustomDownloadState.RUNNING && current.state != CustomDownloadState.QUEUED) ||
            current.state != snapshot.state || current.attemptId != snapshot.attemptId ||
            current.backend != snapshot.backend || current.backendId != snapshot.backendId ||
            current.updatedAt != snapshot.updatedAt
        ) return@mutateCustomDownloadFor null
        current.copy(
            state = CustomDownloadState.FAILED,
            updatedAt = System.currentTimeMillis(),
            errorMessage = message,
            encryptedCookieHeader = null
        )
    } != null

    suspend fun cancelCustomDownloadFor(
        mode: BrowserMode,
        id: String,
        attemptId: String?
    ): CustomDownloadItem? =
        mutateCustomDownloadFor(mode, id) { current ->
            if (!current.canCancel || current.attemptId != attemptId) return@mutateCustomDownloadFor null
            current.copy(
                state = CustomDownloadState.CANCELED,
                updatedAt = System.currentTimeMillis(),
                errorMessage = "Canceled by you.",
                encryptedCookieHeader = null
            )
        }

    /** Creates exactly one fresh retry attempt from a failed/canceled record. */
    suspend fun prepareCustomDownloadRetryFor(
        mode: BrowserMode,
        id: String,
        expectedAttemptId: String?,
        encryptedCookieHeader: String?
    ): CustomDownloadItem? = mutateCustomDownloadFor(mode, id) { current ->
        if (!current.canRetry || current.attemptId != expectedAttemptId) return@mutateCustomDownloadFor null
        current.copy(
            state = CustomDownloadState.QUEUED,
            bytesDownloaded = 0L,
            totalBytes = -1L,
            updatedAt = System.currentTimeMillis(),
            backend = null,
            backendId = null,
            errorMessage = null,
            encryptedCookieHeader = if (current.sourceFilePath == null) encryptedCookieHeader else null,
            attemptId = UUID.randomUUID().toString()
        )
    }

    suspend fun failToScheduleCustomDownloadFor(mode: BrowserMode, item: CustomDownloadItem, message: String) {
        mutateCustomDownloadFor(mode, item.id) { current ->
            if (current.state != CustomDownloadState.QUEUED || current.attemptId != item.attemptId) return@mutateCustomDownloadFor null
            current.copy(
                state = CustomDownloadState.FAILED,
                updatedAt = System.currentTimeMillis(),
                errorMessage = message,
                encryptedCookieHeader = null
            )
        }
    }

    /** Atomically switches the lifecycle row to COMPLETED and inserts its saved-file row. */
    suspend fun completeCustomDownloadFor(
        mode: BrowserMode,
        id: String,
        attemptId: String,
        item: DownloadItem,
        downloaded: Long,
        total: Long
    ): Boolean {
        var completed = false
        context.profileDataStoreFor(mode).edit { preferences ->
            val customItems = parseCustomDownloadItems(preferences[CUSTOM_DOWNLOADS_KEY])
            val current = customItems.firstOrNull { it.id == id }
            if (current == null || current.state != CustomDownloadState.RUNNING || current.attemptId != attemptId) return@edit
            val updated = current.copy(
                fileName = item.fileName,
                state = CustomDownloadState.COMPLETED,
                bytesDownloaded = downloaded.coerceAtLeast(0L),
                totalBytes = total,
                updatedAt = item.timestamp,
                errorMessage = null,
                encryptedCookieHeader = null,
                sourceFilePath = null
            )
            preferences[CUSTOM_DOWNLOADS_KEY] = encodeCustomDownloadItems(
                trimCustomDownloadRecords(
                    customItems.filterNot { it.id == id }.plus(updated),
                    MAX_CUSTOM_DOWNLOAD_RECORDS
                )
            )
            val saved = parseDownloadItems(preferences[DOWNLOADS_KEY]).filterNot { it.transferId == id }
            preferences[DOWNLOADS_KEY] = encodeDownloadItems(saved + item.copy(transferId = id))
            completed = true
        }
        return completed
    }

    private suspend fun mutateCustomDownloadFor(
        mode: BrowserMode,
        id: String,
        transform: (CustomDownloadItem) -> CustomDownloadItem?
    ): CustomDownloadItem? {
        var result: CustomDownloadItem? = null
        context.profileDataStoreFor(mode).edit { preferences ->
            val items = parseCustomDownloadItems(preferences[CUSTOM_DOWNLOADS_KEY])
            val current = items.firstOrNull { it.id == id } ?: return@edit
            val updated = transform(current) ?: return@edit
            preferences[CUSTOM_DOWNLOADS_KEY] = encodeCustomDownloadItems(
                trimCustomDownloadRecords(
                    items.filterNot { it.id == id }.plus(updated),
                    MAX_CUSTOM_DOWNLOAD_RECORDS
                )
            )
            result = updated
        }
        return result
    }

    suspend fun addDownloadItemFor(mode: BrowserMode, item: DownloadItem) {
        context.profileDataStoreFor(mode).edit { preferences ->
            val array = JSONArray(preferences[DOWNLOADS_KEY] ?: "[]")
            array.put(JSONObject().apply {
                put("fileName", item.fileName)
                put("url", item.url)
                put("path", item.path)
                put("timestamp", item.timestamp)
                item.transferId?.let { put("transferId", it) }
            })
            preferences[DOWNLOADS_KEY] = array.toString()
        }
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
                item.transferId?.let { put("transferId", it) }
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

    /** Exact-origin microphone choices live in this profile's DataStore, never in private tabs. */
    val websiteMicrophonePermissionsFlow: Flow<Map<String, Boolean>> =
        context.profileDataStore.data.map { preferences ->
            websiteMicrophonePermissionsFromJson(preferences[WEBSITE_MICROPHONE_PERMISSIONS_KEY])
        }

    suspend fun setWebsiteMicrophonePermission(origin: String, allowed: Boolean) {
        val canonicalOrigin = canonicalWebsiteMicrophoneOrigin(origin) ?: run {
            Log.w(TAG, "Ignoring invalid website microphone origin in set request")
            return
        }
        context.profileDataStore.edit { preferences ->
            val permissions = websiteMicrophonePermissionsFromJson(
                preferences[WEBSITE_MICROPHONE_PERMISSIONS_KEY]
            ).toMutableMap()
            permissions[canonicalOrigin] = allowed
            preferences[WEBSITE_MICROPHONE_PERMISSIONS_KEY] =
                websiteMicrophonePermissionsToJson(permissions)
        }
    }

    suspend fun clearWebsiteMicrophonePermission(origin: String) {
        val canonicalOrigin = canonicalWebsiteMicrophoneOrigin(origin) ?: run {
            Log.w(TAG, "Ignoring invalid website microphone origin in clear request")
            return
        }
        context.profileDataStore.edit { preferences ->
            val permissions = websiteMicrophonePermissionsFromJson(
                preferences[WEBSITE_MICROPHONE_PERMISSIONS_KEY]
            ).toMutableMap()
            permissions.remove(canonicalOrigin)
            preferences[WEBSITE_MICROPHONE_PERMISSIONS_KEY] =
                websiteMicrophonePermissionsToJson(permissions)
        }
    }

    suspend fun clearWebsiteMicrophonePermissionsForHost(host: String) {
        val normalizedHost = websiteMicrophoneHostKey(host) ?: run {
            Log.w(TAG, "Ignoring invalid website microphone host in clear request")
            return
        }
        clearWebsiteMicrophonePermissionsFromStore(context.profileDataStore, normalizedHost)
    }

    internal suspend fun clearWebsiteMicrophonePermissionsForHostAcrossProfiles(host: String) {
        val normalizedHost = websiteMicrophoneHostKey(host) ?: run {
            Log.w(TAG, "Ignoring invalid website microphone host in cross-profile clear request")
            return
        }
        clearWebsiteMicrophonePermissionsFromStore(
            context.profileDataStoreFor(BrowserMode.SIMPLE),
            normalizedHost
        )
        clearWebsiteMicrophonePermissionsFromStore(
            context.profileDataStoreFor(BrowserMode.ADVANCED),
            normalizedHost
        )
    }

    private suspend fun clearWebsiteMicrophonePermissionsFromStore(
        store: DataStore<Preferences>,
        normalizedHost: String
    ) {
        store.edit { preferences ->
            val permissions = websiteMicrophonePermissionsFromJson(
                preferences[WEBSITE_MICROPHONE_PERMISSIONS_KEY]
            ).toMutableMap()
            permissions.keys
                .filter { websiteMicrophoneOriginHost(it) == normalizedHost }
                .forEach(permissions::remove)
            preferences[WEBSITE_MICROPHONE_PERMISSIONS_KEY] =
                websiteMicrophonePermissionsToJson(permissions)
        }
    }

    suspend fun clearAllWebsiteMicrophonePermissions() {
        context.profileDataStore.edit { preferences ->
            preferences[WEBSITE_MICROPHONE_PERMISSIONS_KEY] = "{}"
        }
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

            val microphonePermissions = websiteMicrophonePermissionsFromJson(
                preferences[WEBSITE_MICROPHONE_PERMISSIONS_KEY]
            ).toMutableMap()
            val normalizedHost = host.trim().lowercase(Locale.ROOT).trimEnd('.')
            microphonePermissions.keys
                .filter { websiteMicrophoneOriginHost(it) == normalizedHost }
                .forEach(microphonePermissions::remove)
            preferences[WEBSITE_MICROPHONE_PERMISSIONS_KEY] =
                websiteMicrophonePermissionsToJson(microphonePermissions)
        }
    }

}

private fun websiteMicrophonePermissionsFromJson(json: String?): Map<String, Boolean> {
    val root = runCatching { JSONObject(json ?: "{}") }.getOrElse { JSONObject() }
    val permissions = linkedMapOf<String, Boolean>()
    val keys = root.keys()
    while (keys.hasNext()) {
        val origin = keys.next()
        val allowed = root.opt(origin) as? Boolean ?: continue
        if (canonicalWebsiteMicrophoneOrigin(origin) == origin) {
            permissions[origin] = allowed
        }
    }
    return permissions
}

private fun websiteMicrophonePermissionsToJson(permissions: Map<String, Boolean>): String =
    JSONObject().apply {
        permissions.toSortedMap().forEach { (origin, allowed) -> put(origin, allowed) }
    }.toString()

private fun parseDownloadItems(json: String?): List<DownloadItem> = try {
    val array = JSONArray(json ?: "[]")
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val fileName = item.optString("fileName").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        DownloadItem(
            fileName = fileName,
            url = item.optString("url"),
            path = item.optString("path"),
            timestamp = item.optLong("timestamp"),
            transferId = item.optString("transferId").takeUnless { it.isBlank() || it == "null" }
        )
    }.sortedByDescending { it.timestamp }
} catch (_: Exception) {
    emptyList()
}

private fun encodeDownloadItems(items: List<DownloadItem>): String {
    val array = JSONArray()
    items.forEach { item ->
        array.put(JSONObject().apply {
            put("fileName", item.fileName)
            put("url", item.url)
            put("path", item.path)
            put("timestamp", item.timestamp)
            item.transferId?.let { put("transferId", it) }
        })
    }
    return array.toString()
}

private fun parseCustomDownloadItems(json: String?): List<CustomDownloadItem> = try {
    val array = JSONArray(json ?: "[]")
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val mode = runCatching { BrowserMode.valueOf(item.optString("mode")) }.getOrNull()
            ?: return@mapNotNull null
        val state = runCatching { CustomDownloadState.valueOf(item.optString("state")) }.getOrNull()
            ?: return@mapNotNull null
        val url = item.optString("url")
        val fileName = item.optString("fileName").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val treeUri = item.optString("destinationTreeUri").takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        CustomDownloadItem(
            id = id,
            url = url,
            fileName = fileName,
            mimeType = item.optString("mimeType").takeUnless { it.isBlank() || it == "null" },
            destinationTreeUri = treeUri,
            userAgent = item.optString("userAgent").takeUnless { it.isBlank() || it == "null" },
            mode = mode,
            state = state,
            bytesDownloaded = item.optLong("bytesDownloaded", 0L).coerceAtLeast(0L),
            totalBytes = item.optLong("totalBytes", -1L),
            createdAt = item.optLong("createdAt"),
            updatedAt = item.optLong("updatedAt"),
            backend = runCatching { CustomDownloadBackend.valueOf(item.optString("backend")) }.getOrNull(),
            backendId = item.optString("backendId").takeUnless { it.isBlank() || it == "null" },
            errorMessage = item.optString("errorMessage").takeUnless { it.isBlank() || it == "null" },
            encryptedCookieHeader = item.optString("encryptedCookieHeader")
                .takeUnless { it.isBlank() || it == "null" },
            attemptId = item.optString("attemptId").takeUnless { it.isBlank() || it == "null" },
            sourceFilePath = item.optString("sourceFilePath").takeUnless { it.isBlank() || it == "null" }
        )
    }.sortedByDescending { it.updatedAt }
} catch (_: Exception) {
    emptyList()
}

private fun encodeCustomDownloadItems(items: List<CustomDownloadItem>): String {
    val array = JSONArray()
    items.forEach { item ->
        array.put(JSONObject().apply {
            put("id", item.id)
            put("url", item.url)
            put("fileName", item.fileName)
            put("mimeType", item.mimeType ?: JSONObject.NULL)
            put("destinationTreeUri", item.destinationTreeUri)
            put("userAgent", item.userAgent ?: JSONObject.NULL)
            put("mode", item.mode.name)
            put("state", item.state.name)
            put("bytesDownloaded", item.bytesDownloaded)
            put("totalBytes", item.totalBytes)
            put("createdAt", item.createdAt)
            put("updatedAt", item.updatedAt)
            put("backend", item.backend?.name ?: JSONObject.NULL)
            put("backendId", item.backendId ?: JSONObject.NULL)
            put("errorMessage", item.errorMessage ?: JSONObject.NULL)
            put("encryptedCookieHeader", item.encryptedCookieHeader ?: JSONObject.NULL)
            put("attemptId", item.attemptId ?: JSONObject.NULL)
            put("sourceFilePath", item.sourceFilePath ?: JSONObject.NULL)
        })
    }
    return array.toString()
}
