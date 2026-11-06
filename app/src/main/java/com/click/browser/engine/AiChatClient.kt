package com.click.browser.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GroundingSource(
    val title: String,
    val uri: String
)

/**
 * A chat message. role is "user", "assistant", "system", or "error"
 * ("error" and UI-only moderation "system" notices are never sent as user history).
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val sources: List<GroundingSource> = emptyList()
)

data class AiChatReply(
    val text: String,
    val sources: List<GroundingSource> = emptyList()
)

data class AiProviderInfo(
    val id: String,
    val displayName: String,
    val apiUrl: String,
    val defaultModel: String,
    val keyPrefixHint: String,
    val keySignupUrl: String
)

object AiRolePresets {
    data class RolePreset(val id: String, val label: String, val instruction: String)

    val ALL: List<RolePreset> = listOf(
        RolePreset(
            id = "browser",
            label = "Browser",
            instruction = "You are Click AI inside Click Browser. Help the user summarize web pages, compare facts, translate snippets, and answer questions clearly and concisely."
        ),
        RolePreset(
            id = "researcher",
            label = "Search",
            instruction = "You are Click Research Analyst. Provide accurate, well-structured answers in scannable bullet points."
        ),
        RolePreset(
            id = "translator",
            label = "Translate",
            instruction = "You are Click Multilingual Translator. Translate accurately and offer Urdu, Roman Urdu, or English explanations when helpful."
        ),
        RolePreset(
            id = "developer",
            label = "DevTools",
            instruction = "You are Click DevTools Engineer. Explain web standards, HTTP headers, Kotlin/Android, and JavaScript behavior with crisp technical accuracy."
        )
    )

    fun instructionFor(
        roleId: String,
        pageTitle: String = "",
        pageUrl: String = "",
        pageExcerpt: String = ""
    ): String {
        val base = ALL.firstOrNull { it.id == roleId }?.instruction ?: ALL.first().instruction
        if (pageUrl.isBlank() || pageUrl == "about:blank") return base
        return buildString {
            append(base)
            append("\n\nActive Browser Tab Context:")
            if (pageTitle.isNotBlank() && pageTitle != "New Tab") {
                append("\n- Title: ").append(pageTitle.take(240))
            }
            append("\n- URL: ").append(pageUrl.take(500))
            if (pageExcerpt.isNotBlank()) {
                append("\n- Visible Page Excerpt:\n").append(pageExcerpt.take(3000))
            }
        }
    }
}

object AiProviders {
    val GEMINI = AiProviderInfo(
        id = "gemini",
        displayName = "Google Gemini",
        apiUrl = "https://generativelanguage.googleapis.com/v1beta/models",
        defaultModel = "gemini-2.5-flash",
        keyPrefixHint = "AIza…",
        keySignupUrl = "https://aistudio.google.com/apikey"
    )
    val GROQ = AiProviderInfo(
        id = "groq",
        displayName = "Click AI (Groq)",
        apiUrl = "https://api.groq.com/openai/v1/chat/completions",
        defaultModel = "openai/gpt-oss-20b",
        keyPrefixHint = "gsk_…",
        keySignupUrl = "https://console.groq.com"
    )
    val OPENROUTER = AiProviderInfo(
        id = "openrouter",
        displayName = "OpenRouter",
        apiUrl = "https://openrouter.ai/api/v1/chat/completions",
        defaultModel = "auto",
        keyPrefixHint = "sk-or-…",
        keySignupUrl = "https://openrouter.ai"
    )

    fun byId(id: String): AiProviderInfo = when (id) {
        "gemini" -> GEMINI
        "openrouter" -> OPENROUTER
        else -> GROQ
    }

    fun all(): List<AiProviderInfo> = listOf(GEMINI, GROQ, OPENROUTER)
}

/**
 * Real AI chat client supporting Google Gemini (with optional Google Search
 * Grounding citations), Groq, and OpenRouter. Keys live only in on-device
 * DataStore (or BuildConfig fallback for Groq) and are never logged.
 */
class AiChatClient(dns: Dns = Dns.SYSTEM) {

    private val http = OkHttpClient.Builder()
        .dns(dns)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun send(
        apiKey: String,
        provider: AiProviderInfo,
        model: String,
        history: List<ChatMessage>
    ): Result<String> = sendRich(
        apiKey = apiKey,
        provider = provider,
        model = model,
        history = history
    ).map { it.text }

