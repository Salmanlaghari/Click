package com.click.browser.engine

import org.json.JSONObject

/**
 * Per-site location permission. ASK = default browser behavior (prompt the user).
 */
enum class SiteLocationPref { ASK, ALLOW, BLOCK }

/**
 * Generic per-site device permission (camera, microphone, notifications).
 * ASK = default browser behavior (system prompt), ALLOW = grant silently,
 * BLOCK = deny silently.
 */
enum class SitePermissionPref { ASK, ALLOW, BLOCK }

/**
 * Per-site overrides for one host. Every field is nullable — null means
 * "use the global setting". This keeps the sheet honest: only explicit
 * choices are stored, everything else follows the app defaults.
 *
 * Desktop mode is NOT here — it lives in the existing desktop-hosts set
 * (BrowserRepository.desktopHostsFlow), which already works.
 */
data class SiteSettings(
    val javaScript: Boolean? = null,
    val adBlock: Boolean? = null,
    val location: SiteLocationPref? = null,
    val thirdPartyCookies: Boolean? = null,
    val textZoom: Int? = null,
    // Chrome-style expanded permissions (v2). All nullable = "use global default".
    val camera: SitePermissionPref? = null,
    val microphone: SitePermissionPref? = null,
    val notifications: SitePermissionPref? = null,
    val images: Boolean? = null,
    val popups: Boolean? = null,
    val sound: Boolean? = null,
    val backgroundSync: Boolean? = null
) {
    /** True when nothing is overridden — such entries are pruned from storage. */
    fun isEmpty(): Boolean =
        javaScript == null && adBlock == null && location == null &&
            thirdPartyCookies == null && textZoom == null &&
            camera == null && microphone == null && notifications == null &&
            images == null && popups == null && sound == null &&
            backgroundSync == null

    fun toJson(): JSONObject = JSONObject().apply {
        javaScript?.let { put("js", it) }
        adBlock?.let { put("adblock", it) }
        location?.let { put("location", it.name) }
        thirdPartyCookies?.let { put("tpc", it) }
        textZoom?.let { put("zoom", it) }
        camera?.let { put("cam", it.name) }
        microphone?.let { put("mic", it.name) }
        notifications?.let { put("notif", it.name) }
        images?.let { put("img", it) }
        popups?.let { put("popup", it) }
        sound?.let { put("snd", it) }
        backgroundSync?.let { put("bgsync", it) }
    }

    companion object {
        private fun readPermissionPref(obj: JSONObject, key: String): SitePermissionPref? {
            if (!obj.has(key)) return null
            return try {
                SitePermissionPref.valueOf(obj.getString(key))
            } catch (_: Exception) {
                null
            }
        }

        fun fromJson(obj: JSONObject): SiteSettings = SiteSettings(
            javaScript = if (obj.has("js")) obj.optBoolean("js") else null,
            adBlock = if (obj.has("adblock")) obj.optBoolean("adblock") else null,
            location = if (obj.has("location")) {
                try {
                    SiteLocationPref.valueOf(obj.getString("location"))
                } catch (_: Exception) {
                    null
                }
            } else null,
            thirdPartyCookies = if (obj.has("tpc")) obj.optBoolean("tpc") else null,
            textZoom = if (obj.has("zoom")) obj.optInt("zoom").takeIf { it in 50..300 } else null,
            camera = readPermissionPref(obj, "cam"),
            microphone = readPermissionPref(obj, "mic"),
            notifications = readPermissionPref(obj, "notif"),
            images = if (obj.has("img")) obj.optBoolean("img") else null,
            popups = if (obj.has("popup")) obj.optBoolean("popup") else null,
            sound = if (obj.has("snd")) obj.optBoolean("snd") else null,
            backgroundSync = if (obj.has("bgsync")) obj.optBoolean("bgsync") else null
        )

        fun mapFromJson(jsonStr: String): Map<String, SiteSettings> {
            val map = mutableMapOf<String, SiteSettings>()
            try {
                val root = JSONObject(jsonStr)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val host = keys.next()
                    val settings = fromJson(root.getJSONObject(host))
                    if (!settings.isEmpty()) map[host] = settings
                }
            } catch (_: Exception) {
            }
            return map
        }

        fun mapToJson(map: Map<String, SiteSettings>): String {
            val root = JSONObject()
            for ((host, settings) in map) {
                if (!settings.isEmpty()) root.put(host, settings.toJson())
            }
            return root.toString()
        }
    }
}

/**
 * Finds the settings for a host, checking the exact host first then parent
 * domains (so a rule for "example.com" also covers "www.example.com").
 * Same suffix-aware matching style as the desktop-hosts override.
 */
fun Map<String, SiteSettings>.forHost(host: String): SiteSettings? {
    if (host.isEmpty()) return null
    this[host]?.let { return it }
    return entries.firstOrNull { (h, _) ->
        host == h || host.endsWith(".$h")
    }?.value
}
