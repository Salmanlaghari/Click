package com.click.browser.engine

/**
 * Policy for Click's safe desktop-style layout controls.
 *
 * This changes only WebView viewport/layout settings. It deliberately does not
 * choose or synthesize a User-Agent; sites that select content from the real
 * System WebView identity may continue to return mobile content.
 */
object DesktopLayoutPolicy {
    data class WebViewLayout(
        val useWideViewPort: Boolean,
        val loadWithOverviewMode: Boolean
    )

    fun defaultEnabled(
        mode: BrowserMode,
        forceDesktopMode: Boolean = false,
        desktopDefault: Boolean = false
    ): Boolean = forceDesktopMode || desktopDefault || mode == BrowserMode.HACK || mode == BrowserMode.ADVANCED

    /**
     * Resolve the most-specific matching site preference. False values are
     * explicit mobile-layout overrides and therefore take precedence over the
     * profile defaults and broader parent-domain preferences.
     */
    fun enabledForHost(
        host: String,
        overrides: Map<String, Boolean>,
        mode: BrowserMode,
        forceDesktopMode: Boolean = false,
        desktopDefault: Boolean = false
    ): Boolean {
        val normalizedHost = normalizeHost(host)
        if (normalizedHost.isEmpty()) {
            return defaultEnabled(mode, forceDesktopMode, desktopDefault)
        }

        val siteOverride = overrides.entries
            .mapNotNull { (savedHost, enabled) ->
                val normalizedSavedHost = normalizeHost(savedHost)
                if (normalizedSavedHost.isNotEmpty() &&
                    (normalizedHost == normalizedSavedHost || normalizedHost.endsWith(".$normalizedSavedHost"))
                ) {
                    normalizedSavedHost.length to enabled
                } else {
                    null
                }
            }
            .maxByOrNull { it.first }
            ?.second

        return siteOverride ?: defaultEnabled(mode, forceDesktopMode, desktopDefault)
    }

    fun webViewLayout(enabled: Boolean): WebViewLayout =
        WebViewLayout(
            useWideViewPort = enabled,
            loadWithOverviewMode = enabled
        )

    private fun normalizeHost(host: String): String =
        host.trim().trimEnd('.').lowercase()
}
