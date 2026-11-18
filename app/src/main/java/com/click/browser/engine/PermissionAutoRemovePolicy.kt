package com.click.browser.engine

/** "Automatically remove permissions" window: 30 days, as in Chrome. */
internal const val PERMISSION_AUTO_REMOVE_WINDOW_MS: Long = 30L * 24 * 60 * 60 * 1000

/** Minimum age of a last-visit stamp before a page load refreshes it (24h). */
internal const val PERMISSION_VISIT_REFRESH_THROTTLE_MS: Long = 24L * 60 * 60 * 1000

/**
 * Pure decision logic for the "Automatically remove permissions" sweep.
 *
 * A saved decision is revoked ONLY when its origin carries a last-visit
 * stamp (BrowserRepository's website-permission last-visit store) that is
 * older than [windowMs]. The stamp is written when a choice is saved and
 * refreshed — at most once a day per origin — when a page from the same host
 * finishes loading. Unlike browsing history, which is capped at 100 entries
 * and can silently drop a recent visit, the stamp cannot age a still-used
 * site out from under the user.
 *
 * Fail-safe by contract:
 * - A missing stamp NEVER revokes. The choice may predate the stamp store,
 *   and losing a saved choice on a guess is worse than keeping a stale one.
 * - [recentHistoryHosts] is only a secondary keep-signal: a host seen in
 *   recent history keeps its choices even when the stamp is old. History is
 *   never the trigger for a revoke.
 */
internal fun permissionOriginsToRevoke(
    decisions: Map<String, Boolean>,
    lastVisitByOriginMs: Map<String, Long>,
    recentHistoryHosts: Set<String> = emptySet(),
    nowMs: Long,
    windowMs: Long = PERMISSION_AUTO_REMOVE_WINDOW_MS
): Set<String> {
    if (decisions.isEmpty()) return emptySet()
    val cutoff = nowMs - windowMs
    return decisions.keys
        .filter { origin ->
            val stampMs = lastVisitByOriginMs[origin] ?: return@filter false
            if (stampMs >= cutoff) return@filter false
            // A malformed origin (no host can be derived) is never revoked:
            // without a host its recency cannot be verified, and the sweep
            // is fail-safe — uncertainty keeps the choice.
            val host = websiteMicrophoneOriginHost(origin)
            host != null && host !in recentHistoryHosts
        }
        .toSet()
}

/**
 * Whether a page load should refresh an origin's last-visit stamp: always
 * when no stamp exists yet, otherwise only once the stored stamp is older
 * than [throttleMs], so ordinary browsing does not rewrite the store on
 * every navigation.
 */
internal fun shouldRefreshPermissionVisitStamp(
    stampMs: Long?,
    nowMs: Long,
    throttleMs: Long = PERMISSION_VISIT_REFRESH_THROTTLE_MS
): Boolean = stampMs == null || nowMs - stampMs > throttleMs
