package com.click.browser.engine

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * DNS-over-HTTPS resolver (dns-json profile — no binary DNS parsing needed
 * upstream). Used by [V9VpnService] so every DNS answer on the device is
 * fetched over encrypted HTTPS instead of plaintext port-53.
 */
object V9DohResolver {

    const val PROVIDER_CLOUDFLARE = "cloudflare"
    const val PROVIDER_GOOGLE = "google"
    const val PROVIDER_CUSTOM = "custom"

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

    /**
     * Resolves [name] via DoH. [qtype] 1 = A, 28 = AAAA.
     * Returns IP address strings (empty on failure — caller drops the query).
     */
    fun resolve(name: String, qtype: Int, endpoint: String): List<String> {
        val typeStr = if (qtype == 28) "AAAA" else "A"
        val conn = try {
            val url = URL("$endpoint?name=${URLEncoder.encode(name, "UTF-8")}&type=$typeStr")
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/dns-json")
                connectTimeout = 8000
                readTimeout = 8000
            }
        } catch (t: Throwable) {
            Log.w("V9Doh", "DoH connect failed", t)
            return emptyList()
        }
        return try {
            if (conn.responseCode != 200) return emptyList()
            val body = conn.inputStream.bufferedReader().readText()
            val answers = JSONObject(body).optJSONArray("Answer") ?: return emptyList()
            val ipv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}\$")
            (0 until answers.length()).mapNotNull { i ->
                answers.optJSONObject(i)?.optString("data")?.trim()
            }.filter { ip ->
                if (typeStr == "A") ipv4.matches(ip) else ip.contains(':')
            }
        } catch (t: Throwable) {
            Log.w("V9Doh", "DoH resolve failed for $name", t)
            emptyList()
        } finally {
            conn.disconnect()
        }
    }
}
