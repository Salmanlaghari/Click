package com.click.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryHostFilterTest {
    @Test
    fun removesOnlyExactHostAcrossSchemesPortsCaseAndRootDot() {
        val entries = listOf(
            HistoryItem("plain", "http://Example.com/one", 1L),
            HistoryItem("port", "https://EXAMPLE.COM.:8443/two", 2L),
            HistoryItem("subdomain", "https://shop.example.com/", 3L),
            HistoryItem("deep subdomain", "https://deep.shop.example.com/", 4L),
            HistoryItem("deceptive suffix", "https://notexample.com/", 5L),
            HistoryItem("sibling", "https://example.net/", 6L),
            HistoryItem("hostless", "about:blank", 7L),
            HistoryItem("malformed", "not a valid URL", 8L)
        )

        val retained = HistoryHostFilter.retainOtherHosts(
            entries = entries,
            normalizedHost = "EXAMPLE.COM.",
            urlOf = { it.url }
        )

        assertEquals(entries.drop(2), retained)
        assertEquals(
            listOf("subdomain", "deep subdomain", "deceptive suffix", "sibling", "hostless", "malformed"),
            retained.map { it.title }
        )
        assertEquals(listOf(3L, 4L, 5L, 6L, 7L, 8L), retained.map { it.timestamp })
    }

    @Test
    fun parentChildSiblingAndLookalikeHostsDoNotMatch() {
        val entries = listOf(
            HistoryItem("parent", "https://example.com/", 1L),
            HistoryItem("child", "https://deep.shop.example.com/", 2L),
            HistoryItem("sibling", "https://other.shop.example.com/", 3L),
            HistoryItem("lookalike", "https://notshop.example.com/", 4L)
        )

        val retained = HistoryHostFilter.retainOtherHosts(
            entries = entries,
            normalizedHost = "shop.example.com",
            urlOf = { it.url }
        )

        assertEquals(entries, retained)
    }

    @Test
    fun internationalizedDomainNamesNormalizeToAscii() {
        assertEquals("xn--bcher-kva.example", HistoryHostFilter.normalizeHost("bücher.example"))
        assertEquals("xn--bcher-kva.example", HistoryHostFilter.hostForUrl("https://bücher.example/path"))

        val entries = listOf(
            HistoryItem("unicode", "https://bücher.example/", 1L),
            HistoryItem("ascii", "https://xn--bcher-kva.example/", 2L),
            HistoryItem("other", "https://bucher.example/", 3L)
        )
        assertEquals(
            listOf(entries.last()),
            HistoryHostFilter.retainOtherHosts(entries, "xn--bcher-kva.example") { it.url }
        )
    }

    @Test
    fun malformedUrlsAndInvalidTargetsFailClosed() {
        assertNull(HistoryHostFilter.hostForUrl("not a valid URL"))
        assertNull(HistoryHostFilter.hostForUrl("file:///private/page.html"))
        assertNull(HistoryHostFilter.normalizeHost(""))
        assertNull(HistoryHostFilter.normalizeHost("example.com/path"))
        assertNull(HistoryHostFilter.normalizeHost("example.com:bad"))

        val entries = listOf(
            HistoryItem("valid", "https://example.com/", 1L),
            HistoryItem("hostless", "about:blank", 2L)
        )
        assertEquals(entries, HistoryHostFilter.retainOtherHosts(entries, "example.com/path") { it.url })
    }
}
