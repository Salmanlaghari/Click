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
