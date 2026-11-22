package com.click.browser.ui.screens

import java.net.URLEncoder

/** Formats address-bar text while preserving explicit HTTP(S) URLs exactly as entered. */
internal fun formatBrowserInputUrl(
    input: String,
    searchEngine: String,
    buildSearchUrl: (engine: String, encodedQuery: String) -> String
): String {
    val trimmed = input.trim()
    if (trimmed.startsWith("click://", ignoreCase = true)) {
        return trimmed.lowercase()
    }
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        return trimmed
    }
    if (trimmed.contains(".") && !trimmed.contains(" ")) {
        return "https://$trimmed"
    }
    val query = URLEncoder.encode(trimmed, "UTF-8")
    return buildSearchUrl(searchEngine, query)
}

/**
 * Display form of [url] for the address bar when it is NOT being edited
 * (1.8.7 overhaul, A-list #3): the scheme is dropped so the site name leads,
 * and a bare root page shows just its host. Deep pages keep host + path +
 * query (the single-line field scrolls the rest out of view). Non-HTTP(S)
 * values (blank, about:, click://…) pass through unchanged. Tapping the bar
 * switches back to the full URL for editing — this function is display-only
 * and never used to navigate.
 */
internal fun displayUrlForBar(url: String): String {
    val trimmed = url.trim()
    val schemeEnd = trimmed.indexOf("://")
    if (schemeEnd < 0) return trimmed
    val scheme = trimmed.substring(0, schemeEnd)
    if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
        return trimmed
    }
    val rest = trimmed.substring(schemeEnd + 3)
    val firstSlash = rest.indexOf('/')
    if (firstSlash < 0) return rest
    // Root page (nothing after the host but an optional lone slash): host only.
    val afterHost = rest.substring(firstSlash)
    return if (afterHost == "/") rest.substring(0, firstSlash) else rest
}
