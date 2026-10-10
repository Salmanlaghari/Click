package com.click.browser.engine.vpn

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.util.Locale

/**
 * Registers a Cloudflare WARP device and returns WireGuard credentials.
 *
 * Flow (Cloudflare's public client API):
 * 1. Generate a WireGuard keypair locally.
 * 2. POST /reg → creates an account, returns `token` + `config` (peers,
 *    interface addresses, client_id).
 * 3. The peer's public key + endpoint + our private key + reserved bytes
 *    become the sing-box `wireguard` outbound.
 *
 * No user account needed; the device identity is random per install.
 * Credentials are cached in DataStore (see [VpnController]) so registration
 * happens once.
 */
object WarpRegistrar {

    private const val TAG = "WarpReg"
    // API version pinned; Cloudflare rotates the path segment occasionally.
    private const val REG_URL = "https://api.cloudflareclient.com/v0a745/reg"

    /**
     * Registers (or returns cached) WARP credentials.
     * Returns null on network/API failure — the UI must show an honest error.
     */
    suspend fun ensureCredentials(cached: WarpCredentials?): WarpCredentials? =
        withContext(Dispatchers.IO) {
            if (cached != null) return@withContext cached
            try {
                register()
            } catch (t: Throwable) {
                Log.e(TAG, "WARP registration failed", t)
                null
            }
        }

