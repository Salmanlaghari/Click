package com.click.browser.engine

import android.net.Uri

/**
 * Strips known tracking query parameters from URLs (Brave-style query-param
 * filtering). Invisible privacy win: `?utm_source=...&gclid=...` never reaches
 * the site, but the page itself loads identically.
 *
 * Safety rules (never break legit navigation):
 * - Only http(s) URLs with a query string are touched; everything else is
 *   returned unchanged.
 * - Only parameters on the known-tracking list are removed — never a
 *   parameter we don't recognize (so login tokens, session ids, search
 *   queries, pagination, etc. are never dropped).
 * - Fragment (#...) is left alone: SPA routers live there and rewriting it
 *   breaks apps.
 * - Matching is case-insensitive on the parameter NAME only; values are
 *   never inspected.
 */
object QueryParamStripper {

    /** Prefix match (e.g. utm_source, utm_medium, utm_campaign, ...). */
    private val TRACKING_PREFIXES = setOf("utm_")

    /** Exact matches (lowercase). Pure tracking identifiers only — no params
     * that carry user state (deliberately excludes _ga/_gl/_gac and the like). */
    private val TRACKING_PARAMS = setOf(
        // Google Ads / Analytics
        "gclid", "gclsrc", "dclid", "wbraid", "gbraid", "gad_source", "gad_campaignid",
        "srsltid", "gs_gbg",
        // Meta / Facebook / Instagram
        "fbclid", "fb_action_ids", "fb_action_types", "fb_source", "fb_ref",
        // Microsoft / Bing
        "msclkid",
        // Mailchimp
        "mc_cid", "mc_eid",
        // Instagram share
        "igshid",
        // Yahoo
        "yclid",
        // Twitter/X
        "twclid",
        // LinkedIn
        "li_fat_id",
        // Adobe Analytics
        "sc_campaign", "sc_channel", "sc_content", "sc_medium", "sc_outcome",
        "sc_regionid", "sc_trk", "sc_visitorid",
        // Matomo / Piwik
        "pk_campaign", "pk_kwd", "pk_source", "pk_medium",
        "piwik_campaign", "piwik_kwd", "piwik_source", "piwik_medium",
        "matomo_campaign", "matomo_kwd",
        // Yandex
        "yclid", "_openstat",
        // Vero
        "vero_id", "vero_conv",
        // Misc marketing
        "mkt_tok", "rb_clickid", "oly_anon_id", "oly_enc_id",
        "ref_src", "ref_url", "sms_clickid"
    )

    private fun isTrackingParam(name: String): Boolean {
        val n = name.lowercase()
        if (n in TRACKING_PARAMS) return true
        return TRACKING_PREFIXES.any { n.startsWith(it) }
    }

    /**
     * Returns the URL with tracking params removed, or null when nothing was
     * stripped (caller keeps the original URL untouched).
     *
     * Works on the RAW encoded query so kept parameters survive byte-for-byte
     * (original percent-encoding, value-less flags like `?foo`, repeats) —
     * only known tracking names are dropped.
     */
    fun strip(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val encodedQuery = uri.encodedQuery
            if (encodedQuery.isNullOrEmpty()) return null

            var strippedAny = false
            val kept = ArrayList<String>()
            for (pair in encodedQuery.split("&")) {
                if (pair.isEmpty()) continue
                val eq = pair.indexOf('=')
                val encodedName = if (eq < 0) pair else pair.substring(0, eq)
                // Decode the NAME for matching only; the output keeps the
                // original encoding untouched.
                val name = try {
                    Uri.decode(encodedName)
                } catch (_: Exception) {
                    encodedName
                } ?: encodedName
                if (isTrackingParam(name)) {
                    strippedAny = true
                } else {
                    kept.add(pair)
                }
            }
            if (!strippedAny) return null
            val builder = uri.buildUpon().clearQuery()
            val newQuery = kept.joinToString("&")
            if (newQuery.isNotEmpty()) {
                builder.encodedQuery(newQuery)
            }
            builder.build().toString()
        } catch (_: Exception) {
            null
        }
    }
}
