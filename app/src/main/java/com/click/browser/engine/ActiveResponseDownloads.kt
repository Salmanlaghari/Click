package com.click.browser.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
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
    val startedAtMillis: Long,
    /** True while the user has paused the stream (the copy loop is gated). */
    val paused: Boolean = false
)

/** Thrown out of the streaming copy loop when the user cancels the download. */
class ResponseDownloadCanceledException : Exception("Response download canceled by the user")

/**
 * Pause / cancel gate consulted by the streaming copy loop of one
 * response-body download.
 *
 * A GeckoView response body is a one-shot stream: it cannot be re-fetched
 * later, so pause cannot mean "stop now, fetch the rest tomorrow" the way
 * the URL engine's Range resume works. Pause here is still real, not a UI
 * fiction: the copy loop stops reading, TCP flow control throttles the
 * server, and not one more byte is written to storage until resume. The
 * honest limit is that the connection stays open while paused — if the
 * server eventually drops it, the transfer fails visibly instead of
 * pretending to be parked. Cancel closes the stream, which unblocks a
 * read that is in flight, and the copy paths delete their partial output
 * as the cancellation propagates.
 */
class ResponseDownloadControl internal constructor() {
    @Volatile
    private var pausedFlag = false

    @Volatile
    private var canceledFlag = false

    /** Invoked once on cancel; DownloadHelper wires it to closing the body. */
    @Volatile
    var onCancel: (() -> Unit)? = null

    val isPaused: Boolean get() = pausedFlag
    val isCanceled: Boolean get() = canceledFlag

    fun pause() {
        pausedFlag = true
    }

    fun resume() {
        pausedFlag = false
    }

    fun cancel() {
        canceledFlag = true
        pausedFlag = false
        runCatching { onCancel?.invoke() }
    }

    /**
     * Copy-loop gate: blocks (brief sleeps) while paused, throws
     * [ResponseDownloadCanceledException] once canceled. Called once per
     * buffer iteration; the copy itself runs on a blocking IO thread, so
     * sleeping here is safe and does not stall any UI or dispatcher pool
     * used for short work.
     */
    fun checkpoint() {
        while (pausedFlag && !canceledFlag) {
            try {
                Thread.sleep(PAUSE_POLL_MILLIS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ResponseDownloadCanceledException()
            }
        }
        if (canceledFlag) throw ResponseDownloadCanceledException()
    }

    private companion object {
        const val PAUSE_POLL_MILLIS = 40L
    }
}

/** Process-wide, thread-safe registry of in-flight response-body downloads. */
object ActiveResponseDownloads {
    private val nextId = AtomicLong(1L)
    private val controls = ConcurrentHashMap<Long, ResponseDownloadControl>()
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
        controls[id] = ResponseDownloadControl()
        _active.update { current -> current + item }
        return id
    }

    /** The pause/cancel gate for [id], or null when the download is gone. */
    fun control(id: Long): ResponseDownloadControl? = controls[id]

    fun updateProgress(id: Long, downloadedBytes: Long) {
        _active.update { current ->
            current.map { item ->
                if (item.id == id) item.copy(downloadedBytes = downloadedBytes) else item
            }
        }
    }

    /** Pauses the stream: the copy loop stops reading at the next buffer. */
    fun pause(id: Long) {
        controls[id]?.pause() ?: return
        _active.update { current ->
            current.map { item -> if (item.id == id) item.copy(paused = true) else item }
        }
    }

    /** Resumes a paused stream: the copy loop starts reading again. */
    fun resume(id: Long) {
        controls[id]?.resume() ?: return
        _active.update { current ->
            current.map { item -> if (item.id == id) item.copy(paused = false) else item }
        }
    }

    /**
     * Cancels the download: the gate throws out of the copy loop, which
     * deletes the partial output; [end] then removes the registry entry.
     */
    fun cancel(id: Long) {
        controls[id]?.cancel()
    }

    fun end(id: Long) {
        controls.remove(id)
        _active.update { current -> current.filterNot { it.id == id } }
    }
}
