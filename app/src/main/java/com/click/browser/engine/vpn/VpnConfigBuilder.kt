package com.click.browser.engine.vpn

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Builds sing-box JSON configs for the Click VPN tunnel.
 *
 * ## DNS LEAK PROTECTION (critical)
 * Every config produced here routes DNS **through the tunnel**:
 * - The `dns.servers` entries use `"detour": "proxy"`, so DoH queries to
 *   1.1.1.1 / 8.8.8.8 travel INSIDE the encrypted tunnel, never on the raw
 *   network.
 * - A route rule sends all `protocol: dns` traffic to the `dns-out` outbound.
 * - The TUN inbound sets `auto_route: true`, capturing ALL device traffic
 *   (0.0.0.0/0 + ::/0), unlike V9 Shield's DNS-only design.
 *
 * Verify with the in-app "DNS Leak Test" button (opens ipleak.net).
 */
object VpnConfigBuilder {

    private const val TAG = "VpnConfig"

    /**
     * Builds a full tunnel config around a WireGuard [WarpCredentials].
     * Used for Cloudflare WARP.
     */
    fun buildWarpConfig(creds: WarpCredentials): String {
        val proxyOutbound = JSONObject()
            .put("type", "wireguard")
            .put("tag", "proxy")
            .put("server", creds.endpointHost)
            .put("server_port", creds.endpointPort)
            .put("local_address", JSONArray(creds.localAddresses))
            .put("private_key", creds.privateKey)
            .put("peer_public_key", creds.peerPublicKey)
            .put("mtu", 1280)
            // WARP reserved bytes (from registration response).
            .put("reserved", JSONArray(creds.reserved))
        return wrapWithTunnel(proxyOutbound)
    }

    /**
     * Builds a tunnel config from a user-pasted proxy URL.
     * Supports: vless://, vmess://, trojan://, ss:// (Shadowsocks).
     * Returns null if the URL is not recognized.
     */
    fun buildCustomConfig(proxyUrl: String): String? {
        val outbound = when {
            proxyUrl.startsWith("vless://", ignoreCase = true) -> parseVless(proxyUrl)
            proxyUrl.startsWith("vmess://", ignoreCase = true) -> parseVmess(proxyUrl)
            proxyUrl.startsWith("trojan://", ignoreCase = true) -> parseTrojan(proxyUrl)
            proxyUrl.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(proxyUrl)
            else -> null
        } ?: run {
            Log.w(TAG, "Unrecognized proxy URL scheme")
            return null
        }
        return wrapWithTunnel(outbound)
    }

    // ---------- tunnel wrapper ----------

    /**
     * Wraps a proxy [outbound] with: TUN inbound (full route), DNS-through-
     * tunnel, and standard direct/block outbounds.
     */
    private fun wrapWithTunnel(outbound: JSONObject): String {
        outbound.put("tag", "proxy")

        val config = JSONObject()

        // Log (quiet by default; libbox forwards to our PlatformInterface).
        config.put("log", JSONObject().put("level", "info"))

        // DNS: everything through the tunnel via DoH.
        config.put("dns", JSONObject()
            .put("servers", JSONArray()
                .put(JSONObject()
                    .put("tag", "cf-doh")
                    .put("address", "https://1.1.1.1/dns-query")
                    .put("detour", "proxy"))
                .put(JSONObject()
                    .put("tag", "google-doh")
                    .put("address", "https://8.8.8.8/dns-query")
                    .put("detour", "proxy")))
            .put("rules", JSONArray()
                .put(JSONObject()
                    .put("outbound", "any")
                    .put("server", "cf-doh")))
            .put("final", "cf-doh")
            .put("strategy", "prefer_ipv4"))

        // TUN inbound: full-device tunnel.
        config.put("inbounds", JSONArray()
            .put(JSONObject()
                .put("type", "tun")
                .put("tag", "tun-in")
                .put("interface_name", "click-tun0")
                .put("address", JSONArray()
                    .put("172.19.0.1/30")
                    .put("fdfe:dcba:9876::1/126"))
                .put("mtu", 9000)
                .put("auto_route", true)
                .put("strict_route", true)
                .put("stack", "gvisor")
                .put("sniff", true)))

        config.put("outbounds", JSONArray()
            .put(outbound)
            .put(JSONObject().put("type", "direct").put("tag", "direct"))
            .put(JSONObject().put("type", "block").put("tag", "block"))
            .put(JSONObject().put("type", "dns").put("tag", "dns-out")))

        // Route: DNS -> dns-out; everything else -> proxy.
        config.put("route", JSONObject()
            .put("rules", JSONArray()
                .put(JSONObject()
                    .put("protocol", "dns")
                    .put("outbound", "dns-out")))
            .put("final", "proxy")
            .put("auto_detect_interface", true))

        return config.toString()
    }

    // ---------- proxy URL parsers ----------

