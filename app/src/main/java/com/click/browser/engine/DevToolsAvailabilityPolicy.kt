package com.click.browser.engine

/** Page-data capture is WebView-only, even though Gecko exposes a confirmed DevTools entry route. */
internal object DevToolsAvailabilityPolicy {
    fun canCapture(mode: BrowserMode, engine: BrowserTabEngine): Boolean =
        engine == BrowserTabEngine.WEBVIEW && ModeCapabilityRegistry.availability(
            feature = FeatureId.DEVTOOLS,
            mode = mode,
            engine = engine
        ).enabled
}
