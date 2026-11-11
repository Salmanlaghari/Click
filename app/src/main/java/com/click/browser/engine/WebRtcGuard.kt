package com.click.browser.engine

import android.util.Log
import com.click.browser.extensions.ExtensionBrowserRuntime
import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
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
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID).accept(
            { extension ->
                if (extension == null) {
                    Log.w(TAG, "ensureBuiltIn returned no extension for $EXTENSION_ID")
                    return@accept
                }
                when (webRtcGuardSyncAction(blocked, extension.metaData.enabled)) {
                    WebRtcGuardSyncAction.ENABLE ->
                        controller.enable(extension, WebExtensionController.EnableSource.APP)
                            .accept({ }, { error -> Log.w(TAG, "Could not enable WebRTC guard", error) })
                    WebRtcGuardSyncAction.DISABLE ->
                        controller.disable(extension, WebExtensionController.EnableSource.APP)
                            .accept({ }, { error -> Log.w(TAG, "Could not disable WebRTC guard", error) })
                    WebRtcGuardSyncAction.NONE -> Unit
                }
            },
            { error -> Log.w(TAG, "Could not install the built-in WebRTC guard", error) }
        )
    }
}
