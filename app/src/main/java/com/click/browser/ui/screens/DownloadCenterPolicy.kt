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

internal data class ManagedDownload(
    val id: Long,
    val label: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val state: ManagedDownloadState,
    val sourceUrl: String
)

/** Keeps query failure distinct from a successful empty download list. */
internal sealed class DownloadCenterQueryState {
    object Loading : DownloadCenterQueryState()
    data class Loaded(val downloads: List<ManagedDownload>) : DownloadCenterQueryState()
    data class Failed(val lastSuccessfulDownloads: List<ManagedDownload>?) : DownloadCenterQueryState()

    fun downloadsForDisplay(): List<ManagedDownload> = when (this) {
        Loading -> emptyList()
        is Loaded -> downloads
        is Failed -> lastSuccessfulDownloads.orEmpty()
    }

    fun recordFailure(): DownloadCenterQueryState = when (this) {
        Loading -> Failed(lastSuccessfulDownloads = null)
        is Loaded -> Failed(lastSuccessfulDownloads = downloads)
        is Failed -> this
    }

    fun shouldShowEmptyState(savedFileCount: Int): Boolean =
        this is Loaded && downloads.isEmpty() && savedFileCount == 0
}

internal const val CLICK_DOWNLOAD_TITLE = "Click Browser Download"
internal const val MAX_MANAGED_DOWNLOAD_HISTORY = 100

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

/** Keeps terminal records newest-first, deduplicated by DownloadManager ID and bounded in memory. */
internal fun retainRecentManagedDownload(
    history: MutableList<ManagedDownload>,
    item: ManagedDownload,
    maxItems: Int = MAX_MANAGED_DOWNLOAD_HISTORY
) {
    if (item.state != ManagedDownloadState.COMPLETED && item.state != ManagedDownloadState.FAILED) return
    if (maxItems <= 0) {
        history.clear()
        return
    }
    history.removeAll { it.id == item.id }
    val insertAt = history.indexOfFirst { it.id < item.id }
    if (insertAt < 0) history.add(item) else history.add(insertAt, item)
    while (history.size > maxItems) history.removeAt(history.lastIndex)
}
