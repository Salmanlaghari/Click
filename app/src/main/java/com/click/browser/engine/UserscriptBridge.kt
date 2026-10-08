package com.click.browser.engine

import android.content.Context
import android.webkit.JavascriptInterface

/**
 * Backing store for the GM_* storage functions exposed to userscripts
 * (see UserscriptEngine.buildInjection). Synchronous SharedPreferences —
 * one file per script id — because the JS bridge needs synchronous reads.
 */
class UserscriptBridge(context: Context) {

    private val appContext = context.applicationContext

    private fun prefs(scriptId: String) =
        appContext.getSharedPreferences(
            "userscript_${scriptId.take(64)}",
            Context.MODE_PRIVATE
        )

    @JavascriptInterface
    fun gmSetValue(scriptId: String, key: String, value: String) {
        try {
            prefs(scriptId).edit().putString(key.take(128), value).apply()
        } catch (_: Exception) { }
    }

    @JavascriptInterface
    fun gmGetValue(scriptId: String, key: String, default: String): String {
        return try {
            prefs(scriptId).getString(key.take(128), default) ?: default
        } catch (_: Exception) {
            default
        }
    }

    @JavascriptInterface
    fun gmDeleteValue(scriptId: String, key: String) {
        try {
            prefs(scriptId).edit().remove(key.take(128)).apply()
        } catch (_: Exception) { }
    }

    @JavascriptInterface
    fun gmListValues(scriptId: String): String {
        return try {
            val arr = org.json.JSONArray()
            prefs(scriptId).all.keys.forEach { arr.put(it) }
            arr.toString()
        } catch (_: Exception) {
            "[]"
        }
    }

    @JavascriptInterface
    fun gmLog(scriptId: String, message: String) {
        android.util.Log.i("Userscript", "[$scriptId] ${message.take(500)}")
    }
}
