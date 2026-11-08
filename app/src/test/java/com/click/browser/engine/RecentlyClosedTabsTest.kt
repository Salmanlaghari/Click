package com.click.browser.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecentlyClosedTabsTest {
    @Before
    fun clearBeforeTest() {
        RecentlyClosedTabs.clear()
    }

    @After
    fun clearAfterTest() {
        RecentlyClosedTabs.clear()
    }

    @Test
    fun privateBlankAndNonWebTabsAreNeverRemembered() {
        RecentlyClosedTabs.push(
            title = "Private",
            url = "https://private.example",
            isIncognito = true,
            mode = BrowserMode.SIMPLE
        )
        RecentlyClosedTabs.push("Blank", "about:blank", false, BrowserMode.SIMPLE)
        RecentlyClosedTabs.push("Local file", "file:///tmp/page.html", false, BrowserMode.SIMPLE)

        assertTrue(RecentlyClosedTabs.list().isEmpty())
    }

    @Test
    fun closedTabsAreVisibleOnlyInTheirOriginatingMode() {
        RecentlyClosedTabs.push("Simple", "https://simple.example", false, BrowserMode.SIMPLE)
        RecentlyClosedTabs.push("Advanced", "https://advanced.example", false, BrowserMode.ADVANCED)
        RecentlyClosedTabs.push("Power", "https://power.example", false, BrowserMode.HACK)

        assertEquals(listOf("https://simple.example"), RecentlyClosedTabs.list(BrowserMode.SIMPLE).map { it.url })
        assertEquals(listOf("https://advanced.example"), RecentlyClosedTabs.list(BrowserMode.ADVANCED).map { it.url })
        assertEquals(listOf("https://power.example"), RecentlyClosedTabs.list(BrowserMode.HACK).map { it.url })
        assertTrue(RecentlyClosedTabs.list(BrowserMode.DEVELOPER).isEmpty())
    }

    @Test
    fun duplicateUrlAndUndoRemovalAreScopedToTheOriginatingMode() {
        RecentlyClosedTabs.push("Simple copy", "https://same.example", false, BrowserMode.SIMPLE)
        RecentlyClosedTabs.push("Advanced copy", "https://same.example", false, BrowserMode.ADVANCED)
        RecentlyClosedTabs.push("Power copy", "https://same.example", false, BrowserMode.HACK)
        RecentlyClosedTabs.removeForMode("https://same.example", BrowserMode.SIMPLE)

        assertTrue(RecentlyClosedTabs.list(BrowserMode.SIMPLE).isEmpty())
        assertEquals("Advanced copy", RecentlyClosedTabs.list(BrowserMode.ADVANCED).single().title)
        assertEquals("Power copy", RecentlyClosedTabs.list(BrowserMode.HACK).single().title)
    }

    @Test
    fun eachModeKeepsItsOwnTenMostRecentTabs() {
        repeat(11) { index ->
            RecentlyClosedTabs.push("Simple $index", "https://simple-$index.example", false, BrowserMode.SIMPLE)
            RecentlyClosedTabs.push("Advanced $index", "https://advanced-$index.example", false, BrowserMode.ADVANCED)
        }

        assertEquals(10, RecentlyClosedTabs.list(BrowserMode.SIMPLE).size)
        assertEquals(10, RecentlyClosedTabs.list(BrowserMode.ADVANCED).size)
    }

    @Test
    fun clearingOneModePreservesTheOtherModes() {
        RecentlyClosedTabs.push("Simple", "https://simple.example", false, BrowserMode.SIMPLE)
        RecentlyClosedTabs.push("Developer", "https://developer.example", false, BrowserMode.DEVELOPER)
        RecentlyClosedTabs.push("Advanced", "https://advanced.example", false, BrowserMode.ADVANCED)
        RecentlyClosedTabs.clear(BrowserMode.DEVELOPER)

        assertEquals(listOf("https://simple.example"), RecentlyClosedTabs.list(BrowserMode.SIMPLE).map { it.url })
        assertTrue(RecentlyClosedTabs.list(BrowserMode.DEVELOPER).isEmpty())
        assertEquals(listOf("https://advanced.example"), RecentlyClosedTabs.list(BrowserMode.ADVANCED).map { it.url })
    }
}
