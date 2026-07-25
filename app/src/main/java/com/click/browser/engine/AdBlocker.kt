package com.click.browser.engine

import android.net.Uri

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

    fun shouldBlock(url: String?): Boolean {
        if (url == null) return false
        try {
            val host = Uri.parse(url).host ?: return false
            return blockedHosts.any { host.contains(it, ignoreCase = true) }
        } catch (e: Exception) {
            return false
        }
    }
}
