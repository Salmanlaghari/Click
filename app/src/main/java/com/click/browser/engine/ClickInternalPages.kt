package com.click.browser.engine

/**
 * chrome://-style internal pages for Click Browser (`click://` scheme).
 *
 * Typing e.g. `click://flags` in the address bar opens a native Compose
 * screen instead of loading a web page. Integration points in MainActivity:
 *  1. `formatUrl()` preserves the `click://` scheme (returns it unchanged).
 *  2. `shouldOverrideUrlLoading()` (both overloads) calls
 *     [interceptNavigation] and returns true when handled.
 */
object ClickInternalPages {

    const val SCHEME = "click://"

    data class Page(val key: String, val title: String, val desc: String)

    val PAGES: List<Page> = listOf(
        Page("flags", "Experiments", "Experimental features lab — 20+ real toggles"),
        Page("version", "Version", "V9 engine info & feature count"),
        Page("settings", "Settings", "Open app settings"),
        Page("history", "History", "Browsing history"),
        Page("downloads", "Downloads", "Downloaded files"),
        Page("bookmarks", "Bookmarks", "Saved bookmarks"),
        Page("vpn", "VPN", "V9 secure tunnel"),
        Page("cookies", "Cookies", "Cookie manager"),
        Page("dns", "DNS", "DNS-over-HTTPS settings"),
        Page("newtab", "New Tab", "Premium home page"),
        Page("games", "Games", "Offline mini-games + Today Update"),
    )

    /** Total user-facing features, kept in sync with docs/FEATURES.md. */
    const val FEATURE_COUNT = 142

    fun isInternalUrl(url: String): Boolean =
        url.trim().lowercase().startsWith(SCHEME)

    /** Returns the page key, or null when the URL is not a known internal page. */
    fun pageKey(url: String): String? {
        val key = url.trim().lowercase()
            .removePrefix(SCHEME)
            .trimEnd('/')
            .substringBefore("?")
            .substringBefore("#")
            .trim()
        if (key.isEmpty()) return "newtab"
        return if (PAGES.any { it.key == key }) key else null
    }

    /**
     * ONE integration point for WebView navigation.
     * @return the page key to open natively, or null to let normal handling continue.
     * Unknown click:// pages return "unknown".
     */
    fun interceptNavigation(url: String): String? {
        if (!isInternalUrl(url)) return null
        return pageKey(url) ?: "unknown"
    }
}
