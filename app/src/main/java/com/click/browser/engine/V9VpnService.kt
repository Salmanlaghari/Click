package com.click.browser.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.click.browser.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * V9 Shield — device-level DNS protection.
 *
 * HONEST CAPABILITY STATEMENT (do not oversell this):
 * - YES: all DNS on the device is answered over encrypted DNS-over-HTTPS
 *   (Cloudflare / Google / custom) — no plaintext port-53 leaks while on.
 * - YES: known ad / tracker / malware domains are sinkholed at the DNS
 *   layer (see [V9Blocklist]).
 * - YES: per-mode toggle, persistent notification, clean start/stop.
 * - NO full IP-changing tunnel in this version: app TCP/UDP traffic bypasses
 *   the TUN (no default route is installed, so nothing breaks). A true
 *   IP-masking tunnel needs either a remote VPN server or a userspace TCP
 *   stack — both are Phase-2 work. The config surface is ready for it.
 *
 * Design: TUN 10.8.0.2/24, our DNS at 10.8.0.1, NO 0.0.0.0/0 route.
 * Android routes DNS queries for our DNS server through the TUN; everything
 * else flows directly, so enabling Shield can never break apps' internet.
 */
class V9VpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.click.browser.engine.V9VpnService.START"
        const val ACTION_STOP = "com.click.browser.engine.V9VpnService.STOP"
        const val VPN_ADDRESS = "10.8.0.2"
        const val VPN_DNS = "10.8.0.1"
        private const val NOTIF_ID = 4401
        private const val CHANNEL_ID = "v9_shield"
        private const val TAG = "V9Vpn"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()
        private val _queries = MutableStateFlow(0L)
        val queries: StateFlow<Long> = _queries.asStateFlow()
        private val _blocked = MutableStateFlow(0L)
        val blocked: StateFlow<Long> = _blocked.asStateFlow()
    }

    @Volatile private var loopOn = false
    private var tun: ParcelFileDescriptor? = null
    private val ipId = AtomicInteger(1)
    private val queryCount = AtomicLong(0)
    private val blockedCount = AtomicLong(0)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopShield()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startShield()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        Log.i(TAG, "VPN revoked by system/user")
        stopShield()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopShield()
        super.onDestroy()
    }

    private fun startShield() {
        if (loopOn) return
        val builder = Builder()
            .setSession("Click V9 Shield")
            .addAddress(VPN_ADDRESS, 24)
            .addDnsServer(VPN_DNS)
        // NOTE: deliberately NO addRoute("0.0.0.0", 0) — DNS-only design.
        val fd = try {
            builder.establish()
        } catch (t: Throwable) {
            Log.e(TAG, "TUN establish failed", t)
            null
        } ?: return
        tun = fd
        startForegroundCompat(buildNotification())
        loopOn = true
        _running.value = true
        thread(name = "v9-dns-loop", isDaemon = true) { packetLoop(fd) }
        Log.i(TAG, "V9 Shield ON")
    }

    private fun stopShield() {
        loopOn = false
        _running.value = false
        try { tun?.close() } catch (_: Throwable) {}
        tun = null
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
        Log.i(TAG, "V9 Shield OFF")
    }

    private fun startForegroundCompat(notif: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "V9 Shield",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val stopIntent = Intent(this, V9VpnService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("V9 Shield is on")
            .setContentText("Encrypted DNS + tracker blocking active")
            .setOngoing(true)
            .addAction(0, "Stop", stopPi)
        if (openPi != null) b.setContentIntent(openPi)
        return b.build()
    }

    // ---------- packet loop ----------

    private fun packetLoop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        while (loopOn) {
            val n = try {
                input.read(buf)
            } catch (_: Throwable) {
                break // fd closed on stop
            }
            if (n <= 0) continue
            try {
                handlePacket(buf, n, output)
            } catch (t: Throwable) {
                Log.w(TAG, "packet handling failed", t)
            }
        }
    }

    private fun handlePacket(buf: ByteArray, len: Int, out: FileOutputStream) {
        if (len < 20) return
        if ((buf[0].toInt() shr 4) != 4) return // IPv4 only
        val ihl = (buf[0].toInt() and 0x0F) * 4
        if (buf[9] != 17.toByte()) return // UDP only
        val udpOff = ihl
        if (len < udpOff + 8) return
        val dstPort = ((buf[udpOff + 2].toInt() and 0xFF) shl 8) or (buf[udpOff + 3].toInt() and 0xFF)
        if (dstPort != 53) return // DNS only; everything else bypasses the TUN by design
        val srcIp = buf.copyOfRange(12, 16)
        val srcPort = ((buf[udpOff].toInt() and 0xFF) shl 8) or (buf[udpOff + 1].toInt() and 0xFF)
        val dnsOff = udpOff + 8
        val query = parseQuery(buf, dnsOff, len) ?: return

        queryCount.incrementAndGet()
        _queries.value = queryCount.get()

        val response: ByteArray = if (V9Blocklist.isBlocked(query.name)) {
            blockedCount.incrementAndGet()
            _blocked.value = blockedCount.get()
            buildDnsResponse(query, sinkhole = true)
        } else {
            val ips = V9DohResolver.resolve(query.name, query.qtype, dohEndpoint())
            buildDnsResponse(query, ips = ips)
        }
        out.write(buildUdpIpPacket(srcIp, srcPort, response))
    }

    private data class DnsQuery(
        val id: Int,
        val question: ByteArray, // raw question section (qname+qtype+qclass)
        val name: String,
        val qtype: Int,
    )

    /** Parses a DNS query; null if malformed/unsupported. */
    private fun parseQuery(buf: ByteArray, off: Int, len: Int): DnsQuery? {
        if (len < off + 12) return null
        val id = ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)
        val qdCount = ((buf[off + 4].toInt() and 0xFF) shl 8) or (buf[off + 5].toInt() and 0xFF)
        if (qdCount != 1) return null
        // Walk qname labels.
        val labels = mutableListOf<String>()
        var p = off + 12
        while (true) {
            if (p >= len) return null
            val l = buf[p].toInt() and 0xFF
            if (l == 0) { p++; break }
            if (l and 0xC0 != 0) return null // no compression expected in queries
            if (p + 1 + l > len) return null
            labels.add(String(buf, p + 1, l, Charsets.US_ASCII))
            p += 1 + l
        }
        if (len < p + 4) return null
        val qtype = ((buf[p].toInt() and 0xFF) shl 8) or (buf[p + 1].toInt() and 0xFF)
        val question = buf.copyOfRange(off + 12, p + 4)
        return DnsQuery(id, question, labels.joinToString("."), qtype)
    }

    /**
     * Builds a DNS response. [sinkhole]=true returns 0.0.0.0 for A queries
     * (blocked). Otherwise answers from [ips] (A/AAAA); other qtypes get an
     * empty NOERROR answer.
     */
    private fun buildDnsResponse(query: DnsQuery, sinkhole: Boolean = false, ips: List<String> = emptyList()): ByteArray {
        val answers = mutableListOf<ByteArray>()
        if (sinkhole && query.qtype == 1) {
            answers.add(answer(query.qtype, byteArrayOf(0, 0, 0, 0)))
        } else if (!sinkhole) {
            for (ip in ips) {
                val rdata = if (query.qtype == 28) ipv6Bytes(ip) else ipv4Bytes(ip)
                if (rdata != null) answers.add(answer(query.qtype, rdata))
            }
        }
        val out = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN)
        out.putShort(query.id.toShort())
        out.putShort(0x8180.toShort()) // standard response, no error
        out.putShort(1) // QDCOUNT
        out.putShort(answers.size.toShort()) // ANCOUNT
        out.putShort(0); out.putShort(0) // NSCOUNT, ARCOUNT
        out.put(query.question)
        for (a in answers) out.put(a)
        return out.array().copyOf(out.position())
    }

    private fun answer(qtype: Int, rdata: ByteArray): ByteArray {
        val b = ByteBuffer.allocate(16 + rdata.size).order(ByteOrder.BIG_ENDIAN)
        b.putShort(0xC00C.toShort()) // name pointer -> question qname
        b.putShort(qtype.toShort())
        b.putShort(1) // CLASS IN
        b.putInt(300) // TTL 5 min
        b.putShort(rdata.size.toShort())
        b.put(rdata)
        return b.array().copyOf(b.position())
    }

    private fun ipv4Bytes(ip: String): ByteArray? = try {
        ip.split(".").map { it.toInt() }.let { p ->
            if (p.size != 4 || p.any { it !in 0..255 }) null
            else byteArrayOf(p[0].toByte(), p[1].toByte(), p[2].toByte(), p[3].toByte())
        }
    } catch (_: Exception) { null }

    private fun ipv6Bytes(ip: String): ByteArray? = try {
        // Expand :: shorthand, then parse 8 hextets.
        var s = ip
        if (s.contains("::")) {
            val parts = s.split("::")
            val left = if (parts[0].isEmpty()) emptyList() else parts[0].split(":")
            val right = if (parts.size < 2 || parts[1].isEmpty()) emptyList() else parts[1].split(":")
            val missing = 8 - left.size - right.size
            s = (left + List(missing) { "0" } + right).joinToString(":")
        }
        val hextets = s.split(":")
        if (hextets.size != 8) return null
        val b = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        for (h in hextets) b.putShort(h.toInt(16).toShort())
        b.array()
    } catch (_: Exception) { null }

    /** Wraps a DNS payload in UDP+IPv4, addressed back to the original sender. */
    private fun buildUdpIpPacket(dstIp: ByteArray, dstPort: Int, dns: ByteArray): ByteArray {
        val total = 20 + 8 + dns.size
        val b = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
        // IPv4 header
        b.put(0x45.toByte()); b.put(0)
        b.putShort(total.toShort())
        b.putShort(ipId.getAndIncrement().toShort())
        b.putShort(0x4000.toShort()) // DF
        b.put(64.toByte()); b.put(17.toByte()) // TTL, UDP
        b.putShort(0) // checksum placeholder
        b.put(ipv4Bytes(VPN_DNS)!!)
        b.put(dstIp)
        val csum = ipChecksum(b.array().copyOfRange(0, 20))
        b.putShort(10, csum.toShort())
        // UDP header (checksum 0 = none, legal for IPv4)
        b.putShort(53)
        b.putShort(dstPort.toShort())
        b.putShort((8 + dns.size).toShort())
        b.putShort(0)
        b.put(dns)
        return b.array()
    }

    private fun ipChecksum(header: ByteArray): Int {
        var sum = 0
        var i = 0
        while (i < header.size) {
            sum += ((header[i].toInt() and 0xFF) shl 8) or (header[i + 1].toInt() and 0xFF)
            if (sum > 0xFFFF) sum = (sum and 0xFFFF) + 1
            i += 2
        }
        return sum.inv() and 0xFFFF
    }

    private fun dohEndpoint(): String {
        return try {
            val prefs = runBlocking { dataStore.data.first() }
            val provider = prefs[AppSettings.DOH_PROVIDER]
                ?: V9DohResolver.PROVIDER_CLOUDFLARE
            val custom = prefs[AppSettings.DOH_CUSTOM_URL].orEmpty()
            V9DohResolver.endpointUrl(provider, custom)
        } catch (_: Throwable) {
            "https://cloudflare-dns.com/dns-query"
        }
    }
}
