package com.click.browser.ui.screens

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
    fun clickInternalSchemeRemainsNormalizedForNativeInterception() {
        assertEquals(
            "click://settings",
            formatBrowserInputUrl("CLICK://Settings", "Google") { _, _ -> error("Internal page should not search") }
        )
        assertTrue(formatBrowserInputUrl("https://example.com", "Google") { _, _ -> error("URL should not search") }.startsWith("https://"))
    }

    @Test
    fun narrowToolbarHidesSecondaryActionsButWideToolbarCanShowThem() {
        assertFalse(shouldShowToolbarTranslate(360))
        assertTrue(shouldShowToolbarTranslate(370))
        assertFalse(shouldShowToolbarExtensions(419))
        assertTrue(shouldShowToolbarExtensions(420))
    }
}
