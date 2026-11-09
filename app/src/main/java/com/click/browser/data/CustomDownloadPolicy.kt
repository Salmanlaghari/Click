package com.click.browser.data

/** Null means the size is unknown and the UI should show an indeterminate indicator. */
fun customDownloadProgress(bytesDownloaded: Long, totalBytes: Long): Float? {
    if (totalBytes <= 0L) return null
    return (bytesDownloaded.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
}

/** Keeps every queued/running transfer and applies the record cap only to terminal history. */
internal fun trimCustomDownloadRecords(
    items: List<CustomDownloadItem>,
    maxRecords: Int
): List<CustomDownloadItem> {
    require(maxRecords >= 0)
    val active = items.filter { it.canCancel }
    val terminalLimit = (maxRecords - active.size).coerceAtLeast(0)
    val terminal = items.filterNot { it.canCancel }
        .sortedByDescending { it.updatedAt }
        .take(terminalLimit)
    return (active + terminal).sortedByDescending { it.updatedAt }
}
