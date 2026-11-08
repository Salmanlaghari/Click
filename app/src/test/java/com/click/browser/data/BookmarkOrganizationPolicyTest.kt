package com.click.browser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkOrganizationPolicyTest {

    @Test
    fun moveAssignsNormalizedFolderAndPreservesListOrder() {
        val original = listOf(
            Bookmark("First", "https://first.example", "Reading"),
            Bookmark("Second", "https://second.example")
        )

        val decision = BookmarkOrganizationPolicy.move(original, "https://second.example", "  Work  ")

        assertEquals(BookmarkOrganizationResult.UPDATED, decision.result)
        assertEquals(
            listOf(
                Bookmark("First", "https://first.example", "Reading"),
                Bookmark("Second", "https://second.example", "Work")
            ),
            decision.bookmarks
        )
    }

    @Test
    fun blankFolderMovesBookmarkToUnfiled() {
        val original = listOf(Bookmark("First", "https://first.example", "Reading"))

        val decision = BookmarkOrganizationPolicy.move(original, "https://first.example", "  ")

        assertEquals(BookmarkOrganizationResult.UPDATED, decision.result)
        assertEquals(Bookmark("First", "https://first.example"), decision.bookmarks.single())
    }

    @Test
    fun importedFoldersAreNormalizedAndInvalidLabelsBecomeUnfiled() {
        val bookmark = Bookmark("Example", "https://example.com", "  Work  ")
        assertEquals(
            Bookmark("Example", "https://example.com", "Work"),
            BookmarkOrganizationPolicy.normalizeImportedBookmark(bookmark)
        )

        listOf(
            " ",
            "x".repeat(BookmarkOrganizationPolicy.MAX_FOLDER_NAME_LENGTH + 1),
            "Work/2026",
            "Work\\2026"
        ).forEach { invalidFolder ->
            assertEquals(
                Bookmark("Example", "https://example.com"),
                BookmarkOrganizationPolicy.normalizeImportedBookmark(
                    bookmark.copy(folder = invalidFolder)
                )
            )
        }
    }

    @Test
    fun invalidFolderAndMissingBookmarkDoNotChangeTheList() {
        val original = listOf(Bookmark("First", "https://first.example"))

        val tooLong = BookmarkOrganizationPolicy.move(
            original,
            "https://first.example",
            "x".repeat(BookmarkOrganizationPolicy.MAX_FOLDER_NAME_LENGTH + 1)
        )
        val nestedPath = BookmarkOrganizationPolicy.move(original, "https://first.example", "Work/2026")
        val missing = BookmarkOrganizationPolicy.move(original, "https://missing.example", "Work")

        assertEquals(BookmarkOrganizationResult.INVALID_FOLDER, tooLong.result)
        assertEquals(BookmarkOrganizationResult.INVALID_FOLDER, nestedPath.result)
        assertEquals(BookmarkOrganizationResult.NOT_FOUND, missing.result)
        assertEquals(original, tooLong.bookmarks)
        assertEquals(original, nestedPath.bookmarks)
        assertEquals(original, missing.bookmarks)
    }
}
