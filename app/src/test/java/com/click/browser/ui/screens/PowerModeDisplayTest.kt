package com.click.browser.ui.screens

import com.click.browser.engine.BrowserMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PowerModeDisplayTest {
    @Test
    fun powerDescriptionMatchesItsDesktopStyleLayout() {
        val display = BrowserMode.HACK.display()

        assertEquals("Power", display.title)
        assertEquals("Desktop-style browsing · Wide-page layout", display.tagline)
    }
}
