package com.click.browser.extensions

import android.os.Handler
import android.os.Looper
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebExtension

/** One policy-approved browser/page action that may be shown for a normal GeckoView tab. */
internal data class NormalTabExtensionAction(
    val extensionId: String,
    val label: String,
    val extension: WebExtension,
    val action: WebExtension.Action,
    val pageAction: Boolean
)

internal data class NormalTabExtensionPopup(
    val ownerTabId: String,
    val extensionId: String,
    val title: String,
    val session: GeckoSession,
    val renderState: NormalTabExtensionPopupRenderState = NormalTabExtensionPopupRenderState.LOADING
)

internal enum class NormalTabExtensionPopupRenderState { LOADING, READY, FAILED }

internal enum class NormalTabActionDispatchResult {
    REJECTED,
    REQUESTED
}

internal fun dispatchNormalTabActionClick(
    tabId: String,
    attachedTabIds: Set<String>,
    isEnabled: Boolean,
    isPolicyApproved: Boolean,
    isSupportedPage: Boolean = true,
    activateTab: (String) -> Unit,
    clickAction: () -> Unit
): NormalTabActionDispatchResult {
    if (tabId !in attachedTabIds || !isEnabled || !isPolicyApproved || !isSupportedPage) {
        return NormalTabActionDispatchResult.REJECTED
    }
    activateTab(tabId)
    return try {
        clickAction()
        // GeckoView Action.click() is a void/asynchronous dispatch. This means
        // only that the request was sent, not that a popup was created.
        NormalTabActionDispatchResult.REQUESTED
    } catch (_: RuntimeException) {
        NormalTabActionDispatchResult.REJECTED
    }
}

internal fun supportsNormalTabExtensionActions(url: String): Boolean =
    runCatching { java.net.URI(url).scheme?.lowercase() in setOf("http", "https") }.getOrDefault(false)

internal fun canRetryNormalTabExtensionPopup(
    isOwnerSelected: Boolean,
    hasAvailableAction: Boolean,
    currentUrl: String,
    isPopupFailed: Boolean = true
): Boolean = isPopupFailed && isOwnerSelected && hasAvailableAction && supportsNormalTabExtensionActions(currentUrl)

/** Events that can move a popup's render state. */
internal enum class NormalTabExtensionPopupEvent {
    PAGE_START,
    PAGE_STOP_SUCCESS,
    PAGE_STOP_FAILURE,
    FIRST_COMPOSITE,
    PROCESS_GONE,
    TIMEOUT
}

/**
 * Popup render-state machine.
 *
 * Root cause of the "add-on popup stopped loading" dead-end (2026-11-11):
 * READY was reachable ONLY via onFirstComposite, but MainActivity composed
 * the popup's GeckoView only after READY — and a GeckoSession cannot
 * composite before it is attached to a GeckoView. Every popup therefore sat
 * in LOADING until the render timeout flipped it to FAILED. The view is now
 * attached from LOADING onward; this machine stays the single source of
 * truth for transitions. FAILED is terminal: a late success/composite from
 * the (already closed) failed session must never resurrect it.
 */
internal fun popupRenderStateAfter(
    current: NormalTabExtensionPopupRenderState,
    event: NormalTabExtensionPopupEvent
): NormalTabExtensionPopupRenderState = when (current) {
    NormalTabExtensionPopupRenderState.FAILED -> NormalTabExtensionPopupRenderState.FAILED
    NormalTabExtensionPopupRenderState.LOADING -> when (event) {
        NormalTabExtensionPopupEvent.FIRST_COMPOSITE -> NormalTabExtensionPopupRenderState.READY
        NormalTabExtensionPopupEvent.PAGE_STOP_FAILURE,
        NormalTabExtensionPopupEvent.PROCESS_GONE,
        NormalTabExtensionPopupEvent.TIMEOUT -> NormalTabExtensionPopupRenderState.FAILED
        NormalTabExtensionPopupEvent.PAGE_START,
        NormalTabExtensionPopupEvent.PAGE_STOP_SUCCESS -> NormalTabExtensionPopupRenderState.LOADING
    }
    NormalTabExtensionPopupRenderState.READY -> when (event) {
        NormalTabExtensionPopupEvent.PROCESS_GONE,
        NormalTabExtensionPopupEvent.PAGE_STOP_FAILURE -> NormalTabExtensionPopupRenderState.FAILED
        NormalTabExtensionPopupEvent.PAGE_START -> NormalTabExtensionPopupRenderState.LOADING
        NormalTabExtensionPopupEvent.FIRST_COMPOSITE,
        NormalTabExtensionPopupEvent.PAGE_STOP_SUCCESS,
        NormalTabExtensionPopupEvent.TIMEOUT -> NormalTabExtensionPopupRenderState.READY
    }
}

