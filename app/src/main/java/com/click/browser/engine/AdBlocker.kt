package com.click.browser.engine

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

object AdBlocker {
    private val blockedHosts = setOf(
        "doubleclick.net",
        "google-analytics.com",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adsystem.com",
        "adnxs.com",
        "popads.net",
        "outbrain.com",
        "taboola.com"
    )

    /** Aggressive mode (click://flags): also block analytics/trackers, not just ads. */
    @Volatile
    var aggressive: Boolean = false

    private val aggressiveHosts = setOf(
        "googletagmanager.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "amplitude.com",
        "facebook.net",
        "connect.facebook.net",
        "scorecardresearch.com",
        "quantserve.com",
        "criteo.com"
    )

    /**
     * Suffix-aware host match: exact host or subdomain only.
     * Never a substring false-positive (e.g. "notcriteo.com" won't match "criteo.com").
     */
    private fun hostMatches(host: String, pattern: String): Boolean {
        val h = host.lowercase()
        val p = pattern.lowercase()
        return h == p || h.endsWith(".$p")
    }

    /** Real count of blocked tracker/ad requests this session (for the home privacy pill). */
    private val _blockedCount = AtomicInteger(0)
    private val _blockedCountFlow = MutableStateFlow(0)
    val blockedCountFlow: StateFlow<Int> = _blockedCountFlow.asStateFlow()

    fun shouldBlock(url: String?): Boolean {
        if (url == null) return false
        try {
            val host = Uri.parse(url).host ?: return false
            val blocked = blockedHosts.any { host.contains(it, ignoreCase = true) } ||
                (aggressive && aggressiveHosts.any { hostMatches(host, it) })
            if (blocked) {
                _blockedCountFlow.value = _blockedCount.incrementAndGet()
            }
            return blocked
        } catch (e: Exception) {
            return false
        }
    }
}
