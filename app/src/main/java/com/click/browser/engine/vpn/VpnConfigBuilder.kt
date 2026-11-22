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
 * - `route.default_domain_resolver` points outbound server-name resolution
 *   at the same in-tunnel DoH server, so proxy hostnames never resolve on
 *   the raw network either.
 * - The TUN inbound sets `auto_route: true`, capturing ALL device traffic
 *   (0.0.0.0/0 + ::/0), unlike V9 Shield's DNS-only design.
 *
 * Verify with the in-app "DNS Leak Test" button (opens ipleak.net).
 */
object VpnConfigBuilder {

    private const val TAG = "VpnConfig"
    const val DEFAULT_TUN_MTU = 1400
    private const val MIN_TUN_MTU = 1280

    /** Keep tunnel packets within a conservative mobile-network path MTU. */
    internal fun clampTunnelMtu(requested: Int?): Int =
        requested?.takeIf { it > 0 }?.coerceIn(MIN_TUN_MTU, DEFAULT_TUN_MTU) ?: DEFAULT_TUN_MTU

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
        // NOTE (sing-box >= 1.12): legacy "address"-string server entries
        // were removed — servers now need an explicit "type".
        // NOTE (sing-box >= 1.14): the "outbound" DNS rule item was
        // REMOVED — outbound server-name resolution is now configured via
        // route "default_domain_resolver" (see route block below), not a
        // DNS rule. Emitting the old rule makes the core reject the whole
        // config on any build without deprecated-feature support.
        config.put("dns", JSONObject()
            .put("servers", JSONArray()
                .put(JSONObject()
                    .put("tag", "cf-doh")
                    .put("type", "https")
                    .put("server", "1.1.1.1")
                    .put("detour", "proxy"))
                .put(JSONObject()
                    .put("tag", "google-doh")
                    .put("type", "https")
                    .put("server", "8.8.8.8")
                    .put("detour", "proxy")))
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
                .put("mtu", DEFAULT_TUN_MTU)
                .put("auto_route", true)
                .put("strict_route", true)
                .put("stack", "gvisor")))

        // NOTE (sing-box >= 1.13): the "dns" outbound type was removed —
        // DNS now travels via the servers' "detour", and plain port-53
        // traffic simply falls through to "final" (the proxy). No leak.
        config.put("outbounds", JSONArray()
            .put(outbound)
            .put(JSONObject().put("type", "direct").put("tag", "direct"))
            .put(JSONObject().put("type", "block").put("tag", "block")))

        // Route: everything unmatched goes through the proxy (including
        // plain DNS, which then exits at the server — no leak).
        // NOTE (sing-box >= 1.13): the legacy inbound "sniff" field was
        // REMOVED — sniffing is now the route rule action below (see the
        // sing-box migration guide: migrate legacy inbound fields to
        // rule actions). Without this the core rejects the whole config.
        // The rule intentionally has NO match criteria: it sniffs ALL
        // traffic, exactly the scope the legacy "sniff": true had.
        // "default_domain_resolver" (sing-box >= 1.12, REQUIRED since
        // 1.14): dial fields resolve server hostnames through cf-doh —
        // which itself detours through the tunnel — replacing the removed
        // "outbound" DNS rule item. Same cf-doh the old rule selected.
        config.put("route", JSONObject()
            .put("rules", JSONArray()
                .put(JSONObject()
                    .put("action", "sniff")))
            .put("final", "proxy")
            .put("auto_detect_interface", true)
            .put("default_domain_resolver", JSONObject()
                .put("server", "cf-doh")))

        return config.toString()
    }

    // ---------- proxy URL parsers ----------

    private fun parseVless(url: String): JSONObject? = try {
        // B4: links are parsed by VlessLink — the same model the VPN
        // screen's manual form edits — so a pasted link and a hand-typed
        // server can never drift apart.
        val link = VlessLink.parse(url)
        if (link == null) {
            null
        } else {
            val o = JSONObject()
                .put("type", "vless")
                .put("server", link.host)
                .put("server_port", link.port)
                .put("uuid", link.uuid)
            // NOTE (sing-box >= 1.14): the "encryption" field was REMOVED from
            // the vless outbound (it's always "none" now) — never emit it.
            // XTLS vision flow (e.g. flow=xtls-rprx-vision).
            if (link.flow.isNotBlank()) o.put("flow", link.flow)
            when (link.security) {
                "reality" -> {
                    val tls = JSONObject()
                        .put("enabled", true)
                        // Same host fallback as the TLS branch: an empty
                        // server_name breaks the handshake camouflage.
                        .put("server_name", link.sni.ifBlank { link.host })
                    // uTLS fingerprint (fp) — matters for Reality.
                    if (link.fingerprint.isNotBlank()) {
                        tls.put("utls", JSONObject()
                            .put("enabled", true)
                            .put("fingerprint", link.fingerprint))
                    }
                    tls.put("reality", JSONObject()
                        .put("enabled", true)
                        .put("public_key", link.publicKey)
                        .put("short_id", link.shortId))
                    o.put("tls", tls)
                }
                "tls" -> {
                    val tls = JSONObject()
                        .put("enabled", true)
                        // Fall back to the server host when no SNI is given;
                        // an empty server_name breaks the TLS handshake.
                        .put("server_name", link.sni.ifBlank { link.host })
                    if (link.fingerprint.isNotBlank()) {
                        tls.put("utls", JSONObject()
                            .put("enabled", true)
                            .put("fingerprint", link.fingerprint))
                    }
                    if (link.allowInsecure) tls.put("insecure", true)
                    o.put("tls", tls)
                }
            }
            when (link.network) {
                "ws" -> o.put("transport", JSONObject().put("type", "ws").apply {
                    put("path", link.path.ifBlank { "/" })
                    if (link.hostHeader.isNotBlank()) {
                        put("headers", JSONObject().put("Host", link.hostHeader))
                    }
                })
                // sing-box gRPC transport takes service_name — the old
                // parser wrongly emitted the ws path/headers shape here.
                "grpc" -> o.put("transport", JSONObject().put("type", "grpc").apply {
                    if (link.serviceName.isNotBlank()) put("service_name", link.serviceName)
                })
            }
            o
        }
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
