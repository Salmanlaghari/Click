package com.click.browser.engine

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.BasicSelectionActionDelegate
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse
import com.click.browser.extensions.NormalTabExtensionRouter
import java.util.ArrayDeque

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

private const val GECKO_ACTION_EXPLAIN_SELECTED_TEXT = "com.click.browser.EXPLAIN_SELECTED_TEXT"
private const val GECKO_ACTION_TRANSLATE_SELECTED_TEXT = "com.click.browser.TRANSLATE_SELECTED_TEXT"

/** GeckoView provider for one regular browser tab. */
internal class GeckoBrowserTabSession(
    context: Context,
    selectionActivity: Activity,
    private val runtime: GeckoRuntime,
    private val tabId: String,
    private val isIncognito: Boolean,
    mode: BrowserMode,
    initialUrl: String,
    initialSessionState: String?,
    private val extensionRouter: NormalTabExtensionRouter?,
    private val onWebsiteMicrophoneRequest: (
        String,
        String,
        () -> Unit,
        () -> Unit
    ) -> Unit,
    initialGeckoSession: GeckoSession? = null,
    initiallySelected: Boolean,
    initiallyResumed: Boolean,
    private val navigationTarget: (String) -> String?,
    private val wideLayoutForUrl: (String) -> Boolean,
    private val onNavigationBlocked: (String) -> Unit,
    private val onNewSession: (String, GeckoSession) -> GeckoResult<GeckoSession>?,
    private val onUrlChanged: (String) -> Unit,
    private val onConnectionSecurityChanged: (ConnectionSecurityInfo) -> Unit,
    private val onTitleChanged: (String) -> Unit,
    private val onProgressChanged: (Int, Long) -> Unit,
    private val onNavigationStarted: (String, Long) -> Unit,
    private val onPageFirstContentfulPaint: (Long) -> Unit,
    private val onTabPreviewCaptured: (String, Bitmap) -> Unit,
    private val onNavigationStopped: (GeckoNavigationCompletion) -> Unit,
    private val onPageLoaded: (String) -> Unit,
    private val onSessionStateChanged: (String) -> Unit,
    private val onCanGoBackChanged: (Boolean) -> Unit,
    private val onCanGoForwardChanged: (Boolean) -> Unit,
    private val onFullscreenChanged: (Boolean) -> Unit,
    private val onRendererFailure: (GeckoRenderEvent, Throwable?) -> Unit,
    private val onRendererRecovery: (GeckoRenderEvent) -> Unit,
    private val onExternalResponse: (WebResponse) -> Unit,
    private val onSelectedText: (AiSelectedTextPolicy.PreparedSelection, String) -> Unit,
    private val onTranslateSelectedText: (AiSelectedTextPolicy.PreparedSelection, String) -> Unit,
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
    @Volatile private var previewGeneration = 0L
    @Volatile private var pendingPreviewGeneration: Long? = null
    @Volatile private var completedPreviewGeneration: Long? = null
    @Volatile private var destroyed = false
    private val devicePermissionDelegate = NormalTabDevicePermissionDelegate(
        tabId = tabId,
        onWebsiteMicrophoneRequest = onWebsiteMicrophoneRequest,
        isSessionActive = { !destroyed && session.isOpen }
    )
    private var extensionAttached = false
    @Volatile private var lastKnownPageUrl: String = initialUrl.ifBlank { "about:blank" }
    @Volatile private var latestConnectionSecurityInfo = ConnectionSecurityPolicy.unknown(initialUrl)
    @Volatile private var latestSessionState: String? = initialSessionState
    @Volatile private var rendererRecoveryRequired = false
    @Volatile private var rendererRecoveryInProgress = false
    @Volatile private var pendingSafeRetryUrl: String? = null
    private val navigationTracker = GeckoNavigationTracker()
    private val recoveryFailureLock = Any()
    private var recoveryFailureTimesMillis: List<Long> = emptyList()
    private val adoptedChildSession = initialGeckoSession != null

    private val selectionActionDelegate = object : BasicSelectionActionDelegate(selectionActivity) {
        override fun getAllActions(): Array<String> =
            super.getAllActions() + arrayOf(
                GECKO_ACTION_EXPLAIN_SELECTED_TEXT,
                GECKO_ACTION_TRANSLATE_SELECTED_TEXT
            )

        override fun isActionAvailable(id: String): Boolean {
            if (id != GECKO_ACTION_EXPLAIN_SELECTED_TEXT &&
                id != GECKO_ACTION_TRANSLATE_SELECTED_TEXT
            ) return super.isActionAvailable(id)
            val selection = getSelection() ?: return false
            val flags = selection.flags
            return AiSelectedTextPolicy.prepareSelection(
                rawText = selection.text,
                engine = BrowserTabEngine.GECKOVIEW,
                isPrivateTab = false,
                isCollapsed = (flags and GeckoSession.SelectionActionDelegate.FLAG_IS_COLLAPSED) != 0,
                isEditable = (flags and GeckoSession.SelectionActionDelegate.FLAG_IS_EDITABLE) != 0,
                isPassword = (flags and GeckoSession.SelectionActionDelegate.FLAG_IS_PASSWORD) != 0
            ) != null
        }

        override fun prepareAction(id: String, menuItem: MenuItem) {
            when (id) {
                GECKO_ACTION_EXPLAIN_SELECTED_TEXT -> {
                    menuItem.title = "Explain selection"
                    menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                GECKO_ACTION_TRANSLATE_SELECTED_TEXT -> {
                    menuItem.title = "Translate selection"
                    menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                else -> super.prepareAction(id, menuItem)
            }
        }

        override fun performAction(id: String, menuItem: MenuItem): Boolean {
            if (id != GECKO_ACTION_EXPLAIN_SELECTED_TEXT &&
                id != GECKO_ACTION_TRANSLATE_SELECTED_TEXT
            ) {
                return super.performAction(id, menuItem)
            }
            val selection = getSelection() ?: return false
            val flags = selection.flags
            val prepared = AiSelectedTextPolicy.prepareSelection(
                rawText = selection.text,
                engine = BrowserTabEngine.GECKOVIEW,
                isPrivateTab = false,
                isCollapsed = (flags and GeckoSession.SelectionActionDelegate.FLAG_IS_COLLAPSED) != 0,
                isEditable = (flags and GeckoSession.SelectionActionDelegate.FLAG_IS_EDITABLE) != 0,
                isPassword = (flags and GeckoSession.SelectionActionDelegate.FLAG_IS_PASSWORD) != 0
            ) ?: return false
            if (id == GECKO_ACTION_TRANSLATE_SELECTED_TEXT) {
                onTranslateSelectedText(prepared, lastKnownPageUrl)
            } else {
                onSelectedText(prepared, lastKnownPageUrl)
            }
            clearSelection()
            return true
        }
    }

    override val view: View get() = geckoView

    init {
        session.setSelectionActionDelegate(selectionActionDelegate)
        // Adopted child sessions keep their storage and UA identity, but receive
        // the same viewport preference as a newly-created regular tab.
        session.settings.setViewportMode(
            DesktopLayoutPolicy.geckoViewportMode(wideLayoutForUrl(initialUrl))
        )
        session.setPermissionDelegate(devicePermissionDelegate)
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
                    if (lastKnownPageUrl != it) {
                        previewGeneration += 1
                        pendingPreviewGeneration = null
                        completedPreviewGeneration = null
                    }
                    lastKnownPageUrl = it
                    navigationTracker.updateCurrentUrl(it)
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
                    pendingSafeRetryUrl = null
                    navigationTracker.updateCurrentRetryUrl(null)
                    if (GoogleAuthPolicy.shouldShowBlockedNavigationNotice(request.uri)) {
                        onNavigationBlocked(request.uri)
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                val retryCandidate = if (request.isRedirect) {
                    navigationTracker.currentRetryUrl() ?: pendingSafeRetryUrl
                } else pendingSafeRetryUrl
                val nextRetryUrl = when {
                    request.isRedirect && retryCandidate != null -> geckoSafeRetryUrl("GET", target)
                    !request.isRedirect && retryCandidate != null && request.uri == retryCandidate ->
                        geckoSafeRetryUrl("GET", target)
                    else -> null
                }
                pendingSafeRetryUrl = nextRetryUrl
                if (request.isRedirect) {
                    navigationTracker.updateCurrentUrl(target)
                    navigationTracker.updateCurrentRetryUrl(nextRetryUrl)
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

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: org.mozilla.geckoview.WebRequestError
            ): GeckoResult<String>? {
                navigationTracker.markLoadError(uri)
                // Keep Gecko's default error handling; Click presents its own
                // safe, accessible recovery actions above the browser surface.
                return null
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
                previewGeneration += 1
                pendingPreviewGeneration = null
                completedPreviewGeneration = null
                onRendererRecovery(GeckoRenderEvent.LOAD_STARTED)
                attachExtensionRouterIfOpen()
                updateActiveState()
                val retryUrl = pendingSafeRetryUrl?.takeIf { it == url }
                pendingSafeRetryUrl = null
                val navigation = navigationTracker.begin(url, retryUrl)
                lastKnownPageUrl = url
                latestConnectionSecurityInfo = ConnectionSecurityPolicy.checking(url)
                onConnectionSecurityChanged(latestConnectionSecurityInfo)
                onNavigationStarted(url, navigation.generation)
                onProgressChanged(0, navigation.generation)
                onUrlChanged(url)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                val completion = navigationTracker.complete(success) ?: return
                if (completion.isCurrent &&
                    latestConnectionSecurityInfo.status == ConnectionSecurityStatus.CHECKING
                ) {
                    latestConnectionSecurityInfo = ConnectionSecurityPolicy.unknown(
                        completion.url,
                        "GeckoView did not provide connection-security details for this page."
                    )
                    onConnectionSecurityChanged(latestConnectionSecurityInfo)
                }
                onNavigationStopped(completion)
                if (completion.isCurrent && completion.outcome.commitsContent &&
                    (completion.url.startsWith("http://") || completion.url.startsWith("https://"))
                ) {
                    onPageLoaded(completion.url)
                }
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation
            ) {
                val activePageUrl = lastKnownPageUrl
                if (!ConnectionSecurityPolicy.originMatchesPage(activePageUrl, securityInfo.origin)) return
                val certificate = securityInfo.certificate
                latestConnectionSecurityInfo = ConnectionSecurityPolicy.fromGeckoView(
                    url = activePageUrl,
                    isSecure = securityInfo.isSecure,
                    isException = securityInfo.isException,
                    mixedActiveContentLoaded = securityInfo.mixedModeActive ==
                        GeckoSession.ProgressDelegate.SecurityInformation.CONTENT_LOADED,
                    mixedPassiveContentLoaded = securityInfo.mixedModePassive ==
                        GeckoSession.ProgressDelegate.SecurityInformation.CONTENT_LOADED,
                    certificateSubject = runCatching {
                        certificate?.subjectX500Principal?.name
                    }.getOrNull(),
                    certificateIssuer = runCatching {
                        certificate?.issuerX500Principal?.name
                    }.getOrNull()
                )
                onConnectionSecurityChanged(latestConnectionSecurityInfo)
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                val generation = navigationTracker.progressGeneration() ?: return
                onProgressChanged(progress.coerceIn(0, 100), generation)
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
                recordRendererRecoveryFailure()
                rendererRecoveryRequired = true
                rendererRecoveryInProgress = false
                detachExtensionRouterAfterRendererFailure()
                onRendererFailure(GeckoRenderEvent.PROCESS_CRASHED, null)
            }

            override fun onKill(session: GeckoSession) {
                recordRendererRecoveryFailure()
                rendererRecoveryRequired = true
                rendererRecoveryInProgress = false
                detachExtensionRouterAfterRendererFailure()
                onRendererFailure(GeckoRenderEvent.PROCESS_KILLED, null)
            }

            override fun onFirstComposite(session: GeckoSession) {
                clearRendererRecoveryFailures()
                rendererRecoveryRequired = false
                rendererRecoveryInProgress = false
                onRendererRecovery(GeckoRenderEvent.FIRST_COMPOSITE)
                captureTabPreviewAfterFirstComposite()
            }

            override fun onFirstContentfulPaint(session: GeckoSession) {
                // Paint is session-level and has no load ID. Associate the
                // visibility signal with the latest navigation; page-stop still
                // owns completion and progress for each individual generation.
                val generation = navigationTracker.contentPaintGeneration() ?: return
                onPageFirstContentfulPaint(generation)
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
            pendingSafeRetryUrl = geckoSafeRetryUrl("GET", initialUrl)
            session.loadUri(initialUrl)
        }
        updateActiveState()
    }

    private fun captureTabPreviewAfterFirstComposite() {
        val captureUrl = lastKnownPageUrl
        val captureGeneration = previewGeneration
        if (!shouldCaptureGeckoTabPreview(captureUrl, isIncognito) ||
            pendingPreviewGeneration == captureGeneration ||
            completedPreviewGeneration == captureGeneration
        ) return

        pendingPreviewGeneration = captureGeneration
        geckoView.post {
            if (!isCurrentPreviewCapture(captureUrl, captureGeneration)) {
                if (pendingPreviewGeneration == captureGeneration) pendingPreviewGeneration = null
                return@post
            }
            val capture = try {
                geckoView.capturePixels()
            } catch (_: Exception) {
                if (pendingPreviewGeneration == captureGeneration) pendingPreviewGeneration = null
                return@post
            }
            capture.accept(
                { bitmap ->
                    if (isCurrentPreviewCapture(captureUrl, captureGeneration)) {
                        pendingPreviewGeneration = null
                        completedPreviewGeneration = captureGeneration
                        onTabPreviewCaptured(captureUrl, bitmap)
                    } else {
                        if (pendingPreviewGeneration == captureGeneration) pendingPreviewGeneration = null
                        runCatching { bitmap.recycle() }
                    }
                },
                { _ ->
                    if (pendingPreviewGeneration == captureGeneration) pendingPreviewGeneration = null
                }
            )
        }
    }

    private fun isCurrentPreviewCapture(captureUrl: String, captureGeneration: Long): Boolean =
        !destroyed && session.isOpen &&
            shouldStoreGeckoTabPreview(
                capturedUrl = captureUrl,
                currentUrl = lastKnownPageUrl,
                capturedGeneration = captureGeneration,
                currentGeneration = previewGeneration,
                isIncognito = isIncognito
            )

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
            pendingSafeRetryUrl = geckoSafeRetryUrl("GET", url)
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
        if (rendererRecoveryRequired && !rendererRecoveryRetryDecision().retryAllowed) {
            onRendererRecovery(GeckoRenderEvent.RECOVERY_RETRY_THROTTLED)
            return
        }
        val action = geckoSessionRecoveryAction(
            sessionIsOpen = session.isOpen,
            recoveryInProgress = rendererRecoveryInProgress,
            hasSavedState = !latestSessionState.isNullOrBlank(),
            hasCurrentUrl = lastKnownPageUrl.isNotBlank() && lastKnownPageUrl != "about:blank"
        )
        if (action == GeckoSessionRecoveryAction.RELOAD_OPEN_SESSION) {
            if (rendererRecoveryRequired) {
                beginRendererRecovery(GeckoRenderEvent.RECOVERY_RELOAD_STARTED)
            }
            try {
                session.reload()
                updateActiveState()
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
    override fun stopLoading() {
        if (destroyed) return
        val cancelledGeneration = navigationTracker.cancelActive()
        try {
            session.stop()
        } catch (error: Exception) {
            navigationTracker.clearCancellation(cancelledGeneration)
            throw error
        }
    }

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

    private fun beginRendererRecovery(event: GeckoRenderEvent = GeckoRenderEvent.RECOVERY_STARTED) {
        if (rendererRecoveryInProgress) return
        rendererRecoveryRequired = true
        rendererRecoveryInProgress = true
        onRendererRecovery(event)
    }

    private fun rendererRecoveryRetryDecision(): GeckoRendererRetryDecision = synchronized(recoveryFailureLock) {
        geckoRendererRetryDecision(
            failureTimesMillis = recoveryFailureTimesMillis,
            nowMillis = android.os.SystemClock.elapsedRealtime()
        )
    }

    private fun recordRendererRecoveryFailure() {
        synchronized(recoveryFailureLock) {
            recoveryFailureTimesMillis = recordGeckoRendererFailure(
                failureTimesMillis = recoveryFailureTimesMillis,
                failedAtMillis = android.os.SystemClock.elapsedRealtime()
            )
        }
    }

    private fun clearRendererRecoveryFailures() {
        synchronized(recoveryFailureLock) {
            recoveryFailureTimesMillis = emptyList()
        }
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
        recordRendererRecoveryFailure()
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
        previewGeneration += 1
        pendingPreviewGeneration = null
        devicePermissionDelegate.rejectPendingAndroidPermission()
        extensionRouter?.detach(tabId, session)
        runCatching { geckoView.releaseSession() }
        runCatching { session.close() }
    }
}

/** Only an explicit, audio-only microphone request can reach Click's consent flow. */
private class NormalTabDevicePermissionDelegate(
    private val tabId: String,
    private val onWebsiteMicrophoneRequest: (
        String,
        String,
        () -> Unit,
        () -> Unit
    ) -> Unit,
    private val isSessionActive: () -> Boolean
) : GeckoSession.PermissionDelegate {
    private data class PendingAndroidAudioCallback(
        val callback: GeckoSession.PermissionDelegate.Callback,
        val requestedAtMillis: Long
    )

    // Gecko supplies no correlation ID here; pair callbacks FIFO with the
    // session's subsequent origin-aware media requests instead of replacing one.
    private val pendingAndroidAudioCallbacks = ArrayDeque<PendingAndroidAudioCallback>()

    companion object {
        // Bound per-session memory against a page flooding callbacks before Gecko's
        // paired media requests arrive; 32 permits a generous burst and overflow is logged.
        private const val MAX_PENDING_ANDROID_AUDIO_CALLBACKS = 32
        private const val PENDING_ANDROID_AUDIO_CALLBACK_TIMEOUT_MS = 30_000L
        private const val PERMISSION_LOG_TAG = "ClickMicPermissions"
    }

    fun rejectPendingAndroidPermission() {
        val callbacks = synchronized(pendingAndroidAudioCallbacks) {
            buildList {
                while (pendingAndroidAudioCallbacks.isNotEmpty()) {
                    add(pendingAndroidAudioCallbacks.removeFirst().callback)
                }
            }
        }
        callbacks.forEach { runCatching { it.reject() } }
    }

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        // This callback has no requesting origin. Hold a pure RECORD_AUDIO
        // callback until the media callback supplies the exact site. Never hold
        // camera or mixed Android permission requests.
        if (permissions?.toSet() != setOf(android.Manifest.permission.RECORD_AUDIO)) {
            callback.reject()
            return
        }
        val accepted = synchronized(pendingAndroidAudioCallbacks) {
            if (pendingAndroidAudioCallbacks.size >= MAX_PENDING_ANDROID_AUDIO_CALLBACKS) {
                false
            } else {
                pendingAndroidAudioCallbacks.addLast(
                    PendingAndroidAudioCallback(callback, android.os.SystemClock.elapsedRealtime())
                )
                true
            }
        }
        if (!accepted) {
            Log.w(PERMISSION_LOG_TAG, "Rejecting microphone request: pending callback queue is full")
            callback.reject()
        }
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
        val expiredAndroidCallbacks = mutableListOf<GeckoSession.PermissionDelegate.Callback>()
        val androidAudioCallback = synchronized(pendingAndroidAudioCallbacks) {
            val now = android.os.SystemClock.elapsedRealtime()
            while (true) {
                val firstPending = pendingAndroidAudioCallbacks.peekFirst() ?: break
                if (now - firstPending.requestedAtMillis <= PENDING_ANDROID_AUDIO_CALLBACK_TIMEOUT_MS) break
                expiredAndroidCallbacks += pendingAndroidAudioCallbacks.removeFirst().callback
            }
            if (pendingAndroidAudioCallbacks.isEmpty()) null
            else pendingAndroidAudioCallbacks.removeFirst().callback
        }
        expiredAndroidCallbacks.forEach { runCatching { it.reject() } }
        val microphone = audio?.firstOrNull {
            it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE
        }
        val origin = canonicalWebsiteMicrophoneOrigin(uri)
        val reject: () -> Unit = {
            androidAudioCallback?.let { runCatching { it.reject() } }
            runCatching { callback.reject() }
            Unit
        }
        if (origin == null || !isMicrophoneOnlyMediaRequest(
                origin = origin,
                hasVideoRequest = video != null,
                hasMicrophoneSource = microphone != null
            )
        ) {
            reject()
            return
        }
        onWebsiteMicrophoneRequest(
            tabId,
            origin,
            {
                if (isSessionActive()) {
                    val androidGrantSucceeded = androidAudioCallback
                        ?.let { runCatching { it.grant() }.isSuccess } ?: true
                    if (androidGrantSucceeded) {
                        runCatching { callback.grant(null, microphone) }
                            .onFailure { runCatching { callback.reject() } }
                    } else {
                        runCatching { callback.reject() }
                    }
                } else {
                    reject()
                }
            },
            reject
        )
    }
}
