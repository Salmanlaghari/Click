package com.click.browser.engine

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** One restorable tab — URL + title. The active-tab index is stored separately. */
data class SavedTab(
    val url: String,
    val title: String
)

/**
 * Crash / force-close session restore.
 *
 * Chrome-style semantics, per browsing profile:
 *
 * - While the app runs, [saveSession] keeps a snapshot of the open
 *   non-incognito tabs in the profile's DataStore ([profileDataStoreFor]).
 * - A `clean_exit` flag distinguishes "user closed the app / switched
 *   engine on purpose" from "the process died unexpectedly" (crash,
 *   force-close, system kill).
 * - On the next launch, if the flag says the previous run was NOT clean
 *   and a snapshot exists, the UI offers "Restore N tabs?".
 *
 * ISOLATION (Prince's requirement): everything is keyed per [BrowserMode] —
 * a snapshot saved in Simple is never offered in Hack, and Click Advance
 * (its own DataStore) can never see another mode's session. The store is
 * chosen with [profileDataStoreFor], which — unlike [profileDataStore] —
 * takes the mode explicitly and needs no boot-pinning, so this can run
 * from `onDestroy` and mode-switch paths alike.
 *
 * PRIVACY: incognito tabs, blank tabs and non-http(s) URLs are never
 * persisted. When the "clear data on exit" experimental flag is on, no
 * snapshot is kept at all and no restore is offered.
 */
object SessionRestore {

    /** Hard cap so a 200-tab session can't bloat the preferences store. */
    const val MAX_TABS = 25

    private fun cleanExitKey(mode: BrowserMode) =
        booleanPreferencesKey("session_clean_exit_${mode.name.lowercase()}")

    private fun tabsKey(mode: BrowserMode) =
        stringPreferencesKey("session_tabs_${mode.name.lowercase()}")

    private fun activeIndexKey(mode: BrowserMode) =
        intPreferencesKey("session_active_index_${mode.name.lowercase()}")

    /** True if the URL is worth restoring (real web page, not chrome UI). */
    fun isRestorableUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")

    /**
     * Writes the current open-tab snapshot for [mode]. Call periodically /
     * on tab changes — cheap (small JSON, debounced by the caller).
     * Incognito and non-http(s) tabs are silently dropped.
     */
    suspend fun saveSession(
        context: Context,
        mode: BrowserMode,
        tabs: List<SavedTab>,
        activeIndex: Int
    ) {
        val restorable = tabs
            .filter { isRestorableUrl(it.url) }
            .take(MAX_TABS)
        val array = JSONArray()
        for (t in restorable) {
            array.put(JSONObject().apply {
                put("url", t.url)
                put("title", t.title)
            })
        }
        context.profileDataStoreFor(mode).edit { prefs ->
            prefs[tabsKey(mode)] = array.toString()
            prefs[activeIndexKey(mode)] = activeIndex.coerceIn(0, (restorable.size - 1).coerceAtLeast(0))
        }
    }

    /**
     * Reads the snapshot for [mode]. Returns the tab list (possibly empty)
     * and the saved active-tab index (clamped to the list).
     */
    suspend fun loadSession(context: Context, mode: BrowserMode): Pair<List<SavedTab>, Int> {
        val prefs = context.profileDataStoreFor(mode).data.first()
        val json = prefs[tabsKey(mode)] ?: "[]"
        val list = mutableListOf<SavedTab>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val url = obj.optString("url", "")
                if (isRestorableUrl(url)) {
                    list.add(SavedTab(url, obj.optString("title", url)))
                }
            }
        } catch (_: Exception) {
            // Corrupt snapshot → treat as "nothing to restore".
            return emptyList<SavedTab>() to 0
        }
        val active = prefs[activeIndexKey(mode)] ?: 0
        return list to active.coerceIn(0, (list.size - 1).coerceAtLeast(0))
    }

    /** Was the previous run for [mode] a clean exit? Defaults to true (first launch → no restore prompt). */
    suspend fun wasCleanExit(context: Context, mode: BrowserMode): Boolean {
        val prefs = context.profileDataStoreFor(mode).data.first()
        return prefs[cleanExitKey(mode)] ?: true
    }

    /** Marks the current run's exit state for [mode]. Call with `true` on clean exits, `false` at startup. */
    suspend fun markCleanExit(context: Context, mode: BrowserMode, clean: Boolean) {
        context.profileDataStoreFor(mode).edit { prefs ->
            prefs[cleanExitKey(mode)] = clean
        }
    }

    /** Drops the snapshot for [mode] (used when the user discards the restore offer). */
    suspend fun clearSession(context: Context, mode: BrowserMode) {
        context.profileDataStoreFor(mode).edit { prefs ->
            prefs.remove(tabsKey(mode))
            prefs.remove(activeIndexKey(mode))
        }
    }
}
