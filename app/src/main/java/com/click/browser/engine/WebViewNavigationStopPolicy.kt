package com.click.browser.engine

import java.net.URI

/**
 * Show a stopped-load recovery prompt only when a WebView navigation had not
 * committed visible content. WebView stop callbacks do not identify the request
 * method, so this policy intentionally does not create a retry target.
 */
internal fun shouldShowWebViewStopRecovery(
    hasCommittedContent: Boolean,
    url: String?
): Boolean {
    if (hasCommittedContent || url.isNullOrBlank() || url.length > MAX_STOP_RECOVERY_URL_LENGTH) {
        return false
    }
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val isHttp = uri.scheme.equals("http", ignoreCase = true) ||
        uri.scheme.equals("https", ignoreCase = true)
    return isHttp && !uri.host.isNullOrBlank() && uri.userInfo == null
}

private const val MAX_STOP_RECOVERY_URL_LENGTH = 8_192
