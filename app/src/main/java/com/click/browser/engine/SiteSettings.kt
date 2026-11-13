package com.click.browser.engine

import org.json.JSONObject

/**
 * Per-site overrides for content settings that Click currently enforces.
 * Every field is nullable — null means "use the global setting".
 *
 * Website microphone consent is stored separately as an exact-origin profile
 * permission. Camera and location preferences are intentionally not
 * represented: Click blocks those website capabilities outright (no Android
 * CAMERA / location permission is requested by the browser). Website
 * notifications and background sync have no WebView API to enforce per site,
 * and neither does the clipboard, so those are not represented either.
 * Legacy JSON keys are ignored when read.
 *
 * Nov 2026 additions, each wired to a real effect:
 * - cookies: first-party CookieManager accept flag (the manager is
 *   process-global on Android, so the value follows the most recently
 *   applied site — the same limitation as Click's global cookie flag).
 * - sound: mediaPlaybackRequiresUserGesture per WebView (autoplay gate).
 * - autoDownloads: downloads from the site are refused in the download
 *   funnel when set to false.
 * - protectedContent: RESOURCE_PROTECTED_MEDIA_ID is granted in
 *   onPermissionRequest only for sites explicitly allowed here.
 */
data class SiteSettings(
    val javaScript: Boolean? = null,
    val adBlock: Boolean? = null,
    val thirdPartyCookies: Boolean? = null,
    val textZoom: Int? = null,
    val images: Boolean? = null,
    val popups: Boolean? = null,
    val cookies: Boolean? = null,
    val sound: Boolean? = null,
    val autoDownloads: Boolean? = null,
    val protectedContent: Boolean? = null
) {
    /** True when nothing is overridden — such entries are pruned from storage. */
    fun isEmpty(): Boolean =
        javaScript == null && adBlock == null &&
            thirdPartyCookies == null && textZoom == null &&
            images == null && popups == null &&
            cookies == null && sound == null &&
            autoDownloads == null && protectedContent == null

    fun toJson(): JSONObject = JSONObject().apply {
        javaScript?.let { put("js", it) }
        adBlock?.let { put("adblock", it) }
        thirdPartyCookies?.let { put("tpc", it) }
        textZoom?.let { put("zoom", it) }
        images?.let { put("img", it) }
        popups?.let { put("popup", it) }
        cookies?.let { put("cook", it) }
        sound?.let { put("snd", it) }
        autoDownloads?.let { put("adl", it) }
        protectedContent?.let { put("drm", it) }
    }

    companion object {
        fun fromJson(obj: JSONObject): SiteSettings = SiteSettings(
            javaScript = if (obj.has("js")) obj.optBoolean("js") else null,
            adBlock = if (obj.has("adblock")) obj.optBoolean("adblock") else null,
            thirdPartyCookies = if (obj.has("tpc")) obj.optBoolean("tpc") else null,
            textZoom = if (obj.has("zoom")) obj.optInt("zoom").takeIf { it in 50..300 } else null,
            images = if (obj.has("img")) obj.optBoolean("img") else null,
            popups = if (obj.has("popup")) obj.optBoolean("popup") else null,
            cookies = if (obj.has("cook")) obj.optBoolean("cook") else null,
            sound = if (obj.has("snd")) obj.optBoolean("snd") else null,
            autoDownloads = if (obj.has("adl")) obj.optBoolean("adl") else null,
            protectedContent = if (obj.has("drm")) obj.optBoolean("drm") else null
        )

        /**
         * Parses supported site fields only. Legacy unsupported keys are
         * dropped so they cannot appear as active settings.
         */
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
 * Finds settings for a host: exact host first, then the most-specific parent
 * domain (so example.com applies to www.example.com, but not vice versa).
 */
fun Map<String, SiteSettings>.forHost(host: String): SiteSettings? {
    if (host.isEmpty()) return null
    this[host]?.let { return it }
    return entries
        .asSequence()
        .filter { (domain, _) -> host.endsWith(".$domain") }
        .maxByOrNull { (domain, _) -> domain.length }
        ?.value
}
