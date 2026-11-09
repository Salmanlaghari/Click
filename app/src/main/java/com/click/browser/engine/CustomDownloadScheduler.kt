package com.click.browser.engine

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.click.browser.data.BrowserRepository
import com.click.browser.data.CustomDownloadBackend
import com.click.browser.data.CustomDownloadItem
import com.click.browser.data.CustomDownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Schedules user-started custom-folder URL transfers on the correct Android backend. */
object CustomDownloadScheduler {
    const val EXTRA_RECORD_ID = "custom_download_record_id"
    const val EXTRA_MODE = "custom_download_mode"
    const val EXTRA_FILE_NAME = "custom_download_file_name"
    const val EXTRA_MIME_TYPE = "custom_download_mime_type"
    const val EXTRA_ATTEMPT_ID = "custom_download_attempt_id"

    private const val WORK_TAG = "click-custom-folder-download"
    private const val JOB_ID_PREFS = "click_custom_download_job_ids"
    private const val NEXT_JOB_ID_KEY = "next_job_id"
    private const val SCHEDULING_GRACE_MS = 2 * 60 * 1000L
    private const val ORPHAN_STAGED_FILE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private val explicitlyCancelled = ConcurrentHashMap.newKeySet<String>()

    /** Returns null when the selected tree is unavailable or a new lifecycle row could not be scheduled. */
    suspend fun enqueue(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String?,
        userAgent: String?,
        mode: BrowserMode,
        destinationTreeUri: String,
        encryptedCookieHeader: String?,
        sourceFilePath: String? = null
    ): CustomDownloadItem? {
        val item = CustomDownloadItem(
            id = UUID.randomUUID().toString(),
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            destinationTreeUri = destinationTreeUri,
            userAgent = userAgent,
            encryptedCookieHeader = encryptedCookieHeader,
            mode = mode,
            state = CustomDownloadState.QUEUED,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            sourceFilePath = sourceFilePath
        )
        return schedule(context.applicationContext, item)
    }

    suspend fun enqueueStagedResponse(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String?,
        mode: BrowserMode,
        destinationTreeUri: String,
        sourceFilePath: String
    ): CustomDownloadItem? = enqueue(
        context = context,
        url = url,
        fileName = fileName,
        mimeType = mimeType,
        userAgent = null,
        mode = mode,
        destinationTreeUri = destinationTreeUri,
        encryptedCookieHeader = null,
        sourceFilePath = sourceFilePath
    )

    suspend fun retry(context: Context, item: CustomDownloadItem): CustomDownloadItem? {
        if (!item.canRetry) return null
        CustomFolderDownloadTransfer.deletePartial(context.applicationContext, item)
        val repository = BrowserRepository(context.applicationContext)
        val retry = repository.prepareCustomDownloadRetryFor(
            item.mode,
            item.id,
            item.attemptId,
            item.encryptedCookieHeader
        ) ?: return null
        CustomDownloadNotifications.clearPreviousAttempt(context.applicationContext, item.id, item.attemptId)
        return schedule(context.applicationContext, retry)
    }

    /** Converts orphaned RUNNING/QUEUED records into honest retryable failures. */
    suspend fun reconcile(context: Context, mode: BrowserMode) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val repository = BrowserRepository(appContext)
        val items = repository.customDownloadsFlowFor(mode).first()
        cleanupOrphanStagedSources(appContext, repository)
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull()
        val jobScheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
        val scheduledJobIds = runCatching {
            jobScheduler?.getAllPendingJobs()?.map { it.id }?.toSet()
        }.getOrNull()

