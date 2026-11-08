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

    @Test
    fun adBlockQuickToggleUsesGlobalDefaultWhenNoSiteOverrideExists() {
        assertEquals(
            QuickToggleAdBlockState(
                checked = true,
                toggleEnabled = true,
                siteOverrideActive = false
            ),
            QuickTogglesAvailabilityPolicy.adBlockState(
                BrowserTabEngine.WEBVIEW,
                globalEnabled = true,
                siteOverride = null
            )
        )
    }

    @Test
    fun siteOverrideControlsDisplayedStateAndDisablesGlobalToggle() {
        assertEquals(
            QuickToggleAdBlockState(
                checked = false,
                toggleEnabled = false,
                siteOverrideActive = true
            ),
            QuickTogglesAvailabilityPolicy.adBlockState(
                BrowserTabEngine.WEBVIEW,
                globalEnabled = true,
                siteOverride = false
            )
        )
        assertEquals(
            QuickToggleAdBlockState(
                checked = true,
                toggleEnabled = false,
                siteOverrideActive = true
            ),
            QuickTogglesAvailabilityPolicy.adBlockState(
                BrowserTabEngine.WEBVIEW,
                globalEnabled = false,
                siteOverride = true
            )
        )
    }

    @Test
    fun geckoViewDoesNotShowClickAdFilterAsActiveOrEditable() {
        assertEquals(
            QuickToggleAdBlockState(
                checked = false,
                toggleEnabled = false,
                siteOverrideActive = false
            ),
            QuickTogglesAvailabilityPolicy.adBlockState(
                BrowserTabEngine.GECKOVIEW,
                globalEnabled = true,
                siteOverride = true
            )
        )
    }
}
