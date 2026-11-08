package com.click.browser.data

/** Validates the current page and avoids duplicate bookmark URLs. */
internal object BookmarkAddPolicy {

    fun prepare(title: String, url: String, folderName: String? = null): Bookmark? {
        val normalizedUrl = url.trim()
        val normalizedTitle = title.trim().ifBlank { normalizedUrl }
        if (validateBookmarkEdit(normalizedTitle, normalizedUrl) != null) return null
        val normalizedFolder = when {
            folderName.isNullOrBlank() -> null
            else -> BookmarkOrganizationPolicy.normalizeFolderName(folderName) ?: return null
        }
        return Bookmark(normalizedTitle, normalizedUrl, normalizedFolder)
    }

    fun shouldAdd(existingUrls: List<String>, candidate: Bookmark): Boolean {
        if (validateBookmarkEdit(candidate.title, candidate.url) != null) return false
        if (candidate.folder != null && BookmarkOrganizationPolicy.normalizeFolderName(candidate.folder) != candidate.folder) {
            return false
        }
        return existingUrls.none { it == candidate.url }
    }
}
