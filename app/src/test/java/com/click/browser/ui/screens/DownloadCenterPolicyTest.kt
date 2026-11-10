package com.click.browser.ui.screens

import android.app.DownloadManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCenterPolicyTest {
    @Test
    fun mapsEveryDownloadManagerLifecycleState() {
        assertEquals(ManagedDownloadState.PENDING, managedDownloadState(DownloadManager.STATUS_PENDING))
        assertEquals(ManagedDownloadState.RUNNING, managedDownloadState(DownloadManager.STATUS_RUNNING))
        assertEquals(ManagedDownloadState.PAUSED, managedDownloadState(DownloadManager.STATUS_PAUSED))
        assertEquals(ManagedDownloadState.COMPLETED, managedDownloadState(DownloadManager.STATUS_SUCCESSFUL))
        assertEquals(ManagedDownloadState.FAILED, managedDownloadState(DownloadManager.STATUS_FAILED))
        assertNull(managedDownloadState(Int.MIN_VALUE))
    }

    @Test
    fun includesOnlyClickOwnedRecordsWithRecognizedStatuses() {
        assertTrue(isClickManagedDownload(CLICK_DOWNLOAD_TITLE, DownloadManager.STATUS_SUCCESSFUL))
        assertTrue(isClickManagedDownload(CLICK_DOWNLOAD_TITLE, DownloadManager.STATUS_FAILED))
        assertTrue(isClickManagedDownload(CLICK_DOWNLOAD_TITLE, DownloadManager.STATUS_RUNNING))
        assertFalse(isClickManagedDownload("Another app download", DownloadManager.STATUS_SUCCESSFUL))
        assertFalse(isClickManagedDownload(null, DownloadManager.STATUS_RUNNING))
        assertFalse(isClickManagedDownload(CLICK_DOWNLOAD_TITLE, Int.MIN_VALUE))
    }

    @Test
    fun terminalHistoryIsNewestFirstDeduplicatedAndCapped() {
        val history = mutableListOf<ManagedDownload>()
        (1L..(MAX_MANAGED_DOWNLOAD_HISTORY + 5).toLong()).forEach { id ->
            retainRecentManagedDownload(history, item(id))
        }
        assertEquals(MAX_MANAGED_DOWNLOAD_HISTORY, history.size)
        assertEquals((MAX_MANAGED_DOWNLOAD_HISTORY + 5).toLong(), history.first().id)
        assertEquals(6L, history.last().id)

        retainRecentManagedDownload(history, item(20L, label = "updated name"))
        assertEquals(MAX_MANAGED_DOWNLOAD_HISTORY, history.size)
        assertEquals(1, history.count { it.id == 20L })
        assertEquals("updated name", history.first { it.id == 20L }.label)
    }

    @Test
    fun doesNotRetainActiveItemsAsHistory() {
        val history = mutableListOf<ManagedDownload>()
        retainRecentManagedDownload(history, item(1L, state = ManagedDownloadState.RUNNING))
        assertTrue(history.isEmpty())
    }

    @Test
    fun failedInitialQueryIsNotTreatedAsAnEmptyDownloadList() {
        val state = DownloadCenterQueryState.Loading.recordFailure()

        assertTrue(state is DownloadCenterQueryState.Failed)
        assertFalse(state.shouldShowEmptyState(savedFileCount = 0))
        assertTrue(state.downloadsForDisplay().isEmpty())
    }

    @Test
    fun emptyMessageRequiresSuccessfulQueryAndNoSavedFiles() {
        assertFalse(DownloadCenterQueryState.Loading.shouldShowEmptyState(savedFileCount = 0))
        assertTrue(DownloadCenterQueryState.Loaded(emptyList()).shouldShowEmptyState(savedFileCount = 0))
        assertFalse(DownloadCenterQueryState.Loaded(emptyList()).shouldShowEmptyState(savedFileCount = 1))
    }

    @Test
    fun failedRefreshRetainsLastSuccessfulDownloads() {
        val previouslyLoaded = listOf(item(9L))
        val failed = DownloadCenterQueryState.Loaded(previouslyLoaded).recordFailure()

        assertEquals(previouslyLoaded, failed.downloadsForDisplay())
        assertFalse(failed.shouldShowEmptyState(savedFileCount = 0))
        assertEquals(failed, failed.recordFailure())
    }

    @Test
    fun savedRecordMatchingAManagedDownloadIsDuplicated() {
        val managed = listOf(
            ManagedDownload(
                id = 7L,
                label = "Click-Browser-Release.zip",
                downloadedBytes = 10L,
                totalBytes = 10L,
                state = ManagedDownloadState.COMPLETED,
                sourceUrl = "https://example.com/Click-Browser-Release.zip"
            )
        )
        assertTrue(
            isSavedRecordDuplicatedByManaged(
                "Click-Browser-Release.zip",
                "https://example.com/Click-Browser-Release.zip",
                managed
            )
        )
        // Different file or different source stays visible as its own record.
        assertFalse(
            isSavedRecordDuplicatedByManaged(
                "other.zip",
                "https://example.com/Click-Browser-Release.zip",
                managed
            )
        )
        assertFalse(
            isSavedRecordDuplicatedByManaged(
                "Click-Browser-Release.zip",
                "https://example.com/different.zip",
                managed
            )
        )
        assertFalse(
            isSavedRecordDuplicatedByManaged(
                "Click-Browser-Release.zip",
                "https://example.com/Click-Browser-Release.zip",
                emptyList()
            )
        )
    }

    private fun item(
        id: Long,
        label: String = "download-$id.bin",
        state: ManagedDownloadState = ManagedDownloadState.COMPLETED
    ) = ManagedDownload(
        id = id,
        label = label,
        downloadedBytes = 1,
        totalBytes = 1,
        state = state,
        sourceUrl = "https://example.test/$id.bin"
    )
}
