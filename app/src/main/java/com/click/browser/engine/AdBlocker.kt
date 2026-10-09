package com.click.browser.engine

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

object AdBlocker {
    /**
     * Small built-in fallback for the very first request before
     * [FilterListManager] finishes loading the bundled list (assets read
     * happens on a background thread at startup).
     */
    private val fallbackHosts = setOf(
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
            val blocked = FilterListManager.isBlockedHost(host) ||
                fallbackHosts.any { h ->
                    // Exact host or true subdomain only — plain endsWith would
                    // false-positive on "notdoubleclick.net".
                    host.equals(h, ignoreCase = true) || host.endsWith(".$h", ignoreCase = true)
                }
            if (blocked) {
                _blockedCountFlow.value = _blockedCount.incrementAndGet()
            }
            return blocked
        } catch (e: Exception) {
            return false
        }
    }
}