    suspend fun sendRich(
        apiKey: String,
        provider: AiProviderInfo,
        model: String,
        history: List<ChatMessage>,
        systemInstruction: String = "",
        enableGoogleSearch: Boolean = false
    ): Result<AiChatReply> = withContext(Dispatchers.IO) {
        if (provider.id == "gemini" || apiKey.startsWith("AIza")) {
            sendGemini(
                apiKey = apiKey,
                model = model.ifBlank { AiProviders.GEMINI.defaultModel },
                history = history,
                systemInstruction = systemInstruction,
                enableGoogleSearch = enableGoogleSearch
            )
        } else {
            sendOpenAiCompatible(
                apiKey = apiKey,
                provider = provider,
                model = model,
                history = history,
                systemInstruction = systemInstruction
            )
        }
    }

    private fun sendGemini(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemInstruction: String,
        enableGoogleSearch: Boolean
    ): Result<AiChatReply> {
        return try {
            val contents = JSONArray()
            history
                .filter { it.role == "user" || it.role == "assistant" }
                .takeLast(20)
                .forEach { msg ->
                    val geminiRole = if (msg.role == "assistant") "model" else "user"
                    val part = JSONObject().put("text", msg.content)
                    contents.put(
                        JSONObject()
                            .put("role", geminiRole)
                            .put("parts", JSONArray().put(part))
                    )
                }

            val payload = JSONObject().put("contents", contents)
            if (systemInstruction.isNotBlank()) {
                payload.put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemInstruction))
                    )
                )
            }
            if (enableGoogleSearch) {
                payload.put(
                    "tools",
                    JSONArray().put(JSONObject().put("google_search", JSONObject()))
                )
            }

            val cleanModel = model.removePrefix("models/").trim().ifBlank { "gemini-2.5-flash" }
            val endpoint = "${AiProviders.GEMINI.apiUrl}/$cleanModel:generateContent"
            val req = Request.Builder()
                .url(endpoint)
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = try {
                        JSONObject(raw).optJSONObject("error")?.optString("message")
                            ?.takeIf { it.isNotBlank() } ?: raw.take(300)
                    } catch (_: Exception) {
                        raw.take(300)
                    }
                    return Result.failure(Exception("HTTP ${resp.code}: ${detail.ifBlank { "Gemini request failed" }}"))
                }

                val root = JSONObject(raw)
                val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
                    ?: return Result.failure(Exception("Empty response from Gemini"))
                val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                val sb = StringBuilder()
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val t = parts.optJSONObject(i)?.optString("text").orEmpty()
                        if (t.isNotEmpty()) sb.append(t)
                    }
                }

                val sources = mutableListOf<GroundingSource>()
                val seen = HashSet<String>()
                val chunks = candidate.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
                if (chunks != null) {
                    for (i in 0 until chunks.length()) {
                        val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                        val uri = web.optString("uri").trim()
                        val title = web.optString("title").trim().ifBlank { uri }
                        if (uri.startsWith("http") && seen.add(uri)) {
                            sources.add(GroundingSource(title = title, uri = uri))
                        }
                    }
                }

                Result.success(
                    AiChatReply(
                        text = sb.toString().ifBlank { "No text returned." },
                        sources = sources.take(6)
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "network error"))
        }
    }

    private fun sendOpenAiCompatible(
        apiKey: String,
        provider: AiProviderInfo,
        model: String,
        history: List<ChatMessage>,
        systemInstruction: String
    ): Result<AiChatReply> {
        return try {
            val messages = JSONArray()
            if (systemInstruction.isNotBlank()) {
                messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
            }
            history
                .filter { it.role == "user" || it.role == "assistant" }
                .takeLast(20)
                .forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }

            val bodyJson = JSONObject()
                .put("model", model.ifBlank { provider.defaultModel })
                .put("messages", messages)
                .toString()
                .toRequestBody("application/json".toMediaType())

            val reqBuilder = Request.Builder()
                .url(provider.apiUrl)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(bodyJson)
            if (provider.id == "openrouter") {
                reqBuilder.header("HTTP-Referer", "https://clickbrowser.app")
                reqBuilder.header("X-Title", "Click Browser")
            }

            http.newCall(reqBuilder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = try {
                        val err = JSONObject(text).optJSONObject("error")
                        err?.optString("message")?.takeIf { m -> m.isNotBlank() }
                            ?: text.take(300)
                    } catch (_: Exception) {
                        text.take(300)
                    }
                    return Result.failure(
                        Exception("HTTP ${resp.code}: ${detail.ifBlank { "request failed" }}")
                    )
                }
                val content = JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                Result.success(AiChatReply(text = content))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "network error"))
        }
    }
}
