package com.click.browser.engine

enum class BrowserMode {
    SIMPLE,
    DEVELOPER,
    /** Legacy stored identifier for the public Power profile; retained to preserve existing settings and the v9_hack WebView data directory. */
    HACK,
    /**
     * Click Advance: the 4th fully-isolated space. Its own WebView data
     * directory (separate cookie jar / cache / localStorage via
     * [V9Engine.suffixFor]) AND its own app-data store (bookmarks, history,
     * passwords — see [profileDataStore]), which starts empty
     * by design. Performance-tuned desktop-class profile; the renderer is
     * still the system WebView (Chromium) — there is no separate engine.
     */
    ADVANCED
}
