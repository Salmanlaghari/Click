package com.click.browser.engine

/** Readiness of Click's bundled/cached ad and tracker domain rules. */
internal enum class FilterListReadiness {
    LOADING,
    READY,
    BUILTIN_ONLY
}

internal data class AdBlockStatusPresentation(
    val label: String,
    val active: Boolean
)

/** Produces a conservative status for the active browser tab. */
internal object AdBlockStatusPolicy {
    fun forPage(
        engine: BrowserTabEngine,
        globalEnabled: Boolean,
        siteOverride: Boolean?,
        readiness: FilterListReadiness
    ): AdBlockStatusPresentation {
        if (engine == BrowserTabEngine.GECKOVIEW) {
            return AdBlockStatusPresentation(
                label = "GeckoView tracking protection enabled",
                active = true
            )
        }

        val filterEnabled = siteOverride ?: globalEnabled
        if (!filterEnabled) {
            return AdBlockStatusPresentation(
                label = if (siteOverride == false) {
                    "Ad & tracker filter off for this site"
                } else {
                    "Ad & tracker filter off"
                },
                active = false
            )
        }

        return when (readiness) {
            FilterListReadiness.READY -> AdBlockStatusPresentation(
                label = "Ad & tracker filter ready",
                active = true
            )
            FilterListReadiness.LOADING -> AdBlockStatusPresentation(
                label = "Filter list loading · built-in rules only",
                active = false
            )
            FilterListReadiness.BUILTIN_ONLY -> AdBlockStatusPresentation(
                label = "Filter list unavailable · built-in rules only",
                active = false
            )
        }
    }
}
