package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun pauseAndResumeAreReflectedInTheRegistryItem() {
        val id = ActiveResponseDownloads.begin("big.apk", "Downloads/ClickBrowser", 100L)
        try {
            ActiveResponseDownloads.pause(id)
            assertTrue(ActiveResponseDownloads.active.value.single().paused)
            assertTrue(ActiveResponseDownloads.control(id)?.isPaused == true)

            ActiveResponseDownloads.resume(id)
            assertTrue(!ActiveResponseDownloads.active.value.single().paused)
            assertTrue(ActiveResponseDownloads.control(id)?.isPaused == false)
        } finally {
            ActiveResponseDownloads.end(id)
        }
        // Ending the download drops its control gate as well.
        assertTrue(ActiveResponseDownloads.control(id) == null)
    }

    @Test
    fun checkpointBlocksWhilePausedAndThrowsAfterCancel() {
        val control = ResponseDownloadControl()
        control.checkpoint() // Not paused: passes straight through.

        control.pause()
        val released = java.util.concurrent.atomic.AtomicBoolean(false)
        val worker = Thread {
            try {
                control.checkpoint()
                released.set(true)
            } catch (_: ResponseDownloadCanceledException) {
                // Not expected on the resume path.
            }
        }
        worker.start()
        Thread.sleep(250)
        assertTrue("checkpoint must stay gated while paused", worker.isAlive)
        assertTrue(!released.get())

        control.resume()
        worker.join(5_000)
        assertTrue("checkpoint must pass after resume", released.get())

        control.pause()
        control.cancel()
        assertTrue(control.isCanceled)
        assertTrue(!control.isPaused)
        try {
            control.checkpoint()
            fail("checkpoint must throw after cancel")
        } catch (_: ResponseDownloadCanceledException) {
            // Expected.
        }
    }

    @Test
    fun cancelInvokesTheStreamCloseHook() {
        val control = ResponseDownloadControl()
        val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        control.onCancel = { closed.set(true) }
        control.cancel()
        assertTrue(closed.get())
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
