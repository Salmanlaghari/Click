package com.click.browser.engine

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.click.browser.MainActivity
import com.click.browser.data.customDownloadProgress
import kotlinx.coroutines.flow.first

/**
 * Notifications for Click's own download engine (channel "click_downloads"
 * for silent progress, "click_download_alerts" for completion/failure).
 *
 * Prince's requirements: automatic progress notification in the phone's
 * notification bar as soon as a download starts, tap opens Click's Downloads
 * page, Pause/Resume/Cancel actions on the notification, and a Settings
 * toggle (default ON). When the toggle is off or the Android 13+ runtime
 * permission is missing, downloads still run — just silently.
 */
object ClickDownloadNotifications {
    const val PROGRESS_CHANNEL_ID = "click_downloads"
    const val ALERTS_CHANNEL_ID = "click_download_alerts"

    /** Intent extra: id of a completed Click download to open-with on arrival. */
    const val EXTRA_OPEN_WITH_RECORD = "com.click.browser.EXTRA_OPEN_WITH_RECORD"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(PROGRESS_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(PROGRESS_CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress for downloads handled by Click, with pause, resume and cancel."
                    setShowBadge(false)
                }
            )
        }
        if (manager.getNotificationChannel(ALERTS_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(ALERTS_CHANNEL_ID, "Download alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Download finished or needs attention."
                    setShowBadge(true)
                }
            )
        }
    }

    fun hasRuntimePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** The in-app master toggle (Settings → Downloads), default ON. */
    suspend fun userEnabled(context: Context): Boolean = try {
        context.dataStore.data.first()[AppSettings.DOWNLOAD_NOTIFICATIONS_ENABLED] ?: true
    } catch (_: Exception) {
        true
    }

    suspend fun canPost(context: Context): Boolean {
        if (!userEnabled(context)) return false
        if (!hasRuntimePermission(context)) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        ensureChannels(context)
        return true
    }

    fun notificationId(recordId: String): Int =
        (("clickdl:$recordId").hashCode() and 0x3fffffff).coerceAtLeast(1)

    private fun alertNotificationId(recordId: String): Int =
        (0x40000000 or (("clickdl:$recordId").hashCode() and 0x3fffffff)).coerceAtLeast(1)

    fun progressNotification(context: Context, record: ClickDownloadRecord): Notification {
        ensureChannels(context)
        val percent = customDownloadProgress(record.bytesDownloaded, record.totalBytes)
        val percentText = percent?.let { "${(it * 100).toInt()}% · " }.orEmpty()
        val sizeText = if (record.totalBytes > 0L) {
            "${formatBytes(record.bytesDownloaded)} of ${formatBytes(record.totalBytes)}"
        } else {
            "${formatBytes(record.bytesDownloaded)} downloaded"
        }
        val builder = NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(record.fileName)
            .setContentText(
                when (record.state) {
                    ClickDownloadState.PAUSED -> "Paused · $sizeText"
                    ClickDownloadState.QUEUED -> "Waiting to start…"
                    else -> "$percentText$sizeText"
                }
            )
            .setContentIntent(openDownloadsIntent(context, record.id, openWithRecord = false))
            .setOnlyAlertOnce(true)
            .setOngoing(record.state != ClickDownloadState.PAUSED)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (percent != null) {
            builder.setProgress(100, (percent * 100).toInt(), false)
        } else {
            builder.setProgress(0, 0, record.state == ClickDownloadState.RUNNING)
        }
        if (record.canPause && record.state != ClickDownloadState.PAUSED) {
            builder.addAction(0, "Pause", actionIntent(context, record, ACTION_PAUSE))
        }
        if (record.state == ClickDownloadState.PAUSED) {
            builder.addAction(0, "Resume", actionIntent(context, record, ACTION_RESUME))
        }
        if (record.canCancel) {
            builder.addAction(0, "Cancel", actionIntent(context, record, ACTION_CANCEL))
        }
        return builder.build()
    }

    fun completionNotification(context: Context, record: ClickDownloadRecord): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(record.fileName)
            .setContentIntent(openDownloadsIntent(context, record.id, openWithRecord = true))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    fun failureNotification(context: Context, record: ClickDownloadRecord): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Download failed")
            .setContentText("${record.fileName} · tap to open Downloads and resume")
            .setContentIntent(openDownloadsIntent(context, record.id, openWithRecord = false))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    /** Completion alert for response-body downloads (no engine record). */
    fun responseSavedNotification(context: Context, fileName: String): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(fileName)
            .setContentIntent(openDownloadsIntent(context, fileName, openWithRecord = false))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    suspend fun showProgress(context: Context, record: ClickDownloadRecord) {
        if (!canPost(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { manager.notify(notificationId(record.id), progressNotification(context, record)) }
    }

    suspend fun showCompletion(context: Context, record: ClickDownloadRecord) {
        cancelProgress(context, record.id)
        if (!canPost(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { manager.notify(alertNotificationId(record.id), completionNotification(context, record)) }
    }

    suspend fun showFailure(context: Context, record: ClickDownloadRecord) {
        cancelProgress(context, record.id)
        if (!canPost(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { manager.notify(alertNotificationId(record.id), failureNotification(context, record)) }
    }

    suspend fun showResponseSaved(context: Context, fileName: String) {
        if (!canPost(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { manager.notify(alertNotificationId(fileName), responseSavedNotification(context, fileName)) }
    }

    /** Progress-notification id for a response-body download (registry id). */
    fun responseNotificationId(registryId: Long): Int =
        (0x20000000 or (("respdl:$registryId").hashCode() and 0x3fffffff)).coerceAtLeast(1)

    /**
     * Ongoing progress notification for a response-body download. Until
     * 2026-11-13 these streamed downloads posted NO progress notification
     * at all — only the completion alert above — so a user whose download
     * arrived as a GeckoView external response (e.g. APKPure) saw nothing
     * in the status bar, just the in-app strip. Same channel, same tap
     * target (Click's Downloads page) and the same Pause/Resume/Cancel
     * actions as the engine's progress notification; the actions reach the
     * live stream's gate through [ResponseDownloadActionReceiver].
     */
    fun responseProgressNotification(context: Context, item: ActiveResponseDownload): Notification {
        ensureChannels(context)
        val percent = customDownloadProgress(item.downloadedBytes, item.totalBytes)
        val percentText = percent?.let { "${(it * 100).toInt()}% · " }.orEmpty()
        val sizeText = if (item.totalBytes > 0L) {
            "${formatBytes(item.downloadedBytes)} of ${formatBytes(item.totalBytes)}"
        } else {
            "${formatBytes(item.downloadedBytes)} downloaded"
        }
        val builder = NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(item.fileName)
            .setContentText(
                if (item.paused) "Paused · $sizeText" else "$percentText$sizeText"
            )
            .setContentIntent(openDownloadsIntent(context, "response:${item.id}", openWithRecord = false))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (percent != null) {
            builder.setProgress(100, (percent * 100).toInt(), false)
        } else {
            builder.setProgress(0, 0, !item.paused)
        }
        if (item.paused) {
            builder.addAction(0, "Resume", responseActionIntent(context, item.id, ACTION_RESUME))
        } else {
            builder.addAction(0, "Pause", responseActionIntent(context, item.id, ACTION_PAUSE))
        }
        builder.addAction(0, "Cancel", responseActionIntent(context, item.id, ACTION_CANCEL))
        return builder.build()
    }

    suspend fun showResponseProgress(context: Context, item: ActiveResponseDownload) {
        if (!canPost(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { manager.notify(responseNotificationId(item.id), responseProgressNotification(context, item)) }
    }

    fun cancelResponseProgress(context: Context, registryId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { manager.cancel(responseNotificationId(registryId)) }
    }

    private fun responseActionIntent(context: Context, registryId: Long, action: String): PendingIntent {
        val intent = Intent(context, ResponseDownloadActionReceiver::class.java).apply {
            this.action = action
            data = android.net.Uri.parse("click://response-download-action/$registryId")
            putExtra(ResponseDownloadActionReceiver.EXTRA_RESPONSE_ID, registryId)
        }
        return PendingIntent.getBroadcast(
            context,
            ("resp:$registryId:$action").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancelProgress(context: Context, recordId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { manager.cancel(notificationId(recordId)) }
    }

    fun cancelAll(context: Context, recordId: String) {
        cancelProgress(context, recordId)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { manager.cancel(alertNotificationId(recordId)) }
    }

    private fun actionIntent(context: Context, record: ClickDownloadRecord, action: String): PendingIntent {
        val intent = Intent(context, ClickDownloadActionReceiver::class.java).apply {
            this.action = action
            data = android.net.Uri.parse("click://download-action/${record.id}")
            putExtra(ClickDownloadActionReceiver.EXTRA_RECORD_ID, record.id)
            putExtra(ClickDownloadActionReceiver.EXTRA_MODE, record.mode.name)
        }
        return PendingIntent.getBroadcast(
            context,
            (record.id + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openDownloadsIntent(context: Context, recordId: String, openWithRecord: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = CustomDownloadNotifications.ACTION_OPEN_DOWNLOADS
            putExtra(CustomDownloadNotifications.EXTRA_OPEN_DOWNLOADS, true)
            if (openWithRecord) putExtra(EXTRA_OPEN_WITH_RECORD, recordId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            (recordId + if (openWithRecord) ":open" else ":list").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return if (unit == 0) "$bytes B" else "%.1f %s".format(value, units[unit])
    }

    const val ACTION_PAUSE = "com.click.browser.DOWNLOAD_PAUSE"
    const val ACTION_RESUME = "com.click.browser.DOWNLOAD_RESUME"
    const val ACTION_CANCEL = "com.click.browser.DOWNLOAD_CANCEL"
}
