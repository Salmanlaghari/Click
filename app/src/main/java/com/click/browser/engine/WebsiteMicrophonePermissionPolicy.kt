package com.click.browser.engine

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.Locale

/** The browser's remembered choice for one exact HTTPS origin. */
internal enum class WebsiteMicrophoneDecision {
    ASK,
    ALLOWED,
    BLOCKED_FOR_SITE
}

/** UI/runtime state combines the site's choice with Android's app-level grant. */
internal enum class WebsiteMicrophoneStatus {
    ASK,
    ALLOWED,
    BLOCKED_FOR_SITE,
    ANDROID_PERMISSION_REQUIRED
}

private data class CanonicalWebsiteAuthority(
    val originHost: String,
    val hostKey: String,
    val port: Int?
)

/**
 * Return a canonical origin key for a secure website. Path, query, and fragment
 * are intentionally discarded so a choice applies to that origin only; scheme,
 * normalized hostname, and non-default port remain part of the key.
 */
internal fun canonicalWebsiteMicrophoneOrigin(rawUri: String): String? {
    val uri = runCatching { URI(rawUri) }.getOrNull() ?: return null
    if (!uri.isAbsolute || !uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.rawUserInfo != null) return null
    val authority = uri.rawAuthority ?: return null
    val parsed = parseWebsiteAuthority(authority) ?: return null
    val portSuffix = if (parsed.port == null || parsed.port == 443) "" else ":${parsed.port}"
    return "https://${parsed.originHost}$portSuffix"
}

/** Extract the normalized host from a canonical origin for exact-host cleanup. */
internal fun websiteMicrophoneOriginHost(origin: String): String? {
    val uri = runCatching { URI(origin) }.getOrNull() ?: return null
    if (!uri.isAbsolute || !uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.rawUserInfo != null) return null
    return uri.rawAuthority?.let(::parseWebsiteAuthority)?.hostKey
}

/** Normalize a host supplied by a browser URL or site-reset action. */
internal fun websiteMicrophoneHostKey(rawHost: String): String? {
    val host = rawHost.trim()
    if (host.isBlank()) return null
    val authority = when {
        host.startsWith("[") -> host
        host.count { it == ':' } > 1 -> "[$host]"
        else -> host
    }
    return parseWebsiteAuthority(authority)?.hostKey
}

private fun parseWebsiteAuthority(authority: String): CanonicalWebsiteAuthority? {
    if (authority.isBlank() || '@' in authority) return null

    if (authority.startsWith("[")) {
        val closingBracket = authority.indexOf(']')
        if (closingBracket <= 1) return null
        val literal = authority.substring(1, closingBracket)
        if (':' !in literal || '%' in literal) return null
        val remainder = authority.substring(closingBracket + 1)
        val port = when {
            remainder.isEmpty() -> null
            remainder.startsWith(":") -> parseWebsitePort(remainder.substring(1)) ?: return null
            else -> return null
        }
        val ipv6 = runCatching { InetAddress.getByName(literal) as? Inet6Address }
            .getOrNull() ?: return null
        val hostKey = ipv6.hostAddress.substringBefore('%').lowercase(Locale.ROOT)
        return CanonicalWebsiteAuthority("[$hostKey]", hostKey, port)
    }

    val colon = authority.lastIndexOf(':')
    if (colon >= 0 && authority.indexOf(':') != colon) return null
    val rawHost = if (colon >= 0) authority.substring(0, colon) else authority
    val port = if (colon >= 0) parseWebsitePort(authority.substring(colon + 1)) ?: return null else null
    val hostWithoutFinalDot = if (rawHost.endsWith('.')) rawHost.dropLast(1) else rawHost
    if (hostWithoutFinalDot.isBlank() || hostWithoutFinalDot.endsWith('.')) return null
    val asciiHost = runCatching {
        IDN.toASCII(hostWithoutFinalDot, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
    }.getOrNull()?.takeIf { it.isNotBlank() && it.length <= 253 } ?: return null
    return CanonicalWebsiteAuthority(asciiHost, asciiHost, port)
}

private fun parseWebsitePort(value: String): Int? {
    if (value.isEmpty() || value.any { it !in '0'..'9' }) return null
    val port = value.toIntOrNull() ?: return null
    return port.takeIf { it in 1..65535 }
}

internal fun websiteMicrophoneStatus(
    siteAllowed: Boolean?,
    androidPermissionGranted: Boolean
): WebsiteMicrophoneStatus = when {
    siteAllowed == false -> WebsiteMicrophoneStatus.BLOCKED_FOR_SITE
    siteAllowed == true && androidPermissionGranted -> WebsiteMicrophoneStatus.ALLOWED
    siteAllowed == true -> WebsiteMicrophoneStatus.ANDROID_PERMISSION_REQUIRED
    else -> WebsiteMicrophoneStatus.ASK
}

/** Camera or non-microphone media requests must never ride along with this grant. */
internal fun isMicrophoneOnlyMediaRequest(
    origin: String?,
    hasVideoRequest: Boolean,
    hasMicrophoneSource: Boolean
): Boolean = !hasVideoRequest && hasMicrophoneSource &&
    origin?.let(::canonicalWebsiteMicrophoneOrigin) != null
