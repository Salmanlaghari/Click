package com.click.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAuthPolicyTest {
    @Test
    fun googleAccountAndOAuthEndpointsUseSupportedAuthContext() {
        assertTrue(GoogleAuthPolicy.shouldOpenInCustomTab("https://accounts.google.com/signin/v2/identifier"))
        assertTrue(GoogleAuthPolicy.shouldOpenInCustomTab("https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(GoogleAuthPolicy.shouldOpenInCustomTab("https://oauth2.google.com/o/oauth2/auth"))
        assertTrue(GoogleAuthPolicy.shouldOpenInCustomTab("https://accounts.youtube.com/ServiceLogin"))
    }

    @Test
    fun ordinaryGooglePagesRemainInTheCurrentBrowserTab() {
        assertFalse(GoogleAuthPolicy.shouldOpenInCustomTab("https://www.google.com/search?q=Click"))
        assertFalse(GoogleAuthPolicy.shouldOpenInCustomTab("https://google.com/maps"))
        assertFalse(GoogleAuthPolicy.shouldOpenInCustomTab("https://www.youtube.com/watch?v=example"))
    }

    @Test
    fun authHostChecksRejectLookalikeHostsAndNonWebSchemes() {
        assertFalse(GoogleAuthPolicy.shouldOpenInCustomTab("https://accounts.google.com.evil.example/signin"))
        assertFalse(GoogleAuthPolicy.shouldOpenInCustomTab("https://notaccounts.google.com/signin"))
        assertFalse(GoogleAuthPolicy.shouldOpenInCustomTab("https://accounts.google.com@evil.example/signin"))
        assertFalse(GoogleAuthPolicy.shouldOpenInCustomTab("intent://accounts.google.com/signin"))
    }

    @Test
    fun intentionalGoogleAuthHandoffIsNotReportedAsBlockedNavigation() {
        assertFalse(
            GoogleAuthPolicy.shouldShowBlockedNavigationNotice(
                "https://accounts.google.com/o/oauth2/v2/auth"
            )
        )
        assertTrue(
            GoogleAuthPolicy.shouldShowBlockedNavigationNotice(
                "https://untrusted.example/target"
            )
        )
    }
}
