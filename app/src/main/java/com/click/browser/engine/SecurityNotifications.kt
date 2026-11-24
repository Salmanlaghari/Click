package com.click.browser.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.click.browser.R
import java.util.concurrent.ConcurrentHashMap

/**
 * System notifications for security events (1.8.9 B5): a Safe Browsing
 * dangerous-site block, and V9 Shield blocking a malicious (cryptominer)
 * domain at the DNS layer.
 *
 * Deliberately narrow: ordinary ad/tracker blocks are far too frequent to
 * notify about — they stay visible as counts in the Shield UI. Alerts are
 * throttled per host so a page hammering the same bad domain cannot spam
 * the notification shade. Everything is best-effort: a missing
 * POST_NOTIFICATIONS grant simply means the system drops the post.
 */
object SecurityNotifications {

    private const val CHANNEL_ID = "security_alerts"
    private const val ID_BASE = 0x5EC00
    private const val COOLDOWN_MS = 15 * 60 * 1000L

    private val lastNotifiedAt = ConcurrentHashMap<String, Long>()

    /** True (once per cooldown window) if an alert for [key] may be posted now. */
    internal fun claimAlertSlot(key: String, nowMs: Long): Boolean {
        val last = lastNotifiedAt[key]
        if (last != null && nowMs - last < COOLDOWN_MS) return false
        lastNotifiedAt[key] = nowMs
        return true
    }

    fun notifyDangerousSite(context: Context, url: String, threatLabel: String) {
        val host = hostOf(url)
        if (!claimAlertSlot("danger|$host", System.currentTimeMillis())) return
        post(
            context,
            id = ID_BASE + (host.hashCode() and 0x3FFF),
            title = "Dangerous site blocked",
            text = "$host — $threatLabel. Click stopped this page from loading."
        )
    }

    fun notifyShieldBlockedMalicious(context: Context, host: String, category: String) {
        if (!claimAlertSlot("shield|$host", System.currentTimeMillis())) return
        post(
            context,
            id = ID_BASE + 0x4000 + (host.hashCode() and 0x3FFF),
            title = "Shield blocked a malicious domain",
            text = "$host ($category) was blocked by V9 Shield's DNS protection."
        )
    }

    private fun hostOf(url: String): String =
        runCatching { Uri.parse(url).host } .getOrNull()?.takeIf { it.isNotBlank() }
            ?: url.substringBefore('/').take(60)

    private fun post(context: Context, id: Int, title: String, text: String) {
        try {
            val appContext = context.applicationContext
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, "Security alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Warnings when Click blocks a dangerous site or malicious domain"
                    }
                )
            }
            val openIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            val contentPi = openIntent?.let {
                PendingIntent.getActivity(
                    appContext, id, it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
            if (contentPi != null) builder.setContentIntent(contentPi)
            nm.notify(id, builder.build())
        } catch (_: Throwable) {
            // Notifications are an alerting extra, never a reason to crash browsing.
        }
    }
}
