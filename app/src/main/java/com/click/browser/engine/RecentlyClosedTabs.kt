package com.click.browser.engine

import androidx.compose.runtime.mutableStateListOf

/** A regular web tab the user closed; kept so Recent tabs can re-open it in its original mode. */
data class ClosedTab(
    val title: String,
    val url: String,
    val engine: BrowserTabEngine = BrowserTabEngine.WEBVIEW,
    val mode: BrowserMode = BrowserMode.SIMPLE
)

/**
 * Recently-closed tabs, scoped to the exact browser mode.
 * Each mode has its own WebView cookie/cache suffix, so tabs must reopen in their original mode.
 * Private/incognito tabs, blank tabs, and non-web URLs are never remembered.
 */
object RecentlyClosedTabs {
    private const val MAX_PER_MODE = 10
    // Snapshot state makes list(mode) reads observable to Compose callers.
    private val stack = mutableStateListOf<ClosedTab>()

    @Synchronized
    fun push(
        title: String,
        url: String,
        isIncognito: Boolean,
        mode: BrowserMode,
        engine: BrowserTabEngine = BrowserTabEngine.WEBVIEW
    ) {
        if (isIncognito) return
        if (url.isBlank() || url == "about:blank" || !isHttpOrHttps(url)) return
        stack.removeAll { it.url == url && it.mode == mode }
        stack.add(0, ClosedTab(title.ifBlank { url }, url, engine, mode))
        while (stack.count { it.mode == mode } > MAX_PER_MODE) {
            val oldestIndexInMode = stack.indexOfLast { it.mode == mode }
            if (oldestIndexInMode < 0) break
            stack.removeAt(oldestIndexInMode)
        }
    }

    @Synchronized
    fun list(mode: BrowserMode? = null): List<ClosedTab> = stack.filter { tab ->
        mode == null || tab.mode == mode
    }

    @Synchronized
    fun remove(tab: ClosedTab) {
        stack.remove(tab)
    }

    @Synchronized
    fun removeForMode(url: String, mode: BrowserMode) {
        stack.removeAll { it.url == url && it.mode == mode }
    }

    @Synchronized
    fun clear(mode: BrowserMode? = null) {
        if (mode == null) stack.clear()
        else stack.removeAll { it.mode == mode }
    }

    /** ProfileData.kt maps Simple, Developer and Power to one history store; Advance is isolated. */
    internal fun historyScopeModesFor(mode: BrowserMode): List<BrowserMode> = when (mode) {
        BrowserMode.SIMPLE, BrowserMode.DEVELOPER, BrowserMode.HACK ->
            listOf(BrowserMode.SIMPLE, BrowserMode.DEVELOPER, BrowserMode.HACK)
        BrowserMode.ADVANCED -> listOf(BrowserMode.ADVANCED)
    }

    @Synchronized
    internal fun clearForHistoryScope(mode: BrowserMode) {
        historyScopeModesFor(mode).forEach { clear(it) }
    }

    /** Keep recent URLs if persisted history clearing fails; otherwise clear the same history scope. */
    internal suspend fun clearHistoryAndRecentlyClosedForMode(
        mode: BrowserMode,
        clearStoredHistory: suspend (BrowserMode) -> Unit
    ) {
        clearStoredHistory(mode)
        clearForHistoryScope(mode)
    }

    private fun isHttpOrHttps(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
}
