package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSettingCapabilityPolicyTest {
    @Test
    fun allSettingsRemainAvailableForWebViewTabs() {
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
    fun allSettingsAreDisabledWithAnExplanationOnGeckoViewTabs() {
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

    @Test
    fun everySettingHasItsOwnDescriptionOnBothEngines() {
        BrowserTabEngine.values().forEach { engine ->
            val descriptions = BrowserSetting.values().map { setting ->
                BrowserSettingCapabilityPolicy.availability(setting, engine).description
            }
            assertTrue(descriptions.all(String::isNotBlank))
            assertEquals(
                "descriptions must not be copy-pasted across settings on $engine",
                descriptions.size,
                descriptions.distinct().size
            )
        }
    }
}
