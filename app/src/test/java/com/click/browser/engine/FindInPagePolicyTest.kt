package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FindInPagePolicyTest {

    @Test
    fun blankAndWhitespaceQueriesClearMatches() {
        assertEquals(FindInPageAction.Clear, FindInPagePolicy.actionForQuery(""))
        assertEquals(FindInPageAction.Clear, FindInPagePolicy.actionForQuery(" \t\n "))
    }

    @Test
    fun nonBlankQueryIsPassedThroughUnchanged() {
        assertEquals(
            FindInPageAction.Search("  browser text  "),
            FindInPagePolicy.actionForQuery("  browser text  ")
        )
    }

    @Test
    fun dismissClearsMatchesBeforeHidingTheDialog() {
        val events = mutableListOf<String>()

        FindInPagePolicy.dismiss(
            clearMatches = { events.add("clear") },
            hideDialog = { events.add("hide") }
        )

        assertEquals(listOf("clear", "hide"), events)
    }
}
