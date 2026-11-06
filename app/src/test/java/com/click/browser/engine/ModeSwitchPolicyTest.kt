package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeSwitchPolicyTest {
    @Test
    fun persistsBootModeWhenStoredModeWasLeftOnAnotherProfile() {
        assertTrue(
            shouldPersistNoRestartModeSelection(
                savedMode = BrowserMode.DEVELOPER,
                requestedMode = BrowserMode.SIMPLE
            )
        )
    }

    @Test
    fun avoidsAnUnnecessaryWriteWhenSelectedModeIsAlreadySaved() {
        assertFalse(
            shouldPersistNoRestartModeSelection(
                savedMode = BrowserMode.SIMPLE,
                requestedMode = BrowserMode.SIMPLE
            )
        )
    }

    @Test
    fun relaunchPendingIntentUsesApiSpecificCreatorBackgroundStartMode() {
        assertEquals(
            RelaunchPendingIntentBalMode.PLATFORM_DEFAULT,
            relaunchPendingIntentCreatorBalMode(33)
        )
        assertEquals(
            RelaunchPendingIntentBalMode.ALLOW_BACKGROUND,
            relaunchPendingIntentCreatorBalMode(34)
        )
        assertEquals(
            RelaunchPendingIntentBalMode.ALLOW_BACKGROUND,
            relaunchPendingIntentCreatorBalMode(35)
        )
        assertEquals(
            RelaunchPendingIntentBalMode.ALLOW_ALWAYS,
            relaunchPendingIntentCreatorBalMode(36)
        )
    }

    @Test
    fun processDeathRequiresAScheduledColdRestart() {
        assertTrue(hasEngineRestartPath(alarmScheduled = true))
        // A relaunch into the current singleTask Activity can reuse this process.
        assertFalse(hasEngineRestartPath(alarmScheduled = false))
    }
}
