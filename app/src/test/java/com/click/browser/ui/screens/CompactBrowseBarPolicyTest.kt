package com.click.browser.ui.screens

import com.click.browser.engine.BrowserMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CompactBrowseBarPolicyTest {
    @Test
    fun loadingAlwaysReplacesThePrimaryActionWithStop() {
        BrowserMode.values().forEach { mode ->
            assertEquals(
                "Stop should be available while loading in $mode",
                CompactBrowsePrimaryAction.STOP,
                compactBrowsePrimaryAction(mode, isPageLoading = true)
            )
        }
    }

    @Test
    fun idleSimpleModeKeepsItsNewTabAction() {
        assertEquals(
            CompactBrowsePrimaryAction.NEW_TAB,
            compactBrowsePrimaryAction(BrowserMode.SIMPLE, isPageLoading = false)
        )
    }

    @Test
    fun idleOtherModesKeepTheirReloadAction() {
        BrowserMode.values().filterNot { it == BrowserMode.SIMPLE }.forEach { mode ->
            assertEquals(
                "Reload should be available while idle in $mode",
                CompactBrowsePrimaryAction.RELOAD,
                compactBrowsePrimaryAction(mode, isPageLoading = false)
            )
        }
    }
}
