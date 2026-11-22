package com.click.browser.engine

import android.content.Context
import android.util.Log
import com.click.browser.extensions.ExtensionBrowserRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Userscript support (overhaul B1, Route A): real `.user.js` scripts on
 * GeckoView tabs, Tampermonkey-style.
 *
 * Why a built-in extension: GeckoView gives the embedder no
 * evaluateJavascript and no per-page script hook. The documented vehicle
 * for first-party code in pages is a bundled WebExtension
 * (`WebExtensionController.ensureBuiltIn` over
 * `resource://android/assets/userscript_engine/`) — the same mechanism
 * [WebRtcGuard] uses. Its content script (runner.js) runs at
 * document_start on http/https pages and asks the app, per page load and
 * via native messaging, which installed scripts apply to the URL; the app
 * answers from [UserscriptStore]. Scripts execute in the content-script
 * sandbox (the Greasemonkey 4 model) with the classic GM API subset
 * implemented by the runner: GM_addStyle, GM_setValue / GM_getValue /
 * GM_deleteValue / GM_listValues (native-backed storage), GM_info,
 * GM_log, GM_xmlhttpRequest (native-relayed, http/https only, size
 * capped) and `unsafeWindow`. NOT supported (stated in the manager UI):
 * GM_openInTab, GM_registerMenuCommand, GM_notification, GM_setClipboard,
 * `@require` / `@resource`, and background update checks.
 *
 * The extension stays DISABLED — fully inert, zero page overhead — until
 * at least one userscript is installed and enabled. Userscripts never
 * run in private tabs: the per-session delegate for a private session
 * answers every request with an empty list.
 */
internal object UserscriptEngine {

    const val EXTENSION_ID = "userscripts@click.browser"
    const val EXTENSION_LOCATION = "resource://android/assets/userscript_engine/"
    const val NATIVE_APP = "click.userscripts"

    private const val TAG = "UserscriptEngine"
    private const val MAX_XHR_RESPONSE_CHARS = 2_000_000
    private const val XHR_TIMEOUT_SECONDS = 30L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(XHR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(XHR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(XHR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** In-memory snapshots so the message path never touches disk. */
    @Volatile private var cachedScripts: List<InstalledUserscript> = emptyList()
    @Volatile private var cachedValues: Map<String, JSONObject> = emptyMap()
    @Volatile private var cacheLoaded = false

    @Volatile private var desiredEnabled: Boolean? = null
    @Volatile private var toggleInFlight = false

    /**
     * Reloads the store snapshot and reconciles the built-in extension's
     * enabled state (enabled ⇔ at least one enabled script exists).
     * Called at startup and after every manager-screen mutation.
     */
    fun sync(context: Context) {
        val appContext = context.applicationContext
        capturedAppContext = appContext
        scope.launch {
            runCatching {
                cachedScripts = UserscriptStore.list(appContext)
                cachedValues = UserscriptStore.readValues(appContext)
                cacheLoaded = true
            }.onFailure { Log.w(TAG, "store snapshot failed", it) }
            val runtime = try {
                ExtensionBrowserRuntime.get(appContext)
            } catch (t: Throwable) {
                Log.w(TAG, "Gecko runtime unavailable for userscript sync", t)
                return@launch
            }
            desiredEnabled = cachedScripts.any { it.enabled }
            reconcile(runtime)
        }
    }

    /**
     * Registers the native-message delegate on one tab session. Safe to
     * call for every Gecko tab; [incognito] sessions get a delegate that
     * never hands out scripts.
     */
    fun attachSession(runtime: GeckoRuntime, session: GeckoSession, incognito: Boolean) {
        runtime.webExtensionController.ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID).accept(
            { extension ->
                if (extension == null) {
                    Log.w(TAG, "ensureBuiltIn returned no extension for $EXTENSION_ID")
                    return@accept
                }
                runCatching {
                    session.webExtensionController.setMessageDelegate(
                        extension,
                        SessionMessageDelegate(incognito),
                        NATIVE_APP
                    )
                }.onFailure { Log.w(TAG, "setMessageDelegate failed (session closed?)", it) }
            },
            { error -> Log.w(TAG, "Could not install the built-in userscript engine", error) }
        )
    }

    private fun reconcile(runtime: GeckoRuntime) {
        if (toggleInFlight) return // the in-flight toggle re-reconciles on completion
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID).accept(
            { extension ->
                if (extension == null) return@accept
                val want = desiredEnabled ?: return@accept
                val action = webRtcGuardSyncAction(want, extension.metaData.enabled, toggleInFlight)
                when (action) {
                    WebRtcGuardSyncAction.ENABLE -> applyToggle(controller, extension, enable = true)
                    WebRtcGuardSyncAction.DISABLE -> applyToggle(controller, extension, enable = false)
                    WebRtcGuardSyncAction.NONE -> Unit
                }
            },
            { error -> Log.w(TAG, "ensureBuiltIn failed during reconcile", error) }
        )
    }

    private fun applyToggle(
        controller: WebExtensionController,
        extension: WebExtension,
        enable: Boolean
    ) {
        toggleInFlight = true
        val result = if (enable) {
            controller.enable(extension, WebExtensionController.EnableSource.APP)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.APP)
        }
        result.accept(
            {
                toggleInFlight = false
                ExtensionBrowserRuntime.peek()?.let { reconcile(it) }
            },
            { error ->
                toggleInFlight = false
                Log.w(TAG, "Could not ${if (enable) "enable" else "disable"} userscript engine", error)
            }
        )
    }

