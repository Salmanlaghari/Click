package com.click.browser.engine.vpn

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
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.NetworkInterface
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StringBox
import io.nekohasekai.libbox.TunOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Click VPN — real IP-tunnel via sing-box (libbox).
 *
 * Unlike [com.click.browser.engine.V9VpnService] (DNS-only Shield), this
 * installs a FULL default route (0.0.0.0/0 + ::/0): all device traffic flows
 * through the encrypted tunnel to the selected server, changing the visible
 * IP. DNS is answered through the tunnel via DoH (see [VpnConfigBuilder]) —
 * no DNS leak.
 *
 * Architecture:
 * - Android owns the TUN: [VpnService.Builder.establish] in [openTun].
 * - libbox owns the sing-box core: config lifecycle, TUN packet pump
 *   (gVisor stack), outbound sockets.
 * - [autoDetectInterfaceControl] calls [VpnService.protect] so libbox's own
 *   outbound sockets bypass the TUN (no routing loop).
 *
 * Play policy: the VPN screen carries a prominent disclosure that traffic is
 * routed through third-party servers.
 */
class SingBoxVpnService : VpnService(), PlatformInterface {

    companion object {
        const val ACTION_START = "com.teampkai.clickbrowser.vpn.START"
        const val ACTION_STOP = "com.teampkai.clickbrowser.vpn.STOP"
        const val EXTRA_CONFIG_JSON = "config_json"
        const val EXTRA_SERVER_NAME = "server_name"

        private const val NOTIF_ID = 4402
        private const val CHANNEL_ID = "click_vpn"
        private const val TAG = "ClickVpn"

        private val _state = MutableStateFlow(VpnState.DISCONNECTED)
        val state: StateFlow<VpnState> = _state.asStateFlow()
        private val _serverName = MutableStateFlow("")
        val serverName: StateFlow<String> = _serverName.asStateFlow()
        private val _error = MutableStateFlow("")
        val error: StateFlow<String> = _error.asStateFlow()

        @Volatile private var libboxReady = false

        /** One-time libbox init. Must run before any other libbox call. */
        fun setupLibboxOnce(appContext: android.content.Context) {
            if (libboxReady) return
            synchronized(this) {
                if (libboxReady) return
                val base = appContext.filesDir.absolutePath
                val working = File(appContext.filesDir, "libbox/working").apply { mkdirs() }.absolutePath
                val temp = File(appContext.cacheDir, "libbox/temp").apply { mkdirs() }.absolutePath
                val opts = SetupOptions().apply {
                    basePath = base
                    workingPath = working
                    tempPath = temp
                }
                Libbox.setup(opts)
                // Redirect Go stderr into logcat for diagnostics.
                try { Libbox.redirectStderr(File(working, "stderr.log").absolutePath) }
                catch (t: Throwable) { Log.w(TAG, "redirectStderr failed", t) }
                libboxReady = true
                Log.i(TAG, "libbox setup done")
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var boxService: BoxService? = null
    @Volatile private var tunFd: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val configJson = intent?.getStringExtra(EXTRA_CONFIG_JSON).orEmpty()
                val name = intent?.getStringExtra(EXTRA_SERVER_NAME).orEmpty()
                if (configJson.isBlank()) {
                    Log.e(TAG, "START without config — refusing")
                    _error.value = "Missing VPN configuration."
                    _state.value = VpnState.ERROR
                    stopSelf()
                    return START_NOT_STICKY
                }
                startTunnel(configJson, name)
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        Log.i(TAG, "VPN revoked by system/user")
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel()
        scope.cancel()
        super.onDestroy()
    }

    // ---------- tunnel lifecycle ----------

    private fun startTunnel(configJson: String, serverName: String) {
        if (_state.value == VpnState.CONNECTING || _state.value == VpnState.CONNECTED) {
            Log.i(TAG, "Tunnel already up — ignoring duplicate start")
            return
        }
        _state.value = VpnState.CONNECTING
        _serverName.value = serverName
        _error.value = ""
        startForegroundCompat(buildNotification("Connecting…"))

        scope.launch {
            try {
                setupLibboxOnce(applicationContext)

                // Validate config before handing it to Go (a bad config can
                // crash the process inside native code — fail in Java first).
                try {
                    Libbox.checkConfig(configJson)
                } catch (t: Throwable) {
                    throw IllegalArgumentException(
                        "Invalid VPN config: ${t.message}", t)
                }

                val svc = Libbox.newService(
                    StringBox(configJson),
                    this@SingBoxVpnService,
                )
                boxService = svc
                svc.start()

                _state.value = VpnState.CONNECTED
                startForegroundCompat(buildNotification("Connected — $serverName"))
                Log.i(TAG, "Tunnel CONNECTED ($serverName)")
            } catch (t: Throwable) {
                Log.e(TAG, "Tunnel start failed", t)
                _error.value = t.message ?: "Connection failed"
                _state.value = VpnState.ERROR
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
                stopSelf()
            }
        }
    }

    private fun stopTunnel() {
        try { boxService?.close() } catch (t: Throwable) {
            Log.w(TAG, "boxService.close failed", t)
        }
        boxService = null
        try { tunFd?.close() } catch (_: Throwable) {}
        tunFd = null
        _state.value = VpnState.DISCONNECTED
        _serverName.value = ""
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
        Log.i(TAG, "Tunnel stopped")
    }

    // ---------- PlatformInterface (called by libbox/Go) ----------

    /**
     * libbox asks us to open the TUN. We build it with Android's
     * VpnService.Builder: full default route so ALL traffic enters the
     * tunnel. Returns the raw fd number for Go.
     */
    override fun openTun(options: TunOptions): Long {
        val builder = Builder()
            .setSession("Click VPN")
            .setMtu(options.mtu)
            .addAddress("172.19.0.1", 30)
            .addAddress("fdfe:dcba:9876::1", 126)
            // FULL tunnel: default routes (this is what changes the IP).
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("172.19.0.1")
            .setBlocking(false)
        // Let Android know this is metered-safe; keep underlying networks.
        if (Build.VERSION.SDK_INT >= 29) {
            try { builder.setMetered(false) } catch (_: Throwable) {}
        }
        val pfd = builder.establish()
            ?: throw IllegalStateException("VpnService.Builder.establish() returned null")
        tunFd = pfd
        Log.i(TAG, "TUN established (fd=${pfd.fd}, mtu=${options.mtu})")
        return pfd.fd.toLong()
    }

    /**
     * libbox calls this for every outbound socket it creates. We MUST
     * protect() them so they bypass the TUN — otherwise the tunnel's own
     * traffic loops back into the TUN forever.
     */
    override fun autoDetectInterfaceControl(fd: Int): Boolean {
        val ok = protect(fd)
        if (!ok) Log.w(TAG, "protect($fd) failed")
        return ok
    }

    override fun getInterfaces(): String {
        // Minimal interface list; libbox uses it for auto_detect_interface.
        // Returning empty is acceptable — libbox falls back to passive
        // detection — but we provide the basics when available.
        return try {
            val sb = StringBuilder()
            sb.append("[")
            var first = true
            val ifaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val ni = ifaces.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                if (!first) sb.append(",")
                first = false
                sb.append("{\"name\":\"").append(ni.name).append("\",")
                sb.append("\"index\":").append(ni.index).append("}")
            }
            sb.append("]")
            sb.toString()
        } catch (t: Throwable) {
            Log.w(TAG, "getInterfaces failed", t)
            "[]"
        }
    }

    override fun usePlatformAutoDetect(): Boolean = true

    override fun openURL(url: String?) {
        // libbox occasionally asks to open a URL (e.g. for captive portals).
        if (url.isNullOrBlank()) return
        try {
            val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (t: Throwable) {
            Log.w(TAG, "openURL failed", t)
        }
    }

    override fun writeLog(message: String?) {
        if (!message.isNullOrBlank()) Log.i("libbox", message)
    }

    override fun useProcFS(): Boolean = false

    // Unused on Android for our config (kept for interface completeness).
    override fun findConnectionOwner(
        ipProtocol: Int, sourceAddress: String?, sourcePort: Int,
        destinationAddress: String?, destinationPort: Int,
    ): Int = -1

    override fun packageNameByUid(uid: Int): String = ""

    override fun uidByPackageName(packageName: String?): Int = -1

    override fun startLocalHTTPServer(): StringBox = StringBox("")

    override fun stopLocalHTTPServer() {}

    override fun useSystemProxy(): Boolean = false

    override fun systemProxyStatus(): String = ""

    override fun setInterfaceUpdateListener(listener: InterfaceUpdateListener?) {}

    // ---------- notification ----------

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

    private fun buildNotification(status: String): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Click VPN",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val stopIntent = Intent(this, SingBoxVpnService::class.java)
            .setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(
            this, 2, stopIntent,
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
            .setContentTitle("Click VPN")
            .setContentText(status)
            .setOngoing(true)
            .addAction(0, "Disconnect", stopPi)
        if (openPi != null) b.setContentIntent(openPi)
        return b.build()
    }
}
