package com.click.browser.ui.screens

import android.app.DownloadManager

/** States the Downloads Center can truthfully show for a Click-managed system download. */
internal enum class ManagedDownloadState {
    PENDING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED
}

internal const val CLICK_DOWNLOAD_TITLE = "Click Browser Download"

/** Returns null for DownloadManager statuses that are not part of the supported query. */
internal fun managedDownloadState(status: Int): ManagedDownloadState? = when (status) {
    DownloadManager.STATUS_PENDING -> ManagedDownloadState.PENDING
    DownloadManager.STATUS_RUNNING -> ManagedDownloadState.RUNNING
    DownloadManager.STATUS_PAUSED -> ManagedDownloadState.PAUSED
    DownloadManager.STATUS_SUCCESSFUL -> ManagedDownloadState.COMPLETED
    DownloadManager.STATUS_FAILED -> ManagedDownloadState.FAILED
    else -> null
}

/** Excludes other apps' downloads and any unexpected DownloadManager status. */
internal fun isClickManagedDownload(title: String?, status: Int): Boolean =
    title == CLICK_DOWNLOAD_TITLE && managedDownloadState(status) != null
