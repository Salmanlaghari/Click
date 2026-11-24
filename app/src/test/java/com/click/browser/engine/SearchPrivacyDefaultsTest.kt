package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.8.9 B4: every mode's default search engine is a privacy-respecting one,
 * and every mode offers DuckDuckGo in its picker shortlist.
 */
class SearchPrivacyDefaultsTest {

    @Test
    fun defaultEnginesArePrivacyRespecting() {
        assertEquals("DuckDuckGo", ModePersonalization.defaultSearchEngine(BrowserMode.SIMPLE))
        assertEquals("DuckDuckGo", ModePersonalization.defaultSearchEngine(BrowserMode.DEVELOPER))
        assertEquals("Brave Search", ModePersonalization.defaultSearchEngine(BrowserMode.HACK))
        assertEquals("Perplexity", ModePersonalization.defaultSearchEngine(BrowserMode.ADVANCED))
    }

    @Test
    fun duckDuckGoIsOfferedInEveryMode() {
        BrowserMode.values().forEach { mode ->
            assertTrue(
                "DuckDuckGo missing for $mode",
                ModePersonalization.searchEngines(mode).contains("DuckDuckGo")
            )
        }
    }

    @Test
    fun duckDuckGoHasSearchAndSuggestEndpoints() {
        assertTrue(
            ModePersonalization.searchUrl("DuckDuckGo", "test query")
                .startsWith("https://duckduckgo.com/?q=")
        )
        assertTrue(
            SearchSuggestions.suggestUrl("DuckDuckGo", "test")?.startsWith("https://duckduckgo.com/ac/")
                ?: false
        )
    }
}
