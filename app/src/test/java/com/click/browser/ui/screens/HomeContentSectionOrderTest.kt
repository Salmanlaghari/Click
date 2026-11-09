package com.click.browser.ui.screens

import com.click.browser.engine.BrowserMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeContentSectionOrderTest {
    @Test
    fun pdfCoreAndSecondaryContentOrderIsExplicitForEveryMode() {
        val requiredCoreOrder = listOf(
            HomeContentSection.PRIVACY_STATUS,
            HomeContentSection.QUICK_SITES,
            HomeContentSection.CONTINUE_BROWSING
        )
        val secondaryOrder = listOf(
            HomeContentSection.TODAY_UPDATE,
            HomeContentSection.QUICK_ACTIONS,
            HomeContentSection.OPTIONAL_NEWS_FEED
        )

        BrowserMode.values().forEach { mode ->
            val expected = buildList {
                addAll(requiredCoreOrder)
                if (mode != BrowserMode.SIMPLE) add(HomeContentSection.MODE_FOCUS)
                addAll(secondaryOrder)
            }
            assertEquals("mode=$mode", expected, homeContentSections(mode))
        }
    }

    @Test
    fun onlyModesWithAHomeFocusCardReceiveThatSecondarySection() {
        assertFalse(homeContentSections(BrowserMode.SIMPLE).contains(HomeContentSection.MODE_FOCUS))
        listOf(BrowserMode.DEVELOPER, BrowserMode.HACK, BrowserMode.ADVANCED).forEach { mode ->
            assertTrue(homeContentSections(mode).contains(HomeContentSection.MODE_FOCUS))
        }
    }
}
