package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoRenderRecoveryPolicyTest {
    @Test
    fun recoveryRemainsVisibleAcrossLoadStartAndClearsOnlyAfterFirstComposite() {
        val crashed = geckoRendererRecoveryRequired(false, GeckoRenderEvent.PROCESS_CRASHED)
        assertTrue(crashed)
        assertTrue(geckoRendererRecoveryRequired(crashed, GeckoRenderEvent.LOAD_STARTED))
        assertTrue(geckoRendererRecoveryRequired(crashed, GeckoRenderEvent.RECOVERY_STARTED))
        assertFalse(geckoRendererRecoveryRequired(crashed, GeckoRenderEvent.FIRST_COMPOSITE))
        assertTrue(geckoRendererRecoveryRequired(false, GeckoRenderEvent.PROCESS_KILLED))
        assertTrue(geckoRendererRecoveryRequired(false, GeckoRenderEvent.RECOVERY_FAILED))
        assertTrue(geckoRendererRecoveryRequired(false, GeckoRenderEvent.RECOVERY_RETRY_THROTTLED))
        assertTrue(geckoRendererRecoveryRequired(false, GeckoRenderEvent.RECOVERY_STARTED))
        assertTrue(geckoRendererRecoveryRequired(false, GeckoRenderEvent.RECOVERY_RELOAD_STARTED))
        assertFalse(geckoRendererRecoveryRequired(false, GeckoRenderEvent.LOAD_STARTED))
    }

    @Test
    fun repeatedFailuresTemporarilyThrottleRetryAndAllowItAfterWindowExpires() {
        val failures = listOf(10_000L, 20_000L, 30_000L)

        val throttled = geckoRendererRetryDecision(failures, nowMillis = 31_000L)
        assertFalse(throttled.retryAllowed)
        assertEquals(3, throttled.recentFailureCount)
        assertEquals(39_000L, throttled.retryAfterMillis)

        val afterOldestFailureExpires = geckoRendererRetryDecision(failures, nowMillis = 70_000L)
        assertTrue(afterOldestFailureExpires.retryAllowed)
        assertEquals(2, afterOldestFailureExpires.recentFailureCount)
        assertEquals(0L, afterOldestFailureExpires.retryAfterMillis)
    }

    @Test
    fun failureRecordingPrunesOldEntriesAndKeepsOnlyTheNewestBoundedWindow() {
        assertEquals(
            listOf(20_000L, 30_000L, 40_000L),
            recordGeckoRendererFailure(
                failureTimesMillis = listOf(0L, 10_000L, 20_000L, 30_000L),
                failedAtMillis = 40_000L
            )
        )
        assertEquals(
            listOf(20_000L, 30_000L, 70_000L),
            recordGeckoRendererFailure(
                failureTimesMillis = listOf(0L, 10_000L, 20_000L, 30_000L),
                failedAtMillis = 70_000L
            )
        )
    }

    @Test
    fun closedSessionRecoveryRestoresSavedStateOrLoadsRememberedUrl() {
        assertEquals(
            GeckoSessionRecoveryAction.REOPEN_AND_RESTORE_STATE,
            geckoSessionRecoveryAction(
                sessionIsOpen = false,
                recoveryInProgress = false,
                hasSavedState = true,
                hasCurrentUrl = true
            )
        )
        assertEquals(
            GeckoSessionRecoveryAction.REOPEN_AND_LOAD_CURRENT_URL,
            geckoSessionRecoveryAction(
                sessionIsOpen = false,
                recoveryInProgress = false,
                hasSavedState = false,
                hasCurrentUrl = true
            )
        )
    }

    @Test
    fun recoveryAvoidsDuplicateOpenAndConcurrentRetries() {
        assertEquals(
            GeckoSessionRecoveryAction.RELOAD_OPEN_SESSION,
            geckoSessionRecoveryAction(
                sessionIsOpen = true,
                recoveryInProgress = false,
                hasSavedState = true,
                hasCurrentUrl = true
            )
        )
        assertEquals(
            GeckoSessionRecoveryAction.IGNORE_IN_PROGRESS,
            geckoSessionRecoveryAction(
                sessionIsOpen = false,
                recoveryInProgress = true,
                hasSavedState = true,
                hasCurrentUrl = true
            )
        )
        assertEquals(
            GeckoSessionRecoveryAction.REOPEN_BLANK_SESSION,
            geckoSessionRecoveryAction(
                sessionIsOpen = false,
                recoveryInProgress = false,
                hasSavedState = false,
                hasCurrentUrl = false
            )
        )
    }
}
