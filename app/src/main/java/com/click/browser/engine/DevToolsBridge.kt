package com.click.browser.engine

import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class LogEntry(val type: String, val message: String, val timestamp: Long = System.currentTimeMillis())
data class NetworkRequest(
    val method: String,
    val url: String,
    val status: Int,
    val time: Long,
    val size: String,
    val timestamp: Long = System.currentTimeMillis()
)

class DevToolsBridge(
    private val onLogAdded: (LogEntry) -> Unit,
    private val onNetworkAdded: (NetworkRequest) -> Unit,
    private val onDomUpdated: (String) -> Unit,
    private val onSourcesUpdated: (List<String>) -> Unit
) {
    @JavascriptInterface
    fun log(type: String, message: String) {
        onLogAdded(LogEntry(type, message))
    }

    @JavascriptInterface
    fun network(method: String, url: String, status: Int, time: Long, size: String) {
        onNetworkAdded(NetworkRequest(method, url, status, time, size))
    }

    @JavascriptInterface
    fun dom(html: String) {
        onDomUpdated(html)
    }

    @JavascriptInterface
    fun sources(sourcesJson: String) {
        try {
            val list = mutableListOf<String>()
            val array = org.json.JSONArray(sourcesJson)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            onSourcesUpdated(list)
        } catch (e: Exception) {
            // ignore
        }
    }
}
