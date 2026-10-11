package com.click.browser.engine

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** Serializes read-modify-write mutations to prevent lost updates. */
    private val mutationMutex = Mutex()

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

    suspend fun create(context: Context, name: String, color: Int): TabGroup = mutationMutex.withLock {
        val group = TabGroup(name = name.ifBlank { "Group" }, color = color)
        if (!save(context, list(context) + group)) {
            Log.e(TAG, "create failed to persist group ${group.id}")
        }
        group
    }

    suspend fun rename(context: Context, id: String, name: String) = mutationMutex.withLock {
        save(context, list(context).map {
            if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it
        })
    }

    suspend fun recolor(context: Context, id: String, color: Int) = mutationMutex.withLock {
        save(context, list(context).map { if (it.id == id) it.copy(color = color) else it })
    }

    suspend fun delete(context: Context, id: String) = mutationMutex.withLock {
        save(context, list(context).filterNot { it.id == id })
    }

    suspend fun addTab(context: Context, groupId: String, tabId: String) = mutationMutex.withLock {
        save(context, list(context).map {
            if (it.id == groupId && tabId !in it.tabIds) it.copy(tabIds = it.tabIds + tabId) else it
        })
    }

    suspend fun removeTab(context: Context, groupId: String, tabId: String) = mutationMutex.withLock {
        save(context, list(context).map {
            if (it.id == groupId) it.copy(tabIds = it.tabIds - tabId) else it
        })
    }

    /** Drops tab IDs that no longer exist (caller passes the live IDs). */
    suspend fun pruneDeadTabs(context: Context, liveIds: Set<String>) = mutationMutex.withLock {
        val groups = list(context)
        val pruned = groups.map { it.copy(tabIds = it.tabIds.filter { id -> id in liveIds }) }
        if (pruned != groups) save(context, pruned)
    }

    /** Returns true on success; callers should handle false (data not persisted). */
    private suspend fun save(context: Context, groups: List<TabGroup>): Boolean {
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
        return try {
            context.dataStore.edit { it[AppSettings.TAB_GROUPS_JSON] = arr.toString() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "save failed - tab groups not persisted", e)
            false
        }
    }
}
