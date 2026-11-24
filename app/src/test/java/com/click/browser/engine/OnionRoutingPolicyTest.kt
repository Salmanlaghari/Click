package com.click.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnionRoutingPolicyTest {

    @Test
    fun detectsOnionUrls() {
        assertTrue(OnionRoutingPolicy.isOnionUrl("https://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion/"))
        assertTrue(OnionRoutingPolicy.isOnionUrl("http://sub.deep.onion/path?q=1"))
        assertTrue(OnionRoutingPolicy.isOnionUrl("https://EXAMPLE.ONION/"))
    }

    @Test
    fun rejectsLookalikesAndOtherSchemes() {
        assertFalse(OnionRoutingPolicy.isOnionUrl("https://example.com/onion"))
        assertFalse(OnionRoutingPolicy.isOnionUrl("https://notonion.com/"))
        assertFalse(OnionRoutingPolicy.isOnionUrl("https://fakeonion.org/"))
        assertFalse(OnionRoutingPolicy.isOnionUrl("ftp://site.onion/file"))
        assertFalse(OnionRoutingPolicy.isOnionUrl("about:blank"))
    }

    @Test
    fun hostCheckHandlesNullsAndTrailingDots() {
        assertTrue(OnionRoutingPolicy.isOnionHost("abc.onion."))
        assertFalse(OnionRoutingPolicy.isOnionHost(null))
        assertFalse(OnionRoutingPolicy.isOnionHost(""))
        assertFalse(OnionRoutingPolicy.isOnionHost("onion.com"))
    }
}
