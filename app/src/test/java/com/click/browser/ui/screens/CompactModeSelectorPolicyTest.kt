package com.click.browser.ui.screens

import com.click.browser.engine.BrowserMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CompactModeSelectorPolicyTest {
    @Test
    fun selectorOffersAllFourModesInProductOrderWithPublicTitles() {
        assertEquals(
            listOf(
                BrowserMode.SIMPLE,
                BrowserMode.DEVELOPER,
                BrowserMode.HACK,
                BrowserMode.ADVANCED
            ),
            CompactModeSelectorPolicy.modes
        )
        assertEquals(
            listOf("Simple", "Developer", "Power", "Advance"),
            CompactModeSelectorPolicy.modes.map { CompactModeSelectorPolicy.title(it) }
        )
    }

    @Test
    fun homeModeChipDoesNotExposeTheLegacyHackIdentifier() {
        assertEquals("SIMPLE", CompactModeSelectorPolicy.homeChipLabel(BrowserMode.SIMPLE))
        assertEquals("DEV", CompactModeSelectorPolicy.homeChipLabel(BrowserMode.DEVELOPER))
        assertEquals("POWER", CompactModeSelectorPolicy.homeChipLabel(BrowserMode.HACK))
        assertEquals("ADVANCE", CompactModeSelectorPolicy.homeChipLabel(BrowserMode.ADVANCED))
    }
}
