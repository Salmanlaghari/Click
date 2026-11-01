package com.click.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveTabIndexAfterCloseTest {
    @Test
    fun closingEarlierTabKeepsTheSameTabSelected() {
        assertEquals(1, activeTabIndexAfterClose(activeIndex = 2, closedIndex = 0, remainingCount = 2))
    }

    @Test
    fun closingActiveTabSelectsTheNextOrPreviousAvailableTab() {
        assertEquals(1, activeTabIndexAfterClose(activeIndex = 1, closedIndex = 1, remainingCount = 2))
        assertEquals(1, activeTabIndexAfterClose(activeIndex = 2, closedIndex = 2, remainingCount = 2))
    }

    @Test
    fun resultingIndexAlwaysFitsRemainingTabs() {
        assertEquals(0, activeTabIndexAfterClose(activeIndex = 7, closedIndex = 0, remainingCount = 0))
        assertEquals(1, activeTabIndexAfterClose(activeIndex = 5, closedIndex = 8, remainingCount = 2))
    }
}
