package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure policy behind the Click download engine's pause/resume. */
class ClickDownloadPolicyTest {

    @Test
    fun freshDownloadWritesFromScratch() {
        assertEquals(
            ResumeAction.WRITE_FRESH,
            resumeActionForResponse(statusCode = 200, partialBytes = 0L, totalBytes = -1L)
        )
    }

    @Test
    fun freshDownloadWithErrorStatusFails() {
        assertEquals(
            ResumeAction.ERROR,
            resumeActionForResponse(statusCode = 404, partialBytes = 0L, totalBytes = -1L)
        )
    }

    @Test
    fun partialBodyIsAppendedWhenServerAnswers206() {
        assertEquals(
            ResumeAction.APPEND,
            resumeActionForResponse(statusCode = 206, partialBytes = 400L, totalBytes = 1000L)
        )
    }

    @Test
    fun serverIgnoringRangeRestartsFromScratchInsteadOfCorrupting() {
        assertEquals(
            ResumeAction.WRITE_FRESH,
            resumeActionForResponse(statusCode = 200, partialBytes = 400L, totalBytes = 1000L)
        )
    }

    @Test
    fun rangeNotSatisfiableWithFullPartialMeansAlreadyComplete() {
        assertEquals(
            ResumeAction.ALREADY_COMPLETE,
            resumeActionForResponse(statusCode = 416, partialBytes = 1000L, totalBytes = 1000L)
        )
    }

    @Test
    fun rangeNotSatisfiableWithShortPartialRestarts() {
        assertEquals(
            ResumeAction.WRITE_FRESH,
            resumeActionForResponse(statusCode = 416, partialBytes = 900L, totalBytes = 1000L)
        )
    }

    @Test
    fun contentRangeTotalParsing() {
        assertEquals(12345L, totalFromContentRange("bytes 400-999/12345", -1L, 400L))
        // Unknown total in Content-Range: fall back to remaining length + partial.
        assertEquals(1000L, totalFromContentRange("bytes 400-999/*", 600L, 400L))
        assertEquals(-1L, totalFromContentRange(null, -1L, 0L))
        assertEquals(-1L, totalFromContentRange("garbage", -1L, 0L))
    }

    @Test
    fun activeAndPausedRecordsAreNeverTrimmedAndNewestTerminalWin() {
        val now = 10_000L
        val records = buildList {
            add(record("active1", ClickDownloadState.RUNNING, now))
            add(record("paused1", ClickDownloadState.PAUSED, now - 5))
            repeat(5) { i -> add(record("done$i", ClickDownloadState.COMPLETED, now - 100 - i)) }
        }
        val trimmed = trimClickDownloadRecords(records, maxRecords = 4)
        assertEquals(4, trimmed.size)
        assertTrue(trimmed.any { it.id == "active1" })
        assertTrue(trimmed.any { it.id == "paused1" })
        // Newest finished records survive; the oldest finished are dropped.
        assertTrue(trimmed.any { it.id == "done0" })
        assertTrue(trimmed.none { it.id == "done4" })
    }

    private fun record(id: String, state: ClickDownloadState, updatedAt: Long) = ClickDownloadRecord(
        id = id,
        url = "https://example.com/$id",
        fileName = id,
        mimeType = null,
        mode = BrowserMode.SIMPLE,
        destination = DownloadSettings.LOCATION_PHONE,
        state = state,
        createdAt = updatedAt,
        updatedAt = updatedAt
    )
}
