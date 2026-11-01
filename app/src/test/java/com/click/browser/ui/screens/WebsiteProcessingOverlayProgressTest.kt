package com.click.browser.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebsiteProcessingOverlayProgressTest {
    @Test
    fun zeroAndNegativeProgressStayIndeterminate() {
        assertNull(visibleWebsiteProgressPercent(0))
        assertNull(visibleWebsiteProgressPercent(-1))
    }

    @Test
    fun positiveProgressUsesTheReportedPercentage() {
        assertEquals(37, visibleWebsiteProgressPercent(37))
        assertEquals(100, visibleWebsiteProgressPercent(100))
    }

    @Test
    fun outOfRangeProgressIsClampedToValidDisplayRange() {
        assertEquals(100, visibleWebsiteProgressPercent(145))
    }
}
