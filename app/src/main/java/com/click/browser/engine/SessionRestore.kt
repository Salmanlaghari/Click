package com.click.browser.engine

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject

/** One restorable tab. Gecko state is optional; legacy snapshots remain URL/title-only. */
data class SavedTab(
    val url: String,
    val title: String,
    val engine: BrowserTabEngine = BrowserTabEngine.GECKOVIEW,
    val geckoSessionState: String? = null
)

/** Legacy persisted WebView tabs are restored into the regular GeckoView provider. */
internal fun migrateRestoredRegularTabEngine(storedEngine: BrowserTabEngine): BrowserTabEngine =
    if (storedEngine == BrowserTabEngine.WEBVIEW) BrowserTabEngine.GECKOVIEW else storedEngine

internal data class RestorableTabState(
    val url: String,
    val title: String,
    val isIncognito: Boolean,
    val engine: BrowserTabEngine,
    val geckoSessionState: String? = null
)

/** Pure snapshot policy: keep eligible normal tabs and preserve their selected engine. */
internal fun buildRestorableSnapshot(
    tabStates: List<RestorableTabState>,
    activeIdx: Int
): Pair<List<SavedTab>, Int> {
    val kept = tabStates.mapIndexed { index, tab -> index to tab }
        .filter { (_, tab) -> !tab.isIncognito && SessionRestore.isRestorableUrl(tab.url) }
        .take(SessionRestore.MAX_TABS)
    val active = kept.indexOfFirst { (index, _) -> index == activeIdx }.takeIf { it >= 0 } ?: 0
    val tabs = kept.map { (_, tab) ->
        SavedTab(
            tab.url,
            tab.title.ifBlank { tab.url },
            tab.engine,
            tab.geckoSessionState?.takeIf { it.length <= SessionRestore.MAX_GECKO_STATE_CHARS }
        )
    }
    return tabs to active
}

