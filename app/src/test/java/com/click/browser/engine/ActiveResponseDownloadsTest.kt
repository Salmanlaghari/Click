package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveResponseDownloadsTest {
    @Test
    fun responseDownloadsAreVisibleWhileCopyingAndRemovedWhenDone() {
        val id = ActiveResponseDownloads.begin("release.zip", "Downloads/ClickBrowser", 2_000L)
        try {
            val active = ActiveResponseDownloads.active.value
            assertEquals(1, active.size)
            assertEquals("release.zip", active.single().fileName)
            assertEquals("Downloads/ClickBrowser", active.single().destinationLabel)
            assertEquals(2_000L, active.single().totalBytes)
            assertEquals(0L, active.single().downloadedBytes)

            ActiveResponseDownloads.updateProgress(id, 750L)
            assertEquals(750L, ActiveResponseDownloads.active.value.single().downloadedBytes)

            // Progress for an unknown id changes nothing.
            ActiveResponseDownloads.updateProgress(Long.MAX_VALUE, 5L)
            assertEquals(750L, ActiveResponseDownloads.active.value.single().downloadedBytes)
        } finally {
            ActiveResponseDownloads.end(id)
        }
        assertTrue(ActiveResponseDownloads.active.value.isEmpty())
    }

    @Test
    fun concurrentResponseDownloadsAreTrackedIndependently() {
        val first = ActiveResponseDownloads.begin("a.zip", "Downloads/ClickBrowser", -1L)
        val second = ActiveResponseDownloads.begin("b.pdf", "App private storage", 10L)
        try {
            assertEquals(2, ActiveResponseDownloads.active.value.size)
            ActiveResponseDownloads.end(first)
            val remaining = ActiveResponseDownloads.active.value
            assertEquals(1, remaining.size)
            assertEquals("b.pdf", remaining.single().fileName)
        } finally {
            ActiveResponseDownloads.end(first)
            ActiveResponseDownloads.end(second)
        }
        assertTrue(ActiveResponseDownloads.active.value.isEmpty())
    }
}
