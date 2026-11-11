package com.click.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure policy behind the Play-Core update checker. */
class AppUpdatePolicyTest {

    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun firstEverCheckRunsImmediately() {
        assertTrue(AppUpdateChecker.shouldRunAutomaticCheck(lastCheckMs = 0L, nowMs = day))
    }

    @Test
    fun checksAreThrottledToOnceADay() {
        assertFalse(AppUpdateChecker.shouldRunAutomaticCheck(lastCheckMs = day, nowMs = day + 1000L))
        assertTrue(AppUpdateChecker.shouldRunAutomaticCheck(lastCheckMs = day, nowMs = day * 3))
    }

    @Test
    fun updateNotificationFiresOncePerVersion() {
        assertTrue(
            AppUpdateChecker.shouldNotifyUpdate(
                updateAvailable = true,
                availableVersionCode = 18,
                currentVersionCode = 17,
                lastNotifiedVersionCode = 0
            )
        )
        assertFalse(
            AppUpdateChecker.shouldNotifyUpdate(
                updateAvailable = true,
                availableVersionCode = 18,
                currentVersionCode = 17,
                lastNotifiedVersionCode = 18
            )
        )
    }

    @Test
    fun noNotificationWhenNothingNewerExists() {
        assertFalse(
            AppUpdateChecker.shouldNotifyUpdate(
                updateAvailable = false,
                availableVersionCode = 17,
                currentVersionCode = 17,
                lastNotifiedVersionCode = 0
            )
        )
        assertFalse(
            AppUpdateChecker.shouldNotifyUpdate(
                updateAvailable = true,
                availableVersionCode = 17,
                currentVersionCode = 17,
                lastNotifiedVersionCode = 0
            )
        )
    }
}
