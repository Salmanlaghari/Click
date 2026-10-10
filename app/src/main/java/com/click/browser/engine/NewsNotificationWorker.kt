package com.click.browser.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.json.JSONArray

/**
 * Background worker that checks the RSS news feeds for fresh articles and
 * posts a system notification per new story (WhatsApp-style).
 *
 * Runs on [NewsNotificationScheduler]'s ~2-hour periodic schedule with
 * network-connected + battery-not-low constraints — no exact alarms, no
 * foreground service, so it's battery-friendly by design.
 *
 * Anti-spam rules:
 * - Only articles published AFTER the last check are candidates.
 * - Already-notified links are remembered in DataStore (capped at 100).
 * - Max [MAX_NOTIFICATIONS_PER_RUN] notifications per run.
 * - On the very first run (no previous check timestamp) all current
 *   articles are marked as seen WITHOUT notifying — enabling alerts must
 *   never fire a burst of "old" news.
 *
 * Every notification is a real freshly-fetched article from [NewsFeed] —
 * nothing is fabricated.
 */
class NewsNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        return try {
            val prefs = context.dataStore.data.first()

            // User opted out (or never opted in) — nothing to do.
            if (prefs[AppSettings.NEWS_NOTIFICATIONS_ENABLED] != true) {
                return Result.success()
            }

            // Android 13+: the runtime grant may have been revoked in system
            // settings. Bail silently — the toggle stays on so alerts resume
            // if the user re-grants; we must NOT crash or spam here.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "POST_NOTIFICATIONS not granted — skipping run")
                return Result.success()
            }

            val topics = prefs[AppSettings.NEWS_NOTIFICATION_TOPICS]
                ?.takeIf { it.isNotEmpty() }
                ?: NewsNotifications.DEFAULT_TOPICS
            val lastCheck = prefs[AppSettings.NEWS_LAST_CHECK_MS] ?: 0L
            val firstRun = lastCheck == 0L
            val seen = readSeenLinks(prefs[AppSettings.NEWS_SEEN_LINKS_JSON]).toMutableSet()
            val now = System.currentTimeMillis()

            // Collect fresh articles across the enabled topics.
            val fresh = mutableListOf<NewsArticle>()
            var anyFeedOk = false
            for (topicName in topics) {
                val category = runCatching { NewsCategory.valueOf(topicName) }.getOrNull()
                    ?: continue
                // ALL is a UI aggregate, not a notifiable topic.
                if (category == NewsCategory.ALL) continue
                val result = try {
                    NewsFeed.getArticles(context, category)
                } catch (e: Exception) {
                    Log.w(TAG, "getArticles($topicName) failed", e)
                    continue
                }
                if (result.status != NewsStatus.OK) continue
                anyFeedOk = true
                for (article in result.articles) {
                    if (article.link.isBlank()) continue
                    if (article.link !in seen && article.publishedAt > lastCheck) {
                        fresh.add(article)
                    }
                }
            }

            // Newest first; cap notifications per run to avoid spam.
            val toNotify = fresh.sortedByDescending { it.publishedAt }
                .take(MAX_NOTIFICATIONS_PER_RUN)

            // Mark everything we saw (not just what we notified) so the next
            // run only considers genuinely new articles. If EVERY feed failed
            // this run, don't advance the watermark — otherwise articles
            // published during the outage would be silently skipped forever.
            if (anyFeedOk) {
                for (article in fresh) {
                    seen.add(article.link)
                }
                persistCheck(context, seen, now)
            }

            if (!firstRun) {
                for (article in toNotify) {
                    NewsNotifications.showArticleNotification(context, article)
                }
                if (toNotify.isNotEmpty()) {
                    Log.i(TAG, "Notified ${toNotify.size} new article(s)")
                }
            } else {
                Log.i(TAG, "First run: marked ${fresh.size} article(s) as seen, no notifications")
            }

            Result.success()
        } catch (e: Exception) {
            // Transient failure (network blip, …) — retry on the next
            // periodic run instead of failing loudly.
            Log.w(TAG, "doWork failed", e)
            Result.success()
        }
    }

    private fun readSeenLinks(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                arr.optString(i)?.takeIf { it.isNotBlank() }
            }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private suspend fun persistCheck(
        context: Context,
        seen: Set<String>,
        now: Long
    ) {
        try {
            // Cap the dedup list: keep the most recent links. (Insertion
            // order isn't tracked, so keep an arbitrary stable subset —
            // the cap only bounds storage size.)
            val capped = seen.take(SEEN_LINKS_CAP)
            val arr = JSONArray()
            capped.forEach { arr.put(it) }
            val json = arr.toString()
            context.dataStore.edit { prefs ->
                prefs[AppSettings.NEWS_SEEN_LINKS_JSON] = json
                prefs[AppSettings.NEWS_LAST_CHECK_MS] = now
            }
        } catch (e: Exception) {
            Log.w(TAG, "persistCheck failed", e)
        }
    }

    companion object {
        private const val TAG = "NewsNotifWorker"
        private const val MAX_NOTIFICATIONS_PER_RUN = 3
        private const val SEEN_LINKS_CAP = 100
    }
}
