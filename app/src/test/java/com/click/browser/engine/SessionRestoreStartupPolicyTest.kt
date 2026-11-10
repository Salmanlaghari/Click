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
}
