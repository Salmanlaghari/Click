package com.click.browser.engine

import com.click.browser.data.HistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestionsTest {

    @Test
    fun suggestUrlsExistForEndpointEnginesOnly() {
        assertTrue(SearchSuggestions.suggestUrl("Google", "a%20b")!!.contains("q=a%20b"))
        assertTrue(SearchSuggestions.suggestUrl("Bing", "x")!!.contains("query=x"))
        assertTrue(SearchSuggestions.suggestUrl("DuckDuckGo", "x")!!.contains("q=x"))
        assertTrue(SearchSuggestions.suggestUrl("Brave Search", "x")!!.contains("q=x"))
        // Engines without a public endpoint we rely on get local-only rows.
        assertNull(SearchSuggestions.suggestUrl("Yandex", "x"))
        assertNull(SearchSuggestions.suggestUrl("Startpage", "x"))
        assertNull(SearchSuggestions.suggestUrl("Perplexity", "x"))
    }

    @Test
    fun parseResponseReadsOpenSearchShape() {
        val body = "[\"wea\",[\"weather\",\"weather tomorrow\",\"weather radar\"],[\"\"],[]]"
        assertEquals(
            listOf("weather", "weather tomorrow", "weather radar"),
            SearchSuggestions.parseResponse(body)
        )
    }

    @Test
    fun parseResponseToleratesGarbageAndCapsResults() {
        assertEquals(emptyList<String>(), SearchSuggestions.parseResponse("not json"))
        assertEquals(emptyList<String>(), SearchSuggestions.parseResponse("[\"q\"]"))
        assertEquals(emptyList<String>(), SearchSuggestions.parseResponse("[\"q\",[]]"))
        val many = "[\"q\",[" + (1..20).joinToString(",") { "\"s$it\"" } + "]]"
        assertEquals(SearchSuggestions.MAX_REMOTE, SearchSuggestions.parseResponse(many).size)
    }

    @Test
    fun localMatchesFilterSortAndDedupe() {
        val history = listOf(
            HistoryItem("Weather in Karachi", "https://weather.example/karachi", 100L),
            HistoryItem("News", "https://news.example", 300L),
            HistoryItem("Karachi weather radar", "https://radar.example", 200L),
            HistoryItem("Duplicate", "https://weather.example/karachi", 400L)
        )
        val matches = SearchSuggestions.localMatches("weather", history)
        // Newest matching URL first; the duplicate URL appears once.
        assertEquals(
            listOf("https://weather.example/karachi", "https://radar.example"),
            matches.map { it.url }
        )
        assertTrue(matches.all { it.fromHistory })
        // Too-short queries and non-matches yield nothing.
        assertEquals(emptyList<SearchSuggestion>(), SearchSuggestions.localMatches("w", history))
        assertEquals(emptyList<SearchSuggestion>(), SearchSuggestions.localMatches("zzz", history))
    }

    @Test
    fun mergeKeepsLocalFirstAndSkipsDuplicatesAndQuery() {
        val local = listOf(
            SearchSuggestion("Weather in Karachi", "https://weather.example", fromHistory = true)
        )
        val merged = SearchSuggestions.merge(
            local,
            listOf("weather", "weather in karachi", "weather tomorrow", "Weather In Karachi"),
            query = "weather"
        )
        assertEquals(
            listOf("Weather in Karachi", "weather tomorrow"),
            merged.map { it.text }
        )
    }
}
