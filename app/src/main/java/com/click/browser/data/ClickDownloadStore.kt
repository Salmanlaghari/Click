package com.click.browser.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.ClickDownloadRecord
import com.click.browser.engine.ClickDownloadState
import com.click.browser.engine.profileDataStoreFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable store for [ClickDownloadRecord]s — one JSON list per browser
 * profile, mirroring the custom-folder download store's pattern in
 * [BrowserRepository]. Records (not the partial files) are what pause/resume
 * and the Downloads screen survive on across process death.
 */
class ClickDownloadStore(private val context: Context) {

    fun flowFor(mode: BrowserMode): Flow<List<ClickDownloadRecord>> =
        context.profileDataStoreFor(mode).data.map { preferences ->
            parse(preferences[KEY])
        }

    suspend fun allFor(mode: BrowserMode): List<ClickDownloadRecord> = flowFor(mode).first()

    suspend fun getFor(mode: BrowserMode, id: String): ClickDownloadRecord? =
        allFor(mode).firstOrNull { it.id == id }

    suspend fun add(record: ClickDownloadRecord) {
        mutate(record.mode) { items -> items + record }
    }

    /** Applies [transform] to the record with the same id; no-op when absent. */
    suspend fun update(
        mode: BrowserMode,
        id: String,
        transform: (ClickDownloadRecord) -> ClickDownloadRecord
    ): ClickDownloadRecord? {
        var updated: ClickDownloadRecord? = null
        mutate(mode) { items ->
            items.map { item ->
                if (item.id == id) transform(item).also { updated = it } else item
            }
        }
        return updated
    }

    suspend fun remove(mode: BrowserMode, id: String) {
        mutate(mode) { items -> items.filterNot { it.id == id } }
    }

    private suspend fun mutate(
        mode: BrowserMode,
        transform: (List<ClickDownloadRecord>) -> List<ClickDownloadRecord>
    ) {
        context.profileDataStoreFor(mode).edit { preferences ->
            val current = parse(preferences[KEY])
            preferences[KEY] = encode(
                com.click.browser.engine.trimClickDownloadRecords(transform(current), MAX_RECORDS)
            )
        }
    }

    private fun parse(json: String?): List<ClickDownloadRecord> = try {
        if (json.isNullOrBlank()) emptyList() else {
            val array = JSONArray(json)
            List(array.length()) { index ->
                val o = array.getJSONObject(index)
                ClickDownloadRecord(
                    id = o.getString("id"),
                    url = o.getString("url"),
                    fileName = o.getString("fileName"),
                    mimeType = o.optString("mimeType").takeIf { it.isNotBlank() },
                    mode = runCatching { BrowserMode.valueOf(o.getString("mode")) }
                        .getOrDefault(BrowserMode.SIMPLE),
                    destination = o.optString("destination", "phone"),
                    state = runCatching { ClickDownloadState.valueOf(o.getString("state")) }
                        .getOrDefault(ClickDownloadState.FAILED),
                    bytesDownloaded = o.optLong("bytesDownloaded", 0L),
                    totalBytes = o.optLong("totalBytes", -1L),
                    etag = o.optString("etag").takeIf { it.isNotBlank() },
                    lastModified = o.optString("lastModified").takeIf { it.isNotBlank() },
                    userAgent = o.optString("userAgent").takeIf { it.isNotBlank() },
                    finalLocation = o.optString("finalLocation").takeIf { it.isNotBlank() },
                    errorMessage = o.optString("errorMessage").takeIf { it.isNotBlank() },
                    workId = o.optString("workId").takeIf { it.isNotBlank() },
                    createdAt = o.optLong("createdAt", 0L),
                    updatedAt = o.optLong("updatedAt", 0L)
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun encode(items: List<ClickDownloadRecord>): String {
        val array = JSONArray()
        for (item in items) {
            array.put(JSONObject().apply {
                put("id", item.id)
                put("url", item.url)
                put("fileName", item.fileName)
                item.mimeType?.let { put("mimeType", it) }
                put("mode", item.mode.name)
                put("destination", item.destination)
                put("state", item.state.name)
                put("bytesDownloaded", item.bytesDownloaded)
                put("totalBytes", item.totalBytes)
                item.etag?.let { put("etag", it) }
                item.lastModified?.let { put("lastModified", it) }
                item.userAgent?.let { put("userAgent", it) }
                item.finalLocation?.let { put("finalLocation", it) }
                item.errorMessage?.let { put("errorMessage", it) }
                item.workId?.let { put("workId", it) }
                put("createdAt", item.createdAt)
                put("updatedAt", item.updatedAt)
            })
        }
        return array.toString()
    }

    private companion object {
        val KEY = stringPreferencesKey("click_downloads_v1")
        const val MAX_RECORDS = 100
    }
}
