package com.click.browser.engine

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView

enum class BrowserTabEngine {
    WEBVIEW,
    GECKOVIEW
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
    fun reload()
    fun stopLoading()
}

/**
 * The rendering/lifecycle owner for one Click tab.
 *
 * The view may be detached and reattached when Compose switches tabs. Only the
 * tab LRU path should destroy the underlying view. WebView remains the default
 * provider; GeckoView can be selected explicitly for a normal tab in this phase.
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

/** The compatibility adapter used for the existing default WebView tabs. */
internal class WebViewBrowserTabSession(
    val webView: WebView,
) : BrowserTabSession {
    override val view: View get() = webView

    override fun canGoBack(): Boolean = webView.canGoBack()
    override fun goBack() = webView.goBack()
    override fun canGoForward(): Boolean = webView.canGoForward()
    override fun goForward() = webView.goForward()
    override fun loadUrl(url: String) = webView.loadUrl(url)
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
 * history entry is a no-op; the caller decides what to show instead.
 */
internal fun BrowserTabNavigation?.goBackIfPossible(): Boolean {
    if (this?.canGoBack() != true) return false
    goBack()
    return true
}

internal fun BrowserTabNavigation?.goForwardIfPossible(): Boolean {
    if (this?.canGoForward() != true) return false
    goForward()
    return true
}
