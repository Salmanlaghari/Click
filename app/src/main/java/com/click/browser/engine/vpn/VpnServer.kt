package com.click.browser.engine.vpn

import org.json.JSONObject

/**
 * A VPN server entry.
 *
 * Two kinds:
 * - [BUILT_IN_WARP]: Cloudflare WARP (free, fast, no account). Connects to the
 *   nearest Cloudflare datacenter — country selection is NOT available with
 *   WARP. Honest limitation, shown in the UI.
 * - [CUSTOM]: user-pasted proxy URL (vless://, vmess://, trojan://, ss://,
 *   or a sing-box JSON outbound). Country/speed depend on the user's server.
 */
data class VpnServer(
    val id: String,
    val name: String,
    val countryCode: String, // ISO 3166-1 alpha-2, or "" for WARP-auto
    val flagEmoji: String,
    val kind: ServerKind,
    /** The raw config: WireGuard params for WARP, or the user's proxy URL. */
    val config: String,
) {
    enum class ServerKind { BUILT_IN_WARP, CUSTOM }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("cc", countryCode)
        .put("flag", flagEmoji)
        .put("kind", kind.name)
        .put("config", config)

    companion object {
        const val WARP_ID = "warp-auto"

        fun fromJson(o: JSONObject): VpnServer = VpnServer(
            id = o.getString("id"),
            name = o.getString("name"),
            countryCode = o.optString("cc", ""),
            flagEmoji = o.optString("flag", "\uD83C\uDF10"),
            kind = try {
                ServerKind.valueOf(o.getString("kind"))
            } catch (_: Exception) {
                ServerKind.CUSTOM
            },
            config = o.getString("config"),
        )

        /** The built-in Cloudflare WARP entry (credentials filled at connect). */
        fun warpPlaceholder(): VpnServer = VpnServer(
            id = WARP_ID,
            name = "Cloudflare WARP (Auto)",
            countryCode = "",
            flagEmoji = "\uD83C\uDF10",
            kind = ServerKind.BUILT_IN_WARP,
            config = "",
        )
    }
}

/** Connection state exposed to the UI. */
enum class VpnState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR;
}
