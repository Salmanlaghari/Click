package com.click.browser.engine

import java.net.URI
import java.util.Locale

/** The browser's remembered choice for one exact HTTPS origin. */
internal enum class WebsiteMicrophoneDecision {
    ASK,
    ALLOWED,
    BLOCKED
}

/** UI/runtime state combines the site's choice with Android's app-level grant. */
internal enum class WebsiteMicrophoneStatus {
    ASK,
    ALLOWED,
    BLOCKED_FOR_SITE,
    ANDROID_PERMISSION_REQUIRED
}

/**
 * Return a canonical origin key for a secure website. Path, query, and fragment
 * are intentionally discarded so a choice applies to that origin only; scheme,
 * hostname, and non-default port remain part of the key.
 */
internal fun canonicalWebsiteMicrophoneOrigin(rawUri: String): String? {
    val uri = runCatching { URI(rawUri) }.getOrNull() ?: return null
    if (!uri.isAbsolute || !uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.rawUserInfo != null) return null
    val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.')
        ?.takeIf { it.isNotBlank() } ?: return null
    val port = uri.port
    if (port !in -1..65535) return null
    val portSuffix = if (port == -1 || port == 443) "" else ":$port"
    return "https://$host$portSuffix"
}

/** Extract the host from a previously canonicalized origin for exact-host cleanup. */
internal fun websiteMicrophoneOriginHost(origin: String): String? =
    runCatching { URI(origin).host?.lowercase(Locale.ROOT)?.trimEnd('.') }
        .getOrNull()

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
