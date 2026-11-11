package com.click.browser.engine

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.click.browser.data.ClickDownloadStore
import java.io.File
import java.util.UUID

/**
 * Control plane for Click's own download engine: creates durable records,
 * schedules transfers on WorkManager, and applies pause / resume / cancel
 * from the Downloads screen and from notification actions.
 *
 * Pause semantics: the record is marked PAUSED first (the transfer loop
 * polls the store and stops cleanly at the next chunk boundary), then the
 * WorkManager request is cancelled as a backstop. The partial file is kept
 * either way, so resume continues with a Range request instead of starting
 * over. Pause/resume therefore survives process death and app restarts.
 */
object ClickDownloadEngine {
    const val EXTRA_RECORD_ID = "click_download_record_id"
    const val EXTRA_MODE = "click_download_mode"
    const val WORK_TAG = "click-download"
    const val PARTIAL_DIRECTORY = "click_downloads"

    fun partialFile(context: Context, recordId: String): File {
        val dir = File(context.filesDir, PARTIAL_DIRECTORY).apply { mkdirs() }
        return File(dir, "$recordId.part")
    }

    suspend fun enqueue(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String?,
        mode: BrowserMode,
        destination: String,
        userAgent: String?
    ): ClickDownloadRecord {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val record = ClickDownloadRecord(
            id = UUID.randomUUID().toString(),
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            mode = mode,
            destination = destination,
            state = ClickDownloadState.QUEUED,
            userAgent = userAgent,
            createdAt = now,
            updatedAt = now
        )
        ClickDownloadStore(appContext).add(record)
        schedule(appContext, record)
        return record
    }

    /** (Re)schedules the transfer for a QUEUED record. */
    suspend fun schedule(context: Context, record: ClickDownloadRecord) {
        val appContext = context.applicationContext
        val request = OneTimeWorkRequestBuilder<ClickDownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(EXTRA_RECORD_ID, record.id)
                    .putString(EXTRA_MODE, record.mode.name)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(WORK_TAG)
            .build()
        ClickDownloadStore(appContext).update(record.mode, record.id) { current ->
            current.copy(workId = request.id.toString(), updatedAt = System.currentTimeMillis())
        }
        WorkManager.getInstance(appContext).enqueue(request)
    }

    suspend fun pause(context: Context, mode: BrowserMode, recordId: String) {
        val appContext = context.applicationContext
        val store = ClickDownloadStore(appContext)
        val record = store.getFor(mode, recordId) ?: return
        if (!record.canPause) return
        val paused = store.update(mode, recordId) { current ->
            current.copy(
                state = ClickDownloadState.PAUSED,
                updatedAt = System.currentTimeMillis()
            )
        } ?: return
        record.workId?.let { workId ->
            runCatching {
                WorkManager.getInstance(appContext).cancelWorkById(UUID.fromString(workId))
            }
        }
        ClickDownloadNotifications.showProgress(appContext, paused)
    }

    suspend fun resume(context: Context, mode: BrowserMode, recordId: String) {
        val appContext = context.applicationContext
        val store = ClickDownloadStore(appContext)
        val record = store.getFor(mode, recordId) ?: return
        if (!record.canResume && record.state != ClickDownloadState.QUEUED) return
        val queued = store.update(mode, recordId) { current ->
            current.copy(
                state = ClickDownloadState.QUEUED,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
        } ?: return
        schedule(appContext, queued)
        ClickDownloadNotifications.showProgress(appContext, queued)
    }

    suspend fun cancel(context: Context, mode: BrowserMode, recordId: String) {
        val appContext = context.applicationContext
        val store = ClickDownloadStore(appContext)
        val record = store.getFor(mode, recordId) ?: return
        if (!record.canCancel && record.state != ClickDownloadState.FAILED) return
        record.workId?.let { workId ->
            runCatching {
                WorkManager.getInstance(appContext).cancelWorkById(UUID.fromString(workId))
            }
        }
        store.update(mode, recordId) { current ->
            current.copy(
                state = ClickDownloadState.CANCELED,
                updatedAt = System.currentTimeMillis()
            )
        }
        runCatching { partialFile(appContext, recordId).delete() }
        ClickDownloadNotifications.cancelAll(appContext, recordId)
    }

    /** Removes a terminal record (and any leftover partial) from the list. */
    suspend fun dismiss(context: Context, mode: BrowserMode, recordId: String) {
        val appContext = context.applicationContext
        val store = ClickDownloadStore(appContext)
        val record = store.getFor(mode, recordId) ?: return
        if (record.isActive || record.state == ClickDownloadState.PAUSED) return
        store.remove(mode, recordId)
        runCatching { partialFile(appContext, recordId).delete() }
        ClickDownloadNotifications.cancelAll(appContext, recordId)
    }

    /**
     * Repairs records after process death: a RUNNING record whose work is
     * gone becomes PAUSED (honest "interrupted — tap Resume"); a QUEUED
     * record with no scheduled work is rescheduled.
     */
    suspend fun reconcile(context: Context, mode: BrowserMode) {
        val appContext = context.applicationContext
        val store = ClickDownloadStore(appContext)
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return
        for (record in store.allFor(mode)) {
            when (record.state) {
                ClickDownloadState.RUNNING -> {
                    val workId = runCatching { record.workId?.let { UUID.fromString(it) } }.getOrNull()
                    val alive = workId != null && runCatching {
                        val state = workManager.getWorkInfoById(workId).get()?.state
                        state == androidx.work.WorkInfo.State.ENQUEUED ||
                            state == androidx.work.WorkInfo.State.RUNNING ||
                            state == androidx.work.WorkInfo.State.BLOCKED
                    }.getOrDefault(false)
                    if (!alive) {
                        store.update(mode, record.id) { current ->
                            current.copy(
                                state = ClickDownloadState.PAUSED,
                                errorMessage = "Interrupted — tap Resume to continue.",
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                        ClickDownloadNotifications.cancelProgress(appContext, record.id)
                    }
                }
                ClickDownloadState.QUEUED -> {
                    val workId = runCatching { record.workId?.let { UUID.fromString(it) } }.getOrNull()
                    val scheduled = workId != null && runCatching {
                        val state = workManager.getWorkInfoById(workId).get()?.state
                        state == androidx.work.WorkInfo.State.ENQUEUED ||
                            state == androidx.work.WorkInfo.State.RUNNING ||
                            state == androidx.work.WorkInfo.State.BLOCKED
                    }.getOrDefault(false)
                    if (!scheduled) schedule(appContext, record)
                }
                else -> Unit
            }
        }
    }
}
