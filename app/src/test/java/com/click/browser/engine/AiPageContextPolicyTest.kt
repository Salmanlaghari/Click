package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPageContextPolicyTest {
    @Test
    fun pageTitleUrlAndExcerptAreOmittedUntilConsentIsGranted() {
        val instruction = AiPageContextPolicy.systemInstruction(
            roleId = "browser",
            consentGranted = false,
            pageTitle = "Sensitive title",
            pageUrl = "https://private.example/article?token=secret",
            pageExcerpt = "Private page contents"
        )

        assertFalse(instruction.contains("Sensitive title"))
        assertFalse(instruction.contains("private.example"))
        assertFalse(instruction.contains("token=secret"))
        assertFalse(instruction.contains("Private page contents"))
        assertFalse(instruction.contains("Active Browser Tab Context"))
    }

    @Test
    fun approvedContextIncludesOnlyTheDocumentedBoundedFields() {
        val title = "T".repeat(AiPageContextPolicy.MAX_TITLE_CHARS + 20)
        val url = "https://example.com/" + "u".repeat(AiPageContextPolicy.MAX_URL_CHARS)
        val excerpt = "E".repeat(AiPageContextPolicy.MAX_EXCERPT_CHARS + 50)
        val instruction = AiPageContextPolicy.systemInstruction(
            roleId = "browser",
            consentGranted = true,
            pageTitle = title,
            pageUrl = url,
            pageExcerpt = excerpt
        )

        assertTrue(instruction.contains("Active Browser Tab Context"))
        assertTrue(instruction.contains("T".repeat(AiPageContextPolicy.MAX_TITLE_CHARS)))
        assertFalse(instruction.contains("T".repeat(AiPageContextPolicy.MAX_TITLE_CHARS + 1)))
        assertTrue(instruction.contains(url.take(AiPageContextPolicy.MAX_URL_CHARS)))
        assertFalse(instruction.contains(url.take(AiPageContextPolicy.MAX_URL_CHARS + 1)))
        assertTrue(instruction.contains("E".repeat(AiPageContextPolicy.MAX_EXCERPT_CHARS)))
        assertFalse(instruction.contains("E".repeat(AiPageContextPolicy.MAX_EXCERPT_CHARS + 1)))
        assertEquals(AiPageContextPolicy.MAX_URL_CHARS, url.take(AiPageContextPolicy.MAX_URL_CHARS).length)
    }

    @Test
    fun pageContextIsUnavailableForPrivateUnsupportedOrNonWebPages() {
        assertFalse(
            AiPageContextPolicy.canOfferPageContext(
                isPrivateTab = true,
                isWebViewSupported = true,
                pageUrl = "https://example.com"
            )
        )
        assertFalse(
            AiPageContextPolicy.canOfferPageContext(
                isPrivateTab = false,
                isWebViewSupported = false,
                pageUrl = "https://example.com"
            )
        )
        assertFalse(
            AiPageContextPolicy.canOfferPageContext(
                isPrivateTab = false,
                isWebViewSupported = true,
                pageUrl = "about:blank"
            )
        )
        assertTrue(
            AiPageContextPolicy.canOfferPageContext(
                isPrivateTab = false,
                isWebViewSupported = true,
                pageUrl = "https://example.com"
            )
        )
    }

    @Test
    fun disclosureNamesProviderAndMakesTransmissionTimingAndScopeExplicit() {
        val disclosure = AiPageContextPolicy.consentDisclosure(
            providerName = AiPageContextPolicy.externalProviderName("gemini"),
            includeGoogleSearchGrounding = false
        )

        assertTrue(disclosure.contains("Google Gemini (generativelanguage.googleapis.com)"))
        assertTrue(disclosure.contains("3,000 characters"))
        assertTrue(disclosure.contains("page title (up to 240 characters)"))
        assertTrue(disclosure.contains("page URL (up to 500 characters)"))
        assertTrue(disclosure.contains("your message and up to 20 recent user/assistant chat messages"))
        assertTrue(disclosure.contains("Nothing is sent to the AI provider until you send a message"))
        assertTrue(disclosure.contains("only while this chat remains open"))
        assertFalse(disclosure.contains("Google Search"))
        assertEquals("Groq (api.groq.com)", AiPageContextPolicy.externalProviderName("groq"))
    }

    @Test
    fun geminiDisclosureMentionsSearchGroundingWhenEnabled() {
        val disclosure = AiPageContextPolicy.consentDisclosure(
            providerName = "Google Gemini",
            includeGoogleSearchGrounding = true
        )

        assertTrue(disclosure.contains("Gemini may also use Google Search"))
    }
}
