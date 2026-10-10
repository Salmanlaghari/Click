package com.click.browser.engine

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Text scaling (accessibility): a global default text-size percent plus
 * per-host overrides, applied via WebView `textZoom`.
 *
 * - Range 50..300 (%), default 100.
 * - Per-host map is a small JSON object in DataStore; hosts are matched
 *   case-insensitively on the registrable host string.
 * - Pure helper: no Android framework calls besides DataStore, so every
 *   toggle in the UI genuinely persists and takes effect.
 */
object TextScaleStore {

    const val MIN = 50
    const val MAX = 300
    const val DEFAULT = 100
    const val STEP = 10

    fun clamp(pct: Int): Int = pct.coerceIn(MIN, MAX)

    /** Parse the per-host JSON map; tolerant of corrupt data (returns empty). */
    fun parseHosts(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(json)
            buildMap {
                o.keys().forEach { k ->
                    val v = o.optInt(k, DEFAULT)
                    if (k.isNotBlank()) put(k.lowercase(), clamp(v))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun toJson(map: Map<String, Int>): String =
        JSONObject(map as Map<*, *>).toString()

    /** Effective scale for [host]: per-host override, else global, else 100. */
    fun scaleFor(prefs: Preferences, host: String?): Int {
        val global = prefs[AppSettings.TEXT_SCALE_GLOBAL]?.let(::clamp) ?: DEFAULT
        val h = host?.lowercase()?.takeIf { it.isNotBlank() } ?: return global
        return parseHosts(prefs[AppSettings.TEXT_SCALE_HOSTS_JSON])[h] ?: global
    }

    suspend fun setGlobal(context: Context, pct: Int) {
        context.dataStore.edit { prefs ->
            prefs[AppSettings.TEXT_SCALE_GLOBAL] = clamp(pct)
        }
    }

    suspend fun setHostScale(context: Context, host: String, pct: Int) {
        val h = host.lowercase()
        context.dataStore.edit { prefs ->
            val map = parseHosts(prefs[AppSettings.TEXT_SCALE_HOSTS_JSON]).toMutableMap()
            map[h] = clamp(pct)
            prefs[AppSettings.TEXT_SCALE_HOSTS_JSON] = toJson(map)
        }
    }

    suspend fun clearHostScale(context: Context, host: String) {
        val h = host.lowercase()
        context.dataStore.edit { prefs ->
            val map = parseHosts(prefs[AppSettings.TEXT_SCALE_HOSTS_JSON]).toMutableMap()
            if (map.remove(h) != null) {
                prefs[AppSettings.TEXT_SCALE_HOSTS_JSON] = toJson(map)
            }
        }
    }

    /** Snapshot for compose state: (global, per-host map). */
    suspend fun snapshot(context: Context): Pair<Int, Map<String, Int>> {
        val prefs = context.dataStore.data.first()
        val global = prefs[AppSettings.TEXT_SCALE_GLOBAL]?.let(::clamp) ?: DEFAULT
        return global to parseHosts(prefs[AppSettings.TEXT_SCALE_HOSTS_JSON])
    }
}
