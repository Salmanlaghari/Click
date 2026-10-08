package com.click.browser.engine

import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * DataStore keys for the AI chat + privacy-guard settings.
 * The AI API key lives ONLY in DataStore on the device — it is never
 * hardcoded, never shipped in resources, and never committed to git.
 */
object AppSettings {

    val AI_API_KEY = stringPreferencesKey("ai_api_key")
    val AI_PROVIDER = stringPreferencesKey("ai_provider") // "groq" | "openrouter"
    val AI_MODEL = stringPreferencesKey("ai_model")
    val AI_REPORTS_JSON = stringPreferencesKey("ai_reports_json") // flagged AI responses (Play policy)

    val CUSTOM_HEADERS_JSON = stringPreferencesKey("custom_headers_json")
    val HEADER_SPOOF_ENABLED = booleanPreferencesKey("header_spoof_enabled")
    val FINGERPRINT_PROTECTION = booleanPreferencesKey("fingerprint_protection")
    val SECURE_DNS_ENABLED = booleanPreferencesKey("secure_dns_enabled")
    val UI_DARK_MODE = booleanPreferencesKey("ui_dark_mode")
    val WALLPAPER_URI = stringPreferencesKey("wallpaper_uri")

    data class CustomHeader(val name: String, val value: String)

    /** Auto-detect provider from the key prefix. `gsk_` -> Groq, `sk-or-` -> OpenRouter. */
    fun detectProvider(apiKey: String): String = when {
        apiKey.startsWith("sk-or-") -> "openrouter"
        apiKey.startsWith("gsk_") -> "groq"
        else -> "groq"
    }

    fun parseHeaders(json: String?): List<CustomHeader> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name").trim()
                val value = o.optString("value")
                if (name.isEmpty()) null else CustomHeader(name, value)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun headersToJson(headers: List<CustomHeader>): String {
        val arr = JSONArray()
        headers.forEach { h ->
            arr.put(JSONObject().put("name", h.name).put("value", h.value))
        }
        return arr.toString()
    }

    /**
     * Appends a user-flagged AI response to the on-device report log
     * (Google Play AI-Generated Content policy: in-app reporting).
     * Keeps the last 100 entries.
     */
    fun appendReport(existingJson: String?, text: String): String {
        val arr = try {
            JSONArray(existingJson.orEmpty())
        } catch (e: JSONException) {
            // Malformed stored JSON: log and start fresh rather than silently
            // dropping history without a trace (Kilo review).
            Log.w("AppSettings", "AI reports JSON corrupted, resetting", e)
            JSONArray()
        }
        arr.put(
            JSONObject()
                .put("text", text.take(2000))
                .put("ts", System.currentTimeMillis())
        )
        while (arr.length() > 100) arr.remove(0)
        return arr.toString()
    }
}
