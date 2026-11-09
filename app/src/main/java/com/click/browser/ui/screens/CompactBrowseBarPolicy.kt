package com.click.browser.ui.screens

import com.click.browser.engine.BrowserMode

internal const val BROWSE_BOTTOM_ADDRESS_BAR_OFFSET_DP = 76
private const val COMPACT_WEBPAGE_ROW_HEIGHT_DP = 54
private const val PAGE_LOAD_PROGRESS_HEIGHT_DP = 2
private const val BROWSE_BOTTOM_NAV_INSET_DP = 64

internal enum class CompactBrowsePrimaryAction {
    NEW_TAB,
    RELOAD,
    STOP
}

internal fun compactBrowsePrimaryAction(
    activeMode: BrowserMode,
    isPageLoading: Boolean,
    isHomePage: Boolean = false
): CompactBrowsePrimaryAction = when {
    isPageLoading -> CompactBrowsePrimaryAction.STOP
    isHomePage || activeMode == BrowserMode.SIMPLE -> CompactBrowsePrimaryAction.NEW_TAB
    else -> CompactBrowsePrimaryAction.RELOAD
}

/** Bottom-space policy for the persistent browse nav and optional page address bar. */
internal fun browseBottomChromeInsetDp(
    showBrowseBottomNav: Boolean,
    bottomAddressBarEnabled: Boolean,
    toolbarVisible: Boolean
): Int = when {
    !showBrowseBottomNav -> 0
    bottomAddressBarEnabled && toolbarVisible ->
        BROWSE_BOTTOM_ADDRESS_BAR_OFFSET_DP + COMPACT_WEBPAGE_ROW_HEIGHT_DP + PAGE_LOAD_PROGRESS_HEIGHT_DP
    else -> BROWSE_BOTTOM_NAV_INSET_DP
}
