package com.click.browser.engine

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-mode personalization (V9 "1 Browser, 3 Engines" identity).
 *
 * Each browsing mode feels like a DIFFERENT browser:
 * - its own home quick-sites (Simple = everyday sites, Developer = dev tools,
 *   Hack = ethical-hacking learning platforms — legitimate educational sites only)
 * - its own default search engine (user-changeable per mode)
 *
 * Quick sites are fully user-customizable (add / edit / remove); the stored
 * list is seeded from the per-mode defaults on first run.
 */
data class QuickSiteDef(
    val label: String,
    val url: String,
    val bgArgb: Long,
    val glyph: String,
    val glyphColorArgb: Long
)

object ModePersonalization {

    // ------------------------------------------------------------------
    // Search engines
    // ------------------------------------------------------------------

    /** Sensible default search engine per mode (Prince's requirement). */
    fun defaultSearchEngine(mode: BrowserMode): String = when (mode) {
        BrowserMode.SIMPLE -> "Google"
        BrowserMode.DEVELOPER -> "DuckDuckGo"
        BrowserMode.HACK -> "Brave Search"
    }

    /** Engines offered per mode in Settings (default first). */
    fun searchEngines(mode: BrowserMode): List<String> = when (mode) {
        BrowserMode.SIMPLE -> listOf("Google", "Bing", "Yahoo", "DuckDuckGo")
        BrowserMode.DEVELOPER -> listOf("DuckDuckGo", "Google", "Bing", "Yandex")
        BrowserMode.HACK -> listOf("Brave Search", "Startpage", "Ahmia", "Perplexity")
    }

    /**
     * Builds the search URL for an engine name. [encodedQuery] must already be
     * URL-encoded. Legacy names from earlier builds are mapped too.
     */
    fun searchUrl(engine: String, encodedQuery: String): String = when (engine) {
        "Google" -> "https://www.google.com/search?q=$encodedQuery"
        "Bing" -> "https://www.bing.com/search?q=$encodedQuery"
        "Yahoo" -> "https://search.yahoo.com/search?p=$encodedQuery"
        "DuckDuckGo" -> "https://duckduckgo.com/?q=$encodedQuery"
        "Yandex" -> "https://yandex.com/search/?text=$encodedQuery"
        "Baidu" -> "https://www.baidu.com/s?wd=$encodedQuery"
        "Brave Search" -> "https://search.brave.com/search?q=$encodedQuery"
        "Startpage", "Deep Search" -> "https://www.startpage.com/sp/search?query=$encodedQuery"
        "Ahmia", "Ahmia Search" -> "https://ahmia.fi/search/?q=$encodedQuery"
        "Perplexity", "AI Search", "integrated AI search" -> "https://www.perplexity.ai/search?q=$encodedQuery"
        else -> "https://www.google.com/search?q=$encodedQuery"
    }

    // ------------------------------------------------------------------
    // Default quick sites per mode
    // ------------------------------------------------------------------

    fun defaultQuickSites(mode: BrowserMode): List<QuickSiteDef> = when (mode) {
        BrowserMode.SIMPLE -> listOf(
            QuickSiteDef("Google", "https://google.com", 0xFF4285F4, "G", 0xFFFFFFFF),
            QuickSiteDef("YouTube", "https://youtube.com", 0xFFFF0000, "▶", 0xFFFFFFFF),
            QuickSiteDef("Facebook", "https://facebook.com", 0xFF1877F2, "f", 0xFFFFFFFF),
            QuickSiteDef("Wikipedia", "https://wikipedia.org", 0xFFF5F5F5, "W", 0xFF333333),
            QuickSiteDef("Amazon", "https://amazon.com", 0xFF232F3E, "a", 0xFFFF9900),
            QuickSiteDef("Instagram", "https://instagram.com", 0xFFE4405F, "◉", 0xFFFFFFFF),
            QuickSiteDef("X", "https://x.com", 0xFF111111, "𝕏", 0xFFFFFFFF)
        )
        BrowserMode.DEVELOPER -> listOf(
            QuickSiteDef("GitHub", "https://github.com", 0xFF24292F, "G", 0xFFFFFFFF),
            QuickSiteDef("Stack Overflow", "https://stackoverflow.com", 0xFFF48024, "S", 0xFFFFFFFF),
            QuickSiteDef("MDN", "https://developer.mozilla.org", 0xFF1B1B1B, "M", 0xFFFFFFFF),
            QuickSiteDef("Android", "https://developer.android.com", 0xFF3DDC84, "A", 0xFF111111),
            QuickSiteDef("Kotlin", "https://kotlinlang.org", 0xFF7F52FF, "K", 0xFFFFFFFF),
            QuickSiteDef("Hacker News", "https://news.ycombinator.com", 0xFFFF6600, "H", 0xFFFFFFFF),
            QuickSiteDef("CodePen", "https://codepen.io", 0xFF131417, "C", 0xFFFFFFFF)
        )
        // Ethical-hacking THEME only: legitimate cybersecurity LEARNING
        // platforms. Nothing illegal, no attack tools, no illicit content.
        BrowserMode.HACK -> listOf(
            QuickSiteDef("TryHackMe", "https://tryhackme.com", 0xFFC11130, "T", 0xFFFFFFFF),
            QuickSiteDef("HackTheBox", "https://www.hackthebox.com", 0xFF9FEF00, "H", 0xFF111111),
            QuickSiteDef("OWASP", "https://owasp.org", 0xFF255A7C, "O", 0xFFFFFFFF),
            QuickSiteDef("PortSwigger", "https://portswigger.net/web-security", 0xFFFF6633, "P", 0xFFFFFFFF),
            QuickSiteDef("OverTheWire", "https://overthewire.org", 0xFF1A1A1A, "W", 0xFF00FF00),
            QuickSiteDef("PicoCTF", "https://picoctf.org", 0xFF4B2E83, "C", 0xFFFFFFFF),
            QuickSiteDef("Cybrary", "https://www.cybrary.it", 0xFF0056D2, "C", 0xFFFFFFFF)
        )
    }

