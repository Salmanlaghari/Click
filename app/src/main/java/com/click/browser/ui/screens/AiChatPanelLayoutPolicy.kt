package com.click.browser.ui.screens

/** Height limits for the Ask AI panel, expressed as a fraction of the browser viewport. */
internal object AiChatPanelLayoutPolicy {
    const val COLLAPSED_HEIGHT_FRACTION = 0.68f
    const val EXPANDED_HEIGHT_FRACTION = 0.94f

    fun heightFraction(expanded: Boolean): Float =
        if (expanded) EXPANDED_HEIGHT_FRACTION else COLLAPSED_HEIGHT_FRACTION
}
