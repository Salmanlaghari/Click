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
 * One installed userscript: its parsed metadata plus the full source.
 * The source is stored verbatim — what the user reviewed at install time
 * is exactly what runs.
 */
internal data class InstalledUserscript(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val namespace: String,
    val version: String,
    val description: String,
    val sourceUrl: String?,
    val code: String,
    val enabled: Boolean = true,
    val matchPatterns: List<String> = emptyList(),
    val includes: List<String> = emptyList(),
    val excludes: List<String> = emptyList(),
    val runAt: UserscriptRunAt = UserscriptRunAt.DOCUMENT_END,
    val grants: Set<String> = emptySet(),
    val installedAtMs: Long = 0L,
    /** Last runtime error reported by the runner, shown in the manager. */
    val lastError: String? = null
) {
    val identityKey: String get() = namespace + "|" + name

    fun toMetadata(): UserscriptMetadata = UserscriptMetadata(
        name = name,
        namespace = namespace,
        version = version,
        description = description,
        matchPatterns = matchPatterns,
        includes = includes,
        excludes = excludes,
        runAt = runAt,
        grants = grants,
        noFrames = false
    )

    companion object {
        fun fromMetadata(
            meta: UserscriptMetadata,
            code: String,
            sourceUrl: String?,
            id: String = UUID.randomUUID().toString(),
            enabled: Boolean = true,
            installedAtMs: Long = System.currentTimeMillis()
        ): InstalledUserscript = InstalledUserscript(
            id = id,
            name = meta.name,
            namespace = meta.namespace,
            version = meta.version,
            description = meta.description,
            sourceUrl = sourceUrl,
            code = code,
            enabled = enabled,
            matchPatterns = meta.matchPatterns,
            includes = meta.includes,
            excludes = meta.excludes,
            runAt = meta.runAt,
            grants = meta.grants,
            installedAtMs = installedAtMs
        )
    }
}

