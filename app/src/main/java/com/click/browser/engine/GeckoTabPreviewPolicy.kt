package com.click.browser.engine

import java.net.IDN
import java.net.URI

private const val MAX_GECKO_PREVIEW_URL_LENGTH = 8192

internal fun shouldCaptureGeckoTabPreview(url: String?, isIncognito: Boolean): Boolean {
    if (isIncognito || url.isNullOrBlank() || url.length > MAX_GECKO_PREVIEW_URL_LENGTH) return false
    if (url == "about:blank") return true
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    return scheme in setOf("http", "https") && hasValidGeckoPreviewAuthority(uri)
}

private fun hasValidGeckoPreviewAuthority(uri: URI): Boolean {
    if (uri.rawUserInfo != null) return false
    val authority = uri.rawAuthority ?: return false

    if (authority.startsWith("[")) {
        val closingBracket = authority.indexOf(']')
        if (closingBracket <= 1 || uri.host.isNullOrBlank()) return false
        val portSuffix = authority.substring(closingBracket + 1)
        if (portSuffix.isEmpty()) return true
        if (!portSuffix.startsWith(":")) return false
        return isValidGeckoPreviewPort(portSuffix.substring(1))
    }

    if ('[' in authority || ']' in authority) return false
    val portSeparator = authority.lastIndexOf(':')
    val host = if (portSeparator >= 0) {
        if (!isValidGeckoPreviewPort(authority.substring(portSeparator + 1))) return false
        authority.substring(0, portSeparator)
    } else {
        authority
    }
    if (host.isBlank()) return false
    return runCatching { IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES) }
        .getOrNull()
        ?.isNotBlank() == true
}

private fun isValidGeckoPreviewPort(portText: String): Boolean {
    if (portText.isEmpty() || portText.any { it !in '0'..'9' }) return false
    val port = portText.toIntOrNull() ?: return false
    return port in 0..65535
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