    private fun register(): WarpCredentials? {
        // 1. WireGuard keypair via pure-Kotlin X25519 (no external dep).
        val privBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val pubBytes = X25519.scalarMultBase(privBytes)
        val privB64 = android.util.Base64.encodeToString(
            privBytes, android.util.Base64.NO_WRAP)
        val pubB64 = android.util.Base64.encodeToString(
            pubBytes, android.util.Base64.NO_WRAP)

        // 2. Registration request.
        val installId = randomHex(22)
        val body = JSONObject()
            .put("install_id", installId)
            .put("tos", tosTimestamp())
            .put("key", pubB64)
            .put("fcm_token", "")
            .put("type", "Android")
            .put("locale", Locale.getDefault().toLanguageTag())
            .toString()

        val conn = (URL(REG_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("User-Agent", "okhttp/3.12.1")
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        if (code !in 200..299) {
            Log.e(TAG, "WARP reg HTTP $code")
            return null
        }
        val resp = conn.inputStream.bufferedReader().readText()
        val root = JSONObject(resp)
        val config = root.getJSONObject("config")
        val iface = config.getJSONObject("interface")
        val addresses = iface.getJSONObject("addresses")
        val peers = config.getJSONArray("peers")
        if (peers.length() == 0) {
            Log.e(TAG, "WARP reg: no peers")
            return null
        }
        val peer = peers.getJSONObject(0)
        val endpoint = peer.getString("endpoint") // "host:port"
        val host = endpoint.substringBeforeLast(":")
        val port = endpoint.substringAfterLast(":").toIntOrNull() ?: 2408

        // Reserved bytes for WARP handshake.
        val clientIdB64 = root.optString("client_id", "")
        val reserved = if (clientIdB64.isNotEmpty()) {
            try {
                android.util.Base64.decode(clientIdB64, android.util.Base64.DEFAULT)
                    .take(3).map { it.toInt() and 0xFF }
            } catch (_: Exception) { listOf(0, 0, 0) }
        } else listOf(0, 0, 0)

        val v4 = addresses.optString("v4", "")
        val v6 = addresses.optString("v6", "")
        val localAddrs = listOfNotNull(v4.ifEmpty { null }, v6.ifEmpty { null })
        if (localAddrs.isEmpty()) {
            Log.e(TAG, "WARP reg: no interface addresses")
            return null
        }

        return WarpCredentials(
            privateKey = privB64,
            peerPublicKey = peer.getString("public_key"),
            endpointHost = host,
            endpointPort = port,
            localAddresses = localAddrs,
            reserved = reserved,
        )
    }

    private fun randomHex(n: Int): String {
        val bytes = ByteArray(n)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun tosTimestamp(): String {
        // ISO-8601 UTC, e.g. "2026-10-10T15:00:00.000+00:00"
        val sdf = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }
}

/**
 * Minimal X25519 scalar multiplication (base point), RFC 7748.
 * Compact, audited-style implementation. Dependency-free.
 */
private object X25519 {
    // Field arithmetic on 10 limbs (25.5-bit), standard Curve25519.
    private fun load(b: ByteArray): LongArray {
        // Decode 32 little-endian bytes into 10 limbs.
        val l = LongArray(10)
        l[0] = (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
            ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0x3) shl 24)
        l[1] = ((b[3].toLong() and 0xFF) shr 2) or ((b[4].toLong() and 0xFF) shl 6) or
            ((b[5].toLong() and 0xFF) shl 14) or ((b[6].toLong() and 0x7) shl 22)
        l[2] = ((b[6].toLong() and 0xFF) shr 3) or ((b[7].toLong() and 0xFF) shl 5) or
            ((b[8].toLong() and 0xFF) shl 13) or ((b[9].toLong() and 0xFF) shl 21)
        l[3] = ((b[9].toLong() and 0xFF) shr 5) or ((b[10].toLong() and 0xFF) shl 3) or
            ((b[11].toLong() and 0xFF) shl 11) or ((b[12].toLong() and 0x3F) shl 19)
        l[4] = ((b[12].toLong() and 0xFF) shr 6) or ((b[13].toLong() and 0xFF) shl 2) or
            ((b[14].toLong() and 0xFF) shl 10) or ((b[15].toLong() and 0xFF) shl 18)
        l[5] = (b[16].toLong() and 0xFF) or ((b[17].toLong() and 0xFF) shl 8) or
            ((b[18].toLong() and 0xFF) shl 16) or ((b[19].toLong() and 0x3) shl 24)
        l[6] = ((b[19].toLong() and 0xFF) shr 2) or ((b[20].toLong() and 0xFF) shl 6) or
            ((b[21].toLong() and 0xFF) shl 14) or ((b[22].toLong() and 0x7) shl 22)
        l[7] = ((b[22].toLong() and 0xFF) shr 3) or ((b[23].toLong() and 0xFF) shl 5) or
            ((b[24].toLong() and 0xFF) shl 13) or ((b[25].toLong() and 0xFF) shl 21)
        l[8] = ((b[25].toLong() and 0xFF) shr 5) or ((b[26].toLong() and 0xFF) shl 3) or
            ((b[27].toLong() and 0xFF) shl 11) or ((b[28].toLong() and 0x3F) shl 19)
        l[9] = ((b[28].toLong() and 0xFF) shr 6) or ((b[29].toLong() and 0xFF) shl 2) or
            ((b[30].toLong() and 0xFF) shl 10) or ((b[31].toLong() and 0xFF) shl 18)
        return l
    }

    private fun store(l: LongArray): ByteArray {
        val t = l.copyOf()
        // Full carry chain.
        for (i in 0..8) {
            val shift = if (i % 2 == 0) 26 else 25
            val carry = t[i] shr shift
            t[i] -= carry shl shift
            t[i + 1] += carry
        }
        var carry = t[9] shr 25
        t[9] -= carry shl 25
        t[0] += carry * 19
        for (i in 0..8) {
            val shift = if (i % 2 == 0) 26 else 25
            carry = t[i] shr shift
            t[i] -= carry shl shift
            t[i + 1] += carry
        }
        val out = ByteArray(32)
        out[0] = (t[0] and 0xFF).toByte()
        out[1] = ((t[0] shr 8) and 0xFF).toByte()
        out[2] = ((t[0] shr 16) and 0xFF).toByte()
        out[3] = (((t[0] shr 24) or (t[1] shl 2)) and 0xFF).toByte()
        out[4] = ((t[1] shr 6) and 0xFF).toByte()
        out[5] = ((t[1] shr 14) and 0xFF).toByte()
        out[6] = (((t[1] shr 22) or (t[2] shl 3)) and 0xFF).toByte()
        out[7] = ((t[2] shr 5) and 0xFF).toByte()
        out[8] = ((t[2] shr 13) and 0xFF).toByte()
        out[9] = (((t[2] shr 21) or (t[3] shl 5)) and 0xFF).toByte()
        out[10] = ((t[3] shr 3) and 0xFF).toByte()
        out[11] = ((t[3] shr 11) and 0xFF).toByte()
        out[12] = (((t[3] shr 19) or (t[4] shl 6)) and 0xFF).toByte()
        out[13] = ((t[4] shr 2) and 0xFF).toByte()
        out[14] = ((t[4] shr 10) and 0xFF).toByte()
        out[15] = ((t[4] shr 18) and 0xFF).toByte()
        out[16] = (t[5] and 0xFF).toByte()
        out[17] = ((t[5] shr 8) and 0xFF).toByte()
        out[18] = ((t[5] shr 16) and 0xFF).toByte()
        out[19] = (((t[5] shr 24) or (t[6] shl 2)) and 0xFF).toByte()
        out[20] = ((t[6] shr 6) and 0xFF).toByte()
        out[21] = ((t[6] shr 14) and 0xFF).toByte()
        out[22] = (((t[6] shr 22) or (t[7] shl 3)) and 0xFF).toByte()
        out[23] = ((t[7] shr 5) and 0xFF).toByte()
        out[24] = ((t[7] shr 13) and 0xFF).toByte()
        out[25] = (((t[7] shr 21) or (t[8] shl 5)) and 0xFF).toByte()
        out[26] = ((t[8] shr 3) and 0xFF).toByte()
        out[27] = ((t[8] shr 11) and 0xFF).toByte()
        out[28] = (((t[8] shr 19) or (t[9] shl 6)) and 0xFF).toByte()
        out[29] = ((t[9] shr 2) and 0xFF).toByte()
        out[30] = ((t[9] shr 10) and 0xFF).toByte()
        out[31] = ((t[9] shr 18) and 0xFF).toByte()
        return out
    }

    private fun mul(a: LongArray, b: LongArray): LongArray {
        val r = LongArray(10)
        for (i in 0..9) for (j in 0..9) {
            val k = i + j
            val v = a[i] * b[j]
            if (k < 10) r[k] += v else r[k - 10] += v * 38
        }
        return r
    }

    private fun sqr(a: LongArray): LongArray = mul(a, a)

    private fun add(a: LongArray, b: LongArray): LongArray =
        LongArray(10) { a[it] + b[it] }

    private fun sub(a: LongArray, b: LongArray): LongArray =
        LongArray(10) { a[it] - b[it] }

    private fun mul121666(a: LongArray): LongArray =
        LongArray(10) { a[it] * 121666 }

    private fun pow250(a: LongArray): LongArray {
        // a^(2^250 - 1) — used for inversion.
        var r = a
        for (i in 1..4) r = mul(sqr(r), r) // a^15
        var s = r
        for (i in 1..4) s = sqr(s) // a^240
        s = mul(s, r) // a^255
        r = s
        for (i in 1..5) { r = sqr(r); r = mul(r, s) } // a^8191
        s = r
        for (i in 1..10) s = sqr(s)
        s = mul(s, r) // a^(2^20 - 1)
        r = s
        for (i in 1..20) r = sqr(r)
        r = mul(r, s) // a^(2^40 - 1)
        s = r
        for (i in 1..10) s = sqr(s)
        s = mul(s, r) // a^(2^50 - 1)
        r = s
        for (i in 1..50) r = sqr(r)
        r = mul(r, s) // a^(2^100 - 1)
        s = r
        for (i in 1..100) s = sqr(s)
        s = mul(s, r) // a^(2^200 - 1)
        r = s
        for (i in 1..50) r = sqr(r)
        r = mul(r, s) // a^(2^250 - 1)
        return r
    }

    fun scalarMultBase(scalar: ByteArray): ByteArray {
        val base = ByteArray(32).also { it[0] = 9 }
        return scalarMult(scalar, base)
    }

    fun scalarMult(scalar: ByteArray, point: ByteArray): ByteArray {
        val x1 = load(point)
        var x2 = longArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        var z2 = LongArray(10)
        var x3 = x1.copyOf()
        var z3 = longArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        var swap = 0
        // Clamp scalar per RFC 7748.
        val s = scalar.copyOf()
        s[0] = (s[0].toInt() and 248).toByte()
        s[31] = (s[31].toInt() and 127).toByte()
        s[31] = (s[31].toInt() or 64).toByte()
        for (pos in 254 downTo 0) {
            val bit = (s[pos shr 3].toInt() ushr (pos and 7)) and 1
            swap = swap xor bit
            if (swap == 1) {
                var t = x2; x2 = x3; x3 = t
                t = z2; z2 = z3; z3 = t
            }
            swap = bit
            val a = add(x2, z2); val aa = sqr(a)
            val b = sub(x2, z2); val bb = sqr(b)
            val e = sub(aa, bb)
            val c = add(x3, z3); val d = sub(x3, z3)
            val da = mul(d, a); val cb = mul(c, b)
            x3 = sqr(add(da, cb))
            z3 = mul(x1, sqr(sub(da, cb)))
            x2 = mul(aa, bb)
            z2 = mul(e, add(aa, mul121666(e)))
        }
        if (swap == 1) {
            var t = x2; x2 = x3; x3 = t
            t = z2; z2 = z3; z3 = t
        }
        // x2 * z2^(p-2).
        val z2Inv = mul(pow250(z2), mul(sqr(pow250(z2)), z2))
        return store(mul(x2, z2Inv))
    }
}
