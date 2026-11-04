package com.click.browser.engine

import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse
import com.click.browser.extensions.NormalTabExtensionRouter

/** Stable Gecko storage partition identifier matching Click's existing profile groups. */
internal fun geckoContextIdFor(mode: BrowserMode): String =
    if (mode == BrowserMode.ADVANCED) "click-profile-advanced" else "click-profile-default"

/**
 * GeckoView provider for an explicitly selected, non-private ordinary tab.
 * The Activity owns the shared runtime and app UI; this class owns one GeckoSession.
 */
internal class GeckoBrowserTabSession(
    context: Context,
    private val runtime: GeckoRuntime,
    private val tabId: String,
    mode: BrowserMode,
    initialUrl: String,
    private val extensionRouter: NormalTabExtensionRouter,
    initiallySelected: Boolean,
    initiallyResumed: Boolean,
    private val navigationTarget: (String) -> String?,
    private val onNavigationBlocked: (String) -> Unit,
    private val onUrlChanged: (String) -> Unit,
    private val onTitleChanged: (String) -> Unit,
    private val onProgressChanged: (Int, Boolean) -> Unit,
    private val onCanGoBackChanged: (Boolean) -> Unit,
    private val onCanGoForwardChanged: (Boolean) -> Unit,
    private val onFullscreenChanged: (Boolean) -> Unit,
    private val onExternalResponse: (WebResponse) -> Unit
) : BrowserTabSession {
    private val contextId = geckoContextIdFor(mode)
    private val settings = GeckoSessionSettings.Builder()
        .usePrivateMode(false)
        .useTrackingProtection(true)
        .contextId(contextId)
        .build()
    val session = GeckoSession(settings)
    private val geckoView = GeckoView(context)
    private var selected = initiallySelected
    private var hostResumed = initiallyResumed
    private var canBack = false
    private var canForward = false
    private var destroyed = false

    override val view: View get() = geckoView

    init {
        session.setPermissionDelegate(DenyNormalTabDevicePermissions)
        session.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                url?.let(onUrlChanged)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                canBack = canGoBack
                onCanGoBackChanged(canGoBack)
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                canForward = canGoForward
                onCanGoForwardChanged(canGoForward)
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny> {
                if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                    onNavigationBlocked(request.uri)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                val target = navigationTarget(request.uri)
                if (target == null) {
                    onNavigationBlocked(request.uri)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                if (target != request.uri) {
                    session.loadUri(target)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
        })
        session.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                onProgressChanged(0, true)
                onUrlChanged(url)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                onProgressChanged(100, false)
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                onProgressChanged(progress.coerceIn(0, 100), progress < 100)
            }
        })
        session.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                onTitleChanged(title.orEmpty().ifBlank { "Page" })
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                if (selected) onFullscreenChanged(fullScreen)
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                onExternalResponse(response)
            }
        })
        session.open(runtime)
        geckoView.setSession(session)
        extensionRouter.attach(tabId, session)
        if (initialUrl.isNotBlank() && initialUrl != "about:blank") {
            session.loadUri(initialUrl)
        }
        updateActiveState()
    }

    override fun canGoBack(): Boolean = canBack
    override fun goBack() { if (canBack) session.goBack() }
    override fun canGoForward(): Boolean = canForward
    override fun goForward() { if (canForward) session.goForward() }
    override fun loadUrl(url: String) { if (!destroyed) session.loadUri(url) }
    override fun reload() { if (!destroyed) session.reload() }
    override fun stopLoading() { if (!destroyed) session.stop() }

    override fun onPause() {
        hostResumed = false
        updateActiveState()
    }

    override fun pauseTimers() {
        if (!destroyed) session.setActive(false)
    }

    override fun onResume() {
        hostResumed = true
        updateActiveState()
    }

    override fun resumeTimers() {
        updateActiveState()
    }

    override fun setSelected(selected: Boolean) {
        this.selected = selected
        updateActiveState()
    }

    private fun updateActiveState() {
        if (destroyed) return
        val active = selected && hostResumed
        session.setActive(active)
        extensionRouter.setSelected(tabId, active)
    }

    override fun detachFromParent() {
        (geckoView.parent as? ViewGroup)?.removeView(geckoView)
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        extensionRouter.detach(tabId, session)
        runCatching { geckoView.releaseSession() }
        runCatching { session.close() }
    }
}

/** No website or add-on may obtain Android device permissions from a normal Gecko tab. */
private object DenyNormalTabDevicePermissions : GeckoSession.PermissionDelegate {
    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        callback.reject()
    }

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int> = GeckoResult.fromValue(
        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
    )

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback
    ) {
        callback.reject()
    }
}
