package com.click.browser.extensions

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionToolbarPolicyTest {
    @Test
    fun toolbarActionStaysHiddenWhenNoEligibleExtensionIsEnabled() {
        assertEquals(
            ExtensionToolbarAction.HIDDEN,
            ExtensionToolbarPolicy.actionFor(emptySet())
        )
        assertEquals(
            ExtensionToolbarAction.HIDDEN,
            ExtensionToolbarPolicy.actionFor(setOf("", "  "))
        )

        val ineligible = listOf(
            ExtensionToolbarCandidate("disabled@example", enabled = false, policyApproved = true),
            ExtensionToolbarCandidate("rejected@example", enabled = true, policyApproved = false),
            ExtensionToolbarCandidate("", enabled = true, policyApproved = true)
        )
        assertEquals(emptySet<String>(), ExtensionToolbarPolicy.enabledExtensionIds(ineligible))
    }

    @Test
    fun enabledAndApprovedExtensionsShowTheManagerRoute() {
        val candidates = listOf(
            ExtensionToolbarCandidate("reader@example", enabled = true, policyApproved = true),
            ExtensionToolbarCandidate("disabled@example", enabled = false, policyApproved = true),
            ExtensionToolbarCandidate("rejected@example", enabled = true, policyApproved = false)
        )
        val enabledIds = ExtensionToolbarPolicy.enabledExtensionIds(candidates)

        assertEquals(setOf("reader@example"), enabledIds)
        assertEquals(
            ExtensionToolbarAction.OPEN_EXTENSION_MANAGER,
            ExtensionToolbarPolicy.actionFor(enabledIds)
        )
    }

    @Test
    fun anySupportedEnabledExtensionKeepsManagerRouteAvailable() {
        val enabledIds = ExtensionToolbarPolicy.enabledExtensionIds(
            listOf(
                ExtensionToolbarCandidate("reader@example", enabled = true, policyApproved = true),
                ExtensionToolbarCandidate("translator@example", enabled = true, policyApproved = true)
            )
        )

        assertEquals(setOf("reader@example", "translator@example"), enabledIds)
        assertEquals(
            ExtensionToolbarAction.OPEN_EXTENSION_MANAGER,
            ExtensionToolbarPolicy.actionFor(enabledIds)
        )
    }
}
