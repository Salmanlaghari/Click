package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionAutoRemovePolicyTest {

    private val now = 1_800_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000
    private val hourMs = 60L * 60 * 1000

    @Test
    fun revokesDecisionWhoseStampIsOlderThanTheWindow() {
        val decisions = mapOf("https://stale.example" to true)
        val stamps = mapOf("https://stale.example" to (now - 45 * dayMs))
        assertEquals(
            setOf("https://stale.example"),
            permissionOriginsToRevoke(decisions, stamps, nowMs = now)
        )
    }

    @Test
    fun keepsDecisionWhoseStampIsRecent() {
        val decisions = mapOf("https://recent.example" to false)
        val stamps = mapOf("https://recent.example" to (now - dayMs))
        assertTrue(permissionOriginsToRevoke(decisions, stamps, nowMs = now).isEmpty())
    }

    @Test
    fun missingStampNeverRevokes() {
        // Fail-safe: a choice saved before the stamp store existed (or whose
        // stamp was lost) must survive the sweep, however old it may be.
        val decisions = mapOf("https://legacy.example" to true)
        assertTrue(permissionOriginsToRevoke(decisions, emptyMap(), nowMs = now).isEmpty())
    }

    @Test
    fun stampExactlyAtTheWindowBoundaryKeepsTheDecision() {
        val decisions = mapOf("https://edge.example" to true)
        val stamps = mapOf("https://edge.example" to (now - PERMISSION_AUTO_REMOVE_WINDOW_MS))
        assertTrue(permissionOriginsToRevoke(decisions, stamps, nowMs = now).isEmpty())
    }

    @Test
    fun recentHistoryVisitKeepsDecisionDespiteOldStamp() {
        // History is only a secondary keep-signal: it can save a choice with
        // an old stamp, never cause a revoke on its own.
        val decisions = mapOf("https://mixed.example" to true)
        val stamps = mapOf("https://mixed.example" to (now - 90 * dayMs))
        assertTrue(
            permissionOriginsToRevoke(
                decisions, stamps,
                recentHistoryHosts = setOf("mixed.example"),
                nowMs = now
            ).isEmpty()
        )
    }

    @Test
    fun cappedHistoryCannotRevokeRecentlyVisitedSite() {
        // Regression test for the Kilo finding: browsing history is capped at
        // 100 entries, so a site visited yesterday may be absent from it.
        // The sweep keys off the origin's own stamp, so the fresh stamp
        // keeps the choice even with an empty history keep-signal.
        val decisions = mapOf("https://busy.example" to true)
        val stamps = mapOf("https://busy.example" to (now - dayMs))
        assertTrue(
            permissionOriginsToRevoke(
                decisions, stamps,
                recentHistoryHosts = emptySet(),
                nowMs = now
            ).isEmpty()
        )
    }

    @Test
    fun mixedStampsRevokeOnlyTheStaleOrigins() {
        val decisions = mapOf(
            "https://recent.example" to true,
            "https://stale.example" to false,
            "https://unstamped.example" to true
        )
        val stamps = mapOf(
            "https://recent.example" to (now - 2 * dayMs),
            "https://stale.example" to (now - 60 * dayMs)
        )
        assertEquals(
            setOf("https://stale.example"),
            permissionOriginsToRevoke(decisions, stamps, nowMs = now)
        )
    }

    @Test
    fun emptyDecisionsRevokeNothing() {
        val stamps = mapOf("https://example.com" to (now - 90 * dayMs))
        assertTrue(permissionOriginsToRevoke(emptyMap(), stamps, nowMs = now).isEmpty())
    }

    @Test
    fun portSpecificOriginUsesItsOwnStamp() {
        val decisions = mapOf("https://example.com:8443" to true)
        val stamps = mapOf("https://example.com:8443" to (now - 45 * dayMs))
        assertEquals(
            setOf("https://example.com:8443"),
            permissionOriginsToRevoke(decisions, stamps, nowMs = now)
        )
    }

    @Test
    fun visitStampRefreshThrottle() {
        // No stamp yet: always refresh so a first stamp gets recorded.
        assertTrue(shouldRefreshPermissionVisitStamp(null, now))
        // Stamp older than the 24h throttle: refresh.
        assertTrue(shouldRefreshPermissionVisitStamp(now - 25 * hourMs, now))
        // Fresh stamp: skip the write.
        assertFalse(shouldRefreshPermissionVisitStamp(now - hourMs, now))
        assertFalse(shouldRefreshPermissionVisitStamp(now, now))
    }
}
