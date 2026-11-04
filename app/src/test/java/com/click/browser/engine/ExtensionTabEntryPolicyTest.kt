package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionTabEntryPolicyTest {
    @Test
    fun privateTabsNeverEnterTheExtensionFlow() {
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
    fun normalWebViewTabsRequireAnExplicitInPlaceSwitch() {
        assertEquals(
            ExtensionTabEntryAction.CONFIRM_GECKOVIEW_SWITCH,
            extensionTabEntryAction(BrowserTabEngine.WEBVIEW, isPrivate = false)
        )
    }

    @Test
    fun normalGeckoTabsOpenTheAddonManagerWithoutAnotherTab() {
        assertEquals(
            ExtensionTabEntryAction.OPEN_ADDON_MANAGER,
            extensionTabEntryAction(BrowserTabEngine.GECKOVIEW, isPrivate = false)
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
