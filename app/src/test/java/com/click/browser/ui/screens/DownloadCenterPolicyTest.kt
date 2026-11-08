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
}
