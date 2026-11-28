package com.click.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockRelockPolicyTest {

    @Test
    fun quickReturnWithinGraceReleasesTheLock() {
        // Backgrounded at t=100s, back 5s later: smooth return, no prompt.
        assertFalse(LockRelockPolicy.shouldKeepLock(100_000L, 105_000L))
    }

    @Test
    fun returnAfterGraceKeepsTheLock() {
        // Backgrounded at t=100s, back 31s later: lock stays.
        assertTrue(LockRelockPolicy.shouldKeepLock(100_000L, 131_000L))
    }

    @Test
    fun exactlyAtGraceBoundaryKeepsTheLock() {
        assertTrue(
            LockRelockPolicy.shouldKeepLock(100_000L, 100_000L + LockRelockPolicy.GRACE_MS)
        )
    }

    @Test
    fun coldStartOrLockedBackgroundingKeepsTheLock() {
        // No stamp (0) means cold start or backgrounded while locked.
        assertTrue(LockRelockPolicy.shouldKeepLock(0L, 500_000L))
    }

    @Test
    fun backwardsClockKeepsTheLock() {
        assertTrue(LockRelockPolicy.shouldKeepLock(200_000L, 100_000L))
    }
}
