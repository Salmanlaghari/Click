package com.click.browser.engine

internal enum class BrowserSetting {
    JAVASCRIPT,
    DATA_SAVER
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
        }
        return BrowserSettingAvailability(enabled = supported, description = description)
    }
}
