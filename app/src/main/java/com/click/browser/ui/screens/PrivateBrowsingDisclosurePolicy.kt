package com.click.browser.ui.screens

import com.click.browser.engine.BrowserTabEngine
import com.click.browser.engine.defaultBrowserTabEngine

/** User-facing storage copy derived from the browser's current tab-engine policy. */
internal object PrivateBrowsingDisclosurePolicy {

    const val profileWebViewStorageScopeDescription =
        "this Click profile's Android System WebView storage"

    const val cookieManagerScopeDescription =
        "WebView cookies · current profile only"

    fun engineDefaultsDescription(): String {
        val regularEngine = defaultBrowserTabEngine(isIncognito = false)
        val privateEngine = defaultBrowserTabEngine(isIncognito = true)
        return if (regularEngine == privateEngine) {
            "Regular and private tabs use ${displayName(regularEngine)} by default; " +
                "private tabs run in ${displayName(privateEngine)} private mode."
        } else {
            "Regular tabs use ${displayName(regularEngine)} by default; " +
                "private tabs use ${displayName(privateEngine)}."
        }
    }

    // Private tabs run as Gecko private sessions (1.8.7 overhaul, A-list #4):
    // their storage is a separate temporary store that the engine discards
    // when the private session ends — unlike the old shared WebView profile
    // storage, which closing a tab never cleared.
    val privateTabStorageDescription: String =
        "Private-tab visits are not recorded in local history; private tabs are excluded from session restore and Recent tabs. " +
            "Private tabs run in GeckoView private mode with their own temporary storage, separate from regular tabs. " +
            "Cookies and site data from private tabs are discarded when the private session ends. " +
            "Marking an existing tab private does not remove history or site data recorded before it was marked. " +
            "Websites and network providers may still observe parts of your activity; private tabs are not anonymous."

    val aboutPrivateTabStorageDescription: String =
        "Private tabs run in GeckoView private mode; their cookies and site data are kept separate from regular tabs and discarded when the private session ends."

    private fun displayName(engine: BrowserTabEngine): String = when (engine) {
        BrowserTabEngine.GECKOVIEW -> "GeckoView"
        BrowserTabEngine.WEBVIEW -> "Android System WebView"
    }
}
