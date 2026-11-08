package com.click.browser.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSitesGridPolicyTest {
    @Test
    fun roomyViewportKeepsTheFiveColumnLayout() {
        assertEquals(5, QuickSitesGridPolicy.columnsFor(380f, fontScale = 1f))
    }

    @Test
    fun compactViewportReducesTheColumnCount() {
        assertEquals(4, QuickSitesGridPolicy.columnsFor(320f, fontScale = 1f))
    }

    @Test
    fun enlargedTextReducesTheColumnCount() {
        assertEquals(3, QuickSitesGridPolicy.columnsFor(380f, fontScale = 1.5f))
    }

    @Test
    fun veryNarrowLargeTextCanUseOneColumn() {
        assertEquals(1, QuickSitesGridPolicy.columnsFor(180f, fontScale = 2f))
    }

    @Test
    fun columnCountStaysWithinSupportedBounds() {
        assertEquals(QuickSitesGridPolicy.MIN_COLUMNS, QuickSitesGridPolicy.columnsFor(0f, fontScale = 1f))
        assertEquals(QuickSitesGridPolicy.MAX_COLUMNS, QuickSitesGridPolicy.columnsFor(1_000f, fontScale = 1f))
    }
}
