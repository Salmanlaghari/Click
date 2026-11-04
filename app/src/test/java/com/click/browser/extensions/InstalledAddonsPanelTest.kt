package com.click.browser.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun addonWithoutToolbarActionOrOptionsIsIdentifiedAsHavingNoSeparateWindow() {
        val item = InstalledAddonPanelItem(
            id = "youtube-only@example",
            name = "YouTube GIF Comments",
            version = "1.0",
            enabled = true,
            policyBlockers = emptyList(),
            description = "Adds GIF controls to supported YouTube comment pages."
        )

        assertTrue(item.hasNoSeparateWindow)
        assertFalse(item.canOpenAction)
        assertFalse(item.canOpenOptions)
    }

    @Test
    fun actionAndOptionsRequireAnEnabledPolicyApprovedAddonAndSafeOptionsUrl() {
        val enabledAddon = InstalledAddonPanelItem(
            id = "action@example",
            name = "Action add-on",
            version = "1.0",
            enabled = true,
            policyBlockers = emptyList(),
            optionsPageUrl = "moz-extension://action/options.html",
            optionsPageBaseUrl = "moz-extension://action/",
            toolbarAction = InstalledAddonPanelAction("Action", pageAction = false)
        )
        val blockedAddon = enabledAddon.copy(policyBlockers = listOf("Sensitive API: cookies"))
        val noSafeOptionsAddon = enabledAddon.copy(optionsPageUrl = null, hasDeclaredOptionsPage = true)

        assertTrue(enabledAddon.canOpenAction)
        assertTrue(enabledAddon.canOpenOptions)
        assertFalse(blockedAddon.canOpenAction)
        assertFalse(blockedAddon.canOpenOptions)
        assertFalse(noSafeOptionsAddon.canOpenOptions)
    }
}
