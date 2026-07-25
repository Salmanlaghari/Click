package com.click.browser.engine

import android.webkit.JavascriptInterface

class VideoGrabberBridge(
    private val onVideosDetected: (List<String>) -> Unit
) {
    @JavascriptInterface
    fun onVideosDetected(jsonArrayStr: String) {
        try {
            val list = mutableListOf<String>()
            val array = org.json.JSONArray(jsonArrayStr)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            onVideosDetected(list)
        } catch (e: Exception) {
            // ignore
        }
    }
}
