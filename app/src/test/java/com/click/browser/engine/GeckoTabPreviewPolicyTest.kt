package com.click.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoTabPreviewPolicyTest {
    @Test
    fun regularHttpHttpsAndExactBlankTabsCanBeCaptured() {
        assertTrue(shouldCaptureGeckoTabPreview("http://example.test/page", isIncognito = false))
        assertTrue(shouldCaptureGeckoTabPreview("https://example.test/page", isIncognito = false))
        assertTrue(shouldCaptureGeckoTabPreview("https://例え.テスト/page", isIncognito = false))
        assertTrue(shouldCaptureGeckoTabPreview("https://例え.テスト:443/page", isIncognito = false))
        assertTrue(shouldCaptureGeckoTabPreview("https://[2001:db8::1]/page", isIncognito = false))
        assertTrue(shouldCaptureGeckoTabPreview("about:blank", isIncognito = false))
        assertFalse(shouldCaptureGeckoTabPreview("https://example.test/page", isIncognito = true))
        assertFalse(shouldCaptureGeckoTabPreview("about:blank", isIncognito = true))
        assertFalse(shouldCaptureGeckoTabPreview("about:config", isIncognito = false))
        assertFalse(shouldCaptureGeckoTabPreview("about:blank#fragment", isIncognito = false))
        assertFalse(shouldCaptureGeckoTabPreview("javascript:alert(1)", isIncognito = false))
        assertFalse(shouldCaptureGeckoTabPreview("file:///tmp/page.html", isIncognito = false))
        assertFalse(shouldCaptureGeckoTabPreview("https:///missing-host", isIncognito = false))
        assertFalse(shouldCaptureGeckoTabPreview("https://:443/foo", isIncognito = false))
        assertFalse(shouldCaptureGeckoTabPreview("https://example.test:bad/foo", isIncognito = false))
        assertFalse(shouldCaptureGeckoTabPreview("https://example.test:65536/foo", isIncognito = false))
        assertFalse(shouldCaptureGeckoTabPreview("https://example.test:+443/foo", isIncognito = false))
        assertFalse(shouldCaptureGeckoTabPreview("https://example.test:٤٤٣/foo", isIncognito = false))
        assertFalse(shouldCaptureGeckoTabPreview("https://[2001:db8::1]:+443/foo", isIncognito = false))
        assertFalse(shouldCaptureGeckoTabPreview("https://user:pass@example.test/", isIncognito = false))
        assertFalse(
            shouldCaptureGeckoTabPreview(
                "https://example.test/${"x".repeat(8192)}",
                isIncognito = false
            )
        )
        assertFalse(shouldCaptureGeckoTabPreview(null, isIncognito = false))
    }

    @Test
    fun matchingCurrentUrlAndGenerationCanBeStored() {
        assertTrue(
            shouldStoreGeckoTabPreview(
                capturedUrl = "https://example.test/page",
                currentUrl = "https://example.test/page",
                capturedGeneration = 7,
                currentGeneration = 7,
                isIncognito = false
            )
        )
    }

    @Test
    fun exactBlankPreviewCanBeStoredOnlyForCurrentRegularTab() {
        assertTrue(
            shouldStoreGeckoTabPreview(
                capturedUrl = "about:blank",
                currentUrl = "about:blank",
                capturedGeneration = 3,
                currentGeneration = 3,
                isIncognito = false
            )
        )
        assertFalse(
            shouldStoreGeckoTabPreview(
                capturedUrl = "about:blank",
                currentUrl = "about:blank",
                capturedGeneration = 3,
                currentGeneration = 4,
                isIncognito = false
            )
        )
        assertFalse(
            shouldStoreGeckoTabPreview(
                capturedUrl = "about:blank",
                currentUrl = "about:blank",
                capturedGeneration = 3,
                currentGeneration = 3,
                isIncognito = true
            )
        )
    }

    @Test
    fun staleUrlGenerationAndPrivateResultsAreRejected() {
        assertFalse(
            shouldStoreGeckoTabPreview(
                capturedUrl = "https://example.test/old",
                currentUrl = "https://example.test/new",
                capturedGeneration = 7,
                currentGeneration = 8,
                isIncognito = false
            )
        )
        assertFalse(
            shouldStoreGeckoTabPreview(
                capturedUrl = "https://example.test/page",
                currentUrl = "https://example.test/page",
                capturedGeneration = 7,
                currentGeneration = 8,
                isIncognito = false
            )
        )
        assertFalse(
            shouldStoreGeckoTabPreview(
                capturedUrl = "https://example.test/page",
                currentUrl = "https://example.test/page",
                capturedGeneration = 7,
                currentGeneration = 7,
                isIncognito = true
            )
        )
    }
}
