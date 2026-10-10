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
 * Scripts are stored as files under filesDir/userscripts/<id>.user.js
 * (filesDir/userscripts_advanced for the Advance profile — see
 * [profileDataStore]); the index (metadata + enabled flags) lives in the
 * profile's DataStore.
 */
class UserscriptManager(private val context: Context) {

    companion object {
        /** Display name of the bundled opt-in WebRTC guard script (see assets). */
        const val WEBRTC_GUARD_NAME = "WebRTC Leak Guard"
        private const val WEBRTC_GUARD_ASSET = "16-webrtc-leak-guard.user.js"

        /**
         * Bundled scripts that seed DISABLED (opt-in). Everything else seeds
         * enabled. The WebRTC guard breaks legitimate video calls, so it must
         * never turn itself on.
         */
        private val OPT_IN_SEED_DISABLED = setOf(WEBRTC_GUARD_ASSET)
    }

    private val indexKey = stringPreferencesKey("userscripts_index")

    private val dir: File
        get() {
            // Click Advance keeps its own script folder: nothing installed on
            // the other modes is visible here, and vice versa.
            //
            // Deliberately keyed off V9Engine.bootMode — the process's pinned
            // engine identity — NOT the persisted UI mode. bootMode is the
            // same single source of truth behind profileDataStore and the
            // WebView data-directory suffix, so the folder, the script index,
            // and the cookie jar can never disagree. The only divergence is
            // the restart-failure fallback in MainActivity.v9SwitchMode, which
            // is already documented there as a degraded no-isolation mode.
            // Fail-fast init contract mirrors Context.profileDataStore.
            check(V9Engine.isBootPinned) {
                "UserscriptManager.dir accessed before V9Engine.applyDataDirectorySuffix()"
            }
            val name =
                if (V9Engine.bootMode == BrowserMode.ADVANCED) "userscripts_advanced"
                else "userscripts"
            return File(context.filesDir, name).apply { mkdirs() }
        }

    suspend fun listScripts(): List<UserscriptInfo> = withContext(Dispatchers.IO) {
        val json = context.profileDataStore.data.first()[indexKey].orEmpty()
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

    suspend fun install(source: String, enabled: Boolean = true): Result<UserscriptInfo> = withContext(Dispatchers.IO) {
        val (meta, code) = UserscriptEngine.parse(source)
            ?: return@withContext Result.failure(
                Exception("Not a valid userscript — needs a ==UserScript== block with @name and code.")
            )
        val id = UUID.randomUUID().toString()
        File(dir, "$id.user.js").writeText(code)
        val info = UserscriptInfo(id, meta, enabled = enabled)
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
        context.profileDataStore.edit { it[indexKey] = arr.toString() }
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optString(it)?.takeIf { s -> s.isNotEmpty() } }
    }

    /**
     * First-run seeding: installs the bundled pre-installed userscripts from
     * the app assets folder ("userscripts", files ending with .user.js).
     * Scripts already present (matched by @name) are skipped, so re-seeding
     * never creates duplicates. Most scripts seed enabled; [OPT_IN_SEED_DISABLED]
     * scripts (e.g. the WebRTC guard, which breaks video calls) seed disabled.
     * Runs once per guard version (DataStore flag). The user can enable,
     * disable, or delete any script afterwards from the Userscript Extensions
     * screen.
     *
     * @return how many bundled scripts were newly installed.
     */
    suspend fun seedBundledScripts(): Int = withContext(Dispatchers.IO) {
        val seededKey = stringPreferencesKey("userscripts_bundled_seeded_v2")
        if (context.profileDataStore.data.first()[seededKey] == "1") return@withContext 0
        var count = 0
        try {
            val existingNames = listScripts().map { it.meta.name }.toSet()
            val names = context.assets.list("userscripts").orEmpty()
                .filter { it.endsWith(".user.js") }
                .sorted()
            for (name in names) {
                try {
                    val source = context.assets.open("userscripts/$name")
                        .bufferedReader().use { it.readText() }
                    val meta = UserscriptEngine.parse(source)?.first
                        ?: continue
                    // Already installed (e.g. from an earlier seed version) — skip.
                    if (existingNames.contains(meta.name)) continue
                    val seedEnabled = name !in OPT_IN_SEED_DISABLED
                    if (install(source, enabled = seedEnabled).isSuccess) count++
                } catch (_: Exception) {
                    // One bad asset must not block the rest.
                }
            }
        } catch (_: Exception) {
            // Missing assets folder — nothing to seed.
        }
        context.profileDataStore.edit { it[seededKey] = "1" }
        count
    }

    /**
     * Opt-in switch for the bundled WebRTC Leak Guard script. Finds it by
     * [WEBRTC_GUARD_NAME]; if the user deleted it, toggling ON reinstalls it
     * from the bundled asset. The script seeds disabled and is only ever
     * enabled through this explicit user action.
     *
     * @return whether the guard is actually enabled in stored state after
     * the call (re-read from the index — never assumed from the request).
     */
    suspend fun setWebrtcGuardEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val existing = listScripts().find { it.meta.name == WEBRTC_GUARD_NAME }
        if (existing != null) {
            setEnabled(existing.id, enabled)
            return@withContext listScripts().find { it.id == existing.id }?.enabled == true
        }
        if (!enabled) return@withContext false
        return@withContext try {
            val source = context.assets.open("userscripts/$WEBRTC_GUARD_ASSET")
                .bufferedReader().use { it.readText() }
            if (!install(source, enabled = true).isSuccess) return@withContext false
            listScripts().find { it.meta.name == WEBRTC_GUARD_NAME }?.enabled == true
        } catch (_: Exception) {
            false
        }
    }
}
