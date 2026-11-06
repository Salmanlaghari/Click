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

internal enum class GeckoRenderEvent { LOAD_STARTED, FIRST_COMPOSITE, PROCESS_CRASHED, PROCESS_KILLED }

internal fun geckoRendererRecoveryRequired(event: GeckoRenderEvent): Boolean = when (event) {
    GeckoRenderEvent.LOAD_STARTED, GeckoRenderEvent.FIRST_COMPOSITE -> false
    GeckoRenderEvent.PROCESS_CRASHED, GeckoRenderEvent.PROCESS_KILLED -> true
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
