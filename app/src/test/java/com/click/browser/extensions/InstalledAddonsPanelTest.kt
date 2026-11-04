package com.click.browser.extensions

import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledAddonsPanelTest {
    @Test
    fun statusLabelShowsEnabledForApprovedEnabledAddon() {
        val item = InstalledAddonPanelItem(
            id = "addon@example",
            name = "Example",
            version = "1.0",
            enabled = true,
            policyBlockers = emptyList()
        )

        assertEquals("Enabled", item.statusLabel)
    }

    @Test
    fun statusLabelShowsDisabledForApprovedDisabledAddon() {
        val item = InstalledAddonPanelItem(
            id = "addon@example",
            name = "Example",
            version = "1.0",
            enabled = false,
            policyBlockers = emptyList()
        )

        assertEquals("Disabled", item.statusLabel)
    }

    @Test
    fun policyBlockersOverrideEnabledMetadata() {
        val item = InstalledAddonPanelItem(
            id = "addon@example",
            name = "Example",
            version = "1.0",
            enabled = true,
            policyBlockers = listOf("Sensitive API: nativeMessaging")
        )

        assertEquals("Blocked by Click safety policy", item.statusLabel)
    }
}
