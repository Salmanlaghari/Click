package com.click.browser.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalTabExtensionSupportTest {
    @Test
    fun clickReactivatesTheRequestedVisibleTabBeforeDispatch() {
        val events = mutableListOf<String>()

        val dispatched = dispatchNormalTabActionClick(
            tabId = "visible-tab",
            attachedTabIds = setOf("previous-tab", "visible-tab"),
            isEnabled = true,
            isPolicyApproved = true,
            activateTab = { events += "activate:$it" },
            clickAction = { events += "click" }
        )

        assertTrue(dispatched)
        assertEquals(listOf("activate:visible-tab", "click"), events)
    }

    @Test
    fun clickIsRejectedWhenTheRequestedSessionIsNoLongerAttached() {
        val events = mutableListOf<String>()

        val dispatched = dispatchNormalTabActionClick(
            tabId = "closed-tab",
            attachedTabIds = setOf("visible-tab"),
            isEnabled = true,
            isPolicyApproved = true,
            activateTab = { events += "activate:$it" },
            clickAction = { events += "click" }
        )

        assertFalse(dispatched)
        assertTrue(events.isEmpty())
    }

    @Test
    fun clickPreservesEnabledAndSafetyGates() {
        val events = mutableListOf<String>()
        val activate: (String) -> Unit = { events += "activate:$it" }
        val click: () -> Unit = { events += "click" }

        assertFalse(
            dispatchNormalTabActionClick(
                "visible-tab", setOf("visible-tab"),
                isEnabled = false, isPolicyApproved = true,
                activateTab = activate, clickAction = click
            )
        )
        assertFalse(
            dispatchNormalTabActionClick(
                "visible-tab", setOf("visible-tab"),
                isEnabled = true, isPolicyApproved = false,
                activateTab = activate, clickAction = click
            )
        )
        assertTrue(events.isEmpty())
    }
}
