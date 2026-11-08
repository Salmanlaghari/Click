package com.click.browser.engine

/** Safety rules for the on-device selected-text translation sheet. */
internal object SelectedTextTranslationPolicy {
    fun initialInputText(selectedTextOnly: Boolean, selectedText: String?): String =
        if (selectedTextOnly) {
            selectedText.orEmpty().take(AiSelectedTextPolicy.MAX_SELECTED_TEXT_CHARS)
        } else {
            ""
        }

    fun canTranslatePage(
        selectedTextOnly: Boolean,
        hasWebView: Boolean,
        pageUrl: String
    ): Boolean = !selectedTextOnly && hasWebView &&
        (pageUrl.startsWith("http://", ignoreCase = true) ||
            pageUrl.startsWith("https://", ignoreCase = true))
}
