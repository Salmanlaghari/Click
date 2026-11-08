package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSelectedTextPolicyTest {
    @Test
    fun selectionIsBoundedAndTruncationIsDisclosed() {
        val source = "S".repeat(AiSelectedTextPolicy.MAX_SELECTED_TEXT_CHARS + 25)
        val selection = AiSelectedTextPolicy.prepareSelection(
            rawText = source,
            engine = BrowserTabEngine.GECKOVIEW,
            isPrivateTab = false
        )

        requireNotNull(selection)
        assertEquals(AiSelectedTextPolicy.MAX_SELECTED_TEXT_CHARS, selection.text.length)
        assertEquals(source.take(AiSelectedTextPolicy.MAX_SELECTED_TEXT_CHARS), selection.text)
        assertTrue(selection.wasTruncated)
        assertTrue(
            AiSelectedTextPolicy.consentDisclosure("Groq (api.groq.com)", selection)
                .contains("only the first 4000 selected characters")
        )
    }

    @Test
    fun emptyCollapsedEditableAndPasswordSelectionsAreRejected() {
        assertNull(prepare(" \n "))
        assertNull(prepare("text", isCollapsed = true))
        assertNull(prepare("text", isEditable = true))
        assertNull(prepare("text", isPassword = true))
    }

    @Test
    fun providerDisclosureNamesRecipientAndExcludesWholePageCapture() {
        val selection = requireNotNull(prepare("selected passage"))
        val disclosure = AiSelectedTextPolicy.consentDisclosure(
            providerName = AiPageContextPolicy.externalProviderName("gemini"),
            selection = selection
        )

        assertTrue(disclosure.contains("Google Gemini (generativelanguage.googleapis.com)"))
        assertTrue(disclosure.contains("only the selected text shown below"))
        assertTrue(disclosure.contains("up to 20 recent user/assistant messages"))
        assertTrue(disclosure.contains("Nothing is sent until you choose Explain"))
        assertTrue(disclosure.contains("will not capture the page title, URL"))
        assertTrue(disclosure.contains("Google Search grounding is off"))
        assertFalse(disclosure.contains("visible text"))
        assertFalse(disclosure.contains("page excerpt"))
    }

    @Test
    fun consentMustBeAcceptedAndCancellationCreatesNoPrompt() {
        val selection = requireNotNull(prepare("Text to explain"))

        assertNull(AiSelectedTextPolicy.promptAfterConsent(selection, accepted = false))
        val prompt = requireNotNull(
            AiSelectedTextPolicy.promptAfterConsent(selection, accepted = true)
        )
        assertTrue(prompt.contains("Text to explain"))
        assertTrue(prompt.contains("untrusted content to explain"))
    }

    @Test
    fun promptContainsOnlyTheQuotedSelectionAndDoesNotAddPageMetadata() {
        val selection = requireNotNull(prepare("line one\n\"page injection\""))
        val prompt = requireNotNull(
            AiSelectedTextPolicy.promptAfterConsent(selection, accepted = true)
        )

        assertTrue(prompt.contains("line one\\n\\\"page injection\\\""))
        assertFalse(prompt.contains("document.body"))
        assertFalse(prompt.contains("https://"))
        assertFalse(prompt.contains("page title"))
    }

    @Test
    fun privateTabsNeverOfferSelectionToEitherEngine() {
        assertFalse(
            AiSelectedTextPolicy.canOfferSelection(
                BrowserTabEngine.GECKOVIEW,
                isPrivateTab = true
            )
        )
        assertFalse(
            AiSelectedTextPolicy.canOfferSelection(
                BrowserTabEngine.WEBVIEW,
                isPrivateTab = true
            )
        )
        assertNull(
            AiSelectedTextPolicy.prepareSelection(
                rawText = "private selected text",
                engine = BrowserTabEngine.WEBVIEW,
                isPrivateTab = true
            )
        )
    }

    @Test
    fun onlyGeckoViewOffersNativeSelectionActions() {
        assertTrue(
            AiSelectedTextPolicy.canOfferSelection(
                BrowserTabEngine.GECKOVIEW,
                isPrivateTab = false
            )
        )
        assertFalse(
            AiSelectedTextPolicy.canOfferSelection(
                BrowserTabEngine.WEBVIEW,
                isPrivateTab = false
            )
        )
        val gecko = requireNotNull(
            AiSelectedTextPolicy.prepareSelection("Gecko selection", BrowserTabEngine.GECKOVIEW, false)
        )
        assertNull(
            AiSelectedTextPolicy.prepareSelection("WebView selection", BrowserTabEngine.WEBVIEW, false)
        )
        assertEquals(BrowserTabEngine.GECKOVIEW, gecko.sourceEngine)
    }

    private fun prepare(
        text: String,
        isCollapsed: Boolean = false,
        isEditable: Boolean = false,
        isPassword: Boolean = false
    ): AiSelectedTextPolicy.PreparedSelection? = AiSelectedTextPolicy.prepareSelection(
        rawText = text,
        engine = BrowserTabEngine.GECKOVIEW,
        isPrivateTab = false,
        isCollapsed = isCollapsed,
        isEditable = isEditable,
        isPassword = isPassword
    )
}
