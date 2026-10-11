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
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface
import io.nekohasekai.libbox.Notification as LibboxNotification
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
 * - libbox owns the sing-box core via [CommandServer]: config lifecycle,
 *   TUN packet pump (gVisor stack), outbound sockets.
 * - [autoDetectInterfaceControl] calls [VpnService.protect] so libbox's own
 *   outbound sockets bypass the TUN (no routing loop).
 *
 * Play policy: the VPN screen carries a prominent disclosure that traffic is
 * routed through third-party servers.
 */
class SingBoxVpnService : VpnService(), PlatformInterface, CommandServerHandler {

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
                libboxReady = true
                Log.i(TAG, "libbox setup done")
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var commandServer: CommandServer? = null
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

                val server = CommandServer(
                    this@SingBoxVpnService, // CommandServerHandler
                    this@SingBoxVpnService, // PlatformInterface
                )
                server.start()
                server.startOrReloadService(configJson, OverrideOptions())
                commandServer = server

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
        try { commandServer?.closeService() } catch (t: Throwable) {
            Log.w(TAG, "commandServer.closeService failed", t)
        }
        try { commandServer?.close() } catch (t: Throwable) {
            Log.w(TAG, "commandServer.close failed", t)
        }
        commandServer = null
        try { tunFd?.close() } catch (_: Throwable) {}
        tunFd = null
        _state.value = VpnState.DISCONNECTED
        _serverName.value = ""
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
        Log.i(TAG, "Tunnel stopped")
    }

    // ---------- CommandServerHandler ----------

    override fun serviceStop() {
        Log.i(TAG, "CommandServer requested serviceStop")
        stopTunnel()
    }

    override fun serviceReload() {
        Log.i(TAG, "CommandServer requested serviceReload — not supported, ignoring")
    }

    override fun getSystemProxyStatus(): SystemProxyStatus? = null

    override fun setSystemProxyEnabled(enabled: Boolean) {
        // Not used: we are a TUN VPN, not a system proxy.
    }

    override fun triggerNativeCrash() {
        throw UnsupportedOperationException("triggerNativeCrash not supported")
    }

    override fun writeDebugMessage(message: String?) {
        if (!message.isNullOrBlank()) Log.d("libbox", message)
    }

    override fun connectSSHAgent(): Int {
        throw UnsupportedOperationException("SSH agent not supported")
    }

    // ---------- PlatformInterface (called by libbox/Go) ----------

    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    /**
     * libbox calls this for every outbound socket it creates. We MUST
     * protect() them so they bypass the TUN — otherwise the tunnel's own
     * traffic loops back into the TUN forever.
     */
    override fun autoDetectInterfaceControl(fd: Int) {
        val ok = protect(fd)
        if (!ok) Log.w(TAG, "protect($fd) failed")
    }

    /**
     * libbox asks us to open the TUN. We build it with Android's
     * VpnService.Builder: full default route so ALL traffic enters the
     * tunnel. Returns the raw fd number for Go.
     */
    override fun openTun(options: TunOptions?): Int {
        val mtu = options?.mtu ?: 9000
        val builder = Builder()
            .setSession("Click VPN")
            .setMtu(mtu)
            .addAddress("172.19.0.1", 30)
            .addAddress("fdfe:dcba:9876::1", 126)
            // FULL tunnel: default routes (this is what changes the IP).
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("172.19.0.1")
            .setBlocking(false)
        if (Build.VERSION.SDK_INT >= 29) {
            try { builder.setMetered(false) } catch (_: Throwable) {}
        }
        val pfd = builder.establish()
            ?: throw IllegalStateException("VpnService.Builder.establish() returned null")
        tunFd = pfd
        Log.i(TAG, "TUN established (fd=${pfd.fd}, mtu=$mtu)")
        return pfd.fd
    }

    override fun useProcFS(): Boolean = false

    override fun findConnectionOwner(
        ipProtocol: Int, sourceAddress: String?, sourcePort: Int,
        destinationAddress: String?, destinationPort: Int,
    ): ConnectionOwner {
        // Not used: our config has no per-app / connection-owner routing.
        throw UnsupportedOperationException("findConnectionOwner not supported")
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        // Interface monitoring not wired; libbox falls back to GetInterfaces().
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {}

    override fun getInterfaces(): NetworkInterfaceIterator {
        val result = mutableListOf<LibboxNetworkInterface>()
        try {
            val ifaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val ni = ifaces.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                val boxIface = LibboxNetworkInterface()
                boxIface.name = ni.name
                boxIface.index = ni.index
                try { boxIface.mtu = ni.mtu } catch (_: Throwable) {}
                val addrs = ni.interfaceAddresses.mapNotNull {
                    val host = it.address?.hostAddress ?: return@mapNotNull null
                    "$host/${it.networkPrefixLength}"
                }
                boxIface.addresses = StringArray(addrs.iterator())
                boxIface.type = Libbox.InterfaceTypeOther
                result.add(boxIface)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "getInterfaces failed", t)
        }
        return InterfaceArray(result.iterator())
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun readWIFIState(): WIFIState? = null

    override fun clearDNSCache() {}

    override fun sendNotification(notification: LibboxNotification?) {
        // We manage our own foreground notification; nothing to do.
    }

    override fun cancelNotification(identifier: String?, typeID: Int) {}

    override fun startNeighborMonitor(listener: NeighborUpdateListener?) {}

    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {}

    override fun registerMyInterface(name: String?) {}

    override fun usePlatformShell(): Boolean = false

    override fun checkPlatformShell() {}

    override fun openShellSession(
        user: PlatformUser?, command: String?, environ: StringIterator?,
        term: String?, rows: Int, cols: Int,
    ): ShellSession {
        throw UnsupportedOperationException("shell not supported")
    }

    override fun lookupUser(username: String?): PlatformUser {
        throw UnsupportedOperationException("lookupUser not supported")
    }

    override fun lookupSFTPServer(): String {
        throw UnsupportedOperationException("SFTP not supported")
    }

    override fun readSystemSSHHostKey(): String {
        throw UnsupportedOperationException("SSH not supported")
    }

    override fun tailscaleHostname(): String = ""

    override fun usePlatformBridge(): Boolean = false

    override fun createBridge(options: BridgeOptions?): BridgeSession {
        throw UnsupportedOperationException("bridge not supported")
    }

    // ---------- helpers ----------

    /** Minimal [StringIterator] over a Kotlin iterator. */
    private class StringArray(private val iterator: Iterator<String>) : StringIterator {
        override fun len(): Int = 0 // not used by core
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): String = iterator.next()
    }

    /** Minimal [NetworkInterfaceIterator] over a Kotlin iterator. */
    private class InterfaceArray(
        private val iterator: Iterator<LibboxNetworkInterface>,
    ) : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): LibboxNetworkInterface = iterator.next()
    }

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
