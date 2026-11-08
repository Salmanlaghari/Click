package com.click.browser.engine

import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.Autocomplete
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

internal fun geckoContextIdsToClearOnExit(
    bootMode: BrowserMode,
    usedContextIds: Collection<String>
): List<String> = (listOf(geckoContextIdFor(bootMode)) + usedContextIds).distinct()

internal fun isAllowedGeckoNewWindowUri(uri: String): Boolean {
    if (uri == "about:blank") return true
    val scheme = runCatching { java.net.URI(uri).scheme?.lowercase() }.getOrNull()
    return scheme == "http" || scheme == "https"
}

internal fun shouldAcceptGeckoNewWindowUri(uri: String, policyUri: String?): Boolean =
    isAllowedGeckoNewWindowUri(uri) && policyUri == uri

internal data class GeckoSessionStartupPlan(
    val openLocally: Boolean,
    val restoreSavedState: Boolean,
    val loadInitialUrl: Boolean
)

internal fun geckoSessionStartupPlan(
    adoptedChildSession: Boolean,
    hasRestorableState: Boolean,
    initialUrl: String
): GeckoSessionStartupPlan = when {
    adoptedChildSession -> GeckoSessionStartupPlan(false, false, false)
    hasRestorableState -> GeckoSessionStartupPlan(true, true, false)
    initialUrl.isNotBlank() && initialUrl != "about:blank" -> GeckoSessionStartupPlan(true, false, true)
    else -> GeckoSessionStartupPlan(true, false, false)
}

