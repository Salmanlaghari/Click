package com.click.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevToolsAvailabilityPolicyTest {
    @Test
    fun capturesConsoleOnlyForDeveloperModeWebViewTabs() {
        assertTrue(DevToolsAvailabilityPolicy.canCapture(BrowserMode.DEVELOPER, BrowserTabEngine.WEBVIEW))
        assertFalse(DevToolsAvailabilityPolicy.canCapture(BrowserMode.DEVELOPER, BrowserTabEngine.GECKOVIEW))
        assertFalse(DevToolsAvailabilityPolicy.canCapture(BrowserMode.SIMPLE, BrowserTabEngine.WEBVIEW))
    }

    @Test
    fun offersWebViewSwitchOnlyForLoadedDeveloperModeGeckoPages() {
        assertTrue(
            DevToolsAvailabilityPolicy.shouldOfferWebViewFallback(
                BrowserMode.DEVELOPER,
                BrowserTabEngine.GECKOVIEW,
                "https://example.com"
            )
        )
        assertFalse(
            DevToolsAvailabilityPolicy.shouldOfferWebViewFallback(
                BrowserMode.DEVELOPER,
                BrowserTabEngine.GECKOVIEW,
                "about:blank"
            )
        )
        assertFalse(
            DevToolsAvailabilityPolicy.shouldOfferWebViewFallback(
                BrowserMode.SIMPLE,
                BrowserTabEngine.GECKOVIEW,
                "https://example.com"
            )
        )
    }
}
