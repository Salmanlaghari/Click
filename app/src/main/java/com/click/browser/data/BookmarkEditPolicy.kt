package com.click.browser.data

import java.net.URI

internal enum class BookmarkEditValidationError {
    EMPTY_TITLE,
    INVALID_URL
}

internal enum class BookmarkEditResult {
    UPDATED,
    DUPLICATE_URL,
    NOT_FOUND,
    INVALID_INPUT
}

internal data class BookmarkEditDecision(
    val result: BookmarkEditResult,
    val bookmarks: List<Bookmark>
)

/** Only complete HTTP(S) web URLs can be saved through the edit form. */
internal fun validateBookmarkEdit(title: String, url: String): BookmarkEditValidationError? {
    if (title.trim().isEmpty()) return BookmarkEditValidationError.EMPTY_TITLE
    val parsed = runCatching { URI(url.trim()) }.getOrNull()
        ?: return BookmarkEditValidationError.INVALID_URL
    val isWebScheme = parsed.scheme.equals("http", ignoreCase = true) ||
        parsed.scheme.equals("https", ignoreCase = true)
    return if (isWebScheme && !parsed.host.isNullOrBlank()) {
        null
    } else {
        BookmarkEditValidationError.INVALID_URL
    }
}

/** Replaces one saved bookmark in place while preserving list order and rejecting URL collisions. */
internal fun applyBookmarkEdit(
    bookmarks: List<Bookmark>,
    existingUrl: String,
    replacement: Bookmark
): BookmarkEditDecision {
    if (validateBookmarkEdit(replacement.title, replacement.url) != null) {
        return BookmarkEditDecision(BookmarkEditResult.INVALID_INPUT, bookmarks)
    }

    val existingIndex = bookmarks.indexOfFirst { it.url == existingUrl }
    if (existingIndex < 0) return BookmarkEditDecision(BookmarkEditResult.NOT_FOUND, bookmarks)

    val normalized = Bookmark(
        replacement.title.trim(),
        replacement.url.trim(),
        bookmarks[existingIndex].folder
    )
    val collides = bookmarks.indices.any { index ->
        index != existingIndex && bookmarks[index].url == normalized.url
    }
    if (collides) return BookmarkEditDecision(BookmarkEditResult.DUPLICATE_URL, bookmarks)

    val updated = bookmarks.toMutableList()
    updated[existingIndex] = normalized
    return BookmarkEditDecision(BookmarkEditResult.UPDATED, updated)
}
