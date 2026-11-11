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

    @Test
    fun switchWaitsForWindowFocusThatIsStillReturningAfterDialogDismissal() {
        // The confirmation dialog held window focus; just after dismissal the
        // Activity may still report no focus. The switch must keep waiting
        // instead of failing on that first instantaneous check.
        assertTrue(
            shouldKeepWaitingForSourceWindowFocus(
                hasWindowFocus = false, waitedMs = 0L, timeoutMs = 2_500L
            )
        )
        assertTrue(
            shouldKeepWaitingForSourceWindowFocus(
                hasWindowFocus = false, waitedMs = 2_499L, timeoutMs = 2_500L
            )
        )
        assertFalse(
            shouldFailSwitchForMissingWindowFocus(
                hasWindowFocus = false, waitedMs = 100L, timeoutMs = 2_500L
            )
        )
    }

    @Test
    fun switchStopsWaitingAsSoonAsWindowFocusReturns() {
        assertFalse(
            shouldKeepWaitingForSourceWindowFocus(
                hasWindowFocus = true, waitedMs = 0L, timeoutMs = 2_500L
            )
        )
        assertFalse(
            shouldFailSwitchForMissingWindowFocus(
                hasWindowFocus = true, waitedMs = 5_000L, timeoutMs = 2_500L
            )
        )
    }

    @Test
    fun switchFailsOnlyAfterTheWholeFocusWaitBudgetIsExhausted() {
        assertFalse(
            shouldKeepWaitingForSourceWindowFocus(
                hasWindowFocus = false, waitedMs = 2_500L, timeoutMs = 2_500L
            )
        )
        assertTrue(
            shouldFailSwitchForMissingWindowFocus(
                hasWindowFocus = false, waitedMs = 2_500L, timeoutMs = 2_500L
            )
        )
        assertTrue(
            shouldFailSwitchForMissingWindowFocus(
                hasWindowFocus = false, waitedMs = 4_000L, timeoutMs = 2_500L
            )
        )
    }
}
