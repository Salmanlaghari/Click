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
