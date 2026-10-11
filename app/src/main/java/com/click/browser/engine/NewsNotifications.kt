package com.click.browser.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.click.browser.MainActivity
import com.click.browser.R

/**
 * System notifications for background news alerts (WhatsApp-style).
 *
 * The [NewsNotificationWorker] posts one notification per fresh article
 * (capped per run). Tapping a notification deep-links into [MainActivity]
 * via [EXTRA_OPEN_URL], which opens the article in a new browser tab.
 *
 * Honest behavior:
 * - Notifications are strictly opt-in ([AppSettings.NEWS_NOTIFICATIONS_ENABLED],
 *   default OFF) and require the runtime POST_NOTIFICATIONS grant on
 *   Android 13+.
 * - Every notification is a REAL freshly-fetched RSS article — never
 *   fabricated, never a promo.
 */
object NewsNotifications {

    private const val TAG = "NewsNotifications"

    /** Intent extra carrying the article URL to open in a new tab. */
    const val EXTRA_OPEN_URL = "com.click.browser.EXTRA_OPEN_URL"

    const val CHANNEL_ID = "news"
    private const val CHANNEL_NAME = "News Alerts"

    /** Default topics when the user opted in but never picked any. */
    val DEFAULT_TOPICS: Set<String> = setOf(
        NewsCategory.NEWS.name,
        NewsCategory.TECH.name
    )

    /** Topic chips shown in Settings (category name -> display label). */
    val TOPIC_CHOICES: List<Pair<String, String>> = listOf(
        NewsCategory.NEWS.name to NewsCategory.NEWS.label,
        NewsCategory.TECH.name to NewsCategory.TECH.label,
        NewsCategory.AI.name to NewsCategory.AI.label,
        NewsCategory.SPORTS.name to NewsCategory.SPORTS.label
    )

    /**
     * Creates the "News Alerts" channel if missing. Safe to call any time —
     * channel creation is idempotent and cheap.
     */
    fun ensureChannel(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Breaking-news alerts for your chosen topics."
                    }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "ensureChannel failed", e)
        }
    }

    /**
     * Posts a notification for [article]. Each article gets its own
     * notification id + PendingIntent (keyed by link hash) so multiple
     * alerts don't overwrite each other.
     */
    fun showArticleNotification(context: Context, article: NewsArticle) {
        try {
            ensureChannel(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.click.browser.OPEN_NEWS_ARTICLE"
                putExtra(EXTRA_OPEN_URL, article.link)
                // Bring the existing browser task forward instead of
                // stacking a second MainActivity (singleTask + onNewIntent
                // consume the extra on the warm path).
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val requestCode = article.link.hashCode()
            val pending = PendingIntent.getActivity(
                context,
                requestCode,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(article.title)
                .setContentText("${article.source} • ${NewsFeed.timeAgo(article.publishedAt)}")
                .setStyle(NotificationCompat.BigTextStyle().bigText(article.title))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            nm.notify(requestCode, notification)
        } catch (e: Exception) {
            Log.w(TAG, "showArticleNotification failed for ${article.link}", e)
        }
    }
}
