package com.click.browser.data

/**
 * List rules for the persisted history store: one entry per URL (the latest
 * visit wins) and a generous cap, trimmed oldest-first. Kept pure so the
 * repository's JSON plumbing stays thin and the rules are unit-testable.
 */
internal object HistoryListPolicy {

    /** Real-browser retention; the old 100-entry cap silently ate a day's browsing. */
    const val MAX_ENTRIES = 2000

    /**
     * Insert [newItem] as the latest visit: any earlier entries for the same
     * URL are replaced, and the oldest entries fall off beyond [maxEntries].
     * Input order is oldest-first (store order); the result keeps that order.
     */
    fun upsert(
        items: List<HistoryItem>,
        newItem: HistoryItem,
        maxEntries: Int = MAX_ENTRIES
    ): List<HistoryItem> {
        val merged = items.filterNot { it.url == newItem.url } + newItem
        return if (merged.size > maxEntries) merged.drop(merged.size - maxEntries) else merged
    }

    /**
     * Give the most recent visit to [url] its real [title] when it was stored
     * without one (page-load can beat the title callback, leaving the URL —
     * or a previous page's leftover — as the label). Entries that already
     * carry a real title are never overwritten.
     */
    fun withUpdatedTitle(items: List<HistoryItem>, url: String, title: String): List<HistoryItem> {
        if (title.isBlank()) return items
        val index = items.indexOfLast { it.url == url && (it.title.isBlank() || it.title == it.url) }
        if (index < 0) return items
        return items.mapIndexed { i, item -> if (i == index) item.copy(title = title) else item }
    }
}
