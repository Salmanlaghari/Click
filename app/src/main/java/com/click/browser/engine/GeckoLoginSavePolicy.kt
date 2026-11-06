package com.click.browser.engine

/** A login candidate reported by GeckoView after a successful form submission. */
internal data class GeckoLoginSaveCandidate(
    val origin: String,
    val host: String,
    val username: String,
    val password: String
)

/**
 * Keep the credential scope tied to Gecko's browser-generated LoginEntry.origin.
 * Do not use the form action or the post-submit page URL: a legitimate login may
 * POST to another host and then redirect before the save prompt is displayed.
 */
internal fun geckoLoginSaveCandidate(
    origin: String?,
    username: String?,
    password: String?
): GeckoLoginSaveCandidate? {
    val normalizedOrigin = origin?.let {
        PasswordOriginPolicy.secureOrigin(it, isMainFrame = true)
    } ?: return null
    val host = PasswordOriginPolicy.hostFromSecureOrigin(normalizedOrigin) ?: return null
    val safeUsername = username.orEmpty()
    val safePassword = password ?: return null
    if (safeUsername.length > 1_024 || safePassword.isBlank() || safePassword.length > 4_096) return null
    return GeckoLoginSaveCandidate(
        origin = normalizedOrigin,
        host = host,
        username = safeUsername,
        password = safePassword
    )
}

internal fun shouldOfferGeckoLoginSave(
    passwordManagerEnabled: Boolean,
    activeRegularGeckoTab: Boolean,
    candidate: GeckoLoginSaveCandidate?
): Boolean = passwordManagerEnabled && activeRegularGeckoTab && candidate != null
