package com.click.browser.engine

import android.webkit.JavascriptInterface
import org.json.JSONArray

/**
 * Receives the result of the real WebRTC leak test
 * ([PrivacyGuards.WEBRTC_LEAK_TEST_JS]) running in the current tab.
 */
class WebRtcTestBridge(
    private val onResult: (List<String>) -> Unit
) {
    @JavascriptInterface
    fun onIpsDetected(jsonArrayStr: String) {
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(jsonArrayStr)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (_: Exception) {
            // ignore malformed payloads
        }
        onResult(list)
    }
}
