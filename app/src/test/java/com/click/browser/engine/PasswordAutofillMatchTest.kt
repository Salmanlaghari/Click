package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The GeckoView autofill fetch must only ever return records whose stored
 * host is exactly the requested domain — anything looser would leak a saved
 * login into a different site.
 */
class PasswordAutofillMatchTest {

    private fun saved(host: String, username: String = "user") =
        SavedPassword(host = host, username = username, password = "secret")

    @Test
    fun exactHostMatches() {
        val logins = listOf(saved("example.com"), saved("other.com"))
        val result = savedPasswordsForExactHost(logins, "example.com")
        assertEquals(1, result.size)
        assertEquals("example.com", result.single().host)
    }

    @Test
    fun hostMatchIsCaseInsensitive() {
        val logins = listOf(saved("Example.COM"))
        assertEquals(1, savedPasswordsForExactHost(logins, "example.com").size)
        assertEquals(1, savedPasswordsForExactHost(logins, "EXAMPLE.COM").size)
    }

    @Test
    fun subdomainDoesNotMatchParentRecord() {
        val logins = listOf(saved("example.com"))
        assertTrue(savedPasswordsForExactHost(logins, "login.example.com").isEmpty())
    }

    @Test
    fun parentDomainDoesNotMatchSubdomainRecord() {
        val logins = listOf(saved("login.example.com"))
        assertTrue(savedPasswordsForExactHost(logins, "example.com").isEmpty())
    }

    @Test
    fun lookAlikeHostDoesNotMatch() {
        val logins = listOf(saved("example.com"))
        assertTrue(savedPasswordsForExactHost(logins, "example.com.evil.test").isEmpty())
        assertTrue(savedPasswordsForExactHost(logins, "notexample.com").isEmpty())
    }

    @Test
    fun blankDomainMatchesNothing() {
        val logins = listOf(saved("example.com"))
        assertTrue(savedPasswordsForExactHost(logins, "").isEmpty())
        assertTrue(savedPasswordsForExactHost(logins, "   ").isEmpty())
    }

    @Test
    fun allUsernamesForTheHostAreReturned() {
        val logins = listOf(
            saved("example.com", "alice"),
            saved("example.com", "bob"),
            saved("other.com", "carol")
        )
        val result = savedPasswordsForExactHost(logins, "example.com")
        assertEquals(listOf("alice", "bob"), result.map { it.username })
    }

    @Test
    fun matchedRecordKeepsItsStoredOrigin() {
        // A record saved for a non-default port keeps that origin, so
        // Gecko's own origin check — not just the host filter — decides
        // whether it may fill a given page.
        val logins = listOf(
            SavedPassword(
                host = "example.com",
                username = "user",
                password = "secret",
                origin = "https://example.com:8443"
            )
        )
        val result = savedPasswordsForExactHost(logins, "example.com")
        assertEquals("https://example.com:8443", result.single().origin)
    }
}
