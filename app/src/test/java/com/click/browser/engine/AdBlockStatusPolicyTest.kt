package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockStatusPolicyTest {
    @Test
    fun readyWebViewFilterIsReportedAsActiveWithoutAggregateCount() {
        val status = AdBlockStatusPolicy.forPage(
            engine = BrowserTabEngine.WEBVIEW,
            globalEnabled = true,
            siteOverride = null,
            readiness = FilterListReadiness.READY
        )

        assertEquals("Ad & tracker filter ready", status.label)
        assertTrue(status.active)
    }

    @Test
    fun loadingListDoesNotShowFullProtectionAsActive() {
        val status = AdBlockStatusPolicy.forPage(
            engine = BrowserTabEngine.WEBVIEW,
            globalEnabled = true,
            siteOverride = null,
            readiness = FilterListReadiness.LOADING
        )

        assertEquals("Filter list loading · built-in rules only", status.label)
        assertFalse(status.active)
    }

    @Test
    fun unavailableListReportsBuiltInOnlyFallbackWithoutGreenStatus() {
        val status = AdBlockStatusPolicy.forPage(
            engine = BrowserTabEngine.WEBVIEW,
            globalEnabled = true,
            siteOverride = null,
            readiness = FilterListReadiness.BUILTIN_ONLY
        )

        assertEquals("Filter list unavailable · built-in rules only", status.label)
        assertFalse(status.active)
    }

    @Test
    fun siteOverrideOffWinsOverEnabledGlobalPreference() {
        val status = AdBlockStatusPolicy.forPage(
            engine = BrowserTabEngine.WEBVIEW,
            globalEnabled = true,
            siteOverride = false,
            readiness = FilterListReadiness.READY
        )

        assertEquals("Ad & tracker filter off for this site", status.label)
        assertFalse(status.active)
    }

    @Test
    fun siteOverrideOnWinsOverDisabledGlobalPreference() {
        val status = AdBlockStatusPolicy.forPage(
            engine = BrowserTabEngine.WEBVIEW,
            globalEnabled = false,
            siteOverride = true,
            readiness = FilterListReadiness.READY
        )

        assertEquals("Ad & tracker filter ready", status.label)
        assertTrue(status.active)
    }

    @Test
    fun geckoViewReportsItsOwnProtectionInsteadOfClickWebViewFilter() {
        val status = AdBlockStatusPolicy.forPage(
            engine = BrowserTabEngine.GECKOVIEW,
            globalEnabled = false,
            siteOverride = false,
            readiness = FilterListReadiness.LOADING
        )

        assertEquals("GeckoView tracking protection enabled", status.label)
        assertTrue(status.active)
    }
}
