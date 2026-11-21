package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalSchemePolicyTest {

    // ---- kindOf: web loads stay web loads ----

    @Test
    fun httpAndHttpsAreWeb() {
        assertEquals(
            NavigationSchemeKind.WEB,
            ExternalSchemePolicy.kindOf("https://www.tiktok.com/login")
        )
        assertEquals(
            NavigationSchemeKind.WEB,
            ExternalSchemePolicy.kindOf("http://example.com/page")
        )
    }

    @Test
    fun aboutBlankIsWebButOtherAboutPagesAreNot() {
        assertEquals(
            NavigationSchemeKind.WEB,
            ExternalSchemePolicy.kindOf("about:blank")
        )
        assertEquals(
            NavigationSchemeKind.OTHER,
            ExternalSchemePolicy.kindOf("about:config")
        )
    }

    // ---- kindOf: Click's own destinations stay internal ----

    @Test
    fun clickPagesAndExtensionPagesAreInternal() {
        assertEquals(
            NavigationSchemeKind.INTERNAL,
            ExternalSchemePolicy.kindOf("click://flags")
        )
        assertEquals(
            NavigationSchemeKind.INTERNAL,
            ExternalSchemePolicy.kindOf("moz-extension://abc123/options.html")
        )
    }

    // ---- kindOf: app destinations ----

    @Test
    fun appSchemesAreExternal() {
        val urls = listOf(
            "tiktok://user/@someone",
            "snssdk1128://aweme/detail/123",
            "intent://scan/#Intent;scheme=zxing;package=com.example;end",
            "tel:+923001234567",
            "mailto:someone@example.com",
            "sms:+923001234567",
            "whatsapp://send?text=hello",
            "tg://resolve?domain=example",
            "market://details?id=com.example.app"
        )
        for (url in urls) {
            assertEquals(url, NavigationSchemeKind.EXTERNAL_APP, ExternalSchemePolicy.kindOf(url))
        }
    }

    @Test
    fun schemeMatchingIsCaseInsensitive() {
        assertEquals(
            NavigationSchemeKind.EXTERNAL_APP,
            ExternalSchemePolicy.kindOf("TikTok://user/@someone")
        )
        assertEquals(
            NavigationSchemeKind.WEB,
            ExternalSchemePolicy.kindOf("HTTPS://example.com/")
        )
    }

    // ---- kindOf: schemes that must never be handed off ----

    @Test
    fun dangerousSchemesAreNeverExternal() {
        val urls = listOf(
            "file:///sdcard/secret.txt",
            "content://com.example.provider/data",
            "javascript:alert(1)",
            "data:text/html,<b>hi</b>",
            "blob:https://example.com/9b1deb4d",
            "view-source:https://example.com/",
            "android-app://com.example/https/example.com/"
        )
        for (url in urls) {
            assertEquals(url, NavigationSchemeKind.OTHER, ExternalSchemePolicy.kindOf(url))
        }
    }

    @Test
    fun unparseableAndSchemelessUrlsAreOther() {
        assertEquals(NavigationSchemeKind.OTHER, ExternalSchemePolicy.kindOf(""))
        assertEquals(NavigationSchemeKind.OTHER, ExternalSchemePolicy.kindOf("not a url"))
        assertEquals(NavigationSchemeKind.OTHER, ExternalSchemePolicy.kindOf("example.com/path"))
        assertEquals(NavigationSchemeKind.OTHER, ExternalSchemePolicy.kindOf("://missing-scheme"))
    }

    // ---- redirect pass-through (Fix B) ----

    @Test
    fun onlyWebHopsPassThroughOnRedirect() {
        assertTrue(ExternalSchemePolicy.shouldLetRedirectPassThrough("https://m.facebook.com/login"))
        assertTrue(ExternalSchemePolicy.shouldLetRedirectPassThrough("http://m.facebook.com/login"))
        assertFalse(ExternalSchemePolicy.shouldLetRedirectPassThrough("intent://x#Intent;end"))
        assertFalse(ExternalSchemePolicy.shouldLetRedirectPassThrough("click://flags"))
        assertFalse(ExternalSchemePolicy.shouldLetRedirectPassThrough("file:///etc/passwd"))
    }

    // ---- intent URI parsing ----

    @Test
    fun isIntentUriDetectsIntentSchemeOnly() {
        assertTrue(ExternalSchemePolicy.isIntentUri("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(ExternalSchemePolicy.isIntentUri("https://example.com/"))
        assertFalse(ExternalSchemePolicy.isIntentUri("tiktok://user/@x"))
    }

    @Test
    fun fallbackUrlIsReadFromIntentFragment() {
        val uri = "intent://www.tiktok.com/login#Intent;scheme=https;" +
            "package=com.zhiliaoapp.musically;" +
            "S.browser_fallback_url=https%3A%2F%2Fwww.tiktok.com%2Flogin;end"
        assertEquals("https://www.tiktok.com/login", ExternalSchemePolicy.fallbackUrlOf(uri))
    }

    @Test
    fun fallbackUrlIsNullWhenAbsentOrNotIntent() {
        assertNull(
            ExternalSchemePolicy.fallbackUrlOf("intent://scan/#Intent;scheme=zxing;end")
        )
        assertNull(ExternalSchemePolicy.fallbackUrlOf("https://example.com/"))
        assertNull(ExternalSchemePolicy.fallbackUrlOf("tiktok://user/@x"))
    }

    @Test
    fun fallbackUrlAcceptsPlainHttpToo() {
        val uri = "intent://example.com/x#Intent;scheme=https;" +
            "S.browser_fallback_url=http%3A%2F%2Fexample.com%2Fx;end"
        assertEquals("http://example.com/x", ExternalSchemePolicy.fallbackUrlOf(uri))
    }

    @Test
    fun fallbackUrlRejectsNonWebDestinations() {
        // The fallback is loaded in the tab, so a page-controlled value
        // naming anything but a web page must never come back.
        val badFallbacks = listOf(
            "javascript%3Aalert(1)",
            "file%3A%2F%2F%2Fsdcard%2Fsecret.txt",
            "click%3A%2F%2Fflags",
            "intent%3A%2F%2Fscan%2F%23Intent%3Bscheme%3Dzxing%3Bend",
            "data%3Atext%2Fhtml%2C%3Cb%3Ehi%3C%2Fb%3E"
        )
        for (bad in badFallbacks) {
            val uri = "intent://example.com/#Intent;scheme=https;" +
                "S.browser_fallback_url=$bad;end"
            assertNull(bad, ExternalSchemePolicy.fallbackUrlOf(uri))
        }
    }

    @Test
    fun targetPackageAndWrappedSchemeAreReadFromIntentFragment() {
        val uri = "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end"
        assertEquals("com.google.zxing.client.android", ExternalSchemePolicy.targetPackageOf(uri))
        assertEquals("zxing", ExternalSchemePolicy.wrappedSchemeOf(uri))
    }

    @Test
    fun wrappedSchemeFallsBackToOwnSchemeForPlainAppLinks() {
        assertEquals("tiktok", ExternalSchemePolicy.wrappedSchemeOf("tiktok://user/@x"))
        assertNull(ExternalSchemePolicy.targetPackageOf("tiktok://user/@x"))
    }

    // ---- gesture rule ----

    @Test
    fun gestureLaunchesImmediatelyWithoutGestureAsksFirst() {
        assertEquals(
            ExternalLaunchDecision.LAUNCH_NOW,
            ExternalSchemePolicy.launchDecision(hasUserGesture = true)
        )
        assertEquals(
            ExternalLaunchDecision.ASK_FIRST,
            ExternalSchemePolicy.launchDecision(hasUserGesture = false)
        )
    }
}
