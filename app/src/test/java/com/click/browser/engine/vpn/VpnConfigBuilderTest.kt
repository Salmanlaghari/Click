package com.click.browser.engine.vpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    private fun vlessOutbound(url: String): JSONObject {
        val raw = VpnConfigBuilder.buildCustomConfig(url)
        assertNotNull("link must produce a config: $url", raw)
        val outbounds = JSONObject(raw!!).getJSONArray("outbounds")
        for (i in 0 until outbounds.length()) {
            val o = outbounds.getJSONObject(i)
            if (o.optString("type") == "vless") return o
        }
        fail("no vless outbound in config")
        throw AssertionError("unreachable")
    }

    @Test
    fun canadaRealityOutboundKeepsItsShape() {
        // The built-in preset must survive the VlessLink refactor
        // byte-for-byte in every field that matters.
        val o = vlessOutbound(canadaUrl)
        assertEquals("151.145.53.122", o.getString("server"))
        assertEquals(443, o.getInt("server_port"))
        assertEquals("3eb438a4-d770-4235-bb9a-f78fd4a4a5b8", o.getString("uuid"))
        assertEquals("xtls-rprx-vision", o.getString("flow"))
        assertFalse(o.has("transport"))
        assertFalse(o.has("encryption"))
        val tls = o.getJSONObject("tls")
        assertEquals("www.microsoft.com", tls.getString("server_name"))
        assertEquals("chrome", tls.getJSONObject("utls").getString("fingerprint"))
        val reality = tls.getJSONObject("reality")
        assertEquals(
            "YQQKXtSZJQVC5Ys7zo7y2KAl-fDY3ymR4nBsnuIe3So",
            reality.getString("public_key")
        )
        assertEquals("c2d9535f83dd635b", reality.getString("short_id"))
    }

    @Test
    fun grpcLinkEmitsServiceNameTransport() {
        // Regression: the old parser emitted ws-style path/headers for
        // grpc links; sing-box gRPC transport takes service_name.
        val o = vlessOutbound(
            "vless://aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee@grpc.example.com:443" +
                "?security=tls&type=grpc&serviceName=gun&sni=grpc.example.com"
        )
        val transport = o.getJSONObject("transport")
        assertEquals("grpc", transport.getString("type"))
        assertEquals("gun", transport.getString("service_name"))
        assertFalse(transport.has("path"))
        assertFalse(transport.has("headers"))
    }

    @Test
    fun wsLinkEmitsPathAndHostHeader() {
        val o = vlessOutbound(
            "vless://uuid-1@ws.example.com:8443?security=tls&type=ws" +
                "&path=%2Fchat&host=cdn.example.com&sni=ws.example.com"
        )
        val transport = o.getJSONObject("transport")
        assertEquals("ws", transport.getString("type"))
        assertEquals("/chat", transport.getString("path"))
        assertEquals("cdn.example.com", transport.getJSONObject("headers").getString("Host"))
        assertEquals(8443, o.getInt("server_port"))
    }
}
