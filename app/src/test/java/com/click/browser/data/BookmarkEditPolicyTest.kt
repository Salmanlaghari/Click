package com.click.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarkEditPolicyTest {
    @Test
    fun acceptsCompleteHttpAndHttpsUrlsAndTrimsFormValues() {
        assertNull(validateBookmarkEdit(" Example ", " HTTPS://example.com/path?q=1 "))
        val original = listOf(Bookmark("First", "https://first.example"), Bookmark("Second", "https://second.example"))

        val decision = applyBookmarkEdit(
            bookmarks = original,
            existingUrl = "https://first.example",
            replacement = Bookmark("  Updated title  ", "  https://updated.example/path  ")
        )

        assertEquals(BookmarkEditResult.UPDATED, decision.result)
        assertEquals(
            listOf(Bookmark("Updated title", "https://updated.example/path"), original[1]),
            decision.bookmarks
        )
    }

    @Test
    fun editingBookmarkPreservesItsCurrentFolder() {
        val original = Bookmark("Guide", "https://guide.example", "Work")
        val decision = applyBookmarkEdit(
            bookmarks = listOf(original),
            existingUrl = original.url,
            replacement = Bookmark("Updated guide", "https://guide.example/updated")
        )

        assertEquals(BookmarkEditResult.UPDATED, decision.result)
        assertEquals(
            Bookmark("Updated guide", "https://guide.example/updated", "Work"),
            decision.bookmarks.single()
        )
    }

    @Test
    fun rejectsBlankTitlesAndNonHttpSchemesOrHostlessUrls() {
        assertEquals(BookmarkEditValidationError.EMPTY_TITLE, validateBookmarkEdit("  ", "https://example.com"))
        assertEquals(BookmarkEditValidationError.INVALID_URL, validateBookmarkEdit("Title", "javascript:alert(1)"))
        assertEquals(BookmarkEditValidationError.INVALID_URL, validateBookmarkEdit("Title", "file:///tmp/page.html"))
        assertEquals(BookmarkEditValidationError.INVALID_URL, validateBookmarkEdit("Title", "https:///missing-host"))
        assertEquals(BookmarkEditValidationError.INVALID_URL, validateBookmarkEdit("Title", "not a URL"))
    }

    @Test
    fun rejectsDuplicateUrlButAllowsKeepingTheCurrentUrl() {
        val bookmarks = listOf(Bookmark("First", "https://first.example"), Bookmark("Second", "https://second.example"))
        val duplicate = applyBookmarkEdit(
            bookmarks,
            "https://first.example",
            Bookmark("First renamed", "https://second.example")
        )
        assertEquals(BookmarkEditResult.DUPLICATE_URL, duplicate.result)
        assertEquals(bookmarks, duplicate.bookmarks)

        val sameUrl = applyBookmarkEdit(
            bookmarks,
            "https://first.example",
            Bookmark("First renamed", "https://first.example")
        )
        assertEquals(BookmarkEditResult.UPDATED, sameUrl.result)
        assertEquals("First renamed", sameUrl.bookmarks.first().title)
    }

    @Test
    fun reportsMissingSourceBookmarkWithoutChangingTheList() {
        val bookmarks = listOf(Bookmark("First", "https://first.example"))
        val decision = applyBookmarkEdit(
            bookmarks,
            "https://missing.example",
            Bookmark("Replacement", "https://replacement.example")
        )
        assertEquals(BookmarkEditResult.NOT_FOUND, decision.result)
        assertEquals(bookmarks, decision.bookmarks)
    }
}
