package com.click.browser.engine

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.click.browser.data.BrowserRepository
import com.click.browser.data.CustomDownloadItem
import com.click.browser.data.CustomDownloadState
import com.click.browser.data.DownloadItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

class CustomFolderDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val recordId = inputData.getString(CustomDownloadScheduler.EXTRA_RECORD_ID)
            ?: return Result.failure()
        val mode = runCatching {
            BrowserMode.valueOf(inputData.getString(CustomDownloadScheduler.EXTRA_MODE).orEmpty())
        }.getOrNull() ?: return Result.failure()
        val repository = BrowserRepository(applicationContext)
        val attemptId = inputData.getString(CustomDownloadScheduler.EXTRA_ATTEMPT_ID)
            ?: return Result.failure()
        var current = repository.claimCustomDownloadFor(mode, recordId, attemptId) ?: return Result.success()
        var pendingFinalLocation: String? = null

        return try {
            setForeground(foregroundInfo(current))
            val result = CustomFolderDownloadTransfer.download(applicationContext, current) { downloaded, total ->
                if (!repository.updateCustomDownloadProgressFor(mode, recordId, attemptId, downloaded, total)) {
                    throw CancellationException("Download attempt is no longer active.")
                }
                current = current.copy(bytesDownloaded = downloaded, totalBytes = total)
                setProgress(workDataOf("bytes_downloaded" to downloaded, "total_bytes" to total))
                setForeground(foregroundInfo(current))
            }
            pendingFinalLocation = result.location
            currentCoroutineContext().ensureActive()
            val stagedSourcePath = current.sourceFilePath
            val timestamp = System.currentTimeMillis()
            val completed = repository.completeCustomDownloadFor(
                mode = mode,
                id = recordId,
                attemptId = attemptId,
                item = DownloadItem(
                    fileName = result.fileName,
                    url = current.url,
                    path = result.location,
                    timestamp = timestamp
                ),
                downloaded = result.bytesDownloaded,
                total = result.totalBytes
            )
            if (completed) {
                pendingFinalLocation = null
                current = current.copy(
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
                CustomDownloadNotifications.showCompletion(applicationContext, current)
            } else {
                runCatching { applicationContext.contentResolver.delete(android.net.Uri.parse(result.location), null, null) }
                pendingFinalLocation = null
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            val userCancelled = CustomDownloadScheduler.consumeExplicitCancel(recordId, attemptId)
            withContext(NonCancellable + Dispatchers.IO) {
                repository.stopCustomDownloadAttemptFor(mode, recordId, attemptId, userCancelled)
            }
            throw cancelled
        } catch (failure: CustomTransferFailure) {
            if (repository.failCustomDownloadAttemptFor(mode, recordId, attemptId, failure.userMessage)) {
                CustomDownloadNotifications.showFailure(
                    applicationContext,
                    current.copy(state = CustomDownloadState.FAILED, errorMessage = failure.userMessage)
                )
            }
            Result.failure()
        } catch (error: Exception) {
            val message = "The download could not be saved. Check the connection and folder, then retry."
            if (repository.failCustomDownloadAttemptFor(mode, recordId, attemptId, message)) {
                CustomDownloadNotifications.showFailure(
                    applicationContext,
                    current.copy(state = CustomDownloadState.FAILED, errorMessage = message)
                )
            }
            Result.failure()
        } finally {
            pendingFinalLocation?.let { location ->
                runCatching { applicationContext.contentResolver.delete(android.net.Uri.parse(location), null, null) }
            }
        }
    }

    private fun foregroundInfo(item: CustomDownloadItem): ForegroundInfo {
        val notification = CustomDownloadNotifications.progressNotification(
            applicationContext,
            item,
            CustomDownloadScheduler.cancelPendingIntent(applicationContext, item)
        )
        val notificationId = CustomDownloadNotifications.progressNotificationId(item.id, item.attemptId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            dataSyncForegroundInfo(notificationId, notification)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun dataSyncForegroundInfo(notificationId: Int, notification: android.app.Notification): ForegroundInfo {
        // Newer Android versions require the explicit foreground-service type for this target SDK.
        return ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
