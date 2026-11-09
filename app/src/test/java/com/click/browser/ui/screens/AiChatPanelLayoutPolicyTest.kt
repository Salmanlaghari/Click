package com.click.browser.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatPanelLayoutPolicyTest {
    @Test
    fun collapsedAndExpandedHeightsStayConstrainedAndExpandable() {
        val collapsed = AiChatPanelLayoutPolicy.heightFraction(expanded = false)
        val expanded = AiChatPanelLayoutPolicy.heightFraction(expanded = true)

        assertEquals(AiChatPanelLayoutPolicy.COLLAPSED_HEIGHT_FRACTION, collapsed, 0f)
        assertEquals(AiChatPanelLayoutPolicy.EXPANDED_HEIGHT_FRACTION, expanded, 0f)
        assertTrue(collapsed > 0f)
        assertTrue(expanded > collapsed)
        assertTrue(expanded < 1f)
    }
}
