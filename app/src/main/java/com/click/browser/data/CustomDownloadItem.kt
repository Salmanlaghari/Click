package com.click.browser.data

import com.click.browser.engine.BrowserMode

enum class CustomDownloadState {
    QUEUED,
    RUNNING,
    FAILED,
    CANCELED,
    COMPLETED
}

enum class CustomDownloadBackend {
    WORK_MANAGER,
    USER_INITIATED_JOB
}

/**
 * Durable metadata for a custom-folder transfer. Records live in the originating
 * browser profile; attempt IDs prevent a stopped or stale backend from mutating a retry.
 * A staged response body is held in app-private files until the transfer completes.
 */
data class CustomDownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val destinationTreeUri: String,
    val userAgent: String?,
    val mode: BrowserMode,
    val state: CustomDownloadState,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val createdAt: Long,
    val updatedAt: Long,
    val backend: CustomDownloadBackend? = null,
    val backendId: String? = null,
    val errorMessage: String? = null,
    /** AES-GCM ciphertext only; never store a raw Cookie header in preferences or scheduler input. */
    val encryptedCookieHeader: String? = null,
    /** Unique owner token for the currently scheduled attempt. */
    val attemptId: String? = null,
    /** App-private staged Gecko response; kept across retry/cancel and deleted on completion. */
    val sourceFilePath: String? = null
) {
    val canCancel: Boolean
        get() = state == CustomDownloadState.QUEUED || state == CustomDownloadState.RUNNING

    val canRetry: Boolean
        get() = (state == CustomDownloadState.FAILED || state == CustomDownloadState.CANCELED) &&
            (sourceFilePath != null || url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true))
}
