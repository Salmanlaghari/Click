package com.click.browser.engine

import java.net.URI

/**
 * Recognizes Google authentication endpoints that must use a supported browser
 * authentication context rather than an embedded tab. This is URL routing only;
 * it does not alter the user agent, credentials, redirects, or OAuth request.
 */
internal object GoogleAuthPolicy {
    fun shouldOpenInCustomTab(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        if (uri.rawUserInfo != null) return false

        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (host == "accounts.google.com" || host.endsWith(".accounts.google.com")) return true
        if (host == "accounts.youtube.com") return true

        val isGoogleDomain = host == "google.com" || host.endsWith(".google.com")
        return isGoogleDomain && uri.rawPath.orEmpty().contains("/o/oauth2/")
    }

    /**
     * A null Gecko navigation target is also used after an intentional auth
     * handoff. Do not tell the user that a recognized sign-in request was blocked.
     */
    fun shouldShowBlockedNavigationNotice(rawUrl: String): Boolean =
        !shouldOpenInCustomTab(rawUrl)
}
