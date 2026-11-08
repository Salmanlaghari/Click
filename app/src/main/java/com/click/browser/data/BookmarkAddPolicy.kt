package com.click.browser.data

/** Validates the current page and avoids duplicate bookmark URLs. */
internal object BookmarkAddPolicy {

    fun prepare(title: String, url: String): Bookmark? {
        val normalizedUrl = url.trim()
        val normalizedTitle = title.trim().ifBlank { normalizedUrl }
        if (validateBookmarkEdit(normalizedTitle, normalizedUrl) != null) return null
        return Bookmark(normalizedTitle, normalizedUrl)
    }

    fun shouldAdd(existingUrls: List<String>, candidate: Bookmark): Boolean {
        if (validateBookmarkEdit(candidate.title, candidate.url) != null) return false
        return existingUrls.none { it == candidate.url }
    }
}
