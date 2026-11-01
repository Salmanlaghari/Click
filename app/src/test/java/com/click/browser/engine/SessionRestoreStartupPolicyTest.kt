package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRestoreStartupPolicyTest {
    @Test
    fun pendingModeSwitchRestoresThatProfilesSavedTabsAutomatically() {
        assertEquals(
            SessionRestoreStartupAction.AUTOMATIC_MODE_RESTORE,
            sessionRestoreStartupAction(
                previousExitWasClean = true,
                modeSwitchRestorePending = true,
                savedTabCount = 2,
                clearOnExit = false,
                externalStartUrlPresent = false
            )
        )
    }

    @Test
    fun ordinaryCleanLaunchDoesNotRestoreAnOldSession() {
        assertEquals(
            SessionRestoreStartupAction.NONE,
            sessionRestoreStartupAction(
                previousExitWasClean = true,
                modeSwitchRestorePending = false,
                savedTabCount = 2,
                clearOnExit = false,
                externalStartUrlPresent = false
            )
        )
    }

    @Test
    fun uncleanExitStillOffersCrashRecovery() {
        assertEquals(
            SessionRestoreStartupAction.CRASH_RECOVERY_PROMPT,
            sessionRestoreStartupAction(
                previousExitWasClean = false,
                modeSwitchRestorePending = false,
                savedTabCount = 1,
                clearOnExit = false,
                externalStartUrlPresent = false
            )
        )
    }

    @Test
    fun clearOnExitDisablesBothKindsOfRestore() {
        assertEquals(
            SessionRestoreStartupAction.NONE,
            sessionRestoreStartupAction(
                previousExitWasClean = false,
                modeSwitchRestorePending = true,
                savedTabCount = 2,
                clearOnExit = true,
                externalStartUrlPresent = false
            )
        )
    }

    @Test
    fun externalLinkTakesPrecedenceOverPendingModeRestore() {
        assertEquals(
            SessionRestoreStartupAction.NONE,
            sessionRestoreStartupAction(
                previousExitWasClean = true,
                modeSwitchRestorePending = true,
                savedTabCount = 2,
                clearOnExit = false,
                externalStartUrlPresent = true
            )
        )
    }

    @Test
    fun emptySnapshotDoesNotTriggerRestore() {
        assertEquals(
            SessionRestoreStartupAction.NONE,
            sessionRestoreStartupAction(
                previousExitWasClean = false,
                modeSwitchRestorePending = true,
                savedTabCount = 0,
                clearOnExit = false,
                externalStartUrlPresent = false
            )
        )
    }
}
