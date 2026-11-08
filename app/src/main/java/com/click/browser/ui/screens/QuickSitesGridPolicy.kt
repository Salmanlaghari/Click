package com.click.browser.ui.screens

/** Calculates a quick-sites column count that gives enlarged labels more room. */
internal object QuickSitesGridPolicy {
    const val MIN_COLUMNS = 1
    const val MAX_COLUMNS = 5
    private const val MIN_CELL_WIDTH_DP = 72f

    fun columnsFor(availableWidthDp: Float, fontScale: Float): Int {
        val effectiveFontScale = fontScale.coerceAtLeast(1f)
        val minimumCellWidthDp = MIN_CELL_WIDTH_DP * effectiveFontScale
        return (availableWidthDp / minimumCellWidthDp)
            .toInt()
            .coerceIn(MIN_COLUMNS, MAX_COLUMNS)
    }
}
