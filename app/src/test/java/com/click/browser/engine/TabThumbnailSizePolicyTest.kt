package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabThumbnailSizePolicyTest {
    @Test
    fun largeViewportFitsBothMemoryBounds() {
        assertEquals(
            TabThumbnailDimensions(width = 360, height = 800),
            tabThumbnailDimensions(sourceWidth = 1080, sourceHeight = 2400)
        )
    }

    @Test
    fun smallPreviewIsNotUpscaled() {
        assertEquals(
            TabThumbnailDimensions(width = 320, height = 720),
            tabThumbnailDimensions(sourceWidth = 320, sourceHeight = 720)
        )
    }

    @Test
    fun unusuallyTallPreviewStillFitsTheHeightLimit() {
        assertEquals(
            TabThumbnailDimensions(width = 160, height = 800),
            tabThumbnailDimensions(sourceWidth = 360, sourceHeight = 1800)
        )
    }

    @Test
    fun invalidDimensionsAreRejected() {
        assertNull(tabThumbnailDimensions(sourceWidth = 0, sourceHeight = 100))
        assertNull(tabThumbnailDimensions(sourceWidth = 100, sourceHeight = -1))
        assertNull(tabThumbnailDimensions(sourceWidth = 100, sourceHeight = 100, maxWidth = 0))
    }
}
