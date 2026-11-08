package com.click.browser.engine

/**
 * Consent boundary for sharing the active page with an external AI provider.
 * Page context is absent from every request until the user explicitly accepts
 * the in-chat disclosure. The approval is intentionally in-memory and scoped to
 * one chat, provider, and page.
 */
object AiPageContextPolicy {
    const val MAX_TITLE_CHARS = 240
    const val MAX_URL_CHARS = 500
    const val MAX_EXCERPT_CHARS = 3_000

    fun canOfferPageContext(
        isPrivateTab: Boolean,
        isWebViewSupported: Boolean,
        pageUrl: String
    ): Boolean =
        !isPrivateTab && isWebViewSupported &&
            (pageUrl.startsWith("https://", ignoreCase = true) ||
                pageUrl.startsWith("http://", ignoreCase = true))

    fun externalProviderName(providerId: String): String = when (providerId) {
        "gemini" -> "Google Gemini (generativelanguage.googleapis.com)"
        "groq" -> "Groq (api.groq.com)"
        "openrouter" -> "OpenRouter (openrouter.ai)"
        else -> "the selected AI provider"
    }

    fun systemInstruction(
        roleId: String,
        consentGranted: Boolean,
        pageTitle: String,
        pageUrl: String,
        pageExcerpt: String
    ): String = if (!consentGranted) {
        AiRolePresets.instructionFor(roleId)
    } else {
        AiRolePresets.instructionFor(
            roleId = roleId,
            pageTitle = pageTitle.take(MAX_TITLE_CHARS),
            pageUrl = pageUrl.take(MAX_URL_CHARS),
            pageExcerpt = pageExcerpt.take(MAX_EXCERPT_CHARS)
        )
    }

    fun consentDisclosure(
        providerName: String,
        includeGoogleSearchGrounding: Boolean
    ): String =
        "If you allow, Click will read this page's visible text, up to 3,000 characters. " +
            "When you send an AI message, the page title (up to 240 characters), page URL " +
            "(up to 500 characters), and captured text, along with your message and up to 20 " +
            "recent user/assistant chat messages, will be sent over HTTPS to $providerName. " +
            "Nothing is sent to the AI provider until you send a message. This approval lasts " +
            "only while this chat remains open. The provider's privacy terms apply." +
            if (includeGoogleSearchGrounding) {
                " Gemini may also use Google Search for this request because Search is enabled."
            } else {
                ""
            }
}
