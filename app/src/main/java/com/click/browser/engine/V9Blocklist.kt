package com.click.browser.engine

/**
 * V9 Shield DNS blocklist: well-known ad, tracker and malware domains blocked
 * at the DNS layer by [V9VpnService]. Matching is suffix-based, so blocking
 * "doubleclick.net" also blocks "stats.doubleclick.net".
 *
 * This is a *protection* list (ads / trackers / malware) — Click Browser does
 * NOT censor political or news content of any kind.
 */
object V9Blocklist {

    private val DOMAINS: Set<String> = setOf(
        // Major ad networks
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adnxs.com", "adsrvr.org", "ads.yahoo.com",
        "amazon-adsystem.com", "criteo.com", "criteo.net", "rubiconproject.com",
        "pubmatic.com", "openx.net", "openx.com", "smartadserver.com",
        "adsrv.eacdn.com", "taboola.com", "outbrain.com", "mgid.com",
        "revcontent.com", "adblade.com", "zergnet.com", "nativo.com",
        // Trackers / analytics
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "analytics.yahoo.com", "hotjar.com", "mixpanel.com", "segment.io",
        "segment.com", "amplitude.com", "fullstory.com", "crazyegg.com",
        "mouseflow.com", "inspectlet.com", "luckyorange.com", "optimizely.com",
        "newrelic.com", "nr-data.net", "scorecardresearch.com", "quantserve.com",
        "crashlytics.com", "appsflyer.com", "adjust.com", "branch.io",
        "kochava.com", "singular.net", "tenjin.com", "ironsrc.com",
        "unityads.unity3d.com", "applovin.com", "mopub.com", "inmobi.com",
        "vungle.com", "chartboost.com", "tapjoy.com", "fyber.com",
        // Cryptominers
        "coinhive.com", "coin-hive.com", "cryptoloot.pro", "minero.cc",
        "webminepool.com", "monerominer.rocks",
    )

    /** True if [host] (or any of its parent domains) is on the blocklist. */
    fun isBlocked(host: String): Boolean {
        var h = host.lowercase().trim().trimEnd('.')
        if (h.isEmpty()) return false
        while (true) {
            if (DOMAINS.contains(h)) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }
}
