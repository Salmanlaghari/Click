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

    /** Real count of blocked tracker/ad requests this session (for the home privacy pill). */
    private val _blockedCount = AtomicInteger(0)
    private val _blockedCountFlow = MutableStateFlow(0)
    val blockedCountFlow: StateFlow<Int> = _blockedCountFlow.asStateFlow()

    fun shouldBlock(url: String?): Boolean {
        if (url == null) return false
        try {
            val host = Uri.parse(url).host ?: return false
            val blocked = blockedHosts.any { host.contains(it, ignoreCase = true) }
            if (blocked) {
                _blockedCountFlow.value = _blockedCount.incrementAndGet()
            }
            return blocked
        } catch (e: Exception) {
            return false
        }
    }
}
