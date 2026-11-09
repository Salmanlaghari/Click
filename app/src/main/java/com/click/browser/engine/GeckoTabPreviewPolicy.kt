package com.click.browser.engine

import java.net.URI

private const val MAX_GECKO_PREVIEW_URL_LENGTH = 8192

internal fun shouldCaptureGeckoTabPreview(url: String?, isIncognito: Boolean): Boolean {
    if (isIncognito || url.isNullOrBlank() || url.length > MAX_GECKO_PREVIEW_URL_LENGTH) return false
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    return scheme in setOf("http", "https") &&
        !uri.rawAuthority.isNullOrBlank() &&
        uri.rawUserInfo == null
}

internal fun shouldStoreGeckoTabPreview(
    capturedUrl: String,
    currentUrl: String,
    capturedGeneration: Long,
    currentGeneration: Long,
    isIncognito: Boolean
): Boolean =
    capturedGeneration == currentGeneration &&
        capturedUrl == currentUrl &&
        shouldCaptureGeckoTabPreview(currentUrl, isIncognito)
