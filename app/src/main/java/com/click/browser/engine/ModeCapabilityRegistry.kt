package com.click.browser.engine

/** Stable identifiers for actions in the shared browser feature catalog. */
enum class FeatureId {
    HISTORY, SETTINGS, STORAGE, PASSWORDS, COOKIES, TOOLS, DEVTOOLS,
    DOWNLOADS, BOOKMARKS, AI_CHAT, TRANSLATE, DESKTOP, NEW_TAB,
    PRIVATE_TAB, TABS, RECENT_TABS, SHARE, FIND_IN_PAGE,
    ADBLOCK, READER, SCREENSHOT, SAVE_PDF, ADD_HOME, SITE_INFO,
    PRIVACY_GUARDS, FULLSCREEN, TEXT_SIZE,
    CLEAR_DATA, ABOUT, V9_SHIELD, FLAGS, VERSION, HELP_FEEDBACK, GAMES
}

internal data class FeatureAvailability(
    val visible: Boolean,
    val enabled: Boolean,
    val explanation: String? = null
)

/**
 * Single source of truth for which shared feature shortcuts belong in each mode
 * and which rendering engines can actually perform them. This does not switch
 * engines; it reports unsupported combinations to the UI for hiding/disabling.
 */
internal object ModeCapabilityRegistry {
    private val allModes = BrowserMode.values().toSet()

    /** Features intentionally surfaced only in the modes named by the brief. */
    private val modeRestrictions: Map<FeatureId, Set<BrowserMode>> = mapOf(
        FeatureId.DEVTOOLS to setOf(BrowserMode.DEVELOPER),
        FeatureId.FLAGS to setOf(BrowserMode.DEVELOPER, BrowserMode.ADVANCED),
        FeatureId.DESKTOP to setOf(BrowserMode.DEVELOPER, BrowserMode.HACK),
        FeatureId.FIND_IN_PAGE to setOf(BrowserMode.DEVELOPER, BrowserMode.HACK),
        FeatureId.RECENT_TABS to setOf(BrowserMode.HACK),
        FeatureId.FULLSCREEN to setOf(BrowserMode.HACK),
        FeatureId.STORAGE to setOf(BrowserMode.ADVANCED),
        FeatureId.COOKIES to setOf(BrowserMode.ADVANCED),
        FeatureId.CLEAR_DATA to setOf(BrowserMode.ADVANCED)
    )

    /** Actions whose current implementation directly depends on android.webkit.WebView. */
    private val webViewOnlyReasons: Map<FeatureId, String> = mapOf(
        FeatureId.DEVTOOLS to "DevTools is only connected to WebView; this regular tab uses GeckoView.",
        FeatureId.COOKIES to "Cookie management is connected to WebView tabs only; this tab uses GeckoView.",
        FeatureId.DESKTOP to "Wide-layout overrides are not yet connected to GeckoView tabs.",
        FeatureId.ADBLOCK to "The custom filter is WebView-only; GeckoView tracking protection remains enabled.",
        FeatureId.READER to "Reader mode is not yet connected to GeckoView tabs.",
        FeatureId.SCREENSHOT to "Page screenshots currently capture WebView tabs only.",
        FeatureId.TEXT_SIZE to "Page text zoom is not yet connected to GeckoView tabs."
    )

    private val pageRequiredFeatures = setOf(FeatureId.DEVTOOLS)

    fun visibleFeatures(mode: BrowserMode): List<FeatureId> = FeatureId.values().filter { feature ->
        supportsMode(feature, mode)
    }

    fun supportsMode(feature: FeatureId, mode: BrowserMode): Boolean =
        mode in modeRestrictions[feature].orEmpty().ifEmpty { allModes }

    fun availability(
        feature: FeatureId,
        mode: BrowserMode,
        engine: BrowserTabEngine,
        hasLoadedPage: Boolean = true
    ): FeatureAvailability {
        if (!supportsMode(feature, mode)) {
            return FeatureAvailability(visible = false, enabled = false)
        }

        if (engine == BrowserTabEngine.GECKOVIEW) {
            webViewOnlyReasons[feature]?.let { reason ->
                return FeatureAvailability(visible = true, enabled = false, explanation = reason)
            }
        }

        if (feature in pageRequiredFeatures && !hasLoadedPage) {
            return FeatureAvailability(
                visible = true,
                enabled = false,
                explanation = "Load a web page before opening DevTools."
            )
        }

        return FeatureAvailability(visible = true, enabled = true)
    }

    /** Exposed to tests to make the engine capability matrix auditable. */
    val webViewOnlyFeatures: Set<FeatureId>
        get() = webViewOnlyReasons.keys
}
