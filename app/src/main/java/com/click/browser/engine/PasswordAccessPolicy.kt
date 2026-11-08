package com.click.browser.engine

/** Pure gates for the Click-local password vault and saved-login autofill. */
internal object PasswordAccessPolicy {
    data class AutofillContext(
        val passwordManagerEnabled: Boolean,
        val hasSavedCredential: Boolean,
        val isPrivateTab: Boolean,
        val isActiveTab: Boolean,
        val isSecurePage: Boolean,
        val isSameOrigin: Boolean,
        val deviceAuthenticationAvailable: Boolean,
        val deviceAuthenticated: Boolean
    )

    /** Deduplicates auth offers for each secure origin until a new navigation starts. */
    class AutofillPromptGuard {
        private val promptedOrigins = mutableSetOf<String>()

        fun markIfNewOrigin(origin: String): Boolean =
            origin.isNotBlank() && promptedOrigins.add(origin)

        fun clearIfNotStarted(origin: String) {
            promptedOrigins.remove(origin)
        }

        fun onNavigationStarted() {
            promptedOrigins.clear()
        }
    }

    fun canOpenVault(deviceAuthenticationAvailable: Boolean, deviceAuthenticated: Boolean): Boolean =
        deviceAuthenticationAvailable && deviceAuthenticated

    /** Whether it is safe to ask the user to authenticate for a possible fill. */
    fun shouldRequestAutofillAuthentication(context: AutofillContext): Boolean =
        context.passwordManagerEnabled &&
            context.hasSavedCredential &&
            !context.isPrivateTab &&
            context.isActiveTab &&
            context.isSecurePage &&
            context.isSameOrigin &&
            context.deviceAuthenticationAvailable

    /** Credentials are released to the page only after successful device authentication. */
    fun mayAutofill(context: AutofillContext): Boolean =
        shouldRequestAutofillAuthentication(context) && context.deviceAuthenticated
}
