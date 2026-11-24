package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V9BlocklistTest {

    @Test
    fun blocksKnownAdsAndTrackersBySuffix() {
        assertTrue(V9Blocklist.isBlocked("doubleclick.net"))
        assertTrue(V9Blocklist.isBlocked("stats.doubleclick.net"))
        assertTrue(V9Blocklist.isBlocked("www.google-analytics.com"))
        assertFalse(V9Blocklist.isBlocked("example.com"))
        assertFalse(V9Blocklist.isBlocked("notdoubleclick.net"))
    }

    @Test
    fun cryptominersAreCategorisedAsMalicious() {
        assertEquals("cryptominer", V9Blocklist.maliciousCategory("coinhive.com"))
        assertEquals("cryptominer", V9Blocklist.maliciousCategory("pool.coinhive.com"))
        assertEquals("cryptominer", V9Blocklist.maliciousCategory("MINERO.CC"))
        assertTrue(V9Blocklist.isBlocked("coinhive.com"))
    }

    @Test
    fun ordinaryAdDomainsHaveNoMaliciousCategory() {
        assertNull(V9Blocklist.maliciousCategory("doubleclick.net"))
        assertNull(V9Blocklist.maliciousCategory("google-analytics.com"))
        assertNull(V9Blocklist.maliciousCategory("example.com"))
    }

    @Test
    fun alertThrottleAllowsOncePerCooldownWindow() {
        val t0 = 1_000_000L
        assertTrue(SecurityNotifications.claimAlertSlot("test|a.example", t0))
        assertFalse(SecurityNotifications.claimAlertSlot("test|a.example", t0 + 60_000L))
        assertTrue(SecurityNotifications.claimAlertSlot("test|b.example", t0 + 60_000L))
        assertTrue(SecurityNotifications.claimAlertSlot("test|a.example", t0 + 16 * 60_000L))
    }
}
