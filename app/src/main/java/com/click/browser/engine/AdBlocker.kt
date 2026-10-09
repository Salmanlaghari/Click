package com.click.browser.engine

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

object AdBlocker {
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

    /** Called by SafeBrowsingManager so threats feed the same privacy count. */
    fun reportBlockedThreat() {
        _blockedCountFlow.value = _blockedCount.incrementAndGet()
    }

    fun shouldBlock(url: String?): Boolean {
        if (url == null) return false
        try {
            val host = Uri.parse(url).host ?: return false
            // Suffix-aware matching everywhere (never substring): "notdoubleclick.net"
            // must not match "doubleclick.net". Sources: bundled 278-domain
            // filter list, built-in blocklist, and aggressive-mode trackers.
            val blocked = FilterListManager.isBlockedHost(host) ||
                blockedHosts.any { hostMatches(host, it) } ||
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
