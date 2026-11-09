package com.click.browser.ui.screens

import com.click.browser.engine.ModePersonalization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserUrlInputTest {
    @Test
    fun uppercaseAndMixedCaseHttpSchemesArePreservedAsUrls() {
        var searchCalled = false
        val searchUrl: (String, String) -> String = { _, _ ->
            searchCalled = true
            "https://search.example/?q=unexpected"
        }

        assertEquals(
            "HTTPS://example.com/path?q=one",
            formatBrowserInputUrl("HTTPS://example.com/path?q=one", "Google", searchUrl)
        )
        assertEquals(
            "HtTp://example.com/path",
            formatBrowserInputUrl("HtTp://example.com/path", "Google", searchUrl)
        )
        assertFalse(searchCalled)
    }

    @Test
    fun plainHostAndSearchTextKeepTheirExistingBehavior() {
        assertEquals(
            "https://example.com",
            formatBrowserInputUrl("example.com", "Google") { _, _ -> error("Host should not search") }
        )

        var receivedEngine = ""
        var receivedQuery = ""
        val result = formatBrowserInputUrl("two words", "DuckDuckGo") { engine, query ->
            receivedEngine = engine
            receivedQuery = query
            "https://duckduckgo.com/?q=$query"
        }

        assertEquals("DuckDuckGo", receivedEngine)
        assertEquals("two+words", receivedQuery)
        assertEquals("https://duckduckgo.com/?q=two+words", result)
    }

    @Test
    fun googleAddressQueryEncodesPunctuationAndUnicodeForSelectedProvider() {
        val query = """C++ "quotes" & 50% #1? café"""
        val result = formatBrowserInputUrl(query, "Google") { engine, encodedQuery ->
            assertEquals("Google", engine)
            ModePersonalization.searchUrl(engine, encodedQuery)
        }

        assertEquals(
            "https://www.google.com/search?q=C%2B%2B+%22quotes%22+%26+50%25+%231%3F+caf%C3%A9",
            result
        )
    }
    @Test
    fun clickInternalSchemeRemainsNormalizedForNativeInterception() {
        assertEquals(
            "click://settings",
            formatBrowserInputUrl("CLICK://Settings", "Google") { _, _ -> error("Internal page should not search") }
        )
        assertTrue(formatBrowserInputUrl("https://example.com", "Google") { _, _ -> error("URL should not search") }.startsWith("https://"))
    }

}
