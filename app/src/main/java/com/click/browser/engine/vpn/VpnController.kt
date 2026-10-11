package com.click.browser.engine.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.toMutablePreferences
import androidx.datastore.preferences.core.toPreferences
import com.click.browser.engine.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * UI-facing control for the Click VPN tunnel.
 *
 * - [prepareIntent]: null = VPN permission already granted → [start] directly.
 *   Otherwise launch the returned intent and call [start] on RESULT_OK.
 * - [start]: resolves the selected server → builds sing-box JSON → starts
 *   [SingBoxVpnService]. For WARP this registers the device on first use.
 * - Server list + WARP credentials persist in DataStore.
 */
object VpnController {

    private const val TAG = "VpnCtrl"

    private val KEY_SELECTED_SERVER = stringPreferencesKey("vpn_selected_server_id")
    private val KEY_CUSTOM_SERVERS = stringPreferencesKey("vpn_custom_servers_json")
    private val KEY_WARP_CREDS = stringPreferencesKey("vpn_warp_creds_json")
    private val KEY_VPN_DISCLOSURE_ACK = stringPreferencesKey("vpn_disclosure_ack")

    /** Null = permission already granted, safe to [start]. */
    fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)

    /**
     * Starts the tunnel for the currently selected server.
     * Returns false (with [SingBoxVpnService.error] set) when the server
     * can't be resolved — the UI must surface the error honestly.
     */
    suspend fun start(context: Context): Boolean = withContext(Dispatchers.IO) {
        val server = getSelectedServer(context) ?: run {
            Log.w(TAG, "No server selected")
            return@withContext false
        }
        val configJson: String = when (server.kind) {
            VpnServer.ServerKind.BUILT_IN_WARP -> {
                val creds = WarpRegistrar.ensureCredentials(getWarpCreds(context))
                    ?: run {
                        Log.e(TAG, "WARP registration failed")
                        return@withContext false
                    }
                saveWarpCreds(context, creds)
                VpnConfigBuilder.buildWarpConfig(creds)
            }
            // Built-in VLESS preset and user-added servers share the proxy-URL path.
            VpnServer.ServerKind.BUILT_IN_VLESS,
            VpnServer.ServerKind.CUSTOM -> {
                VpnConfigBuilder.buildCustomConfig(server.config) ?: run {
                    Log.e(TAG, "Custom config parse failed")
                    return@withContext false
                }
            }
        }
        val i = Intent(context, SingBoxVpnService::class.java)
            .setAction(SingBoxVpnService.ACTION_START)
            .putExtra(SingBoxVpnService.EXTRA_CONFIG_JSON, configJson)
            .putExtra(SingBoxVpnService.EXTRA_SERVER_NAME, server.name)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
        else context.startService(i)
        true
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, SingBoxVpnService::class.java)
                .setAction(SingBoxVpnService.ACTION_STOP)
        )
    }

    val state get() = SingBoxVpnService.state
    val serverName get() = SingBoxVpnService.serverName
    val error get() = SingBoxVpnService.error

    // ---------- server list ----------

    /** All servers: built-in WARP + built-in Canada VPS + user's custom entries. */
    suspend fun getServers(context: Context): List<VpnServer> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf(
                VpnServer.warpPlaceholder(),
                VpnServer.canadaVps(),
            )
            list.addAll(getCustomServers(context))
            list
        }

    suspend fun getSelectedServer(context: Context): VpnServer? =
        withContext(Dispatchers.IO) {
            val prefs = context.dataStore.data.first()
            val id = prefs[KEY_SELECTED_SERVER] ?: VpnServer.WARP_ID
            getServers(context).find { it.id == id }
        }

    suspend fun selectServer(context: Context, id: String) {
        withContext(Dispatchers.IO) {
            context.dataStore.updateData { it.toMutablePreferences()
                .apply { set(KEY_SELECTED_SERVER, id) }.toPreferences() }
        }
    }

    suspend fun addCustomServer(
        context: Context, name: String, proxyUrl: String,
    ): Boolean = withContext(Dispatchers.IO) {
        // Validate by building the config first — reject garbage early.
        if (VpnConfigBuilder.buildCustomConfig(proxyUrl) == null) return@withContext false
        val server = VpnServer(
            id = "custom-${System.currentTimeMillis()}",
            name = name.ifBlank { "Custom server" },
            countryCode = "",
            flagEmoji = "\uD83C\uDF10",
            kind = VpnServer.ServerKind.CUSTOM,
            config = proxyUrl,
        )
        val updated = getCustomServers(context) + server
        saveCustomServers(context, updated)
        true
    }

    suspend fun removeCustomServer(context: Context, id: String) {
        withContext(Dispatchers.IO) {
            val updated = getCustomServers(context).filter { it.id != id }
            saveCustomServers(context, updated)
            // Fall back to WARP if the selected server was removed.
            val prefs = context.dataStore.data.first()
            if (prefs[KEY_SELECTED_SERVER] == id) {
                context.dataStore.updateData { it.toMutablePreferences()
                    .apply { set(KEY_SELECTED_SERVER, VpnServer.WARP_ID) }
                    .toPreferences() }
            }
        }
    }

    private suspend fun getCustomServers(context: Context): List<VpnServer> {
        return try {
            val prefs = context.dataStore.data.first()
            val raw = prefs[KEY_CUSTOM_SERVERS].orEmpty()
            if (raw.isBlank()) return emptyList()
            val arr = JSONArray(raw)
            List(arr.length()) { VpnServer.fromJson(arr.getJSONObject(it)) }
        } catch (t: Throwable) {
            Log.w(TAG, "Custom servers parse failed", t)
            emptyList()
        }
    }

    private suspend fun saveCustomServers(context: Context, servers: List<VpnServer>) {
        val arr = JSONArray()
        servers.forEach { arr.put(it.toJson()) }
        context.dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(KEY_CUSTOM_SERVERS, arr.toString())
            }.toPreferences()
        }
    }

    // ---------- WARP credentials ----------

    private suspend fun getWarpCreds(context: Context): WarpCredentials? {
        return try {
            val prefs = context.dataStore.data.first()
            val raw = prefs[KEY_WARP_CREDS] ?: return null
            val o = JSONObject(raw)
            WarpCredentials(
                privateKey = o.getString("private_key"),
                peerPublicKey = o.getString("peer_public_key"),
                endpointHost = o.getString("endpoint_host"),
                endpointPort = o.getInt("endpoint_port"),
                localAddresses = List(o.getJSONArray("addrs").length()) {
                    o.getJSONArray("addrs").getString(it)
                },
                reserved = List(o.getJSONArray("reserved").length()) {
                    o.getJSONArray("reserved").getInt(it)
                },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "WARP creds parse failed", t)
            null
        }
    }

    private suspend fun saveWarpCreds(context: Context, creds: WarpCredentials) {
        val o = JSONObject()
            .put("private_key", creds.privateKey)
            .put("peer_public_key", creds.peerPublicKey)
            .put("endpoint_host", creds.endpointHost)
            .put("endpoint_port", creds.endpointPort)
            .put("addrs", JSONArray(creds.localAddresses))
            .put("reserved", JSONArray(creds.reserved))
        context.dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(KEY_WARP_CREDS, o.toString())
            }.toPreferences()
        }
    }

    // ---------- Play-policy disclosure ----------

    /**
     * The VPN screen shows a prominent disclosure on first open; the user
     * must acknowledge before the toggle works. Required by Play policy for
     * VpnService apps.
     */
    suspend fun isDisclosureAcked(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            context.dataStore.data.first()[KEY_VPN_DISCLOSURE_ACK] == "1"
        }

    suspend fun ackDisclosure(context: Context) {
        withContext(Dispatchers.IO) {
            context.dataStore.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    set(KEY_VPN_DISCLOSURE_ACK, "1")
                }.toPreferences()
            }
        }
    }

    /** Play-policy disclosure text (also shown in Settings). */
    const val DISCLOSURE_TEXT =
        "Click VPN routes ALL of your device's internet traffic through " +
        "third-party servers (Cloudflare WARP, Prince's Canada VPS, or a " +
        "custom server you add yourself). The server operator can see the " +
        "sites you visit. All DNS queries travel inside the encrypted " +
        "tunnel — use the DNS Leak Test to verify."
}
