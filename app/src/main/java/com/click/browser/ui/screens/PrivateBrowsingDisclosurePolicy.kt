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
        val regularEngine = displayName(defaultBrowserTabEngine(isIncognito = false))
        val privateEngine = displayName(defaultBrowserTabEngine(isIncognito = true))
        return "Regular tabs use $regularEngine by default; private tabs use $privateEngine."
    }

    val privateTabStorageDescription: String =
        "Private-tab visits are not recorded in local history; private tabs are excluded from session restore and Recent tabs. " +
            "Private tabs use $profileWebViewStorageScopeDescription, not a temporary private-only store. " +
            "Closing a private tab does not clear those cookies or site data; they can remain in the profile and may be available to other WebView tabs in the same profile. " +
            "Marking an existing tab private does not remove history or site data recorded before it was marked. " +
            "The experimental Clear data on exit setting requests cleanup when Click closes, but it is not a guaranteed wipe. " +
            "Websites and network providers may still observe parts of your activity; private tabs are not anonymous."

    val aboutPrivateTabStorageDescription: String =
        "Private tabs use $profileWebViewStorageScopeDescription, and site data can remain after a tab closes."

    private fun displayName(engine: BrowserTabEngine): String = when (engine) {
        BrowserTabEngine.GECKOVIEW -> "GeckoView"
        BrowserTabEngine.WEBVIEW -> "Android System WebView"
    }
}
