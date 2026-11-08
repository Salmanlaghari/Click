package com.click.browser.data

/** Keeps private browsing and non-web schemes out of the persisted history store. */
internal object HistoryRecordingPolicy {

    fun shouldRecord(isIncognitoTab: Boolean, url: String): Boolean {
        if (isIncognitoTab) return false

        val normalizedUrl = url.trim()
        val schemeSeparator = normalizedUrl.indexOf("://")
        if (schemeSeparator <= 0) return false

        val scheme = normalizedUrl.substring(0, schemeSeparator).lowercase()
        if (scheme != "http" && scheme != "https") return false

        val authority = normalizedUrl.substring(schemeSeparator + 3)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        return authority.isNotBlank()
    }
}
