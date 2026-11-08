package com.click.browser.data

internal enum class BookmarkOrganizationResult {
    UPDATED,
    NOT_FOUND,
    INVALID_FOLDER
}

internal data class BookmarkOrganizationDecision(
    val result: BookmarkOrganizationResult,
    val bookmarks: List<Bookmark>
)

/** Validates folder labels and moves a bookmark without changing list order. */
internal object BookmarkOrganizationPolicy {
    const val MAX_FOLDER_NAME_LENGTH = 40

    fun normalizeFolderName(folderName: String): String? {
        val normalized = folderName.trim()
        if (normalized.isEmpty() || normalized.length > MAX_FOLDER_NAME_LENGTH) return null
        if ('/' in normalized || '\\' in normalized) return null
        return normalized
    }

    /** Keeps imported bookmarks but never persists folder labels outside the organizer's rules. */
    fun normalizeImportedBookmark(bookmark: Bookmark): Bookmark {
        val normalizedFolder = bookmark.folder?.let { normalizeFolderName(it) }
        return bookmark.copy(folder = normalizedFolder)
    }

    fun move(
        bookmarks: List<Bookmark>,
        existingUrl: String,
        folderName: String?
    ): BookmarkOrganizationDecision {
        val normalizedFolder = when {
            folderName.isNullOrBlank() -> null
            else -> normalizeFolderName(folderName)
                ?: return BookmarkOrganizationDecision(BookmarkOrganizationResult.INVALID_FOLDER, bookmarks)
        }
        val index = bookmarks.indexOfFirst { it.url == existingUrl }
        if (index < 0) {
            return BookmarkOrganizationDecision(BookmarkOrganizationResult.NOT_FOUND, bookmarks)
        }

        val updated = bookmarks.toMutableList()
        updated[index] = updated[index].copy(folder = normalizedFolder)
        return BookmarkOrganizationDecision(BookmarkOrganizationResult.UPDATED, updated)
    }
}