/**
 * A FAILED popup's session is closed immediately (its renderer is dead or
 * never rendered); keeping it open would leak a zombie session that the
 * next open/retry would have to fight. LOADING/READY sessions stay open —
 * they are the live popup. "Try again" always builds a brand-new session
 * via a fresh action click; it never reuses the closed one.
 */
internal fun popupSessionMustClose(state: NormalTabExtensionPopupRenderState): Boolean =
    state == NormalTabExtensionPopupRenderState.FAILED

/**
 * Routes GeckoView's runtime-wide ActionDelegate callbacks to the currently active
 * normal tab. Action.click() does not carry a session ID, so the active-tab state
 * must stay synchronized with WebExtensionController.setTabActive().
 */
internal class NormalTabExtensionRouter(
    private val runtime: GeckoRuntime,
    private val onActionsChanged: (String, List<NormalTabExtensionAction>) -> Unit,
    private val onPopupChanged: (NormalTabExtensionPopup?) -> Unit,
    /** Called once when a popup's render state transitions into FAILED, so the
     *  host can record the failure (Developer dashboard Recent Errors card). */
    private val onPopupFailed: (NormalTabExtensionPopup) -> Unit = {}
) : WebExtension.ActionDelegate {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessions = linkedMapOf<String, GeckoSession>()
    private val sessionIds = linkedMapOf<GeckoSession, String>()
    private val defaultActions = linkedMapOf<String, NormalTabExtensionAction>()
    private val sessionOverrides = linkedMapOf<GeckoSession, MutableMap<String, NormalTabExtensionAction?>>()
    private var activeTabId: String? = null
    private var popup: NormalTabExtensionPopup? = null
    private var popupTimeout: Runnable? = null
    /** True once the current popup's session resources were released (either
     *  by failure cleanup or by closePopup) — prevents double-close. */
    private var popupSessionClosed = false

    fun attach(tabId: String, session: GeckoSession) {
        sessions[tabId] = session
        sessionIds[session] = tabId
        sessionOverrides.getOrPut(session) { linkedMapOf() }
        refreshSession(session)
    }

    fun refreshAll() {
        sessions.values.toList().forEach(::refreshSession)
    }

    /** Keep Action.click() pointed away from this Activity while it is backgrounded. */
    fun deactivateActiveSession() {
        closePopup()
        val previousId = activeTabId ?: return
        sessions[previousId]?.let { runtime.webExtensionController.setTabActive(it, false) }
        activeTabId = null
    }

    private fun refreshSession(session: GeckoSession) {
        runtime.webExtensionController.list().accept(
            { installed ->
                val permitted = installed.orEmpty().filter { extension ->
                    !extension.isBuiltIn && extension.metaData.enabled &&
                        extensionPolicyBlockers(extension).isEmpty()
                }
                val permittedIds = permitted.mapTo(mutableSetOf()) { it.id }
                defaultActions.keys.retainAll(permittedIds)
                sessionOverrides.values.forEach { it.keys.retainAll(permittedIds) }
                permitted.forEach { extension ->
                    // The extension-level delegate receives defaults and Action.click();
                    // the per-session delegate receives tab-specific action overrides.
                    extension.setActionDelegate(this)
                    session.webExtensionController.setActionDelegate(extension, this)
                }
                sessionIds[session]?.let(::emitActions)
            },
            {
                sessionIds[session]?.let { onActionsChanged(it, emptyList()) }
            }
        )
    }

    fun setSelected(tabId: String, selected: Boolean) {
        val session = sessions[tabId] ?: return
        if (selected) {
            activeTabId?.takeIf { it != tabId }?.let { previousId ->
                sessions[previousId]?.let { runtime.webExtensionController.setTabActive(it, false) }
                if (popup?.ownerTabId == previousId) closePopup()
            }
            activeTabId = tabId
            runtime.webExtensionController.setTabActive(session, true)
            emitActions(tabId)
        } else {
            runtime.webExtensionController.setTabActive(session, false)
            if (activeTabId == tabId) activeTabId = null
            if (popup?.ownerTabId == tabId) closePopup()
            onActionsChanged(tabId, emptyList())
        }
    }

    fun clickAction(
        tabId: String,
        item: NormalTabExtensionAction,
        currentUrl: String
    ): NormalTabActionDispatchResult = dispatchNormalTabActionClick(
        tabId = tabId,
        attachedTabIds = sessions.keys,
        isEnabled = !item.extension.isBuiltIn && item.extension.metaData.enabled,
        isPolicyApproved = extensionPolicyBlockers(item.extension).isEmpty(),
        isSupportedPage = supportsNormalTabExtensionActions(currentUrl),
        activateTab = { setSelected(it, true) },
        clickAction = { item.action.click() }
    )

    fun detach(tabId: String, session: GeckoSession) {
        setSelected(tabId, false)
        sessions.remove(tabId, session)
        sessionIds.remove(session)
        sessionOverrides.remove(session)
        onActionsChanged(tabId, emptyList())
    }

    /** Forget a renderer-dead session without calling controller methods on a closed GeckoSession. */
    fun detachCrashedSession(tabId: String, session: GeckoSession) {
        if (sessions[tabId] !== session) return
        if (popup?.ownerTabId == tabId) closePopup()
        if (activeTabId == tabId) activeTabId = null
        sessions.remove(tabId, session)
        sessionIds.remove(session)
        sessionOverrides.remove(session)
        onActionsChanged(tabId, emptyList())
    }

    fun closePopup(expected: GeckoSession? = null) {
        val current = popup
        if (current == null) {
            popupTimeout?.let(mainHandler::removeCallbacks)
            popupTimeout = null
            return
        }
        if (expected != null && current.session !== expected) return
        popupTimeout?.let(mainHandler::removeCallbacks)
        popupTimeout = null
        popup = null
        onPopupChanged(null)
        closePopupSessionResources(current.session)
    }

    private fun createPopup(extension: WebExtension): GeckoResult<GeckoSession>? {
        val owner = activeTabId ?: return null
        val ownerSession = sessions[owner] ?: return null
        if (!extension.metaData.enabled || extensionPolicyBlockers(extension).isNotEmpty()) return null

        closePopup()
        val settings = GeckoSessionSettings.Builder()
            .contextId(ownerSession.settings.contextId)
            .build()
        val popupSession = GeckoSession(settings)
        popupSession.setPermissionDelegate(DenyNormalTabExtensionDevicePermissions)
        popupSession.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                handlePopupEvent(session, NormalTabExtensionPopupEvent.PAGE_START)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                handlePopupEvent(
                    session,
                    if (success) NormalTabExtensionPopupEvent.PAGE_STOP_SUCCESS
                    else NormalTabExtensionPopupEvent.PAGE_STOP_FAILURE
                )
            }
        })
        popupSession.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onFirstComposite(session: GeckoSession) {
                handlePopupEvent(session, NormalTabExtensionPopupEvent.FIRST_COMPOSITE)
            }

            override fun onCrash(session: GeckoSession) {
                handlePopupEvent(session, NormalTabExtensionPopupEvent.PROCESS_GONE)
            }

            override fun onKill(session: GeckoSession) {
                handlePopupEvent(session, NormalTabExtensionPopupEvent.PROCESS_GONE)
            }
        })
        popupSession.open(runtime)
        popupSession.setActive(true)
        popupSessionClosed = false
        popup = NormalTabExtensionPopup(
            ownerTabId = owner,
            extensionId = extension.id,
            title = extension.metaData.name?.takeIf(String::isNotBlank) ?: "Add-on",
            session = popupSession
        )
        onPopupChanged(popup)
        popupTimeout?.let(mainHandler::removeCallbacks)
        popupTimeout = Runnable {
            handlePopupEvent(popupSession, NormalTabExtensionPopupEvent.TIMEOUT)
        }.also { mainHandler.postDelayed(it, POPUP_RENDER_TIMEOUT_MS) }
        return GeckoResult.fromValue(popupSession)
    }

    private fun handlePopupEvent(session: GeckoSession, event: NormalTabExtensionPopupEvent) {
        val current = popup ?: return
        if (current.session !== session) return
        val next = popupRenderStateAfter(current.renderState, event)
        if (next == current.renderState) return
        popup = current.copy(renderState = next)
        onPopupChanged(popup)
        if (next == NormalTabExtensionPopupRenderState.FAILED) {
            // FAILED is terminal and reachable only from a non-FAILED state
            // (the guard above already returned for a no-change event), so
            // this fires exactly once per failed popup.
            runCatching { onPopupFailed(popup!!) }
        }
        if (next != NormalTabExtensionPopupRenderState.LOADING) {
            popupTimeout?.let(mainHandler::removeCallbacks)
            popupTimeout = null
        } else {
            // (Re-)entering LOADING — e.g. the popup page navigates — re-arms
            // the render timeout so a hung navigation still fails honestly.
            popupTimeout?.let(mainHandler::removeCallbacks)
            popupTimeout = Runnable {
                handlePopupEvent(session, NormalTabExtensionPopupEvent.TIMEOUT)
            }.also { mainHandler.postDelayed(it, POPUP_RENDER_TIMEOUT_MS) }
        }
        if (popupSessionMustClose(next)) {
            // Failure cleanup: kill the dead renderer now instead of leaving
            // a zombie session open behind the error card. The popup STATE
            // is kept so the card (and its identity-checked retry) survives;
            // retry builds a fresh session via a new action click.
            closePopupSessionResources(current.session)
        }
    }

    private fun closePopupSessionResources(session: GeckoSession) {
        if (popupSessionClosed) return
        popupSessionClosed = true
        runCatching { session.setActive(false) }
        runCatching { session.close() }
    }

    private fun actionFor(
        extension: WebExtension,
        action: WebExtension.Action,
        pageAction: Boolean
    ): NormalTabExtensionAction? {
        if (extension.isBuiltIn || !extension.metaData.enabled ||
            extensionPolicyBlockers(extension).isNotEmpty()
        ) return null
        if (pageAction && action.enabled != true) return null
        if (!pageAction && action.enabled == false) return null
        val label = action.title?.takeIf(String::isNotBlank)
            ?: extension.metaData.name?.takeIf(String::isNotBlank)
            ?: "Add-on"
        return NormalTabExtensionAction(extension.id, label, extension, action, pageAction)
    }

    private fun updateAction(
        extension: WebExtension,
        session: GeckoSession?,
        action: WebExtension.Action,
        pageAction: Boolean
    ) {
        val item = actionFor(extension, action, pageAction)
        if (session == null) {
            if (item == null) defaultActions.remove(extension.id)
            else defaultActions[extension.id] = item
            activeTabId?.let(::emitActions)
            return
        }
        val tabId = sessionIds[session] ?: return
        sessionOverrides.getOrPut(session) { linkedMapOf() }[extension.id] = item
        emitActions(tabId)
    }

    private fun emitActions(tabId: String) {
        val session = sessions[tabId] ?: return
        val visible = defaultActions.toMutableMap()
        sessionOverrides[session].orEmpty().forEach { (id, override) ->
            if (override == null) visible.remove(id) else visible[id] = override
        }
        onActionsChanged(tabId, visible.values.toList())
    }

    override fun onBrowserAction(
        extension: WebExtension,
        session: GeckoSession?,
        action: WebExtension.Action
    ) = updateAction(extension, session, action, pageAction = false)

    override fun onPageAction(
        extension: WebExtension,
        session: GeckoSession?,
        action: WebExtension.Action
    ) = updateAction(extension, session, action, pageAction = true)

    override fun onTogglePopup(
        extension: WebExtension,
        action: WebExtension.Action
    ): GeckoResult<GeckoSession>? = createPopup(extension)

    override fun onOpenPopup(
        extension: WebExtension,
        action: WebExtension.Action
    ): GeckoResult<GeckoSession>? = createPopup(extension)

    private companion object {
        const val POPUP_RENDER_TIMEOUT_MS = 12_000L
    }
}

/** Deny website/add-on requests for Android device permissions in normal Gecko tabs. */
private object DenyNormalTabExtensionDevicePermissions : GeckoSession.PermissionDelegate {
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
