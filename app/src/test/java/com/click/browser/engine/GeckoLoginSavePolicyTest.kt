package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoLoginSavePolicyTest {
    @Test
    fun keepsTheBrowserReportedLoginOriginAcrossRedirects() {
        val candidate = geckoLoginSaveCandidate(
            origin = "https://Accounts.Google.com:443",
            username = "prince@example.com",
            password = "example-test-password"
        )
        assertEquals("https://accounts.google.com", candidate?.origin)
        assertEquals("accounts.google.com", candidate?.host)
    }

    @Test
    fun rejectsInsecureMalformedAndOversizedCredentials() {
        assertNull(geckoLoginSaveCandidate("http://example.com", "user", "pass"))
        assertNull(geckoLoginSaveCandidate("https://user@example.com", "user", "pass"))
        assertNull(geckoLoginSaveCandidate("https://example.com/login", "user", "pass"))
        assertNull(geckoLoginSaveCandidate("https://example.com", "user", " "))
        assertNull(geckoLoginSaveCandidate("https://example.com", "user", "p".repeat(4_097)))
    }

    @Test
    fun requiresEnabledManagerAndAnActiveRegularGeckoTab() {
        val candidate = geckoLoginSaveCandidate("https://example.com", "user", "pass")
        assertTrue(shouldOfferGeckoLoginSave(true, true, candidate))
        assertFalse(shouldOfferGeckoLoginSave(false, true, candidate))
        assertFalse(shouldOfferGeckoLoginSave(true, false, candidate))
        assertFalse(shouldOfferGeckoLoginSave(true, true, null))
    }
}
