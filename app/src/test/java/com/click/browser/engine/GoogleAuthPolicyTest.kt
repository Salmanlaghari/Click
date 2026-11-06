package com.click.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAuthPolicyTest {
    @Test
    fun documentedOAuthAuthorizationEndpointsUseCustomTab() {
        assertTrue(
            GoogleAuthPolicy.shouldOpenInCustomTab(
                "https://accounts.google.com/o/oauth2/v2/auth" +
                    "?client_id=client-123&redirect_uri=https%3A%2F%2Fexample.com%2Foauth%2Fcallback" +
                    "&response_type=code&scope=openid"
            )
        )
        assertTrue(
            GoogleAuthPolicy.shouldOpenInCustomTab(
                "https://accounts.google.com/o/oauth2/auth" +
                    "?client_id=client-123&redirect_uri=https%3A%2F%2Fexample.com%2Foauth%2Fcallback" +
                    "&response_type=token"
            )
        )
    }

    @Test
    fun ordinaryGoogleAccountAndProductPagesStayInTheCurrentTab() {
        assertFalse(
            GoogleAuthPolicy.shouldOpenInCustomTab(
                "https://accounts.google.com/signin/v2/identifier?service=mail"
            )
        )
        assertFalse(GoogleAuthPolicy.shouldOpenInCustomTab("https://accounts.google.com/ServiceLogin"))
        assertFalse(GoogleAuthPolicy.shouldOpenInCustomTab("https://accounts.google.com/"))
        assertFalse(
            GoogleAuthPolicy.shouldOpenInCustomTab("https://accounts.youtube.com/ServiceLogin")
        )
        assertFalse(GoogleAuthPolicy.shouldOpenInCustomTab("https://www.google.com/search?q=Click"))
        assertFalse(GoogleAuthPolicy.shouldOpenInCustomTab("https://www.youtube.com/watch?v=example"))
    }

    @Test
    fun incompleteOAuthUrlsAndNonAuthorizationEndpointsStayInTheCurrentTab() {
        assertFalse(
            GoogleAuthPolicy.shouldOpenInCustomTab(
                "https://accounts.google.com/o/oauth2/v2/auth"
            )
        )
        assertFalse(
            GoogleAuthPolicy.shouldOpenInCustomTab(
                "https://accounts.google.com/o/oauth2/v2/auth?client_id=id&response_type=code"
            )
        )
        assertFalse(
            GoogleAuthPolicy.shouldOpenInCustomTab(
                "https://accounts.google.com/o/oauth2/approval?client_id=id" +
                    "&redirect_uri=https%3A%2F%2Fexample.com%2Fcb&response_type=code"
            )
        )
        assertFalse(
            GoogleAuthPolicy.shouldOpenInCustomTab(
                "https://oauth2.google.com/o/oauth2/auth?client_id=id" +
                    "&redirect_uri=https%3A%2F%2Fexample.com%2Fcb&response_type=code"
            )
        )
    }

    @Test
    fun endpointHostAndSecureSchemeAreStrictlyValidated() {
        val query = "?client_id=id&redirect_uri=https%3A%2F%2Fexample.com%2Fcb&response_type=code"
        assertFalse(
            GoogleAuthPolicy.shouldOpenInCustomTab(
                "https://accounts.google.com.evil.example/o/oauth2/v2/auth$query"
            )
        )
        assertFalse(
            GoogleAuthPolicy.shouldOpenInCustomTab(
                "https://accounts.google.com@evil.example/o/oauth2/v2/auth$query"
            )
        )
        assertFalse(
            GoogleAuthPolicy.shouldOpenInCustomTab(
                "http://accounts.google.com/o/oauth2/v2/auth$query"
            )
        )
        assertFalse(
            GoogleAuthPolicy.shouldOpenInCustomTab(
                "intent://accounts.google.com/o/oauth2/v2/auth$query"
            )
        )
    }

    @Test
    fun onlyAnOAuthAuthorizationHandoffSuppressesBlockedNavigationNotice() {
        assertFalse(
            GoogleAuthPolicy.shouldShowBlockedNavigationNotice(
                "https://accounts.google.com/o/oauth2/v2/auth" +
                    "?client_id=id&redirect_uri=https%3A%2F%2Fexample.com%2Fcb&response_type=code"
            )
        )
        assertTrue(
            GoogleAuthPolicy.shouldShowBlockedNavigationNotice(
                "https://accounts.google.com/signin/v2/identifier"
            )
        )
    }
}
