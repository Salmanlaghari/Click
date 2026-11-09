package com.click.browser.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserErrorPageTest {
    @Test
    fun getRetryLinkTargetsTheFailedUrl() {
        val failedUrl = "https://example.test/path?q=one&next=two"
        val retryUrl = browserRetryUrl("GET", failedUrl)
        val html = browserErrorPageHtml("net::ERR_CONNECTION_FAILED", retryUrl)

        assertTrue(html.contains("href=\"https://example.test/path?q=one&amp;next=two\""))
        assertTrue(html.contains("Retry Connection"))
        assertFalse(html.contains("location.reload"))
    }

    @Test
    fun usesWarmCreamPaletteAndPreservesRetryControl() {
        val html = browserErrorPageHtml("net::ERR_CONNECTION_FAILED", "https://example.test/")

        assertTrue(html.contains("background-color: #FBF8F1"))
        assertTrue(html.contains("color: #302A22"))
        assertTrue(html.contains("color: #66533F"))
        assertTrue(html.contains("color: #393228"))
        assertTrue(html.contains("background-color: #66533F"))
        assertTrue(html.contains("color: #FFFEFB"))
        assertFalse(html.contains("#0f172a"))
        assertTrue(html.contains("Retry Connection"))
    }

    @Test
    fun retryIsUnavailableForNonGetOrNonHttpRequests() {
        assertNull(browserRetryUrl("POST", "https://example.test/submit"))
        assertNull(browserRetryUrl("GET", "javascript:alert(1)"))
        assertNull(browserRetryUrl("GET", "https:///missing-host"))

        val html = browserErrorPageHtml("request failed", browserRetryUrl("POST", "https://example.test/submit"))
        assertFalse(html.contains("<a class=\"btn\""))
        assertTrue(html.contains("Retry is not available for this request."))
    }

    @Test
    fun escapesErrorDetailsAndUrlBeforeAddingThemToHtml() {
        val html = browserErrorPageHtml(
            "<script>alert(\"x\")</script>&",
            "https://example.test/?q=\" onclick=\"alert(1)"
        )

        assertTrue(html.contains("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;&amp;"))
        assertTrue(html.contains("href=\"https://example.test/?q=&quot; onclick=&quot;alert(1)\""))
        assertFalse(html.contains("<script>"))
    }
}
