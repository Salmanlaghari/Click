package com.click.browser.engine

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView

enum class BrowserTabEngine {
    WEBVIEW,
    GECKOVIEW
}

/** Regular tabs use GeckoView; private tabs stay on WebView until cleanup is verified. */
internal fun defaultBrowserTabEngine(isIncognito: Boolean): BrowserTabEngine =
    if (isIncognito) BrowserTabEngine.WEBVIEW else BrowserTabEngine.GECKOVIEW

internal enum class GeckoRenderEvent {
    LOAD_STARTED,
    FIRST_COMPOSITE,
    PROCESS_CRASHED,
    PROCESS_KILLED,
    RECOVERY_STARTED,
    RECOVERY_RELOAD_STARTED,
    RECOVERY_FAILED,
    RECOVERY_RETRY_THROTTLED,
    /** An automatic (no user tap) recovery attempt began. The recovery card
     *  must NOT appear for this — it only shows when automatic recovery is
     *  exhausted or genuinely fails. */
    AUTO_RECOVERY_STARTED
}

internal fun geckoRendererRecoveryRequired(
    currentlyRequired: Boolean,
    event: GeckoRenderEvent
): Boolean = when (event) {
    GeckoRenderEvent.FIRST_COMPOSITE -> false
    GeckoRenderEvent.PROCESS_CRASHED,
    GeckoRenderEvent.PROCESS_KILLED,
    GeckoRenderEvent.RECOVERY_STARTED,
    GeckoRenderEvent.RECOVERY_RELOAD_STARTED,
    GeckoRenderEvent.RECOVERY_FAILED,
    GeckoRenderEvent.RECOVERY_RETRY_THROTTLED -> true
    GeckoRenderEvent.LOAD_STARTED,
    GeckoRenderEvent.AUTO_RECOVERY_STARTED -> currentlyRequired
}

/**
 * Automatic recovery policy (Prince's 2026-11-11 "page stopped rendering"
 * dead-end): when a tab's content process dies, Click first tries to rebuild
 * the session and restore the page BY ITSELF — the error card is a last
 * resort, not the first response. Attempts are capped so a page that keeps
 * killing its renderer cannot loop forever; once the cap is hit the card
 * shows and the user decides (Reload / Back / close).
 */
internal const val GECKO_AUTO_RECOVERY_MAX_ATTEMPTS = 2
internal const val GECKO_AUTO_RECOVERY_DELAY_MS = 600L

internal fun geckoAutoRecoveryAllowed(
    attemptsUsed: Int,
    sessionDestroyed: Boolean,
    recoveryInProgress: Boolean
): Boolean =
    !sessionDestroyed && !recoveryInProgress && attemptsUsed < GECKO_AUTO_RECOVERY_MAX_ATTEMPTS

/** What one recovery attempt should do with the tab's GeckoSession. */
internal enum class GeckoRecoveryPlan {
    IN_PROGRESS,
    /** Healthy session, ordinary reload — no rebuild needed. */
    RELOAD_SESSION,
    /** Session still reports open but its renderer is dead: reload() on it is
     *  a silent no-op, so close + reopen the same session object and restore
     *  its saved state (page + history stack) instead. */
    REBUILD_AND_RESTORE,
    REOPEN_AND_RESTORE_STATE,
    REOPEN_AND_LOAD_CURRENT_URL,
    REOPEN_BLANK
}

internal fun geckoRecoveryPlan(
    sessionIsOpen: Boolean,
    recoveryRequired: Boolean,
    recoveryInProgress: Boolean,
    hasSavedState: Boolean,
    hasCurrentUrl: Boolean
): GeckoRecoveryPlan = when {
    recoveryInProgress -> GeckoRecoveryPlan.IN_PROGRESS
    sessionIsOpen && recoveryRequired -> GeckoRecoveryPlan.REBUILD_AND_RESTORE
    sessionIsOpen -> GeckoRecoveryPlan.RELOAD_SESSION
    hasSavedState -> GeckoRecoveryPlan.REOPEN_AND_RESTORE_STATE
    hasCurrentUrl -> GeckoRecoveryPlan.REOPEN_AND_LOAD_CURRENT_URL
    else -> GeckoRecoveryPlan.REOPEN_BLANK
}

internal const val GECKO_RECOVERY_FAILURE_WINDOW_MS = 60_000L
internal const val GECKO_RECOVERY_FAILURE_LIMIT = 3

internal data class GeckoRendererRetryDecision(
    val retryAllowed: Boolean,
    val retryAfterMillis: Long,
    val recentFailureCount: Int
)

internal fun geckoRendererRetryDecision(
    failureTimesMillis: List<Long>,
    nowMillis: Long
): GeckoRendererRetryDecision {
    val recentFailures = failureTimesMillis
        .filter { failedAt -> failedAt <= nowMillis && nowMillis - failedAt < GECKO_RECOVERY_FAILURE_WINDOW_MS }
        .sorted()
    val isThrottled = recentFailures.size >= GECKO_RECOVERY_FAILURE_LIMIT
    val retryAt = if (isThrottled) {
        recentFailures[recentFailures.size - GECKO_RECOVERY_FAILURE_LIMIT] + GECKO_RECOVERY_FAILURE_WINDOW_MS
    } else {
        nowMillis
    }
    return GeckoRendererRetryDecision(
        retryAllowed = !isThrottled,
        retryAfterMillis = (retryAt - nowMillis).coerceAtLeast(0),
        recentFailureCount = recentFailures.size
    )
}

