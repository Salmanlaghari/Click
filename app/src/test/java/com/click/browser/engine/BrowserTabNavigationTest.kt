package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTabNavigationTest {
    @Test
    fun backIsNoOpWhenSessionIsMissingOrHasNoHistory() {
        val missing: BrowserTabNavigation? = null
        assertFalse(missing.goBackIfPossible())

        val session = FakeNavigation(canBack = false)
        assertFalse(session.goBackIfPossible())
        assertEquals(0, session.backCalls)
    }

    @Test
    fun backAfterExternalGoogleSignInWithNoHistoryKeepsOriginPage() {
        val originUrl = "https://example.test/account"
        val session = FakeNavigation(canBack = false, currentUrl = originUrl)

        assertEquals(BrowserBackResult.KEEP_CURRENT_PAGE, session.handleBrowserBackPress())
        assertEquals(originUrl, session.currentUrl)
        assertTrue(session.loadedUrls.isEmpty())
        assertEquals(0, session.backCalls)
    }

    @Test
    fun backUsesTheSessionWhenHistoryIsAvailable() {
        val session = FakeNavigation(canBack = true)

        assertTrue(session.goBackIfPossible())
        assertEquals(1, session.backCalls)
    }

    @Test
    fun backPressReportsNavigationWhenHistoryIsAvailable() {
        val session = FakeNavigation(canBack = true)

        assertEquals(BrowserBackResult.NAVIGATED_BACK, session.handleBrowserBackPress())
        assertEquals(1, session.backCalls)
    }

    @Test
    fun forwardIsNoOpWhenHistoryIsUnavailable() {
        val session = FakeNavigation(canForward = false)

        assertFalse(session.goForwardIfPossible())
        assertEquals(0, session.forwardCalls)
    }

    @Test
    fun forwardUsesTheSessionWhenHistoryIsAvailable() {
        val session = FakeNavigation(canForward = true)

        assertTrue(session.goForwardIfPossible())
        assertEquals(1, session.forwardCalls)
    }

    private class FakeNavigation(
        private val canBack: Boolean = false,
        private val canForward: Boolean = false,
        var currentUrl: String = "https://example.test/start",
    ) : BrowserTabNavigation {
        var backCalls: Int = 0
            private set
        var forwardCalls: Int = 0
            private set
        val loadedUrls = mutableListOf<String>()

        override fun canGoBack(): Boolean = canBack
        override fun goBack() { backCalls++ }
        override fun canGoForward(): Boolean = canForward
        override fun goForward() { forwardCalls++ }
        override fun loadUrl(url: String) {
            loadedUrls += url
            currentUrl = url
        }
        override fun reload() = Unit
        override fun stopLoading() = Unit
    }
}
