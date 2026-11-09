package com.click.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewNavigationStopPolicyTest {
    @Test
    fun uncommittedHttpAndHttpsNavigationsShowRecovery() {
        assertTrue(shouldShowWebViewStopRecovery(false, "https://example.test/search?q=click"))
        assertTrue(shouldShowWebViewStopRecovery(false, "http://example.test/"))
    }

    @Test
    fun committedContentDoesNotShowStoppedNavigationRecovery() {
        assertFalse(shouldShowWebViewStopRecovery(true, "https://example.test/next"))
    }

    @Test
    fun internalMalformedAndCredentialUrlsDoNotShowWebRecovery() {
        assertFalse(shouldShowWebViewStopRecovery(false, "about:blank"))
        assertFalse(shouldShowWebViewStopRecovery(false, "file:///tmp/page.html"))
        assertFalse(shouldShowWebViewStopRecovery(false, "javascript:alert(1)"))
        assertFalse(shouldShowWebViewStopRecovery(false, "https:///missing-host"))
        assertFalse(shouldShowWebViewStopRecovery(false, "https://user:pass@example.test/"))
    }

    @Test
    fun oversizedUrlDoesNotTriggerRecoveryParsing() {
        val oversized = "https://example.test/" + "a".repeat(8_192)
        assertFalse(shouldShowWebViewStopRecovery(false, oversized))
    }
}