/** GeckoView provider for one regular browser tab. */
internal class GeckoBrowserTabSession(
    context: Context,
    private val runtime: GeckoRuntime,
    private val tabId: String,
    mode: BrowserMode,
    initialUrl: String,
    initialSessionState: String?,
    private val extensionRouter: NormalTabExtensionRouter?,
    initialGeckoSession: GeckoSession? = null,
    initiallySelected: Boolean,
    initiallyResumed: Boolean,
    private val navigationTarget: (String) -> String?,
    private val wideLayoutForUrl: (String) -> Boolean,
    private val onNavigationBlocked: (String) -> Unit,
    private val onNewSession: (String, GeckoSession) -> GeckoResult<GeckoSession>?,
    private val onUrlChanged: (String) -> Unit,
    private val onTitleChanged: (String) -> Unit,
    private val onProgressChanged: (Int, Boolean) -> Unit,
    private val onPageLoaded: (String) -> Unit,
    private val onSessionStateChanged: (String) -> Unit,
    private val onCanGoBackChanged: (Boolean) -> Unit,
    private val onCanGoForwardChanged: (Boolean) -> Unit,
    private val onFullscreenChanged: (Boolean) -> Unit,
    private val onRendererFailure: (GeckoRenderEvent, Throwable?) -> Unit,
    private val onRendererRecovery: (GeckoRenderEvent) -> Unit,
    private val onExternalResponse: (WebResponse) -> Unit,
    private val onLoginSavePrompt: (
        String,
        GeckoSession,
        GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption>
    ) -> GeckoResult<GeckoSession.PromptDelegate.PromptResponse>
) : BrowserTabSession {
    private val contextId = geckoContextIdFor(mode)
    private val settings = GeckoSessionSettings.Builder()
        .usePrivateMode(false)
        .useTrackingProtection(true)
        .contextId(contextId)
        .viewportMode(DesktopLayoutPolicy.geckoViewportMode(wideLayoutForUrl(initialUrl)))
        .build()
    val session = initialGeckoSession ?: GeckoSession(settings)
    private val geckoView = GeckoView(context)
    private var selected = initiallySelected
    private var hostResumed = initiallyResumed
    private var canBack = false
    private var canForward = false
    private var destroyed = false
    private var extensionAttached = false
    private var historyCandidateUrl: String? = initialUrl
    @Volatile private var lastKnownPageUrl: String = initialUrl.ifBlank { "about:blank" }
    @Volatile private var latestSessionState: String? = initialSessionState
    private var rendererRecoveryRequired = false
    private var rendererRecoveryInProgress = false
    private val adoptedChildSession = initialGeckoSession != null

    override val view: View get() = geckoView

    init {
        // Adopted child sessions keep their storage and UA identity, but receive
        // the same viewport preference as a newly-created regular tab.
        session.settings.setViewportMode(
            DesktopLayoutPolicy.geckoViewportMode(wideLayoutForUrl(initialUrl))
        )
        session.setPermissionDelegate(DenyNormalTabDevicePermissions)
        session.setPromptDelegate(object : GeckoSession.PromptDelegate {
            override fun onLoginSave(
                session: GeckoSession,
                request: GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption>
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
                onLoginSavePrompt(tabId, session, request)
        })
        session.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                url?.let {
                    historyCandidateUrl = it
                    lastKnownPageUrl = it
                    onUrlChanged(it)
                }
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
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }
                val target = navigationTarget(request.uri)
                if (target == null) {
                    if (GoogleAuthPolicy.shouldShowBlockedNavigationNotice(request.uri)) {
                        onNavigationBlocked(request.uri)
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                setWideLayout(wideLayoutForUrl(target))
                if (target != request.uri) {
                    session.loadUri(target)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (!shouldAcceptGeckoNewWindowUri(uri, navigationTarget(uri))) {
                    if (GoogleAuthPolicy.shouldShowBlockedNavigationNotice(uri)) {
                        onNavigationBlocked(uri)
                    }
                    return null
                }
                return onNewSession(uri, GeckoSession(settings))
            }
        })
        session.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onSessionStateChange(
                session: GeckoSession,
                state: GeckoSession.SessionState
            ) {
                state.toString().let { encoded ->
                    latestSessionState = encoded
                    onSessionStateChanged(encoded)
                }
            }

            override fun onPageStart(session: GeckoSession, url: String) {
                onRendererRecovery(GeckoRenderEvent.LOAD_STARTED)
                attachExtensionRouterIfOpen()
                updateActiveState()
                historyCandidateUrl = url
                lastKnownPageUrl = url
                onProgressChanged(0, true)
                onUrlChanged(url)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                onProgressChanged(100, false)
                if (success) {
                    historyCandidateUrl
                        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                        ?.let(onPageLoaded)
                }
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

            override fun onCrash(session: GeckoSession) {
                rendererRecoveryRequired = true
                rendererRecoveryInProgress = false
                detachExtensionRouterAfterRendererFailure()
                onRendererFailure(GeckoRenderEvent.PROCESS_CRASHED, null)
            }

            override fun onKill(session: GeckoSession) {
                rendererRecoveryRequired = true
                rendererRecoveryInProgress = false
                detachExtensionRouterAfterRendererFailure()
                onRendererFailure(GeckoRenderEvent.PROCESS_KILLED, null)
            }

            override fun onFirstComposite(session: GeckoSession) {
                rendererRecoveryRequired = false
                rendererRecoveryInProgress = false
                onRendererRecovery(GeckoRenderEvent.FIRST_COMPOSITE)
            }
        })
        val restoredState = if (adoptedChildSession) null else initialSessionState?.let { encoded ->
            runCatching { GeckoSession.SessionState.fromString(encoded) }.getOrNull()
        }
        val startup = geckoSessionStartupPlan(
            adoptedChildSession = adoptedChildSession,
            hasRestorableState = restoredState != null,
            initialUrl = initialUrl
        )
        if (startup.openLocally) session.open(runtime)
        geckoView.setSession(session)
        attachExtensionRouterIfOpen()
        if (startup.restoreSavedState && restoredState != null) {
            session.restoreState(restoredState)
        } else if (startup.loadInitialUrl) {
            session.loadUri(initialUrl)
        }
        updateActiveState()
    }

    override fun canGoBack(): Boolean = canBack
    override fun goBack() {
        if (destroyed || !canBack) return
        try {
            if (!session.isOpen) {
                beginRendererRecovery()
                reopenAndRestore(lastKnownPageUrl)
            }
            session.goBack()
        } catch (error: Exception) {
            reportRecoveryFailure(error)
        }
    }
    override fun canGoForward(): Boolean = canForward
    override fun goForward() {
        if (destroyed || !canForward) return
        try {
            if (!session.isOpen) {
                beginRendererRecovery()
                reopenAndRestore(lastKnownPageUrl)
            }
            session.goForward()
        } catch (error: Exception) {
            reportRecoveryFailure(error)
        }
    }
    override fun loadUrl(url: String) {
        if (destroyed) return
        try {
            setWideLayout(wideLayoutForUrl(url))
            if (!session.isOpen) {
                beginRendererRecovery()
                session.open(runtime)
                val restored = restoreLatestStateOrLoad(url)
                updateActiveState()
                if (!restored) return
            }
            session.loadUri(url)
        } catch (error: Exception) {
            reportRecoveryFailure(error)
        }
    }
    override fun setWideLayout(enabled: Boolean) {
        if (!destroyed) {
            session.settings.setViewportMode(DesktopLayoutPolicy.geckoViewportMode(enabled))
        }
    }
    override fun reload() {
        if (destroyed || rendererRecoveryInProgress) return
        val action = geckoSessionRecoveryAction(
            sessionIsOpen = session.isOpen,
            recoveryInProgress = rendererRecoveryInProgress,
            hasSavedState = !latestSessionState.isNullOrBlank(),
            hasCurrentUrl = lastKnownPageUrl.isNotBlank() && lastKnownPageUrl != "about:blank"
        )
        if (action == GeckoSessionRecoveryAction.RELOAD_OPEN_SESSION && !rendererRecoveryRequired) {
            try {
                session.reload()
            } catch (error: Exception) {
                reportRecoveryFailure(error)
            }
            return
        }
        if (action == GeckoSessionRecoveryAction.IGNORE_IN_PROGRESS) return

        beginRendererRecovery()
        try {
            when (action) {
                GeckoSessionRecoveryAction.IGNORE_IN_PROGRESS -> return
                GeckoSessionRecoveryAction.RELOAD_OPEN_SESSION -> session.reload()
                GeckoSessionRecoveryAction.REOPEN_AND_RESTORE_STATE -> {
                    session.open(runtime)
                    restoreLatestStateOrLoad(lastKnownPageUrl)
                }
                GeckoSessionRecoveryAction.REOPEN_AND_LOAD_CURRENT_URL -> {
                    session.open(runtime)
                    session.loadUri(lastKnownPageUrl)
                }
                GeckoSessionRecoveryAction.REOPEN_BLANK_SESSION -> {
                    session.open(runtime)
                    session.loadUri("about:blank")
                }
            }
            updateActiveState()
        } catch (error: Exception) {
            reportRecoveryFailure(error)
        }
    }
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
        if (destroyed || !session.isOpen) return
        attachExtensionRouterIfOpen()
        val active = selected && hostResumed
        session.setActive(active)
        extensionRouter?.setSelected(tabId, active)
    }

    private fun beginRendererRecovery() {
        if (rendererRecoveryInProgress) return
        rendererRecoveryRequired = true
        rendererRecoveryInProgress = true
        onRendererRecovery(GeckoRenderEvent.RECOVERY_STARTED)
    }

    /** Reopen the same session object, preserving its profile and saved history. */
    private fun reopenAndRestore(fallbackUrl: String): Boolean {
        session.open(runtime)
        val restored = restoreLatestStateOrLoad(fallbackUrl)
        updateActiveState()
        return restored
    }

    private fun restoreLatestStateOrLoad(fallbackUrl: String): Boolean {
        val restoredState = latestSessionState
            ?.takeIf { it.isNotBlank() }
            ?.let { encoded -> runCatching { GeckoSession.SessionState.fromString(encoded) }.getOrNull() }
        if (restoredState != null) {
            session.restoreState(restoredState)
            return true
        }
        session.loadUri(fallbackUrl.ifBlank { "about:blank" })
        return false
    }

    private fun reportRecoveryFailure(error: Exception) {
        rendererRecoveryRequired = true
        rendererRecoveryInProgress = false
        onRendererFailure(GeckoRenderEvent.RECOVERY_FAILED, error)
    }

    private fun attachExtensionRouterIfOpen() {
        if (!extensionAttached && session.isOpen && extensionRouter != null) {
            extensionRouter.attach(tabId, session)
            extensionAttached = true
        }
    }

    private fun detachExtensionRouterAfterRendererFailure() {
        if (!extensionAttached) return
        extensionRouter?.detachCrashedSession(tabId, session)
        extensionAttached = false
    }

    override fun detachFromParent() {
        (geckoView.parent as? ViewGroup)?.removeView(geckoView)
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        extensionRouter?.detach(tabId, session)
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
