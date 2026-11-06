package com.click.browser.engine.vpn

import org.json.JSONObject

/**
 * A VPN server entry.
 *
 * Two kinds:
 * - [BUILT_IN_VLESS]: Prince's own Oracle VPS in Canada (VLESS + Reality).
 *   Fixed country (CA), tested working, no DNS leak.
 * - [CUSTOM]: user-pasted proxy URL (vless://, vmess://, trojan://, ss://,
 *   or a sing-box JSON outbound). Country/speed depend on the user's server.
 */
data class VpnServer(
    val id: String,
    val name: String,
    val countryCode: String, // ISO 3166-1 alpha-2, or "" when unknown
    val flagEmoji: String,
    val kind: ServerKind,
    /** The raw config: the user's proxy URL. */
    val config: String,
) {
    enum class ServerKind { BUILT_IN_VLESS, CUSTOM }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("cc", countryCode)
        .put("flag", flagEmoji)
        .put("kind", kind.name)
        .put("config", config)

    companion object {
        const val CANADA_VPS_ID = "canada-vps-oracle"

        /** Built-in VLESS + Reality server (Canada). */
        fun builtInVless(): VpnServer = VpnServer(
            id = CANADA_VPS_ID,
            name = "Canada VPS (Oracle)",
            countryCode = "CA",
            flagEmoji = "\uD83C\uDDE8\uD83C\uDDE6",
            kind = ServerKind.BUILT_IN_VLESS,
            config = "vless://3eb438a4-d770-4235-bb9a-f78fd4a4a5b8" +
                "@151.145.53.122:443" +
                "?encryption=none" +
                "&flow=xtls-rprx-vision" +
                "&security=reality" +
                "&sni=www.microsoft.com" +
                "&fp=chrome" +
                "&pbk=YQQKXtSZJQVC5Ys7zo7y2KAl-fDY3ymR4nBsnuIe3So" +
                "&sid=c2d9535f83dd635b" +
                "&type=tcp" +
                "&headerType=none" +
                "#Click-Canada-VPS",
        )

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

    }
}

/** Connection state exposed to the UI. */
enum class VpnState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR;
}
