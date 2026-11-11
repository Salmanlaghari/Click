package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prince's news personalisation: Latest News, Gossip, Songs categories,
 *  interest mapping, and feed coverage. */
class NewsInterestsTest {

    @Test
    fun princeCategoriesExistWithFriendlyLabels() {
        assertEquals("News", NewsCategory.NEWS.label)
        assertEquals("Gossip", NewsCategory.ENTERTAINMENT.label)
        assertEquals("Songs", NewsCategory.MUSIC.label)
    }

    @Test
    fun everyNewCategoryHasAtLeastOneFeed() {
        val covered = NewsFeed.feeds.map { it.category }.toSet()
        assertTrue(NewsCategory.ENTERTAINMENT in covered)
        assertTrue(NewsCategory.MUSIC in covered)
        assertTrue(NewsCategory.NEWS in covered)
    }

    @Test
    fun allFeedUrlsAreHttps() {
        NewsFeed.feeds.forEach { feed ->
            assertTrue("${feed.source} must be https", feed.url.startsWith("https://"))
        }
    }

    @Test
    fun topicNamesMapToCategoriesDroppingJunkAndAll() {
        val mapped = NewsNotifications.categoriesForTopics(
            setOf("NEWS", "ENTERTAINMENT", "MUSIC", "ALL", "BOGUS")
        )
        assertEquals(
            setOf(NewsCategory.NEWS, NewsCategory.ENTERTAINMENT, NewsCategory.MUSIC),
            mapped.toSet()
        )
    }

    @Test
    fun notificationDefaultsAndChoicesIncludeGossipAndSongs() {
        assertTrue(NewsCategory.ENTERTAINMENT.name in NewsNotifications.DEFAULT_TOPICS)
        assertTrue(NewsCategory.MUSIC.name in NewsNotifications.DEFAULT_TOPICS)
        val choiceNames = NewsNotifications.TOPIC_CHOICES.map { it.first }
        assertTrue(NewsCategory.ENTERTAINMENT.name in choiceNames)
        assertTrue(NewsCategory.MUSIC.name in choiceNames)
        assertFalse(NewsCategory.ALL.name in choiceNames)
    }

    @Test
    fun interestFilterKeepsOnlyChosenCategories() {
        val articles = listOf(
            article(NewsCategory.NEWS),
            article(NewsCategory.MUSIC),
            article(NewsCategory.SPORTS)
        )
        val interests = NewsNotifications.categoriesForTopics(setOf("MUSIC"))
        val filtered = articles.filter { it.category in interests }
        assertEquals(1, filtered.size)
        assertEquals(NewsCategory.MUSIC, filtered.single().category)
    }

    private fun article(category: NewsCategory) = NewsArticle(
        title = "t",
        link = "https://example.com",
        source = "Test",
        publishedAt = 0L,
        thumbnailUrl = null,
        category = category
    )
}
