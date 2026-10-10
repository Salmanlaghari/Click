package com.click.browser.engine

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Per-profile (per-mode) app data — the foundation of Click Advance.
 *
 * Prince's requirement: "whatever the user signs into on Simple / Developer /
 * Hack must NOT show in Advance." Cookies / cache / localStorage are isolated
 * for free by the WebView data-directory suffix ([V9Engine.suffixFor]).
 * The app-level data (bookmarks, history, downloads, saved passwords,
 * userscripts, quick sites, search-engine prefs) is split here:
 *
 * - SIMPLE / DEVELOPER / HACK keep using the legacy shared `browser_settings`
 *   store — zero migration, existing data untouched.
 * - ADVANCED uses `browser_settings_advanced`, which starts EMPTY by design.
 *   Entering Advance is always a fresh space.
 *
 * Deliberately NOT split: the mode selector itself ([ModeManager.MODE_KEY] —
 * it must stay shared because it is read before the boot mode is known) and
 * device-level services (V9 Shield/VPN toggle, DoH provider, AI API key),
 * which are device configuration, not browsing identity.
 */
val Context.advancedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "browser_settings_advanced"
)

/** DataStore holding [mode]'s private app data. ADVANCED never falls back to the shared store. */
fun Context.profileDataStoreFor(mode: BrowserMode): DataStore<Preferences> =
    if (mode == BrowserMode.ADVANCED) advancedDataStore else dataStore

/** DataStore holding the current engine's private app data. */
val Context.profileDataStore: DataStore<Preferences>
    get() = profileDataStoreFor(V9Engine.bootMode)
