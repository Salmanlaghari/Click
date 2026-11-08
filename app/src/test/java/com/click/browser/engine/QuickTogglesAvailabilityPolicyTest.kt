package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickTogglesAvailabilityPolicyTest {
    @Test
    fun regularWebViewEnablesWebViewSettingsAndAddOnManager() {
        assertEquals(
            QuickTogglesCapabilities(webViewPageActions = true, addOnManager = true),
            QuickTogglesAvailabilityPolicy.forTab(BrowserTabEngine.WEBVIEW, isPrivate = false)
        )
    }

    @Test
    fun privateWebViewKeepsPageSettingsButDisablesAddOns() {
        assertEquals(
            QuickTogglesCapabilities(webViewPageActions = true, addOnManager = false),
            QuickTogglesAvailabilityPolicy.forTab(BrowserTabEngine.WEBVIEW, isPrivate = true)
        )
    }

    @Test
    fun regularGeckoViewKeepsQuickTogglesAvailableButDisablesWebViewSettings() {
        assertEquals(
            QuickTogglesCapabilities(webViewPageActions = false, addOnManager = true),
            QuickTogglesAvailabilityPolicy.forTab(BrowserTabEngine.GECKOVIEW, isPrivate = false)
        )
    }

    @Test
    fun privateTabsDoNotOfferAddOnManager() {
        assertEquals(
            QuickTogglesCapabilities(webViewPageActions = false, addOnManager = false),
            QuickTogglesAvailabilityPolicy.forTab(BrowserTabEngine.GECKOVIEW, isPrivate = true)
        )
    }
}
