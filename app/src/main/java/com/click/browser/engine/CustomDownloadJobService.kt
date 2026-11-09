package com.click.browser.engine

import android.app.NotificationManager
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import com.click.browser.data.BrowserRepository
import com.click.browser.data.CustomDownloadBackend
import com.click.browser.data.CustomDownloadItem
import com.click.browser.data.CustomDownloadState
import com.click.browser.data.DownloadItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** JobScheduler backend for long, user-started custom-folder downloads on API 34+. */
class CustomDownloadJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val running = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val recordId = params.extras.getString(CustomDownloadScheduler.EXTRA_RECORD_ID) ?: return false
        val mode = runCatching {
            BrowserMode.valueOf(params.extras.getString(CustomDownloadScheduler.EXTRA_MODE).orEmpty())
        }.getOrNull() ?: return false
        val attemptId = params.extras.getString(CustomDownloadScheduler.EXTRA_ATTEMPT_ID).orEmpty()
        val notificationId = CustomDownloadNotifications.progressNotificationId(recordId, attemptId)
        val fallbackItem = itemFromParams(params, mode, recordId)
        runCatching {
            setNotification(
                params,
                notificationId,
                CustomDownloadNotifications.progressNotification(
                    this,
                    fallbackItem,
                    CustomDownloadScheduler.cancelPendingIntent(this, fallbackItem)
                ),
                JOB_END_NOTIFICATION_POLICY_DETACH
            )
        }

        val job = serviceScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                val repository = BrowserRepository(applicationContext)
                var item = repository.getCustomDownloadFor(mode, recordId) ?: fallbackItem
                var shouldReschedule = false
                var pendingFinalLocation: String? = null
                var attemptClaimed = false
                try {
                if (attemptId.isBlank()) return@launch
                item = repository.claimCustomDownloadFor(mode, recordId, attemptId) ?: return@launch
                attemptClaimed = true
                updateNotification(params, notificationId, item)

                val result = CustomFolderDownloadTransfer.download(applicationContext, item) { downloaded, total ->
                    if (!repository.updateCustomDownloadProgressFor(mode, recordId, attemptId, downloaded, total)) {
                        throw CancellationException("Download attempt is no longer active.")
                    }
                    item = item.copy(bytesDownloaded = downloaded, totalBytes = total)
                    updateNotification(params, notificationId, item)
                }
                pendingFinalLocation = result.location
                currentCoroutineContext().ensureActive()
                val stagedSourcePath = item.sourceFilePath
                val timestamp = System.currentTimeMillis()
                val completed = repository.completeCustomDownloadFor(
                    mode = mode,
                    id = recordId,
                    attemptId = attemptId,
                    item = DownloadItem(
                        fileName = result.fileName,
                        url = item.url,
                        path = result.location,
                        timestamp = timestamp
                    ),
                    downloaded = result.bytesDownloaded,
                    total = result.totalBytes
                )
                if (completed) {
                    pendingFinalLocation = null
                    item = item.copy(
                        fileName = result.fileName,
                        state = CustomDownloadState.COMPLETED,
                        bytesDownloaded = result.bytesDownloaded,
                        totalBytes = result.totalBytes,
                        updatedAt = timestamp,
                        errorMessage = null,
                        encryptedCookieHeader = null,
                        sourceFilePath = null
                    )
                    stagedSourcePath?.let { runCatching { File(it).delete() } }
                    updateNotification(
                        params,
                        notificationId,
                        item
                    )
                } else {
                    runCatching { contentResolver.delete(android.net.Uri.parse(result.location), null, null) }
                    pendingFinalLocation = null
                }
            } catch (cancelled: CancellationException) {
                val stored = runCatching { repository.getCustomDownloadFor(mode, recordId) }.getOrNull()
                val userCancelled = stored?.state == CustomDownloadState.CANCELED ||
                    CustomDownloadScheduler.consumeExplicitCancel(recordId, attemptId)
                withContext(NonCancellable + Dispatchers.IO) {
                    repository.stopCustomDownloadAttemptFor(mode, recordId, attemptId, userCancelled)
                }
                if (userCancelled) {
                    runCatching {
                        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                            .cancel(notificationId)
                    }
                } else {
                    shouldReschedule = true
                }
            } catch (failure: CustomTransferFailure) {
                if (repository.failCustomDownloadAttemptFor(mode, recordId, attemptId, failure.userMessage)) {
                    item = item.copy(state = CustomDownloadState.FAILED, errorMessage = failure.userMessage)
                    updateNotification(
                        params,
                        notificationId,
                        item
                    )
                }
            } catch (_: Exception) {
                val message = "The download could not be saved. Check the connection and folder, then retry."
                if (repository.failCustomDownloadAttemptFor(mode, recordId, attemptId, message)) {
                    item = item.copy(state = CustomDownloadState.FAILED, errorMessage = message)
                    updateNotification(
                        params,
                        notificationId,
                        item
                    )
                }
            } finally {
                pendingFinalLocation?.let { location ->
                    runCatching { contentResolver.delete(android.net.Uri.parse(location), null, null) }
                }
                if (!attemptClaimed) {
                    val latest = withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { repository.getCustomDownloadFor(mode, recordId) }.getOrNull()
                    }
                    if (latest == null || latest.attemptId != attemptId ||
                        latest.state !in setOf(CustomDownloadState.QUEUED, CustomDownloadState.RUNNING)
                    ) {
                        runCatching {
                            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId)
                        }
                    }
                }
                if (running.remove(params.jobId) != null) {
                    jobFinished(params, shouldReschedule)
                }
            }
        }
        running[params.jobId] = job
        job.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val recordId = params.extras.getString(CustomDownloadScheduler.EXTRA_RECORD_ID).orEmpty()
        val attemptId = params.extras.getString(CustomDownloadScheduler.EXTRA_ATTEMPT_ID)
        val appCanceled = CustomDownloadScheduler.isExplicitlyCancelled(recordId, attemptId)
        running.remove(params.jobId)?.cancel()
        // User cancellation is terminal; constraint/system stops are retried by JobScheduler.
        return !appCanceled
    }

    override fun onDestroy() {
        running.values.forEach { it.cancel() }
        running.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun updateNotification(params: JobParameters, notificationId: Int, item: CustomDownloadItem) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        withContext(Dispatchers.Main.immediate) {
            setNotification(
                params,
                notificationId,
                if (item.state == CustomDownloadState.RUNNING) {
                    CustomDownloadNotifications.progressNotification(
                        this@CustomDownloadJobService,
                        item,
                        CustomDownloadScheduler.cancelPendingIntent(this@CustomDownloadJobService, item)
                    )
                } else {
                    if (item.state == CustomDownloadState.COMPLETED) {
                        CustomDownloadNotifications.completionNotification(this@CustomDownloadJobService, item)
                    } else {
                        CustomDownloadNotifications.failureNotification(this@CustomDownloadJobService, item)
                    }
                },
                JOB_END_NOTIFICATION_POLICY_DETACH
            )
        }
    }

    private fun itemFromParams(params: JobParameters, mode: BrowserMode, recordId: String) =
        CustomDownloadItem(
            id = recordId,
            url = "",
            fileName = params.extras.getString(CustomDownloadScheduler.EXTRA_FILE_NAME).orEmpty(),
            mimeType = params.extras.getString(CustomDownloadScheduler.EXTRA_MIME_TYPE),
            destinationTreeUri = "",
            userAgent = null,
            mode = mode,
            state = CustomDownloadState.QUEUED,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            backend = CustomDownloadBackend.USER_INITIATED_JOB,
            backendId = params.jobId.toString(),
            attemptId = params.extras.getString(CustomDownloadScheduler.EXTRA_ATTEMPT_ID)
        )
}
