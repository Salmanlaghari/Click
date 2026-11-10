package com.click.browser.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * A response-body download (GeckoView external response) that Click is
 * currently streaming to storage itself.
 *
 * These downloads never enter Android's DownloadManager, so without this
 * registry they were completely invisible: no progress anywhere, no entry in
 * the Downloads screen while the (potentially large) file copied, and no
 * record at all if the copy failed. Prince's 2026-11-10 device test hit
 * exactly that — a saved release ZIP left the Downloads screen showing
 * "No downloads yet."
 */
data class ActiveResponseDownload(
    val id: Long,
    val fileName: String,
    /** Human-readable destination, e.g. "Downloads/ClickBrowser". */
    val destinationLabel: String,
    val downloadedBytes: Long,
    /** Total size when the server reported one; -1 when unknown. */
    val totalBytes: Long,
    val startedAtMillis: Long
)

/** Process-wide, thread-safe registry of in-flight response-body downloads. */
object ActiveResponseDownloads {
    private val nextId = AtomicLong(1L)
    private val _active = MutableStateFlow<List<ActiveResponseDownload>>(emptyList())
    val active: StateFlow<List<ActiveResponseDownload>> = _active.asStateFlow()

    fun begin(fileName: String, destinationLabel: String, totalBytes: Long): Long {
        val id = nextId.getAndIncrement()
        val item = ActiveResponseDownload(
            id = id,
            fileName = fileName,
            destinationLabel = destinationLabel,
            downloadedBytes = 0L,
            totalBytes = totalBytes,
            startedAtMillis = System.currentTimeMillis()
        )
        _active.update { current -> current + item }
        return id
    }

    fun updateProgress(id: Long, downloadedBytes: Long) {
        _active.update { current ->
            current.map { item ->
                if (item.id == id) item.copy(downloadedBytes = downloadedBytes) else item
            }
        }
    }

    fun end(id: Long) {
        _active.update { current -> current.filterNot { it.id == id } }
    }
}
