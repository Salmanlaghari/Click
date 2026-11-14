package com.click.browser.ui.screens

import com.click.browser.engine.BrowserMode

private const val COMPACT_WEBPAGE_ROW_HEIGHT_DP = 54
private const val PAGE_LOAD_PROGRESS_HEIGHT_DP = 2

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

/**
 * Bottom space reserved for browse chrome over a web page.
 *
 * Prince's full-screen web rule (14 Nov 2026) removed the persistent
 * Menu · Home · Tabs · More bottom navigation from web pages for good, so
 * the only bottom chrome that can still sit over a page is the opt-in
 * bottom address bar. Reserve exactly that bar's height while it is
 * visible; reserve nothing otherwise, so the WebView truly fills the
 * whole screen with no empty gap left at the bottom.
 */
internal fun browseBottomChromeInsetDp(
    bottomAddressBarEnabled: Boolean,
    toolbarVisible: Boolean
): Int =
    if (bottomAddressBarEnabled && toolbarVisible) {
        COMPACT_WEBPAGE_ROW_HEIGHT_DP + PAGE_LOAD_PROGRESS_HEIGHT_DP
    } else {
        0
    }
