package com.click.browser.engine

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsFeedTest {
    @Test
    fun imageSelectionPrefersTheLargestPublisherSuppliedCandidate() {
        val selected = selectBestNewsImageCandidate(
            listOf(
                NewsImageCandidate("https://publisher.example/thumb.jpg", 240, 135, 2),
                NewsImageCandidate("https://publisher.example/large.jpg", 1280, 720, 1),
                NewsImageCandidate("https://publisher.example/medium.jpg", 640, 360, 3)
            )
        )

        assertEquals("https://publisher.example/large.jpg", selected?.url)
    }

    @Test
    fun unknownImageDimensionsFallBackToSourcePriority() {
        val selected = selectBestNewsImageCandidate(
            listOf(
                NewsImageCandidate("https://publisher.example/thumbnail.jpg", sourcePriority = 1),
                NewsImageCandidate("https://publisher.example/content.jpg", sourcePriority = 3)
            )
        )

        assertEquals("https://publisher.example/content.jpg", selected?.url)
        assertNull(selectBestNewsImageCandidate(emptyList()))
    }

    @Test
    fun unparseableOrMissingDatesStayUnavailableInsteadOfPretendingToBeNow() {
        assertEquals(0L, NewsFeed.parseDate(""))
        assertEquals(0L, NewsFeed.parseDate("not a date"))
        assertEquals("date unavailable", NewsFeed.timeAgo(0L))
        assertEquals(
            Instant.parse("2026-11-02T06:30:27Z").toEpochMilli(),
            NewsFeed.parseDate("2026-11-02T06:30:27Z")
        )
    }

    @Test
    fun onlyRecentResearchItemsAreShownWhileOtherNewsKeepsItsNormalWindow() {
        val now = Instant.parse("2026-11-03T00:00:00Z").toEpochMilli()
        val recent = NewsArticle("Recent", "https://arxiv.org/abs/1", "arXiv AI/ML", now - 1_000, null, NewsCategory.RESEARCH)
        val stale = recent.copy(publishedAt = now - NewsFeed.MAX_RESEARCH_AGE_MS - 1)
        val undated = recent.copy(publishedAt = 0L)
        val oldPublisherNews = NewsArticle("Publisher", "https://news.example/a", "Publisher", 1L, null, NewsCategory.NEWS)

        assertTrue(NewsFeed.isFreshResearchArticle(recent, now))
        assertFalse(NewsFeed.isFreshResearchArticle(stale, now))
        assertFalse(NewsFeed.isFreshResearchArticle(undated, now))
        assertTrue(NewsFeed.isFreshResearchArticle(oldPublisherNews, now))
    }
}