    // ------------------------------------------------------------------
    // Persistence (DataStore)
    // ------------------------------------------------------------------

    private fun sitesKey(mode: BrowserMode) =
        stringPreferencesKey("quick_sites_${mode.name.lowercase()}")

    private fun engineKey(mode: BrowserMode) =
        stringPreferencesKey("search_engine_${mode.name.lowercase()}")

    private fun encode(sites: List<QuickSiteDef>): String {
        val arr = JSONArray()
        for (s in sites) {
            arr.put(
                JSONObject()
                    .put("label", s.label)
                    .put("url", s.url)
                    .put("bg", s.bgArgb)
                    .put("glyph", s.glyph)
                    .put("fg", s.glyphColorArgb)
            )
        }
        return arr.toString()
    }

    private fun decode(json: String?): List<QuickSiteDef>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                QuickSiteDef(
                    label = o.getString("label"),
                    url = o.getString("url"),
                    bgArgb = o.getLong("bg"),
                    glyph = o.optString("glyph", "?"),
                    glyphColorArgb = o.optLong("fg", 0xFFFFFFFF)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Reactive per-mode quick sites (seeded from defaults on first run). */
    fun quickSitesFlow(context: Context, mode: BrowserMode): Flow<List<QuickSiteDef>> =
        context.dataStore.data.map { prefs ->
            decode(prefs[sitesKey(mode)]) ?: defaultQuickSites(mode)
        }

    private suspend fun saveSites(context: Context, mode: BrowserMode, sites: List<QuickSiteDef>) {
        context.dataStore.edit { prefs -> prefs[sitesKey(mode)] = encode(sites) }
    }

    private suspend fun currentSites(context: Context, mode: BrowserMode): List<QuickSiteDef> =
        decode(context.dataStore.data.first()[sitesKey(mode)]) ?: defaultQuickSites(mode)

    suspend fun addQuickSite(context: Context, mode: BrowserMode, site: QuickSiteDef) {
        val sites = currentSites(context, mode).toMutableList()
        // Replace same-label entry, else append.
        val idx = sites.indexOfFirst { it.label.equals(site.label, ignoreCase = true) }
        if (idx >= 0) sites[idx] = site else sites.add(site)
        saveSites(context, mode, sites)
    }

    suspend fun removeQuickSite(context: Context, mode: BrowserMode, label: String) {
        saveSites(context, mode, currentSites(context, mode).filterNot {
            it.label.equals(label, ignoreCase = true)
        })
    }

    suspend fun resetQuickSites(context: Context, mode: BrowserMode) {
        saveSites(context, mode, defaultQuickSites(mode))
    }

    /** Reactive per-mode search engine. */
    fun searchEngineFlow(context: Context, mode: BrowserMode): Flow<String> =
        context.dataStore.data.map { prefs ->
            val saved = prefs[engineKey(mode)]
            val offered = searchEngines(mode)
            if (saved != null && offered.contains(saved)) saved else defaultSearchEngine(mode)
        }

    suspend fun getSearchEngine(context: Context, mode: BrowserMode): String =
        searchEngineFlow(context, mode).first()

    suspend fun setSearchEngine(context: Context, mode: BrowserMode, engine: String) {
        context.dataStore.edit { prefs -> prefs[engineKey(mode)] = engine }
    }
}
