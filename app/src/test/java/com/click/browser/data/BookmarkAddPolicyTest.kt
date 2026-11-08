package com.click.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkAddPolicyTest {

    @Test
    fun trimsPageValuesAndUsesUrlWhenTitleIsBlank() {
        assertEquals(
            Bookmark("Example page", "https://example.com/path"),
            BookmarkAddPolicy.prepare("  Example page  ", "  https://example.com/path  ")
        )
        assertEquals(
            Bookmark("https://example.com/", "https://example.com/"),
            BookmarkAddPolicy.prepare("  ", "https://example.com/")
        )
    }

    @Test
    fun rejectsHomeInternalAndMalformedUrls() {
        assertNull(BookmarkAddPolicy.prepare("Home", "about:blank"))
        assertNull(BookmarkAddPolicy.prepare("Local", "file:///private/page.html"))
        assertNull(BookmarkAddPolicy.prepare("Unsafe", "javascript:alert(1)"))
        assertNull(BookmarkAddPolicy.prepare("Missing host", "https:///path"))
        assertNull(BookmarkAddPolicy.prepare("Malformed", "not a URL"))
    }

    @Test
    fun preventsDuplicateUrlsButAllowsDifferentPages() {
        val existing = listOf("https://example.com/", "https://other.example/")
        assertFalse(BookmarkAddPolicy.shouldAdd(existing, Bookmark("Duplicate", "https://example.com/")))
        assertTrue(BookmarkAddPolicy.shouldAdd(existing, Bookmark("New page", "https://example.com/new")))
    }
}
