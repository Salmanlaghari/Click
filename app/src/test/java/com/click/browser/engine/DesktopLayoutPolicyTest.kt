package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLayoutPolicyTest {
    @Test
    fun profileAndGlobalDefaultsAreRetainedAcrossNavigationPolicyChecks() {
        assertFalse(DesktopLayoutPolicy.defaultEnabled(BrowserMode.SIMPLE))
        assertFalse(DesktopLayoutPolicy.defaultEnabled(BrowserMode.DEVELOPER))
        assertTrue(DesktopLayoutPolicy.defaultEnabled(BrowserMode.HACK))
        assertTrue(DesktopLayoutPolicy.defaultEnabled(BrowserMode.ADVANCED))
        assertTrue(DesktopLayoutPolicy.defaultEnabled(BrowserMode.SIMPLE, desktopDefault = true))
        assertTrue(DesktopLayoutPolicy.defaultEnabled(BrowserMode.SIMPLE, forceDesktopMode = true))
    }

    @Test
    fun explicitSitePreferenceOverridesProfileAndGlobalDefaults() {
        assertTrue(
            DesktopLayoutPolicy.enabledForHost(
                host = "example.com",
                overrides = mapOf("example.com" to true),
                mode = BrowserMode.SIMPLE
            )
        )
        assertFalse(
            DesktopLayoutPolicy.enabledForHost(
                host = "example.com",
                overrides = mapOf("example.com" to false),
                mode = BrowserMode.HACK,
                desktopDefault = true
            )
        )
    }

    @Test
    fun exactAndMoreSpecificSubdomainPreferencesWinOverParentDomains() {
        val overrides = mapOf(
            "example.com" to true,
            "m.example.com" to false
        )
        assertTrue(
            DesktopLayoutPolicy.enabledForHost(
                "www.example.com", overrides, BrowserMode.SIMPLE
            )
        )
        assertFalse(
            DesktopLayoutPolicy.enabledForHost(
                "m.example.com", overrides, BrowserMode.SIMPLE
            )
        )
        assertFalse(
            DesktopLayoutPolicy.enabledForHost(
                "shop.m.example.com", overrides, BrowserMode.SIMPLE
            )
        )
        assertFalse(
            DesktopLayoutPolicy.enabledForHost(
                "notexample.com", overrides, BrowserMode.SIMPLE
            )
        )
    }

    @Test
    fun enabledLayoutUsesOnlyStandardWideViewportSettings() {
        assertEquals(
            DesktopLayoutPolicy.WebViewLayout(true, true),
            DesktopLayoutPolicy.webViewLayout(enabled = true)
        )
        assertEquals(
            DesktopLayoutPolicy.WebViewLayout(false, false),
            DesktopLayoutPolicy.webViewLayout(enabled = false)
        )
    }
}
