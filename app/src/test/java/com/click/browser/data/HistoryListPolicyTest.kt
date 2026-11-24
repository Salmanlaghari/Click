package com.click.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HistoryListPolicyTest {

    private fun item(url: String, timestamp: Long, title: String = "Title $url") =
        HistoryItem(title = title, url = url, timestamp = timestamp)

    @Test
    fun upsertAppendsNewVisit() {
        val existing = listOf(item("https://a.example", 1))
        val result = HistoryListPolicy.upsert(existing, item("https://b.example", 2))
        assertEquals(listOf("https://a.example", "https://b.example"), result.map { it.url })
    }

    @Test
    fun upsertReplacesEarlierVisitToSameUrl() {
        val existing = listOf(
            item("https://a.example", 1),
            item("https://b.example", 2),
            item("https://a.example", 3)
        )
        val result = HistoryListPolicy.upsert(existing, item("https://a.example", 4))
        assertEquals(2, result.size)
        assertEquals("https://b.example", result[0].url)
        assertEquals(4L, result[1].timestamp)
    }

    @Test
    fun upsertTrimsOldestBeyondCap() {
        val existing = (1..5).map { item("https://site$it.example", it.toLong()) }
        val result = HistoryListPolicy.upsert(existing, item("https://new.example", 6), maxEntries = 3)
        assertEquals(
            listOf("https://site4.example", "https://site5.example", "https://new.example"),
            result.map { it.url }
        )
    }

    @Test
    fun defaultCapIsGenerous() {
        assertEquals(2000, HistoryListPolicy.MAX_ENTRIES)
    }

    @Test
    fun titleBackfillFillsUrlLabelledEntry() {
        val existing = listOf(
            item("https://a.example", 1, title = "https://a.example"),
            item("https://b.example", 2)
        )
        val result = HistoryListPolicy.withUpdatedTitle(existing, "https://a.example", "Real Title")
        assertEquals("Real Title", result[0].title)
        assertEquals("Title https://b.example", result[1].title)
    }

    @Test
    fun titleBackfillNeverOverwritesRealTitle() {
        val existing = listOf(item("https://a.example", 1, title = "Original"))
        val result = HistoryListPolicy.withUpdatedTitle(existing, "https://a.example", "Other")
        assertSame(existing, result)
    }

    @Test
    fun titleBackfillIgnoresBlankTitleAndUnknownUrl() {
        val existing = listOf(item("https://a.example", 1, title = ""))
        assertSame(existing, HistoryListPolicy.withUpdatedTitle(existing, "https://a.example", "  "))
        assertSame(existing, HistoryListPolicy.withUpdatedTitle(existing, "https://z.example", "Nope"))
    }
}