        for (item in items) {
            if (item.state == CustomDownloadState.FAILED || item.state == CustomDownloadState.CANCELED) {
                CustomFolderDownloadTransfer.deletePartial(appContext, item)
                continue
            }
            if (!item.canCancel) continue
            if (item.state == CustomDownloadState.QUEUED &&
                System.currentTimeMillis() - item.updatedAt < SCHEDULING_GRACE_MS
            ) continue
            val stillScheduled: Boolean? = when (item.backend) {
                CustomDownloadBackend.WORK_MANAGER -> {
                    val id = runCatching { UUID.fromString(item.backendId) }.getOrNull()
                    when {
                        id == null -> false
                        workManager == null -> null
                        else -> runCatching { workManager.getWorkInfoById(id).get()?.state }
                            .fold(
                                onSuccess = { state ->
                                    if (state == null) false else state == androidx.work.WorkInfo.State.ENQUEUED ||
                                        state == androidx.work.WorkInfo.State.RUNNING ||
                                        state == androidx.work.WorkInfo.State.BLOCKED
                                },
                                onFailure = { null }
                            )
                    }
                }
                CustomDownloadBackend.USER_INITIATED_JOB -> {
                    val id = item.backendId?.toIntOrNull()
                    when {
                        id == null -> false
                        scheduledJobIds == null -> null
                        else -> id in scheduledJobIds
                    }
                }
                null -> false
            }
            if (stillScheduled == false) {
                val failed = repository.failOrphanedCustomDownloadFor(
                    mode,
                    item,
                    "Android stopped this download. Retry it from Downloads."
                )
                if (failed) CustomFolderDownloadTransfer.deletePartial(appContext, item)
            }
        }
    }

    private suspend fun cleanupOrphanStagedSources(context: Context, repository: BrowserRepository) {
        val referencedPaths = BrowserMode.values()
            .flatMap { repository.customDownloadsFlowFor(it).first() }
            .mapNotNull { item ->
                item.sourceFilePath?.let { path -> runCatching { File(path).canonicalPath }.getOrNull() }
            }
            .toSet()
        val directory = File(context.noBackupFilesDir, CustomFolderDownloadTransfer.STAGING_DIRECTORY)
        val cutoff = System.currentTimeMillis() - ORPHAN_STAGED_FILE_MAX_AGE_MS
        directory.listFiles().orEmpty().forEach { file ->
            if (!file.name.endsWith(".ready") && !file.name.endsWith(".part")) return@forEach
            if (file.lastModified() >= cutoff) return@forEach
            val canonicalPath = runCatching { file.canonicalPath }.getOrNull() ?: return@forEach
            if (canonicalPath !in referencedPaths) runCatching { file.delete() }
        }
    }

    suspend fun cancel(context: Context, item: CustomDownloadItem) {
        if (!item.canCancel) return
        val appContext = context.applicationContext
        val repository = BrowserRepository(appContext)
        val canceled = repository.cancelCustomDownloadFor(item.mode, item.id, item.attemptId) ?: return
        explicitlyCancelled.add(cancelKey(canceled.id, canceled.attemptId))
        when (canceled.backend) {
            CustomDownloadBackend.WORK_MANAGER -> {
                val id = runCatching { UUID.fromString(canceled.backendId) }.getOrNull()
                if (id != null) runCatching { WorkManager.getInstance(appContext).cancelWorkById(id) }
            }
            CustomDownloadBackend.USER_INITIATED_JOB -> {
                val jobId = canceled.backendId?.toIntOrNull()
                if (jobId != null) {
                    runCatching {
                        (appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).cancel(jobId)
                    }
                }
            }
            null -> Unit
        }
        CustomDownloadNotifications.cancelProgress(appContext, canceled.id, canceled.attemptId)
    }

    suspend fun cancelById(context: Context, mode: BrowserMode, recordId: String, attemptId: String) {
        val appContext = context.applicationContext
        val item = BrowserRepository(appContext).getCustomDownloadFor(mode, recordId)
        if (item == null || item.attemptId != attemptId) {
            CustomDownloadNotifications.cancelProgress(appContext, recordId, attemptId)
            return
        }
        cancel(context, item)
    }

    fun cancelPendingIntent(context: Context, item: CustomDownloadItem) =
        android.app.PendingIntent.getBroadcast(
            context,
            item.id.hashCode(),
            android.content.Intent(context, CustomDownloadCancelReceiver::class.java).apply {
                action = "com.click.browser.CANCEL_CUSTOM_DOWNLOAD"
                data = android.net.Uri.parse("click://custom-download-cancel/${item.id}/${item.attemptId.orEmpty()}")
                putExtra(EXTRA_RECORD_ID, item.id)
                putExtra(EXTRA_MODE, item.mode.name)
                putExtra(EXTRA_ATTEMPT_ID, item.attemptId.orEmpty())
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

    /** Used by JobService to distinguish app-requested cancellation from system rescheduling. */
    fun consumeExplicitCancel(recordId: String, attemptId: String?): Boolean =
        explicitlyCancelled.remove(cancelKey(recordId, attemptId))

    fun isExplicitlyCancelled(recordId: String, attemptId: String?): Boolean =
        explicitlyCancelled.contains(cancelKey(recordId, attemptId))

    private fun cancelKey(recordId: String, attemptId: String?): String = "$recordId:${attemptId.orEmpty()}"

    private suspend fun schedule(context: Context, item: CustomDownloadItem): CustomDownloadItem? =
        withContext(Dispatchers.IO) {
            val prepared = item.takeIf { !it.attemptId.isNullOrBlank() }
                ?: item.copy(attemptId = UUID.randomUUID().toString())
            val repository = BrowserRepository(context)
            val tree = runCatching {
                androidx.documentfile.provider.DocumentFile.fromTreeUri(
                    context,
                    android.net.Uri.parse(prepared.destinationTreeUri)
                )
            }.getOrNull()
            if (tree?.canWrite() != true) {
                val existing = repository.getCustomDownloadFor(prepared.mode, prepared.id)
                if (existing?.state == CustomDownloadState.QUEUED && existing.attemptId == prepared.attemptId) {
                    repository.failToScheduleCustomDownloadFor(
                        prepared.mode,
                        prepared,
                        "Access to the selected folder is unavailable. Choose it again, then retry."
                    )
                    return@withContext repository.getCustomDownloadFor(prepared.mode, prepared.id)
                }
                return@withContext existing
            }

            val queued = repository.ensureCustomDownloadQueuedFor(prepared)
                ?: return@withContext repository.getCustomDownloadFor(prepared.mode, prepared.id)
            val uidtRecord = scheduleUidt(context, repository, queued)
            if (uidtRecord != null) return@withContext uidtRecord
            scheduleWorkManager(context, repository, queued)
        }

    private suspend fun scheduleUidt(
        context: Context,
        repository: BrowserRepository,
        item: CustomDownloadItem
    ): CustomDownloadItem? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        // UIDT requires a user-visible notification. When notifications are
        // disabled, retain the in-app lifecycle with the WorkManager fallback.
        if (!CustomDownloadNotifications.canPostNotifications(context)) return null
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return null
        if (!runCatching { scheduler.canRunUserInitiatedJobs() }.getOrDefault(false)) return null
        val jobId = nextJobId(context)
        val queued = repository.assignCustomDownloadBackendFor(
            item.mode,
            item.id,
            item.attemptId.orEmpty(),
            CustomDownloadBackend.USER_INITIATED_JOB,
            jobId.toString()
        ) ?: return repository.getCustomDownloadFor(item.mode, item.id)
        val extras = PersistableBundle().apply {
            putString(EXTRA_RECORD_ID, queued.id)
            putString(EXTRA_MODE, queued.mode.name)
            putString(EXTRA_FILE_NAME, queued.fileName)
            queued.mimeType?.let { putString(EXTRA_MIME_TYPE, it) }
            queued.attemptId?.let { putString(EXTRA_ATTEMPT_ID, it) }
        }
        val job = JobInfo.Builder(
            jobId,
            ComponentName(context, CustomDownloadJobService::class.java)
        )
            .setUserInitiated(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setExtras(extras)
            .build()
        val scheduled = runCatching { scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS }
            .getOrDefault(false)
        if (!scheduled) return null
        val latest = repository.getCustomDownloadFor(item.mode, item.id)
        val stillCurrent = latest != null && latest.attemptId == queued.attemptId &&
            latest.backend == CustomDownloadBackend.USER_INITIATED_JOB &&
            latest.backendId == jobId.toString() &&
            latest.state in setOf(CustomDownloadState.QUEUED, CustomDownloadState.RUNNING)
        if (!stillCurrent) {
            runCatching { scheduler.cancel(jobId) }
        }
        return latest
    }

    private suspend fun scheduleWorkManager(
        context: Context,
        repository: BrowserRepository,
        item: CustomDownloadItem
    ): CustomDownloadItem? {
        val request = OneTimeWorkRequestBuilder<CustomFolderDownloadWorker>()
            .setInputData(Data.Builder().apply {
                putString(EXTRA_RECORD_ID, item.id)
                putString(EXTRA_MODE, item.mode.name)
                item.attemptId?.let { putString(EXTRA_ATTEMPT_ID, it) }
            }.build())
            .addTag(WORK_TAG)
            .build()
        val queued = repository.assignCustomDownloadBackendFor(
            item.mode,
            item.id,
            item.attemptId.orEmpty(),
            CustomDownloadBackend.WORK_MANAGER,
            request.id.toString()
        ) ?: return repository.getCustomDownloadFor(item.mode, item.id)
        return try {
            WorkManager.getInstance(context).enqueue(request)
            val latest = repository.getCustomDownloadFor(item.mode, item.id)
            val stillCurrent = latest != null && latest.attemptId == queued.attemptId &&
                latest.backend == CustomDownloadBackend.WORK_MANAGER &&
                latest.backendId == request.id.toString() &&
                latest.state in setOf(CustomDownloadState.QUEUED, CustomDownloadState.RUNNING)
            if (!stillCurrent) {
                runCatching { WorkManager.getInstance(context).cancelWorkById(request.id) }
            }
            latest
        } catch (error: Exception) {
            repository.failToScheduleCustomDownloadFor(
                item.mode,
                queued,
                "The background download could not be started. Retry from Downloads."
            )
            repository.getCustomDownloadFor(item.mode, item.id)
        }
    }

    private fun nextJobId(context: Context): Int {
        val prefs = context.getSharedPreferences(JOB_ID_PREFS, Context.MODE_PRIVATE)
        synchronized(explicitlyCancelled) {
            val previous = prefs.getInt(NEXT_JOB_ID_KEY, 40_000)
            val next = if (previous >= Int.MAX_VALUE - 1) 40_000 else previous + 1
            prefs.edit().putInt(NEXT_JOB_ID_KEY, next).apply()
            return next
        }
    }
}
