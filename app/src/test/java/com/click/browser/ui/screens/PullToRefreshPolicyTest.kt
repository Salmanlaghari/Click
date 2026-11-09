package com.click.browser.ui.screens

import com.click.browser.engine.BrowserTabNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PullToRefreshPolicyTest {
    @Test
    fun pullRefreshStartsBeforeFreshSafeGetAndDoesNotCallReload() {
        val url = "https://example.test/form-result?done=1"
        var refreshing = false
        val navigation = RecordingNavigation { refreshing }

        val started = startPullToRefresh(navigation, url) { refreshing = true }

        assertTrue(started)
        assertEquals(url, navigation.loadedUrl)
        assertTrue(navigation.wasRefreshingWhenLoadStarted)
        assertEquals(0, navigation.reloadCalls)
    }

    @Test
    fun pullRefreshDoesNotStartForUnsafeUrlOrMissingNavigation() {
        var refreshing = false
        val navigation = RecordingNavigation { refreshing }

        assertFalse(startPullToRefresh(navigation, "file:///tmp/page.html") { refreshing = true })
        assertFalse(startPullToRefresh(navigation, "javascript:alert(1)") { refreshing = true })
        assertFalse(startPullToRefresh(null, "https://example.test/page") { refreshing = true })

        assertFalse(refreshing)
        assertNull(navigation.loadedUrl)
        assertEquals(0, navigation.reloadCalls)
        assertNull(pullToRefreshGetUrl("about:blank"))
    }

    @Test
    fun navigationCompletionClearsOnlyItsCurrentRefreshTab() {
        val refreshingTabs = setOf("tab-a", "tab-b")

        assertEquals(
            setOf("tab-b"),
            refreshingTabIdsAfterNavigationEnd(
                refreshingTabIds = refreshingTabs,
                completedTabId = "tab-a",
                isCurrentNavigation = true
            )
        )
        assertEquals(
            refreshingTabs,
            refreshingTabIdsAfterNavigationEnd(
                refreshingTabIds = refreshingTabs,
                completedTabId = "tab-a",
                isCurrentNavigation = false
            )
        )
    }

    private class RecordingNavigation(
        private val isRefreshing: () -> Boolean
    ) : BrowserTabNavigation {
        var loadedUrl: String? = null
        var reloadCalls = 0
        var wasRefreshingWhenLoadStarted = false

        override fun canGoBack(): Boolean = false
        override fun goBack() = Unit
        override fun canGoForward(): Boolean = false
        override fun goForward() = Unit
        override fun loadUrl(url: String) {
            loadedUrl = url
            wasRefreshingWhenLoadStarted = isRefreshing()
        }
        override fun reload() {
            reloadCalls += 1
        }
        override fun stopLoading() = Unit
    }
}