    // ---- Native message handling -------------------------------------------

    private class SessionMessageDelegate(
        private val incognito: Boolean
    ) : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender
        ): GeckoResult<Any>? {
            val json = message as? JSONObject ?: return null
            return when (json.optString("type")) {
                "scripts" -> GeckoResult.fromValue(scriptsResponse(json.optString("url")))
                "setValue" -> {
                    handleSetValue(json)
                    null
                }
                "deleteValue" -> {
                    handleDeleteValue(json)
                    null
                }
                "xhr" -> handleXhr(json)
                "error" -> {
                    handleError(json)
                    null
                }
                else -> null
            }
        }

        private fun scriptsResponse(url: String): JSONObject {
            val scripts = JSONArray()
            if (!incognito && cacheLoaded && (url.startsWith("http://") || url.startsWith("https://"))) {
                for (script in cachedScripts) {
                    if (!script.enabled) continue
                    if (!UserscriptUrlMatcher.appliesTo(script.toMetadata(), url)) continue
                    scripts.put(JSONObject().apply {
                        put("id", script.id)
                        put("name", script.name)
                        put("namespace", script.namespace)
                        put("version", script.version)
                        put("description", script.description)
                        put("runAt", if (script.runAt == UserscriptRunAt.DOCUMENT_START) "start" else "end")
                        put("grantsNone", script.toMetadata().grantsNone)
                        put("code", script.code)
                        put("values", cachedValues[script.id] ?: JSONObject())
                    })
                }
            }
            return JSONObject().put("scripts", scripts)
        }

        private fun handleSetValue(json: JSONObject) {
            val scriptId = json.optString("scriptId")
            val key = json.optString("key")
            if (scriptId.isBlank() || key.isBlank()) return
            val value = if (json.isNull("value")) null else json.opt("value")
            // Update the snapshot first so a page reload sees the new value
            // even before the disk write lands.
            val updated = (cachedValues[scriptId]?.let { JSONObject(it.toString()) } ?: JSONObject())
            if (value == null) updated.remove(key) else updated.put(key, value)
            cachedValues = cachedValues + (scriptId to updated)
            val appContext = appContextOrNull() ?: return
            scope.launch { UserscriptStore.setValue(appContext, scriptId, key, value) }
        }

        private fun handleDeleteValue(json: JSONObject) {
            val scriptId = json.optString("scriptId")
            val key = json.optString("key")
            if (scriptId.isBlank() || key.isBlank()) return
            val updated = (cachedValues[scriptId]?.let { JSONObject(it.toString()) } ?: JSONObject())
            updated.remove(key)
            cachedValues = cachedValues + (scriptId to updated)
            val appContext = appContextOrNull() ?: return
            scope.launch { UserscriptStore.deleteValue(appContext, scriptId, key) }
        }

        private fun handleError(json: JSONObject) {
            val scriptId = json.optString("scriptId")
            val messageText = json.optString("message")
            if (scriptId.isBlank() || messageText.isBlank()) return
            cachedScripts = cachedScripts.map {
                if (it.id == scriptId) it.copy(lastError = messageText.take(300)) else it
            }
            val appContext = appContextOrNull() ?: return
            scope.launch { UserscriptStore.recordError(appContext, scriptId, messageText) }
        }

        /**
         * GM_xmlhttpRequest relay: the request runs natively (no CORS —
         * that is the point of the GM API), restricted to http/https,
         * 30 s timeouts and a 2 MB response cap. Cookies are NOT attached:
         * the native client has no access to the page's cookie store, and
         * scripts are told so in the manager UI.
         */
        private fun handleXhr(json: JSONObject): GeckoResult<Any> {
            val result = GeckoResult<Any>()
            val scriptId = json.optString("scriptId")
            val details = json.optJSONObject("details")
            val script = cachedScripts.firstOrNull { it.id == scriptId && it.enabled }
            val url = details?.optString("url").orEmpty()
            if (incognito || script == null || details == null ||
                !(url.startsWith("http://") || url.startsWith("https://"))
            ) {
                result.complete(JSONObject().put("error", "request not allowed"))
                return result
            }
            val method = details.optString("method", "GET").uppercase()
                .takeIf { it in setOf("GET", "POST", "PUT", "DELETE", "HEAD", "PATCH", "OPTIONS") }
                ?: "GET"
            val builder = Request.Builder().url(url)
            val headers = details.optJSONObject("headers")
            var contentType: String? = null
            if (headers != null) {
                val names = headers.keys()
                while (names.hasNext()) {
                    val name = names.next()
                    if (name.equals("Host", ignoreCase = true) ||
                        name.equals("Content-Length", ignoreCase = true)
                    ) continue
                    if (name.equals("Content-Type", ignoreCase = true)) {
                        contentType = headers.optString(name)
                    }
                    builder.header(name, headers.optString(name))
                }
            }
            val data = if (details.isNull("data")) null else details.optString("data")
            when (method) {
                "GET" -> builder.get()
                "HEAD" -> builder.head()
                "DELETE" -> builder.delete()
                else -> builder.method(
                    method,
                    (data ?: "").toRequestBody(
                        (contentType ?: "application/x-www-form-urlencoded").toMediaTypeOrNull()
                    )
                )
            }
            httpClient.newCall(builder.build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    result.complete(JSONObject().put("error", e.message ?: "network error"))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val text = readBodyCapped(it)
                        val headerText = StringBuilder()
                        for ((name, value) in it.headers) {
                            headerText.append(name).append(": ").append(value).append("\r\n")
                        }
                        result.complete(
                            JSONObject()
                                .put("status", it.code)
                                .put("statusText", it.message)
                                .put("responseText", text)
                                .put("responseHeaders", headerText.toString())
                                .put("finalUrl", it.request.url.toString())
                        )
                    }
                }
            })
            return result
        }

        private fun readBodyCapped(response: Response): String {
            val source = response.body?.source() ?: return ""
            val buffer = okio.Buffer()
            var total = 0L
            try {
                while (total <= MAX_XHR_RESPONSE_CHARS) {
                    val read = source.read(buffer, 8192)
                    if (read == -1L) break
                    total += read
                }
            } catch (_: IOException) {
                // Partial body is still useful to the script.
            }
            val bytes = buffer.readByteArray()
            val capped = if (bytes.size > MAX_XHR_RESPONSE_CHARS) {
                bytes.copyOf(MAX_XHR_RESPONSE_CHARS)
            } else bytes
            return String(capped, Charsets.UTF_8)
        }
    }

    // The store needs a Context; the engine is first touched from
    // MainActivity (sync/attach), which is also where the app context is
    // captured for later message-path writes.
    @Volatile private var capturedAppContext: Context? = null

    private fun appContextOrNull(): Context? = capturedAppContext

    /** Captures the application context for message-path store writes. */
    fun captureContext(context: Context) {
        capturedAppContext = context.applicationContext
    }
}
