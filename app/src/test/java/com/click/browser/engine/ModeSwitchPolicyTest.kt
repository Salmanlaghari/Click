package com.click.browser.engine

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
    fun keepsTheProcessAliveOnlyWhenNeitherRestartPathStarted() {
        assertTrue(hasEngineRestartPath(alarmScheduled = true, directRelaunchStarted = false))
        assertTrue(hasEngineRestartPath(alarmScheduled = false, directRelaunchStarted = true))
        assertTrue(hasEngineRestartPath(alarmScheduled = true, directRelaunchStarted = true))
        assertFalse(hasEngineRestartPath(alarmScheduled = false, directRelaunchStarted = false))
    }
}
