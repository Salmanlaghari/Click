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
        assertTrue(geckoRendererRecoveryRequired(false, GeckoRenderEvent.RECOVERY_STARTED))
        assertTrue(geckoRendererRecoveryRequired(false, GeckoRenderEvent.RECOVERY_RELOAD_STARTED))
        assertFalse(geckoRendererRecoveryRequired(false, GeckoRenderEvent.LOAD_STARTED))
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
