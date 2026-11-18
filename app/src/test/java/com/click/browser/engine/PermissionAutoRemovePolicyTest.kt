package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionAutoRemovePolicyTest {

    private val now = 1_800_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000

    @Test
    fun revokesDecisionsForHostsNotVisitedWithinWindow() {
        val decisions = mapOf(
            "https://recent.example" to true,
            "https://stale.example" to false,
            "https://never-visited.example" to true
        )
        val visits = listOf(
            "recent.example" to (now - dayMs),
            "stale.example" to (now - 45 * dayMs),
            "other.example" to (now - dayMs)
        )
        assertEquals(
            setOf("https://stale.example", "https://never-visited.example"),
            permissionOriginsToRevoke(decisions, visits, now)
        )
    }

    @Test
    fun keepsDecisionsInsideTheWindowBoundary() {
        val decisions = mapOf("https://edge.example" to true)
        val visits = listOf("edge.example" to (now - PERMISSION_AUTO_REMOVE_WINDOW_MS))
        assertTrue(permissionOriginsToRevoke(decisions, visits, now).isEmpty())
    }

    @Test
    fun emptyHistoryRevokesNothing() {
        val decisions = mapOf("https://anything.example" to true)
        assertTrue(permissionOriginsToRevoke(decisions, emptyList(), now).isEmpty())
    }

    @Test
    fun emptyDecisionsRevokeNothing() {
        val visits = listOf("example.com" to now)
        assertTrue(permissionOriginsToRevoke(emptyMap(), visits, now).isEmpty())
    }

    @Test
    fun portSpecificOriginsShareTheirHostVisit() {
        val decisions = mapOf("https://example.com:8443" to true)
        val visits = listOf("example.com" to (now - dayMs))
        assertTrue(permissionOriginsToRevoke(decisions, visits, now).isEmpty())
    }

    @Test
    fun anyRecentVisitToTheHostKeepsTheDecision() {
        val decisions = mapOf("https://mixed.example" to false)
        val visits = listOf(
            "mixed.example" to (now - 90 * dayMs),
            "mixed.example" to (now - 2 * dayMs)
        )
        assertTrue(permissionOriginsToRevoke(decisions, visits, now).isEmpty())
    }
}
