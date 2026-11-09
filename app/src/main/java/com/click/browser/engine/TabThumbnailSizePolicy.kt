package com.click.browser.engine

import kotlin.math.roundToInt

internal data class TabThumbnailDimensions(
    val width: Int,
    val height: Int
)

/** Returns an aspect-preserving size that fits inside the thumbnail memory bounds. */
internal fun tabThumbnailDimensions(
    sourceWidth: Int,
    sourceHeight: Int,
    maxWidth: Int = 360,
    maxHeight: Int = 800
): TabThumbnailDimensions? {
    if (sourceWidth <= 0 || sourceHeight <= 0 || maxWidth <= 0 || maxHeight <= 0) return null

    val scale = minOf(
        1.0,
        maxWidth.toDouble() / sourceWidth,
        maxHeight.toDouble() / sourceHeight
    )
    return TabThumbnailDimensions(
        width = (sourceWidth * scale).roundToInt().coerceIn(1, maxWidth),
        height = (sourceHeight * scale).roundToInt().coerceIn(1, maxHeight)
    )
}
