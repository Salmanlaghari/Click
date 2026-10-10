package com.click.browser.engine

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the background news check ([NewsNotificationWorker]).
 *
 * Battery honesty: this uses a plain [PeriodicWorkRequestBuilder] every
 * [CHECK_INTERVAL_HOURS] hours with [Constraints] requiring a network
 * connection and a non-low battery — NO exact alarms, NO foreground
 * service, NO wake locks. WorkManager batches the work with the system's
 * own maintenance windows, so the cost is negligible. The Settings UI
 * says "Checks every ~2 hours" because WorkManager may defer the run.
 */
object NewsNotificationScheduler {

    private const val TAG = "NewsNotifScheduler"
    private const val UNIQUE_WORK_NAME = "news_check"
    private const val CHECK_INTERVAL_HOURS = 2L

    /**
     * Enqueues (or keeps) the periodic news check. Idempotent — calling it
     * twice does not create duplicate work ([ExistingPeriodicWorkPolicy.KEEP]).
     */
    fun schedule(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<NewsNotificationWorker>(
                CHECK_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(TAG, "News check scheduled (every ~$CHECK_INTERVAL_HOURS h)")
        } catch (e: Exception) {
            Log.w(TAG, "schedule failed", e)
        }
    }

    /** Cancels the periodic news check (user opted out). */
    fun cancel(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.i(TAG, "News check cancelled")
        } catch (e: Exception) {
            Log.w(TAG, "cancel failed", e)
        }
    }

    /**
     * True when the periodic work is currently enqueued or running.
     * Suspend — queries WorkManager off the calling thread.
     */
    suspend fun isScheduled(context: Context): Boolean {
        return try {
            val infos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(UNIQUE_WORK_NAME)
                .get()
            infos.any {
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
            }
        } catch (_: Exception) {
            false
        }
    }
}
