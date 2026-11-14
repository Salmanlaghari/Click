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
    fun idleHomeUsesNewTabInsteadOfReloadInEveryMode() {
        BrowserMode.values().forEach { mode ->
            assertEquals(
                "Home should offer New Tab rather than Reload in $mode",
                CompactBrowsePrimaryAction.NEW_TAB,
                compactBrowsePrimaryAction(
                    activeMode = mode,
                    isPageLoading = false,
                    isHomePage = true
                )
            )
        }
    }

    @Test
    fun loadingHomeStillOffersStop() {
        BrowserMode.values().forEach { mode ->
            assertEquals(
                CompactBrowsePrimaryAction.STOP,
                compactBrowsePrimaryAction(
                    activeMode = mode,
                    isPageLoading = true,
                    isHomePage = true
                )
            )
        }
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

    @Test
    fun visibleBottomAddressBarReservesExactlyItsOwnHeight() {
        // Full-screen web rule (14 Nov 2026): the browse bottom navigation
        // is gone, so a visible bottom address bar reserves only its own
        // 54dp row plus the 2dp load-progress line — no nav offset.
        assertEquals(
            56,
            browseBottomChromeInsetDp(
                bottomAddressBarEnabled = true,
                toolbarVisible = true
            )
        )
    }

    @Test
    fun noVisibleBottomChromeReservesNoSpaceSoTheWebPageFillsTheScreen() {
        assertEquals(
            0,
            browseBottomChromeInsetDp(
                bottomAddressBarEnabled = true,
                toolbarVisible = false
            )
        )
        assertEquals(
            0,
            browseBottomChromeInsetDp(
                bottomAddressBarEnabled = false,
                toolbarVisible = true
            )
        )
        assertEquals(
            0,
            browseBottomChromeInsetDp(
                bottomAddressBarEnabled = false,
                toolbarVisible = false
            )
        )
    }
}
