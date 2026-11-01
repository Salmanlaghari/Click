package com.click.browser.engine

import androidx.datastore.preferences.core.emptyPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsResolutionPolicyTest {
    @Test
    fun strictDnsDefaultsToOffForExistingUsers() {
        assertFalse(AppSettings.strictDnsEnabled(emptyPreferences()))
    }

    @Test
    fun failedDohFallsBackToPlainDnsWhenStrictDnsIsOff() {
        var fallbackCalled = false
        val result = DnsResolutionPolicy.resolve(
            dohSucceeded = false,
            strictDns = false,
            onDoh = { "DoH" },
            onPlainDnsFallback = { fallbackCalled = true; "plain DNS" },
            onBlocked = { "blocked" },
        )

        assertEquals("plain DNS", result)
        assertTrue(fallbackCalled)
    }

    @Test
    fun failedDohDoesNotFallBackWhenStrictDnsIsOn() {
        var fallbackCalled = false
        val result = DnsResolutionPolicy.resolve(
            dohSucceeded = false,
            strictDns = true,
            onDoh = { "DoH" },
            onPlainDnsFallback = { fallbackCalled = true; "plain DNS" },
            onBlocked = { "SERVFAIL" },
        )

        assertEquals("SERVFAIL", result)
        assertFalse(fallbackCalled)
    }

    @Test
    fun successfulDohStillWorksWhenStrictDnsIsOn() {
        var fallbackCalled = false
        val result = DnsResolutionPolicy.resolve(
            dohSucceeded = true,
            strictDns = true,
            onDoh = { "encrypted answer" },
            onPlainDnsFallback = { fallbackCalled = true; "plain DNS" },
            onBlocked = { "SERVFAIL" },
        )

        assertEquals("encrypted answer", result)
        assertFalse(fallbackCalled)
    }
}
