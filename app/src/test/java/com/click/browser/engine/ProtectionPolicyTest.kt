package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.ContentBlocking

/**
 * Verifies the Protection level → GeckoView content-blocking mapping.
 * The ContentBlocking constants are compile-time ints, so these run on
 * the JVM without a Gecko runtime.
 */
class ProtectionPolicyTest {

    @Test
    fun `normalize falls back to standard`() {
        assertEquals("standard", ProtectionPolicy.normalizeLevel(null))
        assertEquals("standard", ProtectionPolicy.normalizeLevel("bogus"))
        assertEquals("off", ProtectionPolicy.normalizeLevel("OFF"))
        assertEquals("strong", ProtectionPolicy.normalizeLevel("Strong"))
    }

    @Test
    fun `off disables anti tracking and ETP`() {
        assertEquals(
            ContentBlocking.AntiTracking.NONE,
            ProtectionPolicy.antiTrackingCategories("off", fingerprinting = true)
        )
        assertEquals(ContentBlocking.EtpLevel.NONE, ProtectionPolicy.etpLevel("off"))
    }

    @Test
    fun `standard is GeckoView default plus fingerprinting when enabled`() {
        val expected = ContentBlocking.AntiTracking.DEFAULT or
            ContentBlocking.AntiTracking.FINGERPRINTING
        assertEquals(expected, ProtectionPolicy.antiTrackingCategories("standard", true))
        assertEquals(
            ContentBlocking.AntiTracking.DEFAULT,
            ProtectionPolicy.antiTrackingCategories("standard", false)
        )
        assertEquals(ContentBlocking.EtpLevel.DEFAULT, ProtectionPolicy.etpLevel("standard"))
        assertEquals(ContentBlocking.EtpCategory.STANDARD, ProtectionPolicy.etpCategory("standard"))
    }

    @Test
    fun `strong is strict set and honors fingerprinting off`() {
        assertEquals(
            ContentBlocking.AntiTracking.STRICT,
            ProtectionPolicy.antiTrackingCategories("strong", true)
        )
        val withoutFp = ProtectionPolicy.antiTrackingCategories("strong", false)
        assertEquals(
            ContentBlocking.AntiTracking.STRICT and ContentBlocking.AntiTracking.FINGERPRINTING.inv(),
            withoutFp
        )
        assertTrue(withoutFp and ContentBlocking.AntiTracking.CRYPTOMINING != 0)
        assertEquals(ContentBlocking.EtpLevel.STRICT, ProtectionPolicy.etpLevel("strong"))
        assertEquals(ContentBlocking.EtpCategory.STRICT, ProtectionPolicy.etpCategory("strong"))
    }

    @Test
    fun `cookie behavior maps the third-party toggle`() {
        assertEquals(
            ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY,
            ProtectionPolicy.cookieBehavior(true)
        )
        assertEquals(
            ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS,
            ProtectionPolicy.cookieBehavior(false)
        )
    }

    @Test
    fun `safe browsing categories toggle as a set`() {
        assertEquals(
            ContentBlocking.SafeBrowsing.DEFAULT,
            ProtectionPolicy.safeBrowsingCategories(true)
        )
        assertEquals(
            ContentBlocking.SafeBrowsing.NONE,
            ProtectionPolicy.safeBrowsingCategories(false)
        )
    }

    @Test
    fun `effective combinations with flags`() {
        assertTrue(ProtectionPolicy.shouldUseAggressiveFilterList("strong", flagsAggressive = false))
        assertTrue(ProtectionPolicy.shouldUseAggressiveFilterList("standard", flagsAggressive = true))
        assertFalse(ProtectionPolicy.shouldUseAggressiveFilterList("standard", flagsAggressive = false))

        assertTrue(ProtectionPolicy.effectiveBlockThirdPartyCookies(true, flagsValue = false))
        assertTrue(ProtectionPolicy.effectiveBlockThirdPartyCookies(false, flagsValue = true))
        assertFalse(ProtectionPolicy.effectiveBlockThirdPartyCookies(false, flagsValue = false))

        assertTrue(ProtectionPolicy.effectiveSafeBrowsing(true, flagsValue = true))
        assertFalse(ProtectionPolicy.effectiveSafeBrowsing(true, flagsValue = false))
        assertFalse(ProtectionPolicy.effectiveSafeBrowsing(false, flagsValue = true))
    }
}
