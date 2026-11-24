package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class V9DohResolverTest {

    @Test
    fun everyProviderHasADistinctEndpoint() {
        val endpoints = listOf(
            V9DohResolver.PROVIDER_CLOUDFLARE,
            V9DohResolver.PROVIDER_GOOGLE,
            V9DohResolver.PROVIDER_QUAD9,
            V9DohResolver.PROVIDER_ADGUARD
        ).map { V9DohResolver.endpointUrl(it, "") }
        assertEquals(endpoints.size, endpoints.toSet().size)
    }

    @Test
    fun knownEndpoints() {
        assertEquals(
            "https://cloudflare-dns.com/dns-query",
            V9DohResolver.endpointUrl(V9DohResolver.PROVIDER_CLOUDFLARE, "")
        )
        assertEquals(
            "https://dns.google/resolve",
            V9DohResolver.endpointUrl(V9DohResolver.PROVIDER_GOOGLE, "")
        )
        assertEquals(
            "https://dns.quad9.net/dns-query",
            V9DohResolver.endpointUrl(V9DohResolver.PROVIDER_QUAD9, "")
        )
        assertEquals(
            "https://dns.adguard-dns.com/dns-query",
            V9DohResolver.endpointUrl(V9DohResolver.PROVIDER_ADGUARD, "")
        )
    }

    @Test
    fun customEndpointUsesUserUrlAndFallsBackToCloudflare() {
        assertEquals(
            "https://dns.example.net/dns-query",
            V9DohResolver.endpointUrl(V9DohResolver.PROVIDER_CUSTOM, "https://dns.example.net/dns-query")
        )
        assertEquals(
            "https://cloudflare-dns.com/dns-query",
            V9DohResolver.endpointUrl(V9DohResolver.PROVIDER_CUSTOM, "")
        )
    }

    @Test
    fun unknownProviderFallsBackToCloudflare() {
        assertEquals(
            "https://cloudflare-dns.com/dns-query",
            V9DohResolver.endpointUrl("something-else", "")
        )
        assertEquals("Cloudflare (1.1.1.1 DoH)", V9DohResolver.labelFor("something-else"))
    }

    @Test
    fun labelsNameEveryProvider() {
        assertEquals("Google (8.8.8.8 DoH)", V9DohResolver.labelFor(V9DohResolver.PROVIDER_GOOGLE))
        assertEquals("Quad9 (9.9.9.9 DoH)", V9DohResolver.labelFor(V9DohResolver.PROVIDER_QUAD9))
        assertEquals("AdGuard (94.140.14.14 DoH)", V9DohResolver.labelFor(V9DohResolver.PROVIDER_ADGUARD))
        assertEquals("Custom", V9DohResolver.labelFor(V9DohResolver.PROVIDER_CUSTOM))
    }
}