    private fun parseVless(url: String): JSONObject? = try {
        // vless://uuid@host:port?encryption=none&security=reality&...#name
        val uri = URI(url)
        val params = parseQuery(uri.rawQuery)
        val o = JSONObject()
            .put("type", "vless")
            .put("server", uri.host)
            .put("server_port", if (uri.port > 0) uri.port else 443)
            .put("uuid", uri.userInfo ?: "")
        params["encryption"]?.let { o.put("encryption", it) }
        val security = params["security"].orEmpty()
        if (security == "reality") {
            o.put("tls", JSONObject()
                .put("enabled", true)
                .put("server_name", params["sni"].orEmpty())
                .put("reality", JSONObject()
                    .put("enabled", true)
                    .put("public_key", params["pbk"].orEmpty())
                    .put("short_id", params["sid"].orEmpty()))
        } else if (security == "tls") {
            o.put("tls", JSONObject()
                .put("enabled", true)
                .put("server_name", params["sni"].orEmpty()))
        }
        params["type"]?.let { net ->
            if (net != "tcp") {
                o.put("transport", JSONObject().put("type", net).apply {
                    params["path"]?.let { put("path", it) }
                    params["host"]?.let { put("headers", JSONObject().put("Host", it)) }
                })
            }
        }
        o
    } catch (t: Throwable) {
        Log.w(TAG, "VLESS parse failed", t); null
    }

    private fun parseVmess(url: String): JSONObject? = try {
        // vmess://base64(json)
        val b64 = url.removePrefix("vmess://").substringBefore("#")
        val json = JSONObject(String(
            android.util.Base64.decode(b64, android.util.Base64.DEFAULT),
            Charsets.UTF_8))
        JSONObject()
            .put("type", "vmess")
            .put("server", json.getString("add"))
            .put("server_port", json.getString("port").toInt())
            .put("uuid", json.getString("id"))
            .put("security", json.optString("scy", "auto"))
            .put("alter_id", json.optString("aid", "0").toIntOrNull() ?: 0)
            .apply {
                if (json.optString("tls") == "tls") {
                    put("tls", JSONObject()
                        .put("enabled", true)
                        .put("server_name", json.optString("sni", json.getString("add"))))
                }
                val net = json.optString("net", "tcp")
                if (net != "tcp") {
                    put("transport", JSONObject().put("type", net).apply {
                        put("path", json.optString("path", "/"))
                        val host = json.optString("host", "")
                        if (host.isNotEmpty()) put("headers", JSONObject().put("Host", host))
                    })
                }
            }
    } catch (t: Throwable) {
        Log.w(TAG, "VMess parse failed", t); null
    }

    private fun parseTrojan(url: String): JSONObject? = try {
        // trojan://password@host:port?sni=...#name
        val uri = URI(url)
        val params = parseQuery(uri.rawQuery)
        JSONObject()
            .put("type", "trojan")
            .put("server", uri.host)
            .put("server_port", if (uri.port > 0) uri.port else 443)
            .put("password", uri.userInfo ?: "")
            .put("tls", JSONObject()
                .put("enabled", true)
                .put("server_name", params["sni"] ?: uri.host))
    } catch (t: Throwable) {
        Log.w(TAG, "Trojan parse failed", t); null
    }

    private fun parseShadowsocks(url: String): JSONObject? = try {
        // ss://base64(method:password)@host:port#name  (or full-URL form)
        var rest = url.removePrefix("ss://").substringBefore("#")
        val o = JSONObject().put("type", "shadowsocks")
        if ("@" in rest && !rest.startsWith("[")) {
            // Try userinfo@hostport; userinfo may itself be base64.
            val atIdx = rest.lastIndexOf("@")
            var userinfo = rest.substring(0, atIdx)
            val hostport = rest.substring(atIdx + 1)
            if (!userinfo.contains(":")) {
                userinfo = String(
                    android.util.Base64.decode(userinfo, android.util.Base64.DEFAULT),
                    Charsets.UTF_8)
            }
            val method = userinfo.substringBefore(":")
            val password = userinfo.substringAfter(":")
            val uri = URI("ss://$hostport")
            o.put("server", uri.host)
                .put("server_port", if (uri.port > 0) uri.port else 8388)
                .put("method", method)
                .put("password", password)
        } else {
            // Fully base64-encoded.
            val decoded = String(
                android.util.Base64.decode(rest, android.util.Base64.DEFAULT),
                Charsets.UTF_8)
            val uri = URI("ss://$decoded")
            val userinfo = uri.userInfo ?: ""
            o.put("server", uri.host)
                .put("server_port", if (uri.port > 0) uri.port else 8388)
                .put("method", userinfo.substringBefore(":"))
                .put("password", userinfo.substringAfter(":"))
        }
        o
    } catch (t: Throwable) {
        Log.w(TAG, "Shadowsocks parse failed", t); null
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        return rawQuery.split("&").mapNotNull {
            val kv = it.split("=", limit = 2)
            if (kv.size == 2) {
                try {
                    java.net.URLDecoder.decode(kv[0], "UTF-8") to
                        java.net.URLDecoder.decode(kv[1], "UTF-8")
                } catch (_: Exception) { null }
            } else null
        }.toMap()
    }
}

/**
 * Cloudflare WARP WireGuard credentials, obtained via [WarpRegistrar].
 */
data class WarpCredentials(
    val privateKey: String,
    val peerPublicKey: String,
    val endpointHost: String,
    val endpointPort: Int,
    val localAddresses: List<String>,
    /** WARP reserved bytes (3 ints) for the WireGuard handshake. */
    val reserved: List<Int>,
)