internal fun recordGeckoRendererFailure(
    failureTimesMillis: List<Long>,
    failedAtMillis: Long
): List<Long> = (failureTimesMillis.filter { failedAt ->
    failedAt <= failedAtMillis && failedAtMillis - failedAt < GECKO_RECOVERY_FAILURE_WINDOW_MS
} + failedAtMillis).takeLast(GECKO_RECOVERY_FAILURE_LIMIT)

internal enum class GeckoSessionRecoveryAction {
    IGNORE_IN_PROGRESS,
    RELOAD_OPEN_SESSION,
    REOPEN_AND_RESTORE_STATE,
    REOPEN_AND_LOAD_CURRENT_URL,
    REOPEN_BLANK_SESSION
}

internal fun geckoSessionRecoveryAction(
    sessionIsOpen: Boolean,
    recoveryInProgress: Boolean,
    hasSavedState: Boolean,
    hasCurrentUrl: Boolean
): GeckoSessionRecoveryAction = when {
    recoveryInProgress -> GeckoSessionRecoveryAction.IGNORE_IN_PROGRESS
    sessionIsOpen -> GeckoSessionRecoveryAction.RELOAD_OPEN_SESSION
    hasSavedState -> GeckoSessionRecoveryAction.REOPEN_AND_RESTORE_STATE
    hasCurrentUrl -> GeckoSessionRecoveryAction.REOPEN_AND_LOAD_CURRENT_URL
    else -> GeckoSessionRecoveryAction.REOPEN_BLANK_SESSION
}

/**
 * Navigation operations shared by a logical browser tab regardless of its
 * rendering engine. Keep profile-wide storage, cookies, history, downloads,
 * permissions, and app-specific page features outside this per-tab contract.
 */
internal interface BrowserTabNavigation {
    fun canGoBack(): Boolean
    fun goBack()
    fun canGoForward(): Boolean
    fun goForward()
    fun loadUrl(url: String)
    /** Apply the standard wide/mobile viewport without changing browser identity. */
    fun setWideLayout(enabled: Boolean) = Unit
    fun reload()
    fun stopLoading()
}

/**
 * The rendering/lifecycle owner for one Click tab.
 *
 * The view may be detached and reattached when Compose switches tabs. Only the
 * tab LRU path should destroy the underlying view. GeckoView is the regular-tab
 * provider; private tabs remain on WebView pending verified cleanup.
 */
internal interface BrowserTabSession : BrowserTabNavigation {
    val view: View

    fun onPause()
    fun pauseTimers()
    fun onResume()
    fun resumeTimers()
    /** Called when this logical tab becomes (or ceases to be) the selected tab. */
    fun setSelected(selected: Boolean) = Unit
    fun detachFromParent()
    fun destroy()
}

/** Compatibility adapter retained for private tabs and explicit legacy screens. */
internal class WebViewBrowserTabSession(
    val webView: WebView,
) : BrowserTabSession {
    override val view: View get() = webView

    override fun canGoBack(): Boolean = webView.canGoBack()
    override fun goBack() = webView.goBack()
    override fun canGoForward(): Boolean = webView.canGoForward()
    override fun goForward() = webView.goForward()
    override fun loadUrl(url: String) = webView.loadUrl(url)
    override fun setWideLayout(enabled: Boolean) {
        val layout = DesktopLayoutPolicy.webViewLayout(enabled)
        webView.settings.useWideViewPort = layout.useWideViewPort
        webView.settings.loadWithOverviewMode = layout.loadWithOverviewMode
    }
    override fun reload() = webView.reload()
    override fun stopLoading() = webView.stopLoading()

    override fun onPause() = webView.onPause()
    override fun pauseTimers() = webView.pauseTimers()
    override fun onResume() = webView.onResume()
    override fun resumeTimers() = webView.resumeTimers()

    override fun detachFromParent() {
        (webView.parent as? ViewGroup)?.removeView(webView)
    }

    override fun destroy() = webView.destroy()
}

/**
 * Shared, testable browser-chrome behavior. A missing session or unavailable
 * history entry is a no-op; callers should preserve the currently rendered page.
 */
internal fun BrowserTabNavigation?.goBackIfPossible(): Boolean {
    if (this?.canGoBack() != true) return false
    goBack()
    return true
}

internal enum class BrowserBackResult { NAVIGATED_BACK, KEEP_CURRENT_PAGE }

/** Back without a history entry preserves the current page instead of navigating to about:blank. */
internal fun BrowserTabNavigation?.handleBrowserBackPress(): BrowserBackResult {
    if (this?.canGoBack() != true) return BrowserBackResult.KEEP_CURRENT_PAGE
    goBack()
    return BrowserBackResult.NAVIGATED_BACK
}

internal fun BrowserTabNavigation?.goForwardIfPossible(): Boolean {
    if (this?.canGoForward() != true) return false
    goForward()
    return true
}
