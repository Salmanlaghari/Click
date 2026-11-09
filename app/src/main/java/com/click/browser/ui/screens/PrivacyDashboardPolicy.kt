package com.click.browser.ui.screens

import com.click.browser.engine.ConnectionSecurityInfo
import com.click.browser.engine.ConnectionSecurityPolicy
import com.click.browser.engine.ConnectionSecurityStatus
import java.net.URI
import java.util.Locale

/** Minimal current-page facts for the Privacy & Security overview; never contains a full URL. */
internal data class PrivacyPageOverview(
    val host: String,
    val connectionStatus: ConnectionSecurityStatus,
    val connectionTitle: String,
    val connectionDescription: String
)

/** Pure presentation mapping for the consolidated Privacy & Security overview. */
internal object PrivacyDashboardPolicy {
    const val WEBSITE_PERMISSION_SCOPE_DESCRIPTION =
        "Microphone: per-site consent first, then the existing Android RECORD_AUDIO permission if needed. " +
            "Camera, location, and website notifications remain blocked."

    /** Returns null for internal/non-web or malformed URLs; stale evidence maps to UNKNOWN. */
    fun currentPage(url: String?, connection: ConnectionSecurityInfo): PrivacyPageOverview? {
        val rawUrl = url?.takeIf(String::isNotBlank) ?: return null
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank) ?: return null

        // The policy checks that the engine's connection facts belong to this exact page.
        val status = ConnectionSecurityPolicy.forPage(rawUrl, connection).status
        return PrivacyPageOverview(
            host = host,
            connectionStatus = status,
            connectionTitle = ConnectionSecurityPolicy.title(status),
            connectionDescription = ConnectionSecurityPolicy.description(status)
        )
    }
}
