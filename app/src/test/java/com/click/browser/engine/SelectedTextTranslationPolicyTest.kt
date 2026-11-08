package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedTextTranslationPolicyTest {
    @Test
    fun selectedTextModePrefillsOnlyTheSelectedPassage() {
        val selected = "Only this passage should be translated."

        assertEquals(
            selected,
            SelectedTextTranslationPolicy.initialInputText(
                selectedTextOnly = true,
                selectedText = selected
            )
        )
        assertEquals("", SelectedTextTranslationPolicy.initialInputText(true, null))
        assertEquals("", SelectedTextTranslationPolicy.initialInputText(false, selected))

        val oversizedSelection = "x".repeat(AiSelectedTextPolicy.MAX_SELECTED_TEXT_CHARS + 1)
        assertEquals(
            "x".repeat(AiSelectedTextPolicy.MAX_SELECTED_TEXT_CHARS),
            SelectedTextTranslationPolicy.initialInputText(true, oversizedSelection)
        )
    }

    @Test
    fun selectionOnlyModeNeverAllowsPageTranslationEvenWithAWebViewAndUrl() {
        assertFalse(
            SelectedTextTranslationPolicy.canTranslatePage(
                selectedTextOnly = true,
                hasWebView = true,
                pageUrl = "https://example.com/article"
            )
        )
    }

    @Test
    fun fullPageTranslationRemainsLimitedToHttpWebViewPagesOutsideSelectionMode() {
        assertTrue(
            SelectedTextTranslationPolicy.canTranslatePage(
                selectedTextOnly = false,
                hasWebView = true,
                pageUrl = "https://example.com/article"
            )
        )
        assertTrue(
            SelectedTextTranslationPolicy.canTranslatePage(
                selectedTextOnly = false,
                hasWebView = true,
                pageUrl = "HTTP://example.com/article"
            )
        )
        assertFalse(
            SelectedTextTranslationPolicy.canTranslatePage(
                selectedTextOnly = false,
                hasWebView = false,
                pageUrl = "https://example.com/article"
            )
        )
        assertFalse(
            SelectedTextTranslationPolicy.canTranslatePage(
                selectedTextOnly = false,
                hasWebView = true,
                pageUrl = "file:///private/document.txt"
            )
        )
    }
}
