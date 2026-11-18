package com.click.browser.engine

/** "Automatically remove permissions" window: 30 days, as in Chrome. */
internal const val PERMISSION_AUTO_REMOVE_WINDOW_MS: Long = 30L * 24 * 60 * 60 * 1000

/**
 * Pure decision logic for the "Automatically remove permissions" sweep.
 *
 * Given one permission store's saved decisions (canonical origin -> allowed)
 * and the profile's history as (normalized host -> last-visit epoch ms)
 * pairs, returns the origins whose decision should be revoked because their
 * host has no visit inside the last [windowMs].
 *
 * Fail-safe by contract: when [historyHostVisits] is empty — nothing
 * recorded, history cleared, or the history store unreadable — NOTHING is
 * revoked. Losing every saved choice because history happened to be empty
 * would be far worse than keeping a stale permission.
 */
internal fun permissionOriginsToRevoke(
    decisions: Map<String, Boolean>,
    historyHostVisits: List<Pair<String, Long>>,
    nowMs: Long,
    windowMs: Long = PERMISSION_AUTO_REMOVE_WINDOW_MS
): Set<String> {
    if (decisions.isEmpty() || historyHostVisits.isEmpty()) return emptySet()
    val cutoff = nowMs - windowMs
    val recentHosts = historyHostVisits
        .filter { (_, timestampMs) -> timestampMs >= cutoff }
        .mapTo(mutableSetOf()) { (host, _) -> host }
    return decisions.keys
        .filter { origin ->
            val host = websiteMicrophoneOriginHost(origin)
            host == null || host !in recentHosts
        }
        .toSet()
}
