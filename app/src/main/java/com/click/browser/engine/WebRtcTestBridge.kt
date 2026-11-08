package com.click.browser.engine

import android.webkit.JavascriptInterface

/** Receives only the status-bearing result for the currently active WebRTC test run. */
class WebRtcTestBridge(
    private val onResultCallback: (String, WebRtcTestResult) -> Unit
) {
    @JavascriptInterface
    fun onResult(runId: String, payload: String) {
        if (!WebRtcTestRunGate.consume(runId)) return
        onResultCallback(runId, WebRtcTestResult.parse(payload))
    }
}
