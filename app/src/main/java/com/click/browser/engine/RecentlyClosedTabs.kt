package com.click.browser.engine

/** A tab the user closed; kept so "Recent tabs" can re-open it. */
data class ClosedTab(
    val title: String,
    val url: String
)

/**
 * Recently-closed-tabs stack for the "Recent tabs" menu item.
 * Private/incognito tabs and blank tabs are NEVER remembered.
 */
object RecentlyClosedTabs {
    private const val MAX = 10
    private val stack = ArrayDeque<ClosedTab>()

    @Synchronized
    fun push(title: String, url: String, isIncognito: Boolean) {
        if (isIncognito) return
        if (url.isBlank() || url == "about:blank" || !url.startsWith("http")) return
        stack.removeAll { it.url == url }
        stack.addFirst(ClosedTab(title.ifBlank { url }, url))
        while (stack.size > MAX) stack.removeLast()
    }

    @Synchronized
    fun list(): List<ClosedTab> = stack.toList()

    @Synchronized
    fun remove(tab: ClosedTab) {
        stack.remove(tab)
    }

    @Synchronized
    fun clear() {
        stack.clear()
    }
}
