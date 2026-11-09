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
import com.click.browser.data.CustomDownloadItem

internal object CustomDownloadNotifications {
    const val CHANNEL_ID = "custom_downloads"
    private const val CHANNEL_NAME = "Custom-folder downloads"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress and completion for downloads saved to a chosen folder."
                    setShowBadge(false)
                }
            )
        }
    }

    fun progressNotification(
        context: Context,
        item: CustomDownloadItem,
        cancelIntent: PendingIntent
    ): Notification {
        ensureChannel(context)
        val percent = if (item.totalBytes > 0L) {
            ((item.bytesDownloaded.toDouble() / item.totalBytes.toDouble()) * 100.0)
                .toInt().coerceIn(0, 100)
        } else 0
        val size = if (item.totalBytes > 0L) {
            "${formatBytes(item.bytesDownloaded)} of ${formatBytes(item.totalBytes)}"
        } else {
            "${formatBytes(item.bytesDownloaded)} downloaded"
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading ${item.fileName}")
            .setContentText(size)
            .setStyle(NotificationCompat.BigTextStyle().bigText(size))
            .setProgress(100, percent, item.totalBytes <= 0L)
            .setContentIntent(openDownloadsIntent(context, item.id.hashCode()))
            .addAction(0, "Cancel", cancelIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun completionNotification(context: Context, item: CustomDownloadItem): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download saved")
            .setContentText(item.fileName)
            .setContentIntent(openDownloadsIntent(context, item.id.hashCode()))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    fun failureNotification(context: Context, item: CustomDownloadItem): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Download needs attention")
            .setContentText("${item.fileName} · retry in Downloads")
            .setContentIntent(openDownloadsIntent(context, item.id.hashCode()))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    fun showCompletion(context: Context, item: CustomDownloadItem) {
        if (!canPostNotifications(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.notify(completionNotificationId(item.id), completionNotification(context, item))
    }

    fun showFailure(context: Context, item: CustomDownloadItem) {
        if (!canPostNotifications(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.notify(completionNotificationId(item.id), failureNotification(context, item))
    }

    fun cancelProgress(context: Context, id: String, attemptId: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.cancel(progressNotificationId(id, attemptId))
    }

    fun clearPreviousAttempt(context: Context, id: String, attemptId: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.cancel(progressNotificationId(id, attemptId))
        manager.cancel(completionNotificationId(id))
    }

    fun hasRuntimePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun canPostNotifications(context: Context): Boolean {
        ensureChannel(context)
        if (!hasRuntimePermission(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
            val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return false
            return channel.importance != NotificationManager.IMPORTANCE_NONE
        }
        return true
    }

    fun progressNotificationId(id: String, attemptId: String?): Int =
        ((31 * id.hashCode() + attemptId.orEmpty().hashCode()) and 0x3fffffff).coerceAtLeast(1)

    private fun completionNotificationId(id: String): Int =
        (0x40000000 or (id.hashCode() and 0x3fffffff)).coerceAtLeast(1)

    private fun openDownloadsIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_DOWNLOADS
            putExtra(EXTRA_OPEN_DOWNLOADS, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
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

    const val ACTION_OPEN_DOWNLOADS = "com.click.browser.OPEN_DOWNLOADS"
    const val EXTRA_OPEN_DOWNLOADS = "com.click.browser.EXTRA_OPEN_DOWNLOADS"
}