/**
 * Crash / force-close session restore.
 *
 * Chrome-style semantics, per browsing profile:
 *
 * - While the app runs, [saveSession] keeps a snapshot of the open
 *   non-incognito tabs in the profile's DataStore ([profileDataStoreFor]).
 * - A `clean_exit` flag distinguishes a normal close or intentional profile
 *   switch from an unexpected process death (crash, force-close, system kill).
 * - An intentional switch also arms a one-shot marker in the source profile;
 *   returning to that same profile restores its own tabs automatically.
 * - On the next launch, if the flag says the previous run was NOT clean
 *   and a snapshot exists, the UI offers "Restore N tabs?".
 *
 * SESSION ISOLATION (Prince's requirement): snapshots are keyed per [BrowserMode] —
 * a snapshot saved in Simple is never offered in Power, and Click Advance
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
    /** Bound optional Gecko history/form-state data stored per tab. */
    internal const val MAX_GECKO_STATE_CHARS = 32_768

    private fun cleanExitKey(mode: BrowserMode) =
        booleanPreferencesKey("session_clean_exit_${mode.name.lowercase()}")

    private fun tabsKey(mode: BrowserMode) =
        stringPreferencesKey("session_tabs_${mode.name.lowercase()}")

    private fun activeIndexKey(mode: BrowserMode) =
        intPreferencesKey("session_active_index_${mode.name.lowercase()}")

    private fun modeSwitchRestoreKey(mode: BrowserMode) =
        booleanPreferencesKey("session_mode_switch_restore_${mode.name.lowercase()}")

    /** True if the URL is worth restoring (real web page, not chrome UI). */
    fun isRestorableUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")

    /**
     * Writes the current open-tab snapshot for [mode]. Call periodically /
     * on tab changes — cheap (small JSON, debounced by the caller).
     * Incognito and non-http(s) tabs are silently dropped.
     *
     * @param activeIndex index of the active tab within [tabs] (the already
     *   filtered list). It is clamped defensively, but callers that filter
     *   must translate the original index themselves — see
     *   [buildRestorableSnapshot].
     */
    suspend fun saveSession(
        context: Context,
        mode: BrowserMode,
        tabs: List<SavedTab>,
        activeIndex: Int
    ) = writeSession(context, mode, tabs, activeIndex, armModeSwitchRestore = false)

    /** Saves this profile's tabs and arms automatic restoration when it is re-entered. */
    suspend fun saveSessionForModeSwitch(
        context: Context,
        mode: BrowserMode,
        tabs: List<SavedTab>,
        activeIndex: Int
    ) = writeSession(context, mode, tabs, activeIndex, armModeSwitchRestore = true)

    private suspend fun writeSession(
        context: Context,
        mode: BrowserMode,
        tabs: List<SavedTab>,
        activeIndex: Int,
        armModeSwitchRestore: Boolean
    ) {
        val restorable = tabs
            .filter { isRestorableUrl(it.url) }
            .take(MAX_TABS)
        val array = JSONArray()
        for (t in restorable) {
            array.put(JSONObject().apply {
                put("url", t.url)
                put("title", t.title)
                put("engine", t.engine.name)
                t.geckoSessionState
                    ?.takeIf { it.length <= MAX_GECKO_STATE_CHARS }
                    ?.let { put("geckoState", it) }
            })
        }
        context.profileDataStoreFor(mode).edit { prefs ->
            prefs[tabsKey(mode)] = array.toString()
            prefs[activeIndexKey(mode)] = activeIndex.coerceIn(0, (restorable.size - 1).coerceAtLeast(0))
            if (armModeSwitchRestore) {
                if (restorable.isEmpty()) {
                    prefs.remove(modeSwitchRestoreKey(mode))
                } else {
                    prefs[modeSwitchRestoreKey(mode)] = true
                }
            }
        }
    }

    /**
     * Reads the snapshot for [mode]. Returns the tab list (possibly empty)
     * and the saved active-tab index (clamped to the list).
     */
    suspend fun loadSession(context: Context, mode: BrowserMode): Pair<List<SavedTab>, Int> {
        val prefs = context.profileDataStoreFor(mode).data.firstOrNull()
            ?: return emptyList<SavedTab>() to 0
        val json = prefs[tabsKey(mode)] ?: "[]"
        val list = mutableListOf<SavedTab>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val url = obj.optString("url", "")
                if (isRestorableUrl(url)) {
                    val engine = runCatching {
                        BrowserTabEngine.valueOf(
                            obj.optString("engine", BrowserTabEngine.GECKOVIEW.name)
                        )
                    }.getOrDefault(BrowserTabEngine.GECKOVIEW)
                    list.add(
                        SavedTab(
                            url,
                            obj.optString("title", url),
                            migrateRestoredRegularTabEngine(engine),
                            obj.optString("geckoState")
                                .takeIf { it.isNotBlank() && it.length <= MAX_GECKO_STATE_CHARS }
                        )
                    )
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
        val prefs = context.profileDataStoreFor(mode).data.firstOrNull() ?: return true
        return prefs[cleanExitKey(mode)] ?: true
    }

    /** Marks the current run's exit state for [mode]. Call with `true` on clean exits, `false` at startup. */
    suspend fun markCleanExit(context: Context, mode: BrowserMode, clean: Boolean) {
        context.profileDataStoreFor(mode).edit { prefs ->
            prefs[cleanExitKey(mode)] = clean
        }
    }

    /** Atomically reads and removes the one-shot marker for this profile. */
    suspend fun consumeModeSwitchRestore(context: Context, mode: BrowserMode): Boolean {
        var pending = false
        context.profileDataStoreFor(mode).edit { prefs ->
            pending = prefs[modeSwitchRestoreKey(mode)] == true
            prefs.remove(modeSwitchRestoreKey(mode))
        }
        return pending
    }

    /** Cancels a pending restore if the requested process restart could not be scheduled. */
    suspend fun clearModeSwitchRestore(context: Context, mode: BrowserMode) {
        context.profileDataStoreFor(mode).edit { prefs ->
            prefs.remove(modeSwitchRestoreKey(mode))
        }
    }

    /**
     * Drops the snapshot for [mode] (used when the user discards the restore
     * offer, or when "clear data on exit" wipes it). Also resets the
     * clean-exit flag: the discarded crash is now resolved, so a stale
     * "unclean" marker must not cause a phantom restore prompt later.
     * (Crash detection for the current run is re-armed by the caller —
     * the snapshot saver re-marks unclean on every save.)
     */
    suspend fun clearSession(context: Context, mode: BrowserMode) {
        context.profileDataStoreFor(mode).edit { prefs ->
            prefs.remove(tabsKey(mode))
            prefs.remove(activeIndexKey(mode))
            prefs.remove(modeSwitchRestoreKey(mode))
            prefs.remove(cleanExitKey(mode)) // default = true = clean
        }
    }
}
