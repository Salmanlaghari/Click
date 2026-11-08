package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevToolsAvailabilityPolicyTest {
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
    fun GeckoViewDevToolsIsDisabledAndNeverRequestsAnEngineFallback() {
        val access = ModeCapabilityRegistry.availability(
            FeatureId.DEVTOOLS,
            BrowserMode.DEVELOPER,
            BrowserTabEngine.GECKOVIEW
        )

        assertTrue(access.visible)
        assertFalse(access.enabled)
        assertTrue(access.explanation.orEmpty().contains("GeckoView"))
    }
}
