package com.click.browser.engine

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Recognizes valid Google OAuth authorization requests that must use a
 * supported browser authentication context. Ordinary Google account/site pages
 * are not OAuth authorization requests and stay in Click's current tab.
 *
 * This only classifies URLs; it does not alter user agents, credentials,
 * redirects, OAuth parameters, or cookies.
 */
internal object GoogleAuthPolicy {
    private const val GOOGLE_AUTH_HOST = "accounts.google.com"
    private val authorizationPaths = setOf(
        "/o/oauth2/v2/auth",
        "/o/oauth2/auth", // Legacy Google OAuth authorization endpoint.
    )
    private val requiredAuthorizationParameters = setOf(
        "client_id",
        "redirect_uri",
        "response_type",
    )

    fun shouldOpenInCustomTab(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() != "https" || uri.rawUserInfo != null) return false
        if (uri.host?.lowercase()?.trimEnd('.') != GOOGLE_AUTH_HOST) return false

        val path = uri.rawPath?.removeSuffix("/") ?: return false
        if (path !in authorizationPaths) return false
        return hasRequiredAuthorizationParameters(uri.rawQuery)
    }

    private fun hasRequiredAuthorizationParameters(rawQuery: String?): Boolean {
        if (rawQuery.isNullOrBlank()) return false
        val parameters = rawQuery.split('&').mapNotNull { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val name = decodeQueryPart(pair.substring(0, separator)) ?: return@mapNotNull null
            val value = decodeQueryPart(pair.substring(separator + 1)) ?: return@mapNotNull null
            name.lowercase() to value
        }.groupBy({ it.first }, { it.second })

        return requiredAuthorizationParameters.all { name ->
            parameters[name]?.any(String::isNotBlank) == true
        }
    }

    private fun decodeQueryPart(value: String): String? =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrNull()

    /** A null Gecko navigation target is an intentional handoff only for OAuth authorization requests. */
    fun shouldShowBlockedNavigationNotice(rawUrl: String): Boolean =
        !shouldOpenInCustomTab(rawUrl)
}
