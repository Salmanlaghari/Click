package com.click.browser.engine

/**
 * Click's own download engine records (Prince's 2026-11-11 downloads upgrade).
 *
 * Until now, downloads to the phone's public folder or app storage went
 * through Android's DownloadManager: its notification is system-owned (tap
 * opens the system Downloads UI, no Pause/Resume/Cancel buttons for Click),
 * and DownloadManager exposes no pause/resume API at all. This engine
 * downloads with OkHttp into an app-private partial file instead, so Click
 * owns the whole lifecycle: progress notifications with Pause/Resume/Cancel,
 * real Range-header resume (the partial file survives app restarts), and a
 * durable record the Downloads screen and the open-with sheet work from.
 *
 * Custom-folder (SAF) downloads deliberately keep using the existing
 * [CustomDownloadScheduler] engine — SAF trees cannot be written by this
 * engine's file-based path.
 */
enum class ClickDownloadState {
    QUEUED,
    RUNNING,
    PAUSED,
    FAILED,
    COMPLETED,
    CANCELED
}

data class ClickDownloadRecord(
    val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val mode: BrowserMode,
    /** [DownloadSettings.LOCATION_PHONE] or [DownloadSettings.LOCATION_APP]. */
    val destination: String,
    val state: ClickDownloadState,
    val bytesDownloaded: Long = 0L,
    /** Total size when the server reported one; -1 when unknown. */
    val totalBytes: Long = -1L,
    /** Validators captured from the first response, used for If-Range resume. */
    val etag: String? = null,
    val lastModified: String? = null,
    val userAgent: String? = null,
    /** Content URI string (MediaStore) or absolute file path once completed. */
    val finalLocation: String? = null,
    val errorMessage: String? = null,
    /** WorkManager request id of the currently scheduled transfer, if any. */
    val workId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    val isActive: Boolean
        get() = state == ClickDownloadState.QUEUED || state == ClickDownloadState.RUNNING

    val canPause: Boolean get() = isActive
    val canResume: Boolean
        get() = state == ClickDownloadState.PAUSED || state == ClickDownloadState.FAILED
    val canCancel: Boolean
        get() = state == ClickDownloadState.QUEUED || state == ClickDownloadState.RUNNING ||
            state == ClickDownloadState.PAUSED
}

/** What the transfer loop should do with a response to a resume request. */
enum class ResumeAction {
    /** Fresh download (no partial file, or the server ignored our Range). */
    WRITE_FRESH,
    /** HTTP 206: append to the partial file. */
    APPEND,
    /** HTTP 416 and the partial file already holds the whole body. */
    ALREADY_COMPLETE,
    /** Any other status: the transfer failed. */
    ERROR
}

/**
 * Pure resume decision for a response received after requesting
 * `Range: bytes=<partialBytes>-` (only sent when [partialBytes] > 0).
 *
 * - 206 Partial Content → append.
 * - 200 OK → the server ignored Range (or validators changed under
 *   If-Range): the body is the full file, so restart from zero with THIS
 *   response instead of corrupting the partial by appending.
 * - 416 Range Not Satisfiable → the partial is already complete when its
 *   size matches the known total; otherwise it is stale and restarts.
 */
fun resumeActionForResponse(
    statusCode: Int,
    partialBytes: Long,
    totalBytes: Long
): ResumeAction = when {
    partialBytes <= 0L -> if (statusCode in 200..299) ResumeAction.WRITE_FRESH else ResumeAction.ERROR
    statusCode == 206 -> ResumeAction.APPEND
    statusCode == 200 -> ResumeAction.WRITE_FRESH
    statusCode == 416 && totalBytes > 0L && partialBytes >= totalBytes -> ResumeAction.ALREADY_COMPLETE
    statusCode == 416 -> ResumeAction.WRITE_FRESH
    else -> ResumeAction.ERROR
}

/** Total size of the resource behind a 206 response, from Content-Range. */
fun totalFromContentRange(contentRange: String?, fallbackContentLength: Long, partialBytes: Long): Long {
    // Content-Range: bytes <start>-<end>/<total>
    val total = contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull()
    if (total != null && total > 0L) return total
    return if (fallbackContentLength > 0L) fallbackContentLength + partialBytes else -1L
}

/** Keeps active records forever and caps terminal history (mirrors the
 *  custom-folder store's trimming policy). */
fun trimClickDownloadRecords(
    items: List<ClickDownloadRecord>,
    maxRecords: Int
): List<ClickDownloadRecord> {
    require(maxRecords >= 0)
    val active = items.filter { it.isActive || it.state == ClickDownloadState.PAUSED }
    val terminalLimit = (maxRecords - active.size).coerceAtLeast(0)
    val terminal = items.filterNot { it.isActive || it.state == ClickDownloadState.PAUSED }
        .sortedByDescending { it.updatedAt }
        .take(terminalLimit)
    return (active + terminal).sortedByDescending { it.updatedAt }
}
