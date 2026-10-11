package com.click.browser.engine

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Tab Groups: named, color-coded collections of tab IDs.
 *
 * Storage is a JSON array in DataStore: [{id,name,color,tabIds[]}].
 * Tab IDs here are the browser's tab identifiers (opaque strings) — the
 * store never assumes a tab still exists; callers prune dead IDs.
 */
data class TabGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** ARGB color int for the group chip. */
    val color: Int,
    val tabIds: List<String> = emptyList()
)

object TabGroupStore {

    private const val TAG = "TabGroupStore"

    /** Fixed palette offered in the group editor (ARGB). */
    val PALETTE = listOf(
        0xFF1D4ED8.toInt(), // blue
        0xFF7C3AED.toInt(), // purple
        0xFFDC2626.toInt(), // red
        0xFF16A34A.toInt(), // green
        0xFFD97706.toInt(), // amber
        0xFF0E7490.toInt(), // teal
        0xFFDB2777.toInt(), // pink
        0xFF4B5563.toInt()  // gray
    )

    suspend fun list(context: Context): List<TabGroup> {
        val json = try {
            context.dataStore.data.map { it[AppSettings.TAB_GROUPS_JSON] }.first()
        } catch (e: Exception) {
            Log.w(TAG, "read failed", e); return emptyList()
        } ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val ids = o.optJSONArray("tabIds")
                TabGroup(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = o.optString("name").ifBlank { "Group" },
                    color = o.optInt("color", PALETTE[0]),
                    tabIds = (0 until (ids?.length() ?: 0)).mapNotNull { j ->
                        ids?.optString(j)?.takeIf { it.isNotBlank() }
                    }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "JSON corrupt", e); emptyList()
        }
    }

    suspend fun create(context: Context, name: String, color: Int): TabGroup {
        val group = TabGroup(name = name.ifBlank { "Group" }, color = color)
        save(context, list(context) + group)
        return group
    }

    suspend fun rename(context: Context, id: String, name: String) {
        save(context, list(context).map {
            if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it
        })
    }

    suspend fun recolor(context: Context, id: String, color: Int) {
        save(context, list(context).map { if (it.id == id) it.copy(color = color) else it })
    }

    suspend fun delete(context: Context, id: String) {
        save(context, list(context).filterNot { it.id == id })
    }

    suspend fun addTab(context: Context, groupId: String, tabId: String) {
        save(context, list(context).map {
            if (it.id == groupId && tabId !in it.tabIds) it.copy(tabIds = it.tabIds + tabId) else it
        })
    }

    suspend fun removeTab(context: Context, groupId: String, tabId: String) {
        save(context, list(context).map {
            if (it.id == groupId) it.copy(tabIds = it.tabIds - tabId) else it
        })
    }

    /** Drops tab IDs that no longer exist (caller passes the live IDs). */
    suspend fun pruneDeadTabs(context: Context, liveIds: Set<String>) {
        val groups = list(context)
        val pruned = groups.map { it.copy(tabIds = it.tabIds.filter { id -> id in liveIds }) }
        if (pruned != groups) save(context, pruned)
    }

    private suspend fun save(context: Context, groups: List<TabGroup>) {
        val arr = JSONArray()
        groups.forEach { g ->
            arr.put(
                JSONObject()
                    .put("id", g.id)
                    .put("name", g.name)
                    .put("color", g.color)
                    .put("tabIds", JSONArray(g.tabIds))
            )
        }
        try {
            context.dataStore.edit { it[AppSettings.TAB_GROUPS_JSON] = arr.toString() }
        } catch (e: Exception) {
            Log.w(TAG, "save failed", e)
        }
    }
}
