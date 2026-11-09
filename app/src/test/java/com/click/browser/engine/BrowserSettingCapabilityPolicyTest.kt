package com.click.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSettingCapabilityPolicyTest {
    @Test
    fun javascriptAndDataSaverRemainAvailableForWebViewTabs() {
        BrowserSetting.values().forEach { setting ->
            val availability = BrowserSettingCapabilityPolicy.availability(
                setting = setting,
                engine = BrowserTabEngine.WEBVIEW
            )

            assertTrue("$setting should be available on WebView", availability.enabled)
            assertTrue(availability.description.contains("WebView"))
        }
    }

    @Test
    fun javascriptAndDataSaverAreDisabledWithAnExplanationOnGeckoViewTabs() {
        BrowserSetting.values().forEach { setting ->
            val availability = BrowserSettingCapabilityPolicy.availability(
                setting = setting,
                engine = BrowserTabEngine.GECKOVIEW
            )

            assertFalse("$setting must not appear actionable on GeckoView", availability.enabled)
            assertTrue(availability.description.contains("WebView"))
            assertTrue(availability.description.contains("GeckoView"))
        }
    }
}