package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure policy behind the Click download engine's pause/resume. */
class ClickDownloadPolicyTest {

    @Test
    fun freshDownloadSendsNoRangeHeaders() {
        val action = resumeActionForResponse(
            httpCode = 200,
            partialBytes = 0L,
            contentRangeTotal = null,
            contentLength = 1000L
        )
        assertEquals(ResumeAction.FRESH_OR_FULL, action)
    }

    @Test
    fun partialBodyIsAppendedWhenServerAnswers206() {
        val action = resumeActionForResponse(
            httpCode = 206,
            partialBytes = 400L,
            contentRangeTotal = 1000L,
            contentLength = 600L
        )
        assertEquals(ResumeAction.APPEND, action)
    }

    @Test
    fun serverIgnoringRangeRestartsFromScratchInsteadOfCorrupting() {
        val action = resumeActionForResponse(
            httpCode = 200,
            partialBytes = 400L,
            contentRangeTotal = null,
            contentLength = 1000L
        )
        assertEquals(ResumeAction.RESTART_WITH_BODY, action)
    }

    @Test
    fun rangeNotSatisfiableWithMatchingTotalMeansAlreadyComplete() {
        val action = resumeActionForResponse(
            httpCode = 416,
            partialBytes = 1000L,
            contentRangeTotal = 1000L,
            contentLength = -1L
        )
        assertEquals(ResumeAction.ALREADY_COMPLETE, action)
    }

    @Test
    fun rangeNotSatisfiableWithMismatchedTotalRestarts() {
        val action = resumeActionForResponse(
            httpCode = 416,
            partialBytes = 900L,
            contentRangeTotal = 1000L,
            contentLength = -1L
        )
        assertEquals(ResumeAction.RESTART, action)
    }

    @Test
    fun contentRangeTotalParsing() {
        assertEquals(12345L, totalFromContentRange("bytes 400-999/12345"))
        assertEquals(null, totalFromContentRange("bytes 400-999/*"))
        assertEquals(null, totalFromContentRange(null))
        assertEquals(null, totalFromContentRange("garbage"))
    }

    @Test
    fun activeRecordsAreNeverTrimmedAndOldestFinishedGoFirst() {
        val now = 10_000L
        val records = buildList {
            add(record("active1", ClickDownloadState.RUNNING, now))
            add(record("active2", ClickDownloadState.PAUSED, now))
            repeat(5) { i -> add(record("done$i", ClickDownloadState.COMPLETED, now - i)) }
        }
        val trimmed = trimClickDownloadRecords(records, max = 4)
        assertEquals(4, trimmed.size)
        assertTrue(trimmed.any { it.id == "active1" })
        assertTrue(trimmed.any { it.id == "active2" })
        // Newest finished records survive; the oldest finished are dropped.
        assertTrue(trimmed.any { it.id == "done0" })
        assertTrue(trimmed.none { it.id == "done4" })
    }

    private fun record(id: String, state: ClickDownloadState, updatedAt: Long) = ClickDownload(
        id = id,
        url = "https://example.com/$id",
        fileName = id,
        state = state,
        updatedAtMs = updatedAt
    )
}
