package com.click.browser.data

import com.click.browser.engine.BrowserMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDownloadPolicyTest {
    @Test
    fun onlyActiveTransfersCanBeCanceled() {
        assertTrue(item(CustomDownloadState.QUEUED).canCancel)
        assertTrue(item(CustomDownloadState.RUNNING).canCancel)
        assertFalse(item(CustomDownloadState.FAILED).canCancel)
        assertFalse(item(CustomDownloadState.CANCELED).canCancel)
        assertFalse(item(CustomDownloadState.COMPLETED).canCancel)
    }

    @Test
    fun retryIsAvailableOnlyForTerminalHttpTransfers() {
        assertTrue(item(CustomDownloadState.FAILED).canRetry)
        assertTrue(item(CustomDownloadState.CANCELED).canRetry)
        assertFalse(item(CustomDownloadState.QUEUED).canRetry)
        assertFalse(item(CustomDownloadState.RUNNING).canRetry)
        assertFalse(item(CustomDownloadState.COMPLETED).canRetry)
        assertFalse(item(CustomDownloadState.FAILED, url = "file:///private/download").canRetry)
        assertTrue(
            item(
                CustomDownloadState.FAILED,
                url = "file:///private/download",
                sourceFilePath = "/private/staged-response"
            ).canRetry
        )
    }

    @Test
    fun progressIsIndeterminateForUnknownSizeAndClampedForKnownSize() {
        assertNull(customDownloadProgress(5, -1))
        assertNull(customDownloadProgress(5, 0))
        assertEquals(0.5f, customDownloadProgress(50, 100)!!, 0.0001f)
        assertEquals(1f, customDownloadProgress(150, 100)!!, 0f)
    }

    @Test
    fun retentionKeepsEveryActiveTransferAndNewestTerminalHistory() {
        val oldFailure = item(CustomDownloadState.FAILED).copy(id = "old", updatedAt = 1L)
        val active = item(CustomDownloadState.RUNNING).copy(id = "active", updatedAt = 2L)
        val recentCanceled = item(CustomDownloadState.CANCELED).copy(id = "recent", updatedAt = 3L)

        val retained = trimCustomDownloadRecords(listOf(oldFailure, active, recentCanceled), maxRecords = 2)

        assertEquals(setOf("active", "recent"), retained.map { it.id }.toSet())
    }

    @Test
    fun retentionDoesNotEvictActiveRowsWhenTheyExceedTheNominalCap() {
        val queued = item(CustomDownloadState.QUEUED).copy(id = "queued")
        val running = item(CustomDownloadState.RUNNING).copy(id = "running")

        assertEquals(2, trimCustomDownloadRecords(listOf(queued, running), maxRecords = 1).size)
    }

    private fun item(
        state: CustomDownloadState,
        url: String = "https://example.test/file.bin",
        sourceFilePath: String? = null
    ) = CustomDownloadItem(
        id = "id",
        url = url,
        fileName = "file.bin",
        mimeType = null,
        destinationTreeUri = "content://example/tree/1",
        userAgent = null,
        mode = BrowserMode.SIMPLE,
        state = state,
        createdAt = 1L,
        updatedAt = 1L,
        sourceFilePath = sourceFilePath
    )
}
