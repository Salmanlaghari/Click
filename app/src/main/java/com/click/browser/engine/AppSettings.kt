package com.click.browser.engine

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.json.JSONArray
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
}
