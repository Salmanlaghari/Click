package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadTimingPolicyTest {
    @Test
    fun speedIsNullWhenNothingMeasurableMoved() {
        assertNull(downloadSpeedBps(bytesMoved = 0L, elapsedMillis = 5_000L))
        assertNull(downloadSpeedBps(bytesMoved = -10L, elapsedMillis = 5_000L))
        assertNull(downloadSpeedBps(bytesMoved = 500L, elapsedMillis = 0L))
    }

    @Test
    fun speedIsBytesPerSecondOverTheMeasuredInterval() {
        assertEquals(250.0, downloadSpeedBps(bytesMoved = 1_000L, elapsedMillis = 4_000L)!!, 0.001)
    }

    @Test
    fun etaIsNullWhenTotalSizeIsUnknown() {
        // Streamed responses report totalBytes = -1: no invented ETA, ever.
        assertNull(downloadEtaSeconds(downloadedBytes = 500L, totalBytes = -1L, speedBps = 100.0))
        assertNull(downloadEtaSeconds(downloadedBytes = 500L, totalBytes = 0L, speedBps = 100.0))
    }

    @Test
    fun etaIsNullUntilASpeedHasBeenMeasured() {
        assertNull(downloadEtaSeconds(downloadedBytes = 500L, totalBytes = 1_500L, speedBps = null))
        assertNull(downloadEtaSeconds(downloadedBytes = 500L, totalBytes = 1_500L, speedBps = 0.0))
    }

    @Test
    fun etaIsRemainingBytesDividedByMeasuredSpeed() {
        assertEquals(
            10L,
            downloadEtaSeconds(downloadedBytes = 250L, totalBytes = 1_250L, speedBps = 100.0)
        )
        assertEquals(
            0L,
            downloadEtaSeconds(downloadedBytes = 1_250L, totalBytes = 1_250L, speedBps = 100.0)
        )
    }

    @Test
    fun elapsedIsWholeSecondsAndNeverNegative() {
        assertEquals(25L, downloadElapsedSeconds(startedAtMillis = 10_000L, nowMillis = 35_400L))
        assertEquals(0L, downloadElapsedSeconds(startedAtMillis = 10_000L, nowMillis = 9_000L))
    }

    @Test
    fun durationsFormatCompactly() {
        assertEquals("45 sec", formatDownloadDuration(45))
        assertEquals("2 min", formatDownloadDuration(120))
        assertEquals("1 hr", formatDownloadDuration(3_600))
        assertEquals("1 hr 5 min", formatDownloadDuration(3_900))
    }

    @Test
    fun bytesFormatLikeTheDownloadsScreen() {
        assertEquals("0 B", formatDownloadBytes(0))
        assertEquals("512 B", formatDownloadBytes(512))
        assertEquals("1.5 KB", formatDownloadBytes(1_536))
        assertEquals("2.0 MB", formatDownloadBytes(2L * 1024 * 1024))
    }
}
