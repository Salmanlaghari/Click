package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.8.9 B4 search-privacy facts: DuckDuckGo is always reachable as a search
 * choice (the address-bar picker offers the full engine list in every mode),
 * and its search/suggest endpoints are wired. Per-mode first-run defaults
 * stay the distinct set guarded by ModePersonalizationTest.
 */
class SearchPrivacyDefaultsTest {

    @Test
    fun duckDuckGoIsInTheFullEnginePickerForEveryMode() {
        assertTrue(ModePersonalization.allSearchEngines.contains("DuckDuckGo"))
    }

    @Test
    fun duckDuckGoIsInMostModeShortlists() {
        assertTrue(ModePersonalization.searchEngines(BrowserMode.SIMPLE).contains("DuckDuckGo"))
        assertTrue(ModePersonalization.searchEngines(BrowserMode.DEVELOPER).contains("DuckDuckGo"))
        assertTrue(ModePersonalization.searchEngines(BrowserMode.HACK).contains("DuckDuckGo"))
    }

    @Test
    fun developerModeDefaultsToDuckDuckGo() {
        assertEquals("DuckDuckGo", ModePersonalization.defaultSearchEngine(BrowserMode.DEVELOPER))
    }

    @Test
    fun duckDuckGoHasSearchAndSuggestEndpoints() {
        assertTrue(
            ModePersonalization.searchUrl("DuckDuckGo", "test+query")
                .startsWith("https://duckduckgo.com/?q=")
        )
        assertTrue(
            SearchSuggestions.suggestUrl("DuckDuckGo", "test")?.startsWith("https://duckduckgo.com/ac/")
                ?: false
        )
    }
}
