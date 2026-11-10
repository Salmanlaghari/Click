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
    fun sourceProcessCannotExitBeforeTheBridgeHasWindowFocus() {
        assertFalse(shouldRequestSourceProcessExit(bridgeHasWindowFocus = false))
        assertTrue(shouldRequestSourceProcessExit(bridgeHasWindowFocus = true))
    }

    @Test
    fun targetProfileCannotOpenUntilTheSourceProcessHasExited() {
        assertFalse(shouldOpenTargetProfile(sourceProcessExited = false))
        assertTrue(shouldOpenTargetProfile(sourceProcessExited = true))
    }
}
