package com.click.browser.engine

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Classifies Google OAuth authorization requests.
 *
 * Prince's direction (2026-11-10 device test): a website's sign-in/sign-up
 * flow must stay inside Click's current tab — the user opens the site's
 * sign-in page in Click, completes the whole process there, and is signed in
 * there. Handing the flow off to Chrome / the default browser (where a
 * different account list appears) is the bug being fixed, so Click no longer
 * opens Custom Tabs or external browsers for these URLs; the navigation
 * simply proceeds in the current tab like any other page load.
 *
 * This only classifies URLs; it does not alter user agents, credentials,
 * redirects, OAuth parameters, or cookies, and it never automates a sign-in.
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

    /** True when [rawUrl] is a well-formed Google OAuth authorization request. */
    fun isGoogleOAuthAuthorizationRequest(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() != "https" || uri.rawUserInfo != null) return false
        if (uri.host?.lowercase()?.trimEnd('.') != GOOGLE_AUTH_HOST) return false

        val path = uri.rawPath?.removeSuffix("/") ?: return false
        if (path !in authorizationPaths) return false
        return hasRequiredAuthorizationParameters(uri.rawQuery)
    }

    /**
     * OAuth authorization requests stay in Click's current tab (see class
     * doc), so no URL is ever handed off to a Custom Tab anymore.
     */
    fun shouldOpenInCustomTab(rawUrl: String): Boolean = false

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

    /**
     * Blocked Gecko navigations always show Click's blocked-navigation
     * notice: no navigation is silently handed off to another browser
     * anymore, so there is no silent-handoff case left to suppress it for.
     */
    fun shouldShowBlockedNavigationNotice(rawUrl: String): Boolean = true
}
