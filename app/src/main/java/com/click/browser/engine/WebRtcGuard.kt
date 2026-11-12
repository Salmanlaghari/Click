package com.click.browser.engine

import android.content.Context
import android.util.Log
import com.click.browser.extensions.ExtensionBrowserRuntime
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** What a WebRTC-guard sync pass must do to make the guard match the setting. */
internal enum class WebRtcGuardSyncAction { ENABLE, DISABLE, NONE }

/**
 * Pure sync decision: the guard extension must be enabled exactly when the
 * user has "Block WebRTC" on.
 */
internal fun webRtcGuardSyncAction(
    blocked: Boolean,
    currentlyEnabled: Boolean
): WebRtcGuardSyncAction = when {
    blocked && !currentlyEnabled -> WebRtcGuardSyncAction.ENABLE
    !blocked && currentlyEnabled -> WebRtcGuardSyncAction.DISABLE
    else -> WebRtcGuardSyncAction.NONE
}

/**
 * Sync decision while an enable/disable is still in flight. The extension's
 * reported `metaData.enabled` is stale until the pending GeckoResult
 * resolves, so no new toggle may be issued from it — NONE here; when the
 * in-flight toggle lands, its completion handler reconciles again against
 * the latest user intent (see [WebRtcGuard]). Without this, a rapid
 * ON→OFF could read the stale "disabled" state, issue nothing, and then
 * watch the pending enable land and strand the guard ON.
 */
internal fun webRtcGuardSyncAction(
    blocked: Boolean,
    currentlyEnabled: Boolean,
    toggleInFlight: Boolean
): WebRtcGuardSyncAction =
    if (toggleInFlight) WebRtcGuardSyncAction.NONE
    else webRtcGuardSyncAction(blocked, currentlyEnabled)

/**
 * Built-in WebRTC block for GeckoView tabs (the default engine for regular
 * tabs).
 *
 * Why an extension: GeckoView 153 exposes no supported API to disable
 * WebRTC — GeckoRuntimeSettings has no WebRTC/peerconnection setter and
 * there is no generic preference API — so `media.peerconnection.enabled`
 * cannot be set from the embedder. The documented mechanism for running
 * first-party code in pages is a bundled built-in WebExtension
 * (`WebExtensionController.ensureBuiltIn` over
 * `resource://android/assets/...`), whose content script runs at
 * document_start and replaces RTCPeerConnection with a throwing stub.
 *
 * The extension is Click's own code shipped in the APK (assets/webrtc_guard),
 * not a userscript and not remote code. It stays disabled — and therefore
 * completely inert — until the user turns on "Block WebRTC" in Privacy
 * Guards (AppSettings.WEBRTC_BLOCKED, default off). Built-in extensions are
 * excluded from the add-on manager's policy sweep, so nothing else in the
 * app enables or disables it behind the setting's back.
 */
internal object WebRtcGuard {
    const val EXTENSION_ID = "webrtc-guard@click.browser"
    const val EXTENSION_LOCATION = "resource://android/assets/webrtc_guard/"

    private const val TAG = "WebRtcGuard"

    /**
     * The user's latest intent, written synchronously on every [sync] call.
     * enable/disable are async GeckoResults and `metaData.enabled` only
     * reflects the state once they resolve, so sync decisions re-read this
     * flag instead of trusting whichever `blocked` value a late callback
     * captured — and a completed toggle reconciles against it again.
     */
    @Volatile
    private var desiredBlocked: Boolean? = null

    /** True while an enable/disable GeckoResult is still in flight. */
    @Volatile
    private var toggleInFlight = false

    /** Ensures the guard is installed in the shared runtime and matches [blocked]. */
    fun sync(context: Context, blocked: Boolean) {
        val runtime: GeckoRuntime = try {
            ExtensionBrowserRuntime.get(context.applicationContext)
        } catch (t: Throwable) {
            Log.w(TAG, "Gecko runtime unavailable for WebRTC guard sync", t)
            return
        }
        sync(runtime, blocked)
    }

    /** Ensures the guard is installed in [runtime] and matches [blocked]. */
    fun sync(runtime: GeckoRuntime, blocked: Boolean) {
        desiredBlocked = blocked
        reconcile(runtime)
    }

    private fun reconcile(runtime: GeckoRuntime) {
        if (toggleInFlight) {
            // The pending toggle's completion handler calls reconcile()
            // again, against the then-latest desiredBlocked — deferring is
            // safe and avoids issuing a second toggle from stale metadata.
            return
        }
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID).accept(
            { extension ->
                if (extension == null) {
                    Log.w(TAG, "ensureBuiltIn returned no extension for $EXTENSION_ID")
                    return@accept
                }
                // Read intent fresh: it may have changed while
                // ensureBuiltIn was resolving.
                val want = desiredBlocked ?: return@accept
                when (webRtcGuardSyncAction(want, extension.metaData.enabled, toggleInFlight)) {
                    WebRtcGuardSyncAction.ENABLE ->
                        applyToggle(runtime, controller, extension, enable = true)
                    WebRtcGuardSyncAction.DISABLE ->
                        applyToggle(runtime, controller, extension, enable = false)
                    WebRtcGuardSyncAction.NONE -> Unit
                }
            },
            { error -> Log.w(TAG, "Could not install the built-in WebRTC guard", error) }
        )
    }

    private fun applyToggle(
        runtime: GeckoRuntime,
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
                // Reconcile: the user may have flipped the setting again
                // while this toggle was landing (rapid ON→OFF).
                reconcile(runtime)
            },
            { error ->
                toggleInFlight = false
                Log.w(TAG, "Could not ${if (enable) "enable" else "disable"} WebRTC guard", error)
                reconcile(runtime)
            }
        )
    }
}
