package com.click.browser.engine

internal data class QuickTogglesCapabilities(
    val webViewPageActions: Boolean,
    val addOnManager: Boolean
)

/** Describes which Quick Toggles can actually act on a selected browser tab. */
internal object QuickTogglesAvailabilityPolicy {
    fun forTab(engine: BrowserTabEngine, isPrivate: Boolean): QuickTogglesCapabilities =
        QuickTogglesCapabilities(
            webViewPageActions = engine == BrowserTabEngine.WEBVIEW,
            addOnManager = !isPrivate
        )
}
