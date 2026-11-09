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
    fun handlesExpectedSynchronousDispatchFailures() {
        listOf<RuntimeException>(
            IllegalArgumentException("invalid navigation target"),
            IllegalStateException("session closed")
        ).forEach { failure ->
            var refreshing = false
            val navigation = RecordingNavigation { refreshing }.apply {
                loadFailure = failure
            }

            val started = startPullToRefresh(navigation, "https://example.test/page") {
                refreshing = true
            }

            assertFalse(started)
            assertTrue(navigation.wasRefreshingWhenLoadStarted)
            assertEquals(1, navigation.loadAttempts)
            assertNull(navigation.loadedUrl)
            assertEquals(0, navigation.reloadCalls)
        }
    }

    @Test(expected = UnsupportedOperationException::class)
    fun doesNotSuppressUnexpectedDispatchFailures() {
        val navigation = RecordingNavigation { false }.apply {
            loadFailure = UnsupportedOperationException("unexpected navigation failure")
        }

        startPullToRefresh(navigation, "https://example.test/page") {}
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
        var loadFailure: RuntimeException? = null
        var loadAttempts = 0

        override fun canGoBack(): Boolean = false
        override fun goBack() = Unit
        override fun canGoForward(): Boolean = false
        override fun goForward() = Unit
        override fun loadUrl(url: String) {
            loadAttempts += 1
            wasRefreshingWhenLoadStarted = isRefreshing()
            loadFailure?.let { throw it }
            loadedUrl = url
        }
        override fun reload() {
            reloadCalls += 1
        }
        override fun stopLoading() = Unit
    }
}
