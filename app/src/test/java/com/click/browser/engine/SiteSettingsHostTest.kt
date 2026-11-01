package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteSettingsHostTest {
    @Test
    fun exactHostOverridesParentDomain() {
        val parent = SiteSettings(javaScript = false)
        val exact = SiteSettings(javaScript = true)
        val saved = mapOf("example.com" to parent, "shop.example.com" to exact)

        assertEquals(exact, saved.forHost("shop.example.com"))
    }

    @Test
    fun mostSpecificParentDomainWins() {
        val broad = SiteSettings(javaScript = false)
        val specific = SiteSettings(adBlock = false)
        val saved = mapOf("example.com" to broad, "sub.example.com" to specific)

        assertEquals(specific, saved.forHost("news.sub.example.com"))
    }

    @Test
    fun unrelatedHostDoesNotMatchByTextualSuffix() {
        val saved = mapOf("example.com" to SiteSettings(javaScript = false))

        assertNull(saved.forHost("notexample.com"))
    }
}
