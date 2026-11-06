package com.click.browser.engine

internal object DevToolsAvailabilityPolicy {
    fun canCapture(mode: BrowserMode, engine: BrowserTabEngine): Boolean =
        mode == BrowserMode.DEVELOPER && engine == BrowserTabEngine.WEBVIEW

    fun shouldOfferWebViewFallback(
        mode: BrowserMode,
        engine: BrowserTabEngine,
        url: String
    ): Boolean = mode == BrowserMode.DEVELOPER &&
        engine == BrowserTabEngine.GECKOVIEW &&
        url.isNotBlank() && url != "about:blank"
}
