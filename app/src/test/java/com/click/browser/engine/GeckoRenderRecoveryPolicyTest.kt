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
    fun autoRecoveryStartNeverShowsOrHidesTheCardByItself() {
        // Card hidden + auto attempt starts: stays hidden (recovery is silent).
        assertFalse(geckoRendererRecoveryRequired(false, GeckoRenderEvent.AUTO_RECOVERY_STARTED))
        // Card already visible + another attempt starts: stays visible.
        assertTrue(geckoRendererRecoveryRequired(true, GeckoRenderEvent.AUTO_RECOVERY_STARTED))
    }

    @Test
    fun automaticRecoveryIsBoundedAndStopsWhenSessionIsGoneOrBusy() {
        assertTrue(geckoAutoRecoveryAllowed(attemptsUsed = 0, sessionDestroyed = false, recoveryInProgress = false))
        assertTrue(geckoAutoRecoveryAllowed(attemptsUsed = 1, sessionDestroyed = false, recoveryInProgress = false))
        assertFalse(geckoAutoRecoveryAllowed(attemptsUsed = 2, sessionDestroyed = false, recoveryInProgress = false))
        assertFalse(geckoAutoRecoveryAllowed(attemptsUsed = 0, sessionDestroyed = true, recoveryInProgress = false))
        assertFalse(geckoAutoRecoveryAllowed(attemptsUsed = 0, sessionDestroyed = false, recoveryInProgress = true))
    }

    @Test
    fun recoveryPlanRebuildsOpenButDeadSessionsInsteadOfNoOpReload() {
        // Open session + renderer dead: plain reload() would silently no-op,
        // so the plan must close + reopen + restore.
        assertEquals(
            GeckoRecoveryPlan.REBUILD_AND_RESTORE,
            geckoRecoveryPlan(
                sessionIsOpen = true,
                recoveryRequired = true,
                recoveryInProgress = false,
                hasSavedState = true,
                hasCurrentUrl = true
            )
        )
        // Open + healthy: ordinary reload, no rebuild.
        assertEquals(
            GeckoRecoveryPlan.RELOAD_SESSION,
            geckoRecoveryPlan(
                sessionIsOpen = true,
                recoveryRequired = false,
                recoveryInProgress = false,
                hasSavedState = true,
                hasCurrentUrl = true
            )
        )
        // Closed session: reopen with saved state first, URL second, blank last.
        assertEquals(
            GeckoRecoveryPlan.REOPEN_AND_RESTORE_STATE,
            geckoRecoveryPlan(
                sessionIsOpen = false,
                recoveryRequired = true,
                recoveryInProgress = false,
                hasSavedState = true,
                hasCurrentUrl = true
            )
        )
        assertEquals(
            GeckoRecoveryPlan.REOPEN_AND_LOAD_CURRENT_URL,
            geckoRecoveryPlan(
                sessionIsOpen = false,
                recoveryRequired = true,
                recoveryInProgress = false,
                hasSavedState = false,
                hasCurrentUrl = true
            )
        )
        assertEquals(
            GeckoRecoveryPlan.REOPEN_BLANK,
            geckoRecoveryPlan(
                sessionIsOpen = false,
                recoveryRequired = true,
                recoveryInProgress = false,
                hasSavedState = false,
                hasCurrentUrl = false
            )
        )
        assertEquals(
            GeckoRecoveryPlan.IN_PROGRESS,
            geckoRecoveryPlan(
                sessionIsOpen = true,
                recoveryRequired = true,
                recoveryInProgress = true,
                hasSavedState = true,
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
