package com.click.browser.engine

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Install / list / enable / delete lifecycle for userscript extensions.
 * Scripts are stored as files under filesDir/userscripts/<id>.user.js;
 * the index (metadata + enabled flags) lives in DataStore.
 */
class UserscriptManager(private val context: Context) {

    private val indexKey = stringPreferencesKey("userscripts_index")

    private val dir: File
        get() = File(context.filesDir, "userscripts").apply { mkdirs() }

    suspend fun listScripts(): List<UserscriptInfo> = withContext(Dispatchers.IO) {
        val json = context.dataStore.data.first()[indexKey].orEmpty()
        if (json.isBlank()) return@withContext emptyList()
        try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                UserscriptInfo(
                    id = o.getString("id"),
                    meta = UserscriptMeta(
                        name = o.getString("name"),
                        namespace = o.optString("namespace"),
                        version = o.optString("version"),
                        description = o.optString("description"),
                        matches = jsonArrayToList(o.optJSONArray("matches")),
                        includes = jsonArrayToList(o.optJSONArray("includes")),
                        runAt = o.optString("runAt", "document-end"),
                        grants = jsonArrayToList(o.optJSONArray("grants"))
                    ),
                    enabled = o.optBoolean("enabled", true)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun install(source: String): Result<UserscriptInfo> = withContext(Dispatchers.IO) {
        val (meta, code) = UserscriptEngine.parse(source)
            ?: return@withContext Result.failure(
                Exception("Not a valid userscript — needs a ==UserScript== block with @name and code.")
            )
        val id = UUID.randomUUID().toString()
        File(dir, "$id.user.js").writeText(code)
        val info = UserscriptInfo(id, meta, enabled = true)
        saveIndex(listScripts() + info)
        Result.success(info)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        saveIndex(listScripts().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        try { File(dir, "$id.user.js").delete() } catch (_: Exception) { }
        try {
            context.getSharedPreferences("userscript_$id", Context.MODE_PRIVATE)
                .edit().clear().apply()
        } catch (_: Exception) { }
        saveIndex(listScripts().filter { it.id != id })
    }

    fun getCode(id: String): String? = try {
        File(dir, "$id.user.js").takeIf { it.exists() }?.readText()
    } catch (_: Exception) {
        null
    }

    private suspend fun saveIndex(list: List<UserscriptInfo>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.meta.name)
                    .put("namespace", s.meta.namespace)
                    .put("version", s.meta.version)
                    .put("description", s.meta.description)
                    .put("matches", JSONArray(s.meta.matches))
                    .put("includes", JSONArray(s.meta.includes))
                    .put("runAt", s.meta.runAt)
                    .put("grants", JSONArray(s.meta.grants))
                    .put("enabled", s.enabled)
            )
        }
        context.dataStore.edit { it[indexKey] = arr.toString() }
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optString(it)?.takeIf { s -> s.isNotEmpty() } }
    }

    /**
     * First-run seeding: installs the bundled pre-installed userscripts from
     * the app assets folder ("userscripts", files ending with .user.js),
     * enabled by default. Runs exactly once (guarded by a DataStore flag).
     * The user can disable, delete, or add scripts afterwards from the
     * Userscript Extensions screen.
     *
     * @return how many bundled scripts were newly installed.
     */
    suspend fun seedBundledScripts(): Int = withContext(Dispatchers.IO) {
        val seededKey = stringPreferencesKey("userscripts_bundled_seeded_v1")
        if (context.dataStore.data.first()[seededKey] == "1") return@withContext 0
        var count = 0
        try {
            val names = context.assets.list("userscripts").orEmpty()
                .filter { it.endsWith(".user.js") }
                .sorted()
            for (name in names) {
                try {
                    val source = context.assets.open("userscripts/$name")
                        .bufferedReader().use { it.readText() }
                    if (install(source).isSuccess) count++
                } catch (_: Exception) {
                    // One bad asset must not block the rest.
                }
            }
        } catch (_: Exception) {
            // Missing assets folder — nothing to seed.
        }
        context.dataStore.edit { it[seededKey] = "1" }
        count
    }
}
