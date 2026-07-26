package com.click.browser.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.click.browser.engine.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(val title: String, val url: String)
data class HistoryItem(val title: String, val url: String, val timestamp: Long)
data class DownloadItem(val fileName: String, val url: String, val path: String, val timestamp: Long)

class BrowserRepository(private val context: Context) {

    companion object {
        private val BOOKMARKS_KEY = stringPreferencesKey("bookmarks")
        private val HISTORY_KEY = stringPreferencesKey("history")
        private val DOWNLOADS_KEY = stringPreferencesKey("downloads")
    }

    // --- Bookmarks ---
    val bookmarksFlow: Flow<List<Bookmark>> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[BOOKMARKS_KEY] ?: "[]"
        val list = mutableListOf<Bookmark>()
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(Bookmark(obj.getString("title"), obj.getString("url")))
        }
        list
    }

    suspend fun addBookmark(bookmark: Bookmark) {
        context.dataStore.edit { preferences ->
            val jsonStr = preferences[BOOKMARKS_KEY] ?: "[]"
            val array = JSONArray(jsonStr)

            // Avoid duplicate URLs
            var duplicate = false
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.getString("url") == bookmark.url) {
                    duplicate = true
                    break
                }
            }
            if (!duplicate) {
                val newObj = JSONObject()
                newObj.put("title", bookmark.title)
                newObj.put("url", bookmark.url)
                array.put(newObj)
                preferences[BOOKMARKS_KEY] = array.toString()
            }
        }
    }

    suspend fun deleteBookmark(url: String) {
        context.dataStore.edit { preferences ->
            val jsonStr = preferences[BOOKMARKS_KEY] ?: "[]"
            val array = JSONArray(jsonStr)
            val newArray = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.getString("url") != url) {
                    newArray.put(obj)
                }
            }
            preferences[BOOKMARKS_KEY] = newArray.toString()
        }
    }

    // --- History ---
    val historyFlow: Flow<List<HistoryItem>> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[HISTORY_KEY] ?: "[]"
        val list = mutableListOf<HistoryItem>()
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(HistoryItem(obj.getString("title"), obj.getString("url"), obj.getLong("timestamp")))
        }
        list.sortedByDescending { it.timestamp }
    }

    suspend fun addHistoryItem(item: HistoryItem) {
        context.dataStore.edit { preferences ->
            val jsonStr = preferences[HISTORY_KEY] ?: "[]"
            val array = JSONArray(jsonStr)

            // Limit history size to 100
            if (array.length() >= 100) {
                val newArray = JSONArray()
                for (i in 1 until array.length()) {
                    newArray.put(array.get(i))
                }
                newArray.put(JSONObject().apply {
                    put("title", item.title)
                    put("url", item.url)
                    put("timestamp", item.timestamp)
                })
                preferences[HISTORY_KEY] = newArray.toString()
            } else {
                val newObj = JSONObject().apply {
                    put("title", item.title)
                    put("url", item.url)
                    put("timestamp", item.timestamp)
                }
                array.put(newObj)
                preferences[HISTORY_KEY] = array.toString()
            }
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { preferences ->
            preferences[HISTORY_KEY] = "[]"
        }
    }

    // --- Downloads ---
    val downloadsFlow: Flow<List<DownloadItem>> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[DOWNLOADS_KEY] ?: "[]"
        val list = mutableListOf<DownloadItem>()
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(DownloadItem(obj.getString("fileName"), obj.getString("url"), obj.getString("path"), obj.getLong("timestamp")))
        }
        list.sortedByDescending { it.timestamp }
    }

    suspend fun addDownloadItem(item: DownloadItem) {
        context.dataStore.edit { preferences ->
            val jsonStr = preferences[DOWNLOADS_KEY] ?: "[]"
            val array = JSONArray(jsonStr)
            val newObj = JSONObject().apply {
                put("fileName", item.fileName)
                put("url", item.url)
                put("path", item.path)
                put("timestamp", item.timestamp)
            }
            array.put(newObj)
            preferences[DOWNLOADS_KEY] = array.toString()
        }
    }
}
