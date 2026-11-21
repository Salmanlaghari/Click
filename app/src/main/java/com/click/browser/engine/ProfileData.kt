package com.click.browser.engine

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Per-profile (per-mode) app data — the foundation of Click Advance.
 *
 * Prince's requirement: "whatever the user signs into on Simple / Developer /
 * Power must NOT show in Advance." Cookies / cache / localStorage are isolated
 * for free by the WebView data-directory suffix ([V9Engine.suffixFor]).
 * The app-level data (bookmarks, history, downloads, saved passwords,
 * quick sites, search-engine prefs, per-site desktop
 * preferences) is split here:
 *
 * - SIMPLE keeps using the legacy shared `browser_settings` store — zero
 *   migration, existing data untouched.
 * - ADVANCED uses `browser_settings_advanced`, DEVELOPER uses
 *   `browser_settings_developer` and POWER/HACK uses `browser_settings_hack`;
 *   each starts EMPTY by design. Per-mode profiles (Prince's product vision,
 *   21 Nov 2026): every mode is its own profile with its own settings and
 *   saved data, isolated from the other modes.
 *
 * Deliberately NOT split: the mode selector itself ([ModeManager.MODE_KEY] —
 * it must stay shared because it is read before the boot mode is known) and
 * device-level services (V9 Shield/VPN toggle, DoH provider, AI API key),
 * which are device configuration, not browsing identity.
 */
val Context.advancedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "browser_settings_advanced"
)

val Context.developerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "browser_settings_developer"
)

val Context.hackDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "browser_settings_hack"
)

/** DataStore holding [mode]'s private app data. Non-SIMPLE modes never fall back to the shared store. */
fun Context.profileDataStoreFor(mode: BrowserMode): DataStore<Preferences> {
    check(!V9Engine.isBootBlocked) {
        "Browser profile initialization failed; browsing data is unavailable to prevent cross-profile access"
    }
    return when (mode) {
        BrowserMode.ADVANCED -> advancedDataStore
        BrowserMode.DEVELOPER -> developerDataStore
        BrowserMode.HACK -> hackDataStore
        BrowserMode.SIMPLE -> dataStore
    }
}

/** DataStore holding the current engine's private app data.
 *
 * Init-order contract: may only be accessed after
 * [V9Engine.applyDataDirectorySuffix] has run (first thing in
 * `ClickApplication.onCreate`, before any Activity, Service, or ViewModel
 * exists). Fails fast via [V9Engine.isBootPinned] instead of silently
 * returning the wrong profile's store — writing to the wrong profile would
 * leak browsing data across engines, which is exactly what Click Advance
 * must never do.
 */
val Context.profileDataStore: DataStore<Preferences>
    get() {
        check(V9Engine.isBootPinned) {
            "profileDataStore accessed before V9Engine.applyDataDirectorySuffix() " +
                "(must run first in ClickApplication.onCreate)"
        }
        return profileDataStoreFor(V9Engine.bootMode)
    }