/** Pure JSON codec for the script list (unit-testable, no Android deps). */
internal object UserscriptJson {
    fun encode(scripts: List<InstalledUserscript>): String {
        val arr = JSONArray()
        for (s in scripts) {
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("namespace", s.namespace)
                put("version", s.version)
                put("description", s.description)
                put("sourceUrl", s.sourceUrl ?: JSONObject.NULL)
                put("code", s.code)
                put("enabled", s.enabled)
                put("match", JSONArray(s.matchPatterns))
                put("include", JSONArray(s.includes))
                put("exclude", JSONArray(s.excludes))
                put("runAt", s.runAt.name)
                put("grants", JSONArray(s.grants.toList()))
                put("installedAtMs", s.installedAtMs)
                put("lastError", s.lastError ?: JSONObject.NULL)
            })
        }
        return arr.toString()
    }

    fun decode(json: String?): List<InstalledUserscript> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val code = o.optString("code")
                val name = o.optString("name")
                if (code.isBlank() || name.isBlank()) return@mapNotNull null
                fun strings(key: String): List<String> {
                    val a = o.optJSONArray(key) ?: return emptyList()
                    return (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
                }
                InstalledUserscript(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    namespace = o.optString("namespace"),
                    version = o.optString("version"),
                    description = o.optString("description"),
                    sourceUrl = o.optString("sourceUrl").takeIf { it.isNotBlank() && !o.isNull("sourceUrl") },
                    code = code,
                    enabled = o.optBoolean("enabled", true),
                    matchPatterns = strings("match"),
                    includes = strings("include"),
                    excludes = strings("exclude"),
                    runAt = runCatching { UserscriptRunAt.valueOf(o.optString("runAt")) }
                        .getOrDefault(UserscriptRunAt.DOCUMENT_END),
                    grants = strings("grants").toSet(),
                    installedAtMs = o.optLong("installedAtMs"),
                    lastError = if (o.isNull("lastError")) null else o.optString("lastError").takeIf(String::isNotBlank)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/**
 * Persistence for installed userscripts and their GM value storage.
 * Same conventions as [TabGroupStore]: JSON in DataStore, mutations
 * serialized through a mutex so concurrent edits cannot lose updates.
 */
internal object UserscriptStore {

    private const val TAG = "UserscriptStore"
    private val mutationMutex = Mutex()

    suspend fun list(context: Context): List<InstalledUserscript> {
        val json = try {
            context.dataStore.data.map { it[AppSettings.USERSCRIPTS_JSON] }.first()
        } catch (e: Exception) {
            Log.w(TAG, "read failed", e); return emptyList()
        }
        return UserscriptJson.decode(json)
    }

    /**
     * Installs [script]; a script with the same namespace+name identity
     * replaces the existing record (keeping its id, enabled state and GM
     * values) — that is the update path, since Click does not poll
     * update URLs in the background.
     */
    suspend fun install(context: Context, script: InstalledUserscript): InstalledUserscript =
        mutationMutex.withLock {
            val current = list(context)
            val existing = current.firstOrNull { it.identityKey == script.identityKey }
            val stored = if (existing != null) {
                script.copy(id = existing.id, enabled = existing.enabled, installedAtMs = existing.installedAtMs)
            } else script
            val next = current.filter { it.id != stored.id } + stored
            save(context, next)
            stored
        }

    suspend fun setEnabled(context: Context, id: String, enabled: Boolean) {
        mutationMutex.withLock {
            val next = list(context).map { if (it.id == id) it.copy(enabled = enabled, lastError = null) else it }
            save(context, next)
        }
    }

    suspend fun delete(context: Context, id: String) {
        mutationMutex.withLock {
            save(context, list(context).filter { it.id != id })
            // GM values belong to the script; they go with it.
            val values = readValues(context)
            if (values.remove(id) != null) saveValues(context, values)
        }
    }

    suspend fun recordError(context: Context, id: String, message: String) {
        mutationMutex.withLock {
            val next = list(context).map {
                if (it.id == id) it.copy(lastError = message.take(300)) else it
            }
            save(context, next)
        }
    }

    // ---- GM value storage -------------------------------------------------

    /** All GM values: scriptId → (key → JSON value). Values are kept as
     *  parsed JSON so numbers/booleans/objects survive the round trip. */
    suspend fun readValues(context: Context): MutableMap<String, JSONObject> {
        val json = try {
            context.dataStore.data.map { it[AppSettings.USERSCRIPT_VALUES_JSON] }.first()
        } catch (e: Exception) {
            Log.w(TAG, "values read failed", e); return mutableMapOf()
        }
        if (json.isNullOrBlank()) return mutableMapOf()
        return try {
            val root = JSONObject(json)
            val out = mutableMapOf<String, JSONObject>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                root.optJSONObject(k)?.let { out[k] = it }
            }
            out
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    suspend fun setValue(context: Context, scriptId: String, key: String, value: Any?) {
        mutationMutex.withLock {
            val values = readValues(context)
            val perScript = values.getOrPut(scriptId) { JSONObject() }
            if (value == null || value === JSONObject.NULL) perScript.remove(key)
            else perScript.put(key, value)
            saveValues(context, values)
        }
    }

    suspend fun deleteValue(context: Context, scriptId: String, key: String) {
        setValue(context, scriptId, key, null)
    }

    private suspend fun save(context: Context, scripts: List<InstalledUserscript>) {
        try {
            context.dataStore.edit { it[AppSettings.USERSCRIPTS_JSON] = UserscriptJson.encode(scripts) }
        } catch (e: Exception) {
            Log.w(TAG, "write failed", e)
        }
    }

    private suspend fun saveValues(context: Context, values: Map<String, JSONObject>) {
        try {
            val root = JSONObject()
            values.forEach { (k, v) -> root.put(k, v) }
            context.dataStore.edit { it[AppSettings.USERSCRIPT_VALUES_JSON] = root.toString() }
        } catch (e: Exception) {
            Log.w(TAG, "values write failed", e)
        }
    }
}
