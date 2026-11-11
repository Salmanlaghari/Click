package com.click.browser.engine.vpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnConfigBuilderTest {

    // The built-in Canada VPS (Oracle) server URL from VpnServer.
    private val canadaUrl =
        "vless://3eb438a4-d770-4235-bb9a-f78fd4a4a5b8@151.145.53.122:443" +
            "?encryption=none&flow=xtls-rprx-vision&security=reality" +
            "&sni=www.microsoft.com&fp=chrome" +
            "&pbk=YQQKXtSZJQVC5Ys7zo7y2KAl-fDY3ymR4nBsnuIe3So" +
            "&sid=c2d9535f83dd635b&type=tcp&headerType=none#Click-Canada-VPS"

    private fun builtInConfig(): JSONObject {
        val raw = VpnConfigBuilder.buildCustomConfig(canadaUrl)
        assertNotNull("built-in Canada URL must produce a config", raw)
        return JSONObject(raw!!)
    }

    @Test
    fun emittedDnsHasNoRemovedOutboundRuleItem() {
        // sing-box 1.14 removed the "outbound" DNS rule item: emitting it
        // makes the core reject the entire config. Resolution of outbound
        // server names is configured via route.default_domain_resolver.
        val dns = builtInConfig().getJSONObject("dns")
        if (dns.has("rules")) {
            val rules = dns.getJSONArray("rules")
            for (i in 0 until rules.length()) {
                assertFalse(
                    "DNS rules must not use the removed \"outbound\" item",
                    rules.getJSONObject(i).has("outbound")
                )
            }
        }
    }

    @Test
    fun routeDeclaresDefaultDomainResolverThroughTunnelDns() {
        val route = builtInConfig().getJSONObject("route")
        val resolver = route.getJSONObject("default_domain_resolver")
        assertEquals("cf-doh", resolver.getString("server"))
    }

    @Test
    fun routeSniffsViaRuleActionAndInboundHasNoLegacySniffField() {
        // sing-box 1.13 removed the legacy inbound "sniff" field: the TUN
        // inbound must not carry it, and sniffing must happen through the
        // route rule action instead (same all-traffic scope as before).
        val config = builtInConfig()
        val inbound = config.getJSONArray("inbounds").getJSONObject(0)
        assertFalse(
            "TUN inbound must not use the removed legacy \"sniff\" field",
            inbound.has("sniff")
        )
        val rules = config.getJSONObject("route").getJSONArray("rules")
        var hasSniffAction = false
        for (i in 0 until rules.length()) {
            if (rules.getJSONObject(i).optString("action") == "sniff") {
                hasSniffAction = true
            }
        }
        assertTrue("route rules must contain a sniff action", hasSniffAction)
    }

    @Test
    fun dnsStillDetoursThroughProxyWithCfFinal() {
        // Leak protection must survive the migration: both DoH servers
        // detour through the tunnel and cf-doh stays the final server.
        val dns = builtInConfig().getJSONObject("dns")
        assertEquals("cf-doh", dns.getString("final"))
        val servers = dns.getJSONArray("servers")
        assertEquals(2, servers.length())
        for (i in 0 until servers.length()) {
            assertEquals("proxy", servers.getJSONObject(i).getString("detour"))
        }
        assertTrue(dns.getString("strategy") == "prefer_ipv4")
    }
}
