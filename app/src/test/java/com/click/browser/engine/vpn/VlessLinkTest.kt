package com.click.browser.engine.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B4: the vless:// share-link parser must handle every common variant a
 * user might paste in (HA Tunnel / v2rayNG style links) and reject links
 * that can never connect instead of saving them.
 */
class VlessLinkTest {

    @Test
    fun parsesTcpRealityVisionLink() {
        val link = VlessLink.parse(
            "vless://3eb438a4-d770-4235-bb9a-f78fd4a4a5b8@151.145.53.122:443" +
                "?encryption=none&flow=xtls-rprx-vision&security=reality" +
                "&sni=www.microsoft.com&fp=chrome" +
                "&pbk=YQQKXtSZJQVC5Ys7zo7y2KAl-fDY3ymR4nBsnuIe3So&sid=c2d9535f83dd635b" +
                "&type=tcp&headerType=none#Click-Canada-VPS"
        )!!
        assertEquals("3eb438a4-d770-4235-bb9a-f78fd4a4a5b8", link.uuid)
        assertEquals("151.145.53.122", link.host)
        assertEquals(443, link.port)
        assertEquals("xtls-rprx-vision", link.flow)
        assertEquals("tcp", link.network)
        assertEquals("reality", link.security)
        assertEquals("www.microsoft.com", link.sni)
        assertEquals("chrome", link.fingerprint)
        assertEquals("YQQKXtSZJQVC5Ys7zo7y2KAl-fDY3ymR4nBsnuIe3So", link.publicKey)
        assertEquals("c2d9535f83dd635b", link.shortId)
        assertEquals("Click-Canada-VPS", link.name)
    }

    @Test
    fun parsesWsTlsLinkWithEncodedPathAndHost() {
        val link = VlessLink.parse(
            "vless://11111111-2222-3333-4444-555555555555@ws.example.com:8443" +
                "?security=tls&type=ws&path=%2Fchat&host=cdn.example.com&sni=ws.example.com#My%20WS"
        )!!
        assertEquals("ws.example.com", link.host)
        assertEquals(8443, link.port)
        assertEquals("ws", link.network)
        assertEquals("tls", link.security)
        assertEquals("/chat", link.path)
        assertEquals("cdn.example.com", link.hostHeader)
        assertEquals("ws.example.com", link.sni)
        assertEquals("My WS", link.name)
    }

    @Test
    fun parsesGrpcTlsLinkWithServiceName() {
        val link = VlessLink.parse(
            "vless://aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee@grpc.example.com:443" +
                "?security=tls&type=grpc&serviceName=gun&sni=grpc.example.com#Grpc"
        )!!
        assertEquals("grpc", link.network)
        assertEquals("tls", link.security)
        assertEquals("gun", link.serviceName)
    }

    @Test
    fun parsesPlainTcpLinkWithDefaults() {
        val link = VlessLink.parse("vless://uuid-1@plain.example.com:8080#Plain")!!
        assertEquals(8080, link.port)
        assertEquals("tcp", link.network)
        assertEquals("none", link.security)
        assertEquals("none", link.encryption)
        assertEquals("", link.flow)
    }

    @Test
    fun missingPortDefaultsTo443() {
        val link = VlessLink.parse("vless://uuid-1@host.example.com?security=tls&type=ws")!!
        assertEquals(443, link.port)
    }

    @Test
    fun roundTripsThroughShareLink() {
        val original = VlessLink(
            uuid = "uuid-9",
            host = "node.example.com",
            port = 8443,
            name = "Round Trip",
            flow = "",
            network = "ws",
            security = "tls",
            sni = "node.example.com",
            fingerprint = "firefox",
            path = "/ws-path",
            hostHeader = "cdn.example.com"
        )
        assertEquals(original, VlessLink.parse(original.toShareLink()))
    }

    @Test
    fun rejectsLinksThatCanNeverConnect() {
        // Not a vless link at all.
        assertNull(VlessLink.parse("https://example.com"))
        // Missing UUID.
        assertNull(VlessLink.parse("vless://@host.example.com:443?security=tls"))
        // Missing host.
        assertNull(VlessLink.parse("vless://uuid-1@:443?security=tls"))
        // Bad port.
        assertNull(VlessLink.parse("vless://uuid-1@host.example.com:99999?security=tls"))
        // Unknown transport (kcp is not wired into the config builder).
        assertNull(VlessLink.parse("vless://uuid-1@host.example.com:443?type=kcp"))
        // Unknown security layer.
        assertNull(VlessLink.parse("vless://uuid-1@host.example.com:443?security=xtls"))
    }

    @Test
    fun summaryShowsHostPortTransportAndSecurity() {
        val link = VlessLink.parse(
            "vless://uuid-1@ws.example.com:8443?security=tls&type=ws"
        )!!
        assertEquals("ws.example.com:8443 · ws · tls", link.summary())
        assertFalse(link.allowInsecure)
    }
}
