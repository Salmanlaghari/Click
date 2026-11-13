package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRestoreStartupPolicyTest {
    @Test
    fun savedTabsAutomaticallyRestoreAfterAnOrdinaryCleanLaunch() {
        assertEquals(
            SessionRestoreStartupAction.AUTOMATIC_RESTORE,
            sessionRestoreStartupAction(savedTabCount = 2, clearOnExit = false)
        )
    }

    @Test
    fun savedTabsAutomaticallyRestoreAfterAnUncleanExitWithoutARepeatPrompt() {
        assertEquals(
            SessionRestoreStartupAction.AUTOMATIC_RESTORE,
            sessionRestoreStartupAction(savedTabCount = 1, clearOnExit = false)
        )
    }

    @Test
    fun clearOnExitAlwaysSuppressesSessionRestore() {
        assertEquals(
            SessionRestoreStartupAction.NONE,
            sessionRestoreStartupAction(savedTabCount = 2, clearOnExit = true)
        )
    }

    @Test
    fun emptySnapshotDoesNotTriggerRestore() {
        assertEquals(
            SessionRestoreStartupAction.NONE,
            sessionRestoreStartupAction(savedTabCount = 0, clearOnExit = false)
        )
    }

    // Home-landing regression (1.7.0 device report): dashboards compose
    // only on the home tab, and a home tab is never restorable
    // (SessionRestore persists http/https only), so a plain launch must
    // land on the dashboard even when a session was restored — otherwise
    // the restored web page hides every mode dashboard.
    @Test
    fun plainLaunchLandsOnHomeDashboard() {
        assertEquals(true, shouldLandOnHomeDashboard(startViewUrl = null))
    }

    @Test
    fun externalLinkLaunchKeepsTheLinkInFront() {
        assertEquals(
            false,
            shouldLandOnHomeDashboard(startViewUrl = "https://example.com/article")
        )
    }
}
