package com.click.browser.engine

/**
 * .onion routing policy (1.8.9 B10, stage 1).
 *
 * Onion services only resolve inside the Tor network — a direct load can
 * never work and previously died as a confusing DNS error. Click therefore
 * never loads a .onion address itself; the navigation is held and the user
 * is offered the Tor route that exists on their device (Tor Browser /
 * Orbot), or pointed at installing one.
 *
 * Stage 2 (a Tor daemon embedded in Click, measured at ~2.4 MB extra per
 * device for arm64) is a separate, device-verified step — see the 1.8.9
 * plan. This policy is the permanent front door either way: even with an
 * embedded Tor, only .onion traffic may use it.
 */
object OnionRoutingPolicy {

    /** True when [host] is an onion service address (any subdomain depth). */
    fun isOnionHost(host: String?): Boolean {
        val h = host?.lowercase()?.trim()?.trimEnd('.') ?: return false
        return h == "onion" || h.endsWith(".onion")
    }

    /** True for http(s) URLs whose host is an onion service. */
    fun isOnionUrl(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val host = try {
            java.net.URI(url.trim()).host
        } catch (_: Exception) {
            null
        }
        return isOnionHost(host)
    }
}
