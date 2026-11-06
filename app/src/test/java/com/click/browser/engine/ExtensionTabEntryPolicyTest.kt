package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionTabEntryPolicyTest {
    @Test
    fun privateTabsNeverEnterTheExtensionStoreFlow() {
        assertEquals(
            ExtensionTabEntryAction.PRIVATE_TAB_UNAVAILABLE,
            extensionTabEntryAction(BrowserTabEngine.WEBVIEW, isPrivate = true)
        )
        assertEquals(
            ExtensionTabEntryAction.PRIVATE_TAB_UNAVAILABLE,
            extensionTabEntryAction(BrowserTabEngine.GECKOVIEW, isPrivate = true)
        )
    }

    @Test
    fun extensionsTapOpensTheStoreImmediatelyForBothNormalTabEngines() {
        assertEquals(
            ExtensionTabEntryAction.OPEN_AMO_STORE,
            extensionTabEntryAction(BrowserTabEngine.WEBVIEW, isPrivate = false)
        )
        assertEquals(
            ExtensionTabEntryAction.OPEN_AMO_STORE,
            extensionTabEntryAction(BrowserTabEngine.GECKOVIEW, isPrivate = false)
        )
    }

    @Test
    fun sameTabSwitchIsOfferedOnlyAfterInstallAndOnlyForTheOriginTab() {
        assertTrue(
            shouldOfferPostInstallGeckoSwitch(
                originTabIsStillSelected = true,
                hasEnabledInstalledExtension = true,
                engine = BrowserTabEngine.WEBVIEW,
                isPrivate = false,
                url = "https://example.com/path"
            )
        )
        assertFalse(
            shouldOfferPostInstallGeckoSwitch(
                originTabIsStillSelected = false,
                hasEnabledInstalledExtension = true,
                engine = BrowserTabEngine.WEBVIEW,
                isPrivate = false,
                url = "https://example.com/path"
            )
        )
        assertFalse(
            shouldOfferPostInstallGeckoSwitch(
                originTabIsStillSelected = true,
                hasEnabledInstalledExtension = false,
                engine = BrowserTabEngine.WEBVIEW,
                isPrivate = false,
                url = "https://example.com/path"
            )
        )
        assertFalse(
            shouldOfferPostInstallGeckoSwitch(
                originTabIsStillSelected = true,
                hasEnabledInstalledExtension = true,
                engine = BrowserTabEngine.GECKOVIEW,
                isPrivate = false,
                url = "https://example.com/path"
            )
        )
        assertFalse(
            shouldOfferPostInstallGeckoSwitch(
                originTabIsStillSelected = true,
                hasEnabledInstalledExtension = true,
                engine = BrowserTabEngine.WEBVIEW,
                isPrivate = true,
                url = "https://example.com/path"
            )
        )
        assertFalse(
            shouldOfferPostInstallGeckoSwitch(
                originTabIsStillSelected = true,
                hasEnabledInstalledExtension = true,
                engine = BrowserTabEngine.WEBVIEW,
                isPrivate = false,
                url = "click://settings"
            )
        )
    }

    @Test
    fun onlyHttpHttpsAndBlankPagesCanSwitchToGecko() {
        assertTrue(isSupportedGeckoTabUrl("https://example.com/path"))
        assertTrue(isSupportedGeckoTabUrl("HTTP://example.com"))
        assertTrue(isSupportedGeckoTabUrl("about:blank"))
        assertFalse(isSupportedGeckoTabUrl("click://settings"))
        assertFalse(isSupportedGeckoTabUrl("file:///data/page.html"))
        assertFalse(isSupportedGeckoTabUrl("javascript:alert(1)"))
    }
}
