package com.click.browser.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.click.browser.extensions.ExtensionBrowserRuntime
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Developer Tools for GeckoView tabs (overhaul B2).
 *
 * GeckoView has no DevTools protocol and no evaluateJavascript, so the
 * WebView panel's approach cannot run on the default engine. The vehicle
 * is the same one as [WebRtcGuard] and [UserscriptEngine]: a bundled
 * built-in WebExtension (`assets/devtools_bridge/`) whose content script
 *  - wraps console.* / window errors into a bounded buffer and streams
 *    them over a native-messaging port, and
 *  - executes the panel's own first-party inspection snippets (the same
 *    bounded, redacting scripts in [DevToolsInspectionPolicy]) in the
 *    content-script sandbox and returns their string results encoded
 *    exactly like WebView's evaluateJavascript, so both engines share
 *    the panel and the parsers.
 *
 * The extension is enabled only while Developer mode is active and is
 * inert everywhere else. What it deliberately does NOT provide, because
 * GeckoView exposes no such data to the embedder: a per-request network
 * log (the panel says so on Gecko tabs) and response headers.
 */
internal object DevToolsBridge {

    const val EXTENSION_ID = "devtools-bridge@click.browser"
    const val EXTENSION_LOCATION = "resource://android/assets/devtools_bridge/"
    const val NATIVE_APP = "click.devtools"

    private const val TAG = "DevToolsBridge"
    private const val EVAL_TIMEOUT_MS = 10_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val nextMessageId = AtomicLong(0L)
    private val sessions = ConcurrentHashMap<GeckoSession, SessionBridge>()

    @Volatile private var desiredEnabled: Boolean? = null
    @Volatile private var toggleInFlight = false

    /**
     * Live console entries from any attached session. Set by the host
     * (MainActivity) while the DevTools panel is open; it is responsible
     * for checking the entry belongs to the panel's target tab and that
     * the user consented to page-message capture.
     */
    @Volatile
    var consoleListener: ((GeckoSession, LogEntry) -> Unit)? = null

    /** Enables the bridge exactly while Developer mode is active. */
    fun sync(context: Context, developerModeActive: Boolean) {
        val runtime = try {
            ExtensionBrowserRuntime.get(context.applicationContext)
        } catch (t: Throwable) {
            Log.w(TAG, "Gecko runtime unavailable for DevTools bridge sync", t)
            return
        }
        desiredEnabled = developerModeActive
        reconcile(runtime)
    }

    /** Registers the port delegate on one tab session. */
    fun attachSession(runtime: GeckoRuntime, session: GeckoSession) {
        runtime.webExtensionController.ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID).accept(
            { extension ->
                if (extension == null) {
                    Log.w(TAG, "ensureBuiltIn returned no extension for $EXTENSION_ID")
                    return@accept
                }
                runCatching {
                    session.webExtensionController.setMessageDelegate(
                        extension,
                        SessionMessageDelegate(session),
                        NATIVE_APP
                    )
                }.onFailure { Log.w(TAG, "setMessageDelegate failed (session closed?)", it) }
            },
            { error -> Log.w(TAG, "Could not install the built-in DevTools bridge", error) }
        )
    }

    /**
     * Runs [code] (one of Click's inspection snippets) in the tab's
     * bridge sandbox. The callback receives the result encoded exactly
     * like WebView evaluateJavascript (a JSON string literal), or null
     * when the bridge is unreachable, the eval failed, or it timed out.
     */
    fun evaluate(session: GeckoSession, code: String, callback: (String?) -> Unit) {
        val bridge = sessions[session]
        val port = bridge?.port
        if (bridge == null || port == null) {
            callback(null)
            return
        }
        val id = nextMessageId.incrementAndGet()
        bridge.pending[id] = { response ->
            val result = if (response.has("result") && !response.isNull("result")) {
                response.optString("result")
            } else null
            callback(result)
        }
        mainHandler.postDelayed({
            bridge.pending.remove(id)?.invoke(JSONObject().put("error", "timeout"))
        }, EVAL_TIMEOUT_MS)
        runCatching {
            port.postMessage(JSONObject().put("id", id).put("code", code))
        }.onFailure {
            bridge.pending.remove(id)
            callback(null)
        }
    }

    /** Fetches the bridge's buffered console entries for [session]. */
    fun fetchConsoleHistory(session: GeckoSession, callback: (List<LogEntry>) -> Unit) {
        val bridge = sessions[session]
        val port = bridge?.port
        if (bridge == null || port == null) {
            callback(emptyList())
            return
        }
        val id = nextMessageId.incrementAndGet()
        bridge.pending[id] = { response ->
            val entries = mutableListOf<LogEntry>()
            val arr = response.optJSONArray("entries")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    entries.add(
                        LogEntry(
                            type = e.optString("type", "log"),
                            message = e.optString("message"),
                            timestamp = e.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
            callback(entries)
        }
        mainHandler.postDelayed({
            bridge.pending.remove(id)?.invoke(JSONObject())
        }, EVAL_TIMEOUT_MS)
        runCatching {
            port.postMessage(JSONObject().put("id", id).put("kind", "consoleHistory"))
        }.onFailure {
            bridge.pending.remove(id)
            callback(emptyList())
        }
    }

    private fun reconcile(runtime: GeckoRuntime) {
        if (toggleInFlight) return
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID).accept(
            { extension ->
                if (extension == null) return@accept
                val want = desiredEnabled ?: return@accept
                when (webRtcGuardSyncAction(want, extension.metaData.enabled, toggleInFlight)) {
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
                Log.w(TAG, "Could not ${if (enable) "enable" else "disable"} DevTools bridge", error)
            }
        )
    }

    private class SessionBridge {
        @Volatile var port: WebExtension.Port? = null
        val pending = ConcurrentHashMap<Long, (JSONObject) -> Unit>()
    }

    private class SessionMessageDelegate(
        private val session: GeckoSession
    ) : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            val bridge = sessions.getOrPut(session) { SessionBridge() }
            bridge.port = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    val json = message as? JSONObject ?: return
                    // All app-side consumers (Compose state, panel lists)
                    // live on the main thread; port callbacks are posted
                    // there before anything is invoked.
                    if (json.optString("type") == "console") {
                        val e = json.optJSONObject("entry") ?: return
                        val entry = LogEntry(
                            type = e.optString("type", "log"),
                            message = e.optString("message"),
                            timestamp = e.optLong("timestamp", System.currentTimeMillis())
                        )
                        mainHandler.post { consoleListener?.invoke(session, entry) }
                        return
                    }
                    val id = json.optLong("id", -1L)
                    if (id > 0) {
                        val callback = bridge.pending.remove(id) ?: return
                        mainHandler.post { callback.invoke(json) }
                    }
                }

                override fun onDisconnect(port: WebExtension.Port) {
                    // A stale port from a previous page can disconnect
                    // after its replacement connected; the live port still
                    // owns the pending evaluations, so ignore stale
                    // disconnects entirely (the eval timeout still bounds
                    // anything that never gets answered).
                    if (bridge.port !== port) return
                    bridge.port = null
                    val stranded = bridge.pending.values.toList()
                    bridge.pending.clear()
                    val error = JSONObject().put("error", "disconnected")
                    mainHandler.post { stranded.forEach { it.invoke(error) } }
                    // Drop the per-session entry once its port is gone so
                    // closed tabs do not accumulate in the map for the
                    // process lifetime. A later page load reconnects and
                    // re-creates the entry via getOrPut.
                    sessions.remove(session, bridge)
                }
            })
        }
    }
}
