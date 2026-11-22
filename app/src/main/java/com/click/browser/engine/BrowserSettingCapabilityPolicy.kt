package com.click.browser.engine

internal enum class BrowserSetting {
    JAVASCRIPT,
    DATA_SAVER,
    TEXT_SIZE,
    FORCE_ZOOM,
    READER_MODE,
    REDUCED_MOTION,
    FORCE_NIGHT_MODE,
    SHARP_VIEW
}

internal data class BrowserSettingAvailability(
    val enabled: Boolean,
    val description: String
)

/** Keeps WebView-only global settings honest for the currently selected tab engine. */
internal object BrowserSettingCapabilityPolicy {
    fun availability(
        setting: BrowserSetting,
        engine: BrowserTabEngine
    ): BrowserSettingAvailability {
        val supported = engine == BrowserTabEngine.WEBVIEW
        val description = when (setting) {
            BrowserSetting.JAVASCRIPT -> if (supported) {
                "Change JavaScript execution for WebView tabs."
            } else {
                "Unavailable for this GeckoView tab; this control changes JavaScript in WebView tabs only."
            }
            BrowserSetting.DATA_SAVER -> if (supported) {
                "Reduce resource use by blocking images in WebView tabs."
            } else {
                "Unavailable for this GeckoView tab; Data Saver blocks images in WebView tabs only."
            }
            BrowserSetting.TEXT_SIZE -> if (supported) {
                "Change the text size of pages in WebView tabs."
            } else {
                "Unavailable for this GeckoView tab; text size changes apply in WebView tabs only."
            }
            BrowserSetting.FORCE_ZOOM -> if (supported) {
                "Allow pinch-zoom on every WebView tab, even pages that try to block it."
            } else {
                "Unavailable for this GeckoView tab; force zoom is injected in WebView tabs only."
            }
            BrowserSetting.READER_MODE -> if (supported) {
                "Show a clean reading view for articles in WebView tabs."
            } else {
                "Unavailable for this GeckoView tab; reader mode is injected in WebView tabs only."
            }
            BrowserSetting.REDUCED_MOTION -> if (supported) {
                "Minimize animations on pages in WebView tabs."
            } else {
                "Unavailable for this GeckoView tab; reduced motion is injected in WebView tabs only."
            }
            BrowserSetting.FORCE_NIGHT_MODE -> if (supported) {
                "Darken websites in WebView tabs."
            } else {
                "Unavailable for this GeckoView tab; forced night mode darkens WebView tabs only."
            }
            BrowserSetting.SHARP_VIEW -> if (supported) {
                "Full-density page rendering for crisp text and images in WebView tabs."
            } else {
                "Unavailable for this GeckoView tab; Sharp View changes rendering in WebView tabs only."
            }
        }
        return BrowserSettingAvailability(enabled = supported, description = description)
    }
}
