package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordOriginPolicyTest {
    @Test
    fun acceptsHttpsTopFrameAndCanonicalizesHost() {
        assertEquals("accounts.example.com", PasswordOriginPolicy.secureHost("https://Accounts.Example.com", true))
        assertEquals("accounts.example.com", PasswordOriginPolicy.secureHost("https://accounts.example.com:443/", true))
        assertEquals("accounts.example.com", PasswordOriginPolicy.securePageHost("https://accounts.example.com/login?next=%2Fhome"))
    }

    @Test
    fun rejectsHttpSubframesAndNonOriginValues() {
        assertNull(PasswordOriginPolicy.secureHost("http://accounts.example.com", true))
        assertNull(PasswordOriginPolicy.secureHost("https://accounts.example.com", false))
        assertNull(PasswordOriginPolicy.secureHost("null", true))
        assertNull(PasswordOriginPolicy.secureHost("https://accounts.example.com/login", true))
        assertNull(PasswordOriginPolicy.secureHost("https://accounts.example.com?next=/", true))
        assertNull(PasswordOriginPolicy.securePageHost("http://accounts.example.com/login"))
    }

    @Test
    fun rejectsCredentialBearingOrMalformedAuthorities() {
        assertNull(PasswordOriginPolicy.secureHost("https://attacker@example.com", true))
        assertNull(PasswordOriginPolicy.secureHost("https:///missing-host", true))
        assertNull(PasswordOriginPolicy.secureHost("https://bad host.example", true))
    }

    @Test
    fun saveOfferMustStillMatchTheCurrentSecureOrigin() {
        assertTrue(
            PasswordOriginPolicy.isSameSecureOrigin(
                "https://accounts.example.com",
                "https://accounts.example.com/sign-in?next=%2Fhome"
            )
        )
        assertTrue(
            PasswordOriginPolicy.isSameSecureOrigin(
                "https://accounts.example.com:443",
                "https://accounts.example.com/"
            )
        )
        assertFalse(
            PasswordOriginPolicy.isSameSecureOrigin(
                "https://accounts.example.com",
                "https://evil.example.com/sign-in"
            )
        )
        assertFalse(
            PasswordOriginPolicy.isSameSecureOrigin(
                "https://accounts.example.com:8443",
                "https://accounts.example.com/sign-in"
            )
        )
        assertFalse(
            PasswordOriginPolicy.isSameSecureOrigin(
                "https://accounts.example.com",
                "http://accounts.example.com/sign-in"
            )
        )
    }

    @Test
    fun detectorRequiresTrustedTopFrameSubmitAndDoesNotTrustPageHostname() {
        val script = PasswordDetector.PASSWORD_DETECT_JS
        assertTrue(script.contains("!event.isTrusted"))
        assertTrue(script.contains("window.top !== window"))
        assertTrue(script.contains("window.PasswordBridge.postMessage"))
        assertFalse(script.contains("window.location.hostname"))
    }
}
