package com.click.browser.engine

internal data class QuickTogglesCapabilities(
    val webViewPageActions: Boolean,
    val addOnManager: Boolean
)

internal data class QuickToggleAdBlockState(
    val checked: Boolean,
    val toggleEnabled: Boolean,
    val siteOverrideActive: Boolean
)

/** Describes which Quick Toggles can actually act on a selected browser tab. */
internal object QuickTogglesAvailabilityPolicy {
    fun forTab(engine: BrowserTabEngine, isPrivate: Boolean): QuickTogglesCapabilities =
        QuickTogglesCapabilities(
            webViewPageActions = engine == BrowserTabEngine.WEBVIEW,
            addOnManager = !isPrivate
        )

    fun adBlockState(
        engine: BrowserTabEngine,
        globalEnabled: Boolean,
        siteOverride: Boolean?
    ): QuickToggleAdBlockState {
        val supported = engine == BrowserTabEngine.WEBVIEW
        return QuickToggleAdBlockState(
            checked = supported && (siteOverride ?: globalEnabled),
            toggleEnabled = supported && siteOverride == null,
            siteOverrideActive = supported && siteOverride != null
        )
    }
}
