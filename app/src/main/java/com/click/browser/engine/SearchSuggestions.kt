package com.click.browser.engine

import com.click.browser.data.HistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** One row in the address-bar suggestion list. */
data class SearchSuggestion(
    /** Display text: a search phrase, or a history entry's title. */
    val text: String,
    /** Set for history matches: picking the row opens this URL directly. */
    val url: String? = null,
    val fromHistory: Boolean = false
)

/**
 * Address-bar search suggestions (1.8.7 overhaul, A-list #2). Two sources:
 * local history matches (instant, offline) and the selected search engine's
 * public suggest endpoint (the same OpenSearch-style JSON every major engine
 * serves). Queries go only to the user's own selected engine — never to a
 * third party — and any failure simply yields no remote rows.
 */
object SearchSuggestions {
    const val MAX_REMOTE = 6
    const val MAX_LOCAL = 4
    const val MAX_TOTAL = 8

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Suggest endpoint for [engine]; [encodedQuery] must already be
     * URL-encoded. Null for engines with no public endpoint we rely on —
     * those get local history suggestions only.
     */
    fun suggestUrl(engine: String, encodedQuery: String): String? = when (engine) {
        "Google" -> "https://suggestqueries.google.com/complete/search?client=firefox&q=$encodedQuery"
        "Bing" -> "https://api.bing.com/osjson.aspx?query=$encodedQuery"
        "DuckDuckGo" -> "https://duckduckgo.com/ac/?q=$encodedQuery&type=list"
        "Brave Search" -> "https://search.brave.com/api/suggest?q=$encodedQuery"
        else -> null
    }

    /** Parses the OpenSearch-style response: `["query", ["s1", "s2", ...], ...]`. */
    fun parseResponse(body: String, limit: Int = MAX_REMOTE): List<String> = try {
        val suggestions = JSONArray(body).optJSONArray(1) ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until suggestions.length()) {
            val item = suggestions.optString(i).trim()
            if (item.isNotEmpty()) out.add(item)
            if (out.size >= limit) break
        }
        out
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * History rows whose title or URL contains the query (case-insensitive),
     * one row per URL. [history] must already be newest-first — that is the
     * order BrowserRepository.historyFlow guarantees — so matches keep that
     * order without re-sorting on every keystroke.
     */
    fun localMatches(
        query: String,
        history: List<HistoryItem>,
        limit: Int = MAX_LOCAL
    ): List<SearchSuggestion> {
        val needle = query.trim().lowercase()
        if (needle.length < 2) return emptyList()
        val seenUrls = HashSet<String>()
        return history.asSequence()
            .filter { it.title.lowercase().contains(needle) || it.url.lowercase().contains(needle) }
            .filter { seenUrls.add(it.url) }
            .take(limit)
            .map {
                SearchSuggestion(
                    text = it.title.ifBlank { it.url },
                    url = it.url,
                    fromHistory = true
                )
            }
            .toList()
    }

    /**
     * Local rows first, then remote phrases, skipping phrases that repeat a
     * local row's text or the query itself.
     */
    fun merge(
        local: List<SearchSuggestion>,
        remote: List<String>,
        query: String,
        limit: Int = MAX_TOTAL
    ): List<SearchSuggestion> {
        val seen = HashSet<String>()
        local.forEach { seen.add(it.text.lowercase()) }
        val out = ArrayList(local)
        for (phrase in remote) {
            if (out.size >= limit) break
            val key = phrase.trim().lowercase()
            if (key.isEmpty() || key == query.trim().lowercase()) continue
            if (seen.add(key)) out.add(SearchSuggestion(text = phrase.trim()))
        }
        return out
    }

    /** Remote phrases for [query] from [engine]'s endpoint; empty on any failure. */
    suspend fun fetchRemote(engine: String, query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        val url = suggestUrl(engine, URLEncoder.encode(trimmed, "UTF-8")) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Click Browser")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) emptyList()
                    else parseResponse(response.body?.string().orEmpty())
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
