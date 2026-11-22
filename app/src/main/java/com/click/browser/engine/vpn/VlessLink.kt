package com.click.browser.engine.vpn

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A parsed `vless://` share link (the HA Tunnel / v2rayNG link format):
 * every field a user needs to describe their own VLESS server.
 *
 * This is the single source of truth for VLESS links in Click:
 * [VpnConfigBuilder] builds the sing-box outbound from it, and the VPN
 * screen's manual form edits it field by field. Parsing is strict about
 * the parts that would silently break a connection (missing UUID/host,
 * bad port, unknown transport/security) and lenient about the rest.
 *
 * Supported transports: tcp, ws, grpc. Supported security: none, tls,
 * reality. Anything else returns null from [parse] so the UI can reject
 * the link honestly instead of saving a server that can never connect.
 */
data class VlessLink(
    val uuid: String,
    val host: String,
    val port: Int,
    /** Display name from the `#fragment` (may be blank). */
    val name: String = "",
    val encryption: String = "none",
    val flow: String = "",
    /** tcp | ws | grpc */
    val network: String = "tcp",
    /** none | tls | reality */
    val security: String = "none",
    val sni: String = "",
    /** uTLS fingerprint (`fp`), e.g. "chrome". */
    val fingerprint: String = "",
    /** Reality public key (`pbk`). */
    val publicKey: String = "",
    /** Reality short id (`sid`). */
    val shortId: String = "",
    /** WebSocket path. */
    val path: String = "",
    /** WebSocket Host header. */
    val hostHeader: String = "",
    /** gRPC service name. */
    val serviceName: String = "",
    val allowInsecure: Boolean = false
) {
    /** One-line technical summary for server lists, e.g. "example.com:443 · ws · tls". */
    fun summary(): String = "$host:$port · $network · $security"

    /** Regenerates a canonical share link from the fields. */
    fun toShareLink(): String {
        val params = mutableListOf<Pair<String, String>>()
        params += "encryption" to encryption.ifBlank { "none" }
        if (flow.isNotBlank()) params += "flow" to flow
        params += "security" to security
        params += "type" to network
        if (sni.isNotBlank()) params += "sni" to sni
        if (fingerprint.isNotBlank()) params += "fp" to fingerprint
        if (security == "reality") {
            if (publicKey.isNotBlank()) params += "pbk" to publicKey
            if (shortId.isNotBlank()) params += "sid" to shortId
        }
        if (network == "ws") {
            if (path.isNotBlank()) params += "path" to path
            if (hostHeader.isNotBlank()) params += "host" to hostHeader
        }
        if (network == "grpc" && serviceName.isNotBlank()) {
            params += "serviceName" to serviceName
        }
        if (allowInsecure) params += "allowInsecure" to "1"
        val query = params.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
        val fragment = if (name.isNotBlank()) "#${encode(name)}" else ""
        return "vless://$uuid@$host:$port?$query$fragment"
    }

    companion object {
        private val NETWORKS = setOf("tcp", "ws", "grpc")
        private val SECURITIES = setOf("none", "tls", "reality")

        fun parse(link: String): VlessLink? {
            val trimmed = link.trim()
            if (!trimmed.startsWith("vless://", ignoreCase = true)) return null
            val uri = try {
                URI(trimmed)
            } catch (_: Exception) {
                return null
            }
            // java.net.URI only decomposes server-based authorities:
            // hostnames containing '_' (common on VPS panels) come back
            // with null host/userInfo/port, so fall back to splitting the
            // raw authority by hand before giving up.
            val rawAuthority = uri.rawAuthority?.takeIf { it.isNotBlank() } ?: return null
            val uuid = uri.userInfo?.takeIf { it.isNotBlank() }
                ?: rawAuthority.substringBefore('@', "").takeIf {
                    it.isNotBlank() && rawAuthority.contains('@')
                }
                ?: return null
            val hostPort = rawAuthority.substringAfterLast('@')
            val host: String
            val port: Int
            when {
                uri.host != null -> {
                    host = uri.host!!
                    port = if (uri.port > 0) uri.port else 443
                }
                hostPort.startsWith("[") -> {
                    host = hostPort.drop(1).substringBefore(']')
                    port = hostPort.substringAfter(']', "").removePrefix(":")
                        .toIntOrNull() ?: 443
                }
                else -> {
                    host = hostPort.substringBefore(':')
                    port = hostPort.substringAfter(':', "").toIntOrNull() ?: 443
                }
            }
            if (host.isBlank()) return null
            if (port !in 1..65535) return null
            val params = parseQuery(uri.rawQuery)
            val network = (params["type"] ?: "tcp").lowercase()
            if (network !in NETWORKS) return null
            val security = (params["security"] ?: "none").lowercase()
            if (security !in SECURITIES) return null
            val name = uri.fragment?.let {
                try {
                    URLDecoder.decode(it, "UTF-8")
                } catch (_: Exception) {
                    it
                }
            }.orEmpty()
            return VlessLink(
                uuid = uuid,
                host = host,
                port = port,
                name = name,
                encryption = params["encryption"] ?: "none",
                flow = params["flow"].orEmpty(),
                network = network,
                security = security,
                sni = params["sni"].orEmpty(),
                fingerprint = params["fp"].orEmpty(),
                publicKey = params["pbk"].orEmpty(),
                shortId = params["sid"].orEmpty(),
                path = params["path"].orEmpty(),
                hostHeader = params["host"].orEmpty(),
                serviceName = params["serviceName"].orEmpty(),
                allowInsecure = params["allowInsecure"] == "1" || params["allowInsecure"] == "true"
            )
        }

        private fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        private fun parseQuery(rawQuery: String?): Map<String, String> {
            if (rawQuery.isNullOrEmpty()) return emptyMap()
            return rawQuery.split("&").mapNotNull {
                val kv = it.split("=", limit = 2)
                if (kv.size == 2) {
                    try {
                        // URLDecoder's form rule maps '+' to a space, which
                        // corrupts values that legitimately contain '+'
                        // (standard-base64 Reality keys, ws paths). Escape
                        // literal pluses first so only %20 means space.
                        URLDecoder.decode(kv[0].replace("+", "%2B"), "UTF-8") to
                            URLDecoder.decode(kv[1].replace("+", "%2B"), "UTF-8")
                    } catch (_: Exception) {
                        null
                    }
                } else null
            }.toMap()
        }
    }
}
