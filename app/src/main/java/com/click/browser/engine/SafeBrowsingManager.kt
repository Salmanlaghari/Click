package com.click.browser.engine

import android.util.Log
import android.webkit.SafeBrowsingResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds a pending Safe Browsing hit so the UI can show Click's own
 * premium-styled interstitial instead of the stock WebView one.
 *
 * The [SafeBrowsingResponse] callback MUST always be resolved
 * (backToSafety / proceed) — otherwise the WebView navigation hangs.
 * Framework API 27+; the client only reports hits on supported devices.
 */
object SafeBrowsingManager {

    data class PendingHit(
        val url: String,
        val threatType: Int,
        val response: SafeBrowsingResponse
    )

    private val _pendingHit = MutableStateFlow<PendingHit?>(null)
    val pendingHit: StateFlow<PendingHit?> = _pendingHit.asStateFlow()

    private val _threatsBlocked = MutableStateFlow(0)
    val threatsBlocked: StateFlow<Int> = _threatsBlocked.asStateFlow()

    fun reportHit(url: String, threatType: Int, response: SafeBrowsingResponse) {
        _pendingHit.value = PendingHit(url, threatType, response)
        _threatsBlocked.value = _threatsBlocked.value + 1
        // Feed the home privacy pill ("Protected · N trackers blocked").
        AdBlocker.reportBlockedThreat()
    }

    /** User chose safety: report a false positive and navigate back. */
    fun backToSafety() {
        try {
            _pendingHit.value?.response?.backToSafety(true)
        } catch (e: Exception) {
            Log.e("SafeBrowsing", "backToSafety failed", e)
        } finally {
            _pendingHit.value = null
        }
    }

    /** User explicitly accepted the risk after the warning. */
    fun proceedAnyway() {
        try {
            _pendingHit.value?.response?.proceed(true)
        } catch (e: Exception) {
            Log.e("SafeBrowsing", "proceedAnyway failed", e)
        } finally {
            _pendingHit.value = null
        }
    }

    fun dismiss() {
        backToSafety()
    }

    fun threatLabel(threatType: Int): String = when (threatType) {
        // Framework constants are compile-time inlined; the callback itself
        // only fires on API 27+, so referencing them here is safe on minSdk 26.
        android.webkit.WebViewClient.SAFE_BROWSING_THREAT_MALWARE -> "Malware"
        android.webkit.WebViewClient.SAFE_BROWSING_THREAT_PHISHING -> "Deceptive site (phishing)"
        android.webkit.WebViewClient.SAFE_BROWSING_THREAT_UNWANTED_SOFTWARE -> "Unwanted software"
        android.webkit.WebViewClient.SAFE_BROWSING_THREAT_BILLING -> "Deceptive billing"
        else -> "Harmful content"
    }

    fun threatDescription(threatType: Int): String = when (threatType) {
        android.webkit.WebViewClient.SAFE_BROWSING_THREAT_MALWARE ->
            "This site may try to install malicious software that can harm your device or steal your data."
        android.webkit.WebViewClient.SAFE_BROWSING_THREAT_PHISHING ->
            "This site may be pretending to be a site you trust in order to steal your passwords or personal information."
        android.webkit.WebViewClient.SAFE_BROWSING_THREAT_UNWANTED_SOFTWARE ->
            "This site may try to install software that changes your device without clear consent."
        android.webkit.WebViewClient.SAFE_BROWSING_THREAT_BILLING ->
            "This site may try to trick you into unwanted paid subscriptions."
        else ->
            "Google Safe Browsing flagged this page as potentially harmful."
    }
}
