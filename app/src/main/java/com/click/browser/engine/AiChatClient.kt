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

/**
 * A chat message. role is "user", "assistant" or "error" (error is UI-only,
 * never sent to the API).
 */
data class ChatMessage(val role: String, val content: String)

data class AiProviderInfo(
    val id: String,
    val displayName: String,
    val apiUrl: String,
    val defaultModel: String,
    val keyPrefixHint: String,
    val keySignupUrl: String
)

object AiProviders {
    val GROQ = AiProviderInfo(
        id = "groq",
        displayName = "Groq",
        apiUrl = "https://api.groq.com/openai/v1/chat/completions",
        defaultModel = "llama-3.3-70b-versatile",
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

    fun byId(id: String): AiProviderInfo = if (id == "openrouter") OPENROUTER else GROQ
    fun all(): List<AiProviderInfo> = listOf(GROQ, OPENROUTER)
}

/**
 * Real AI chat client. Both Groq and OpenRouter speak the OpenAI-compatible
 * /chat/completions API. The user's key is sent ONLY in the Authorization
 * header of these HTTPS calls — it is never logged, never embedded in the
 * app, and lives only in the on-device DataStore.
 */
class AiChatClient(dns: Dns = Dns.SYSTEM) {

    private val http = OkHttpClient.Builder()
        .dns(dns)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Sends the conversation and returns the assistant's reply text.
     * Result.failure carries a human-readable error (HTTP status / message).
     */
    suspend fun send(
        apiKey: String,
        provider: AiProviderInfo,
        model: String,
        history: List<ChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray()
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
                // OpenRouter recommends identifying the app.
                reqBuilder.header("HTTP-Referer", "https://clickbrowser.app")
                reqBuilder.header("X-Title", "Click Browser")
            }

            val resp = http.newCall(reqBuilder.build()).execute()
            resp.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    val detail = try {
                        val err = JSONObject(text).optJSONObject("error")
                        err?.optString("message")?.takeIf { m -> m.isNotBlank() }
                            ?: text.take(300)
                    } catch (_: Exception) {
                        text.take(300)
                    }
                    return@withContext Result.failure(
                        Exception("HTTP ${it.code}: ${detail.ifBlank { "request failed" }}")
                    )
                }
                val content = JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "network error"))
        }
    }
}
