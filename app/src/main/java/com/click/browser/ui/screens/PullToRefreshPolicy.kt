package com.click.browser.ui.screens

import com.click.browser.engine.BrowserTabNavigation

/** Pull refresh is a new HTTP(S) GET; it must not call reload() and replay a previous POST body. */
internal fun pullToRefreshGetUrl(currentUrl: String?): String? =
    browserRetryUrl(method = "GET", url = currentUrl)

/**
 * Start refresh through loadUrl(), which issues a fresh URL navigation. The callback runs first so
 * synchronous navigation callbacks cannot finish before the UI records the refreshing tab.
 */
internal fun startPullToRefresh(
    navigation: BrowserTabNavigation?,
    currentUrl: String?,
    onStarted: () -> Unit
): Boolean {
    val targetUrl = pullToRefreshGetUrl(currentUrl) ?: return false
    val tabNavigation = navigation ?: return false
    onStarted()
    return try {
        tabNavigation.loadUrl(targetUrl)
        true
    } catch (_: IllegalArgumentException) {
        // The browser adapter rejected this GET synchronously; let unrelated failures surface.
        false
    } catch (_: IllegalStateException) {
        // A tab session can close between the refresh gesture and dispatch.
        false
    }
}

/** Stale navigation callbacks must not clear a newer refresh for the same tab. */
internal fun refreshingTabIdsAfterNavigationEnd(
    refreshingTabIds: Set<String>,
    completedTabId: String,
    isCurrentNavigation: Boolean
): Set<String> = if (isCurrentNavigation) refreshingTabIds - completedTabId else refreshingTabIds
