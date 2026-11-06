package com.click.browser.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalTabExtensionSupportTest {
    @Test
    fun clickRequestReactivatesVisibleTabButDoesNotClaimPopupOpened() {
        val events = mutableListOf<String>()

        val dispatched = dispatchNormalTabActionClick(
            tabId = "visible-tab",
            attachedTabIds = setOf("previous-tab", "visible-tab"),
            isEnabled = true,
            isPolicyApproved = true,
            activateTab = { events += "activate:$it" },
            clickAction = { events += "click" }
        )

        // No popup callback is simulated here: REQUESTED only means that the
        // asynchronous GeckoView action request was sent.
        assertEquals(NormalTabActionDispatchResult.REQUESTED, dispatched)
        assertEquals(listOf("activate:visible-tab", "click"), events)
    }

    @Test
    fun clickActionFailureIsRejectedAfterReactivatingTheRequestedTab() {
        val events = mutableListOf<String>()

        val dispatched = dispatchNormalTabActionClick(
            tabId = "visible-tab",
            attachedTabIds = setOf("visible-tab"),
            isEnabled = true,
            isPolicyApproved = true,
            activateTab = { events += "activate:$it" },
            clickAction = {
                events += "click"
                throw IllegalStateException("action no longer available")
            }
        )

        assertEquals(NormalTabActionDispatchResult.REJECTED, dispatched)
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

        assertEquals(NormalTabActionDispatchResult.REJECTED, dispatched)
        assertTrue(events.isEmpty())
    }

    @Test
    fun clickPreservesEnabledAndSafetyGates() {
        val events = mutableListOf<String>()
        val activate: (String) -> Unit = { events += "activate:$it" }
        val click: () -> Unit = { events += "click" }

        assertEquals(NormalTabActionDispatchResult.REJECTED,
            dispatchNormalTabActionClick(
                "visible-tab", setOf("visible-tab"),
                isEnabled = false, isPolicyApproved = true,
                activateTab = activate, clickAction = click
            )
        )
        assertEquals(NormalTabActionDispatchResult.REJECTED,
            dispatchNormalTabActionClick(
                "visible-tab", setOf("visible-tab"),
                isEnabled = true, isPolicyApproved = false,
                activateTab = activate, clickAction = click
            )
        )
        assertTrue(events.isEmpty())
    }
}
