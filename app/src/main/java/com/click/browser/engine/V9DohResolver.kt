package com.click.browser.engine

import android.util.Log
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/**
 * DNS-over-HTTPS resolver for the V9 Shield TUN.
 *
 * CRITICAL DESIGN NOTE (fix for ERR_NAME_NOT_RESOLVED bug):
 * The DoH endpoint MUST be reachable without a DNS lookup. When V9 Shield
 * is active, the device DNS is 10.8.0.1 (our own TUN) — resolving the DoH
 * provider's *hostname* (e.g. cloudflare-dns.com) would send a query back
 * into the TUN, which calls this resolver, which needs the hostname's IP…
 * a bootstrap deadlock where EVERY lookup fails. We therefore use OkHttp's
 * [DnsOverHttps] with [bootstrapDnsHosts] (hardcoded provider IPs), so the
 * TLS connection goes to a literal IP with correct SNI — zero DNS needed.
 */
object V9DohResolver {

    const val PROVIDER_CLOUDFLARE = "cloudflare"
    const val PROVIDER_GOOGLE = "google"
    const val PROVIDER_CUSTOM = "custom"

    private const val TAG = "V9Doh"

    fun labelFor(provider: String): String = when (provider) {
        PROVIDER_GOOGLE -> "Google (8.8.8.8 DoH)"
        PROVIDER_CUSTOM -> "Custom"
        else -> "Cloudflare (1.1.1.1 DoH)"
    }

    fun endpointUrl(provider: String, customUrl: String): String = when (provider) {
        PROVIDER_GOOGLE -> "https://dns.google/resolve"
        PROVIDER_CUSTOM -> customUrl.ifBlank { "https://cloudflare-dns.com/dns-query" }
        else -> "https://cloudflare-dns.com/dns-query"
    }

    /** Bootstrap IPs per provider — literals, never resolved via DNS. */
    private fun bootstrapIps(provider: String): List<InetAddress> = try {
        when (provider) {
            PROVIDER_GOOGLE -> listOf("8.8.8.8", "8.8.4.4")
            else -> listOf("1.1.1.1", "1.0.0.1")
        }.map { InetAddress.getByName(it) }
    } catch (_: Exception) {
        emptyList()
    }

    @Volatile private var cachedDoh: DnsOverHttps? = null
    @Volatile private var cachedKey: String = ""

    /**
     * Socket protection: the VPN service MUST exempt its own DoH sockets from
     * the TUN, otherwise the TLS handshake loops back into our packet loop.
     * Set from [V9VpnService] via [VpnService.protect].
     */
    @Volatile var socketProtector: ((Socket) -> Boolean)? = null

    private fun protectedSocketFactory(): SocketFactory {
        val protector = socketProtector
        return object : SocketFactory() {
            private val def = SocketFactory.getDefault()
            override fun createSocket(): Socket =
                def.createSocket().also { protector?.invoke(it) }
            override fun createSocket(h: String?, p: Int): Socket =
                def.createSocket(h, p).also { protector?.invoke(it) }
            override fun createSocket(h: String?, p: Int, l: InetAddress?, lp: Int): Socket =
                def.createSocket(h, p, l, lp).also { protector?.invoke(it) }
            override fun createSocket(host: InetAddress?, port: Int): Socket =
                def.createSocket(host, port).also { protector?.invoke(it) }
            override fun createSocket(a: InetAddress?, p: Int, l: InetAddress?, lp: Int): Socket =
                def.createSocket(a, p, l, lp).also { protector?.invoke(it) }
        }
    }

    private fun dohFor(provider: String, customUrl: String): DnsOverHttps? {
        val key = "$provider|$customUrl"
        cachedDoh?.let { if (cachedKey == key) return it }
        val url = try {
            endpointUrl(provider, customUrl).toHttpUrl()
        } catch (_: Exception) {
            return null
        }
        val bootstrap = bootstrapIps(provider)
        if (bootstrap.isEmpty()) return null
        // For custom providers we keep the user's URL but still bootstrap via
        // Cloudflare IPs only if the URL host is a known one; otherwise we
        // cannot safely bootstrap and return null (caller falls back).
        if (provider == PROVIDER_CUSTOM) {
            val host = url.host.lowercase()
            if (host != "cloudflare-dns.com" && host != "dns.google"
                && host != "1.1.1.1" && host != "1.0.0.1"
                && host != "8.8.8.8" && host != "8.8.4.4"
            ) {
                Log.w(TAG, "Custom DoH host not bootstrappable, skipping DoH")
                return null
            }
        }
        val bootstrapClient = OkHttpClient.Builder()
            .socketFactory(protectedSocketFactory())
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        val doh = DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(url)
            .bootstrapDnsHosts(bootstrap)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        cachedDoh = doh
        cachedKey = key
        return doh
    }

    /**
     * Resolves [name] via DoH. [qtype] 1 = A, 28 = AAAA.
     * Returns IP address strings (empty on failure — caller may fail-open).
     */
    fun resolve(name: String, qtype: Int, provider: String, customUrl: String): List<String> {
        val doh = dohFor(provider, customUrl) ?: return emptyList()
        return try {
            val addrs = doh.lookup(name)
            val wantV6 = qtype == 28
            addrs.mapNotNull { a ->
                val ip = a.hostAddress ?: return@mapNotNull null
                val isV6 = ip.contains(':')
                if (isV6 == wantV6) ip else null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "DoH resolve failed for $name", t)
            emptyList()
        }
    }

    /**
     * Legacy signature kept for compatibility; resolves provider from args.
     * Prefer [resolve] with explicit provider.
     */
    fun resolve(name: String, qtype: Int, endpoint: String): List<String> {
        val provider = when {
            endpoint.contains("dns.google") -> PROVIDER_GOOGLE
            endpoint.contains("cloudflare-dns.com") || endpoint.contains("1.1.1.1") -> PROVIDER_CLOUDFLARE
            else -> PROVIDER_CUSTOM
        }
        val custom = if (provider == PROVIDER_CUSTOM) endpoint else ""
        return resolve(name, qtype, provider, custom)
    }
}
