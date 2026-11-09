package com.click.browser.ui.screens

import com.click.browser.engine.BrowserMode

internal enum class CompactBrowsePrimaryAction {
    NEW_TAB,
    RELOAD,
    STOP
}

internal fun compactBrowsePrimaryAction(
    activeMode: BrowserMode,
    isPageLoading: Boolean
): CompactBrowsePrimaryAction = when {
    isPageLoading -> CompactBrowsePrimaryAction.STOP
    activeMode == BrowserMode.SIMPLE -> CompactBrowsePrimaryAction.NEW_TAB
    else -> CompactBrowsePrimaryAction.RELOAD
}

/** Bottom-space policy for the persistent browse nav and optional page address bar. */
internal fun browseBottomChromeInsetDp(
    showBrowseBottomNav: Boolean,
    bottomAddressBarEnabled: Boolean,
    toolbarVisible: Boolean
): Int = when {
    !showBrowseBottomNav -> 0
    // 76dp bottom offset + 54dp compact row (incl. vertical padding) + 2dp progress bar.
    bottomAddressBarEnabled && toolbarVisible -> 76 + 54 + 2
    else -> 64
}
