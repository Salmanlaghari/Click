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
    fun backUsesTheSessionWhenHistoryIsAvailable() {
        val session = FakeNavigation(canBack = true)

        assertTrue(session.goBackIfPossible())
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
    ) : BrowserTabNavigation {
        var backCalls: Int = 0
            private set
        var forwardCalls: Int = 0
            private set

        override fun canGoBack(): Boolean = canBack
        override fun goBack() { backCalls++ }
        override fun canGoForward(): Boolean = canForward
        override fun goForward() { forwardCalls++ }
        override fun loadUrl(url: String) = Unit
        override fun reload() = Unit
        override fun stopLoading() = Unit
    }
}
