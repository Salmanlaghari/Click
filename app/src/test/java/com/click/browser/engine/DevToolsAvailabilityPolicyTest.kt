package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevToolsAvailabilityPolicyTest {
    @Test
    fun loadedInspectablePageRequiresCommittedNonLoadingHttpOrHttpsUrl() {
        assertTrue(
            DevToolsAvailabilityPolicy.hasLoadedInspectablePage(
                hasCommittedContent = true,
                isPageLoading = false,
                pageUrl = "https://example.com/page"
            )
        )
        assertFalse(
            DevToolsAvailabilityPolicy.hasLoadedInspectablePage(
                hasCommittedContent = false,
                isPageLoading = false,
                pageUrl = "https://example.com/page"
            )
        )
        assertFalse(
            DevToolsAvailabilityPolicy.hasLoadedInspectablePage(
                hasCommittedContent = true,
                isPageLoading = true,
                pageUrl = "https://example.com/page"
            )
        )
        assertFalse(
            DevToolsAvailabilityPolicy.hasLoadedInspectablePage(
                hasCommittedContent = true,
                isPageLoading = false,
                pageUrl = "about:blank"
            )
        )
    }

    @Test
    fun capturesOnlyForDeveloperModeWebViewPages() {
        BrowserMode.values().forEach { mode ->
            BrowserTabEngine.values().forEach { engine ->
                assertEquals(
                    mode == BrowserMode.DEVELOPER && engine == BrowserTabEngine.WEBVIEW,
                    DevToolsAvailabilityPolicy.canCapture(mode, engine)
                )
            }
        }
    }

    @Test
    fun GeckoViewDevToolsInspectsDirectlyViaBridgeButWebViewCaptureStaysWebViewOnly() {
        val access = ModeCapabilityRegistry.availability(
            FeatureId.DEVTOOLS,
            BrowserMode.DEVELOPER,
            BrowserTabEngine.GECKOVIEW,
            hasLoadedPage = true
        )

        assertTrue(access.visible)
        assertTrue(access.enabled)
        // B2: no WebView-reload explanation anymore — Gecko tabs are
        // inspected directly through the DevTools bridge extension.
        assertEquals(null, access.explanation)
        // The WebView evaluateJavascript capture path stays WebView-only…
        assertFalse(DevToolsAvailabilityPolicy.canCapture(BrowserMode.DEVELOPER, BrowserTabEngine.GECKOVIEW))
        // …while the bridge capture path is Developer-mode Gecko only.
        assertTrue(DevToolsAvailabilityPolicy.canCaptureGecko(BrowserMode.DEVELOPER, BrowserTabEngine.GECKOVIEW))
        assertFalse(DevToolsAvailabilityPolicy.canCaptureGecko(BrowserMode.SIMPLE, BrowserTabEngine.GECKOVIEW))
        assertFalse(DevToolsAvailabilityPolicy.canCaptureGecko(BrowserMode.DEVELOPER, BrowserTabEngine.WEBVIEW))
    }
}
