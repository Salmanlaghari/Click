package com.click.browser.engine

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Removes persistent state for retired page-modification and identity features.
 * A versioned marker avoids repeating the synchronous migration on later launches.
 */
object LegacyPolicyFeatureCleanup {

    // Bump this filename when new retired state is added to the cleanup.
    private const val COMPLETION_MARKER_FILE = "legacy_policy_cleanup_v1.complete"

    internal fun isComplete(context: Context): Boolean =
        completionMarker(context).isFile

    private fun completionMarker(context: Context): File =
        File(context.noBackupFilesDir, COMPLETION_MARKER_FILE)

    private val retiredStringKeys = listOf(
        "userscripts_index",
        "userscripts_bundled_seeded_v2",
        "custom_headers_json",
        "location_spoof_lat",
        "location_spoof_lng",
        "location_spoof_label",
        "location_mode",
        "location_mode_hosts",
        "flag_custom_ua",
        "fingerprint_mode",
    )
    private val retiredBooleanKeys = listOf(
        "header_spoof_enabled",
        "fingerprint_protection",
        "block_consent_banners",
    )
    private val retiredPowerShortcutHosts = setOf(
        "tryhackme.com",
        "hackthebox.com",
        "owasp.org",
        "portswigger.net",
        "overthewire.org",
        "picoctf.org",
        "cybrary.it",
    )

    suspend fun run(context: Context) = withContext(Dispatchers.IO) {
        runCleanupOnce(completionMarker(context)) {
            cleanupLegacyState(context)
        }
    }

    private suspend fun cleanupLegacyState(context: Context): Boolean {
        val filesPurged = purgeScriptFiles(context)
        val stores = listOf(context.dataStore, context.advancedDataStore)
        stores.forEach { store ->
            store.edit { prefs ->
                retiredStringKeys.forEach { prefs.remove(stringPreferencesKey(it)) }
                retiredBooleanKeys.forEach { prefs.remove(androidx.datastore.preferences.core.booleanPreferencesKey(it)) }

                // Remove legacy per-site location permissions without changing
                // unrelated site preferences such as JavaScript or ad blocking.
                val siteSettingsKey = stringPreferencesKey("site_settings")
                val rawSiteSettings = prefs[siteSettingsKey]
                if (!rawSiteSettings.isNullOrBlank()) {
                    try {
                        val siteSettings = JSONObject(rawSiteSettings)
                        val hosts = siteSettings.keys().asSequence().toList()
                        hosts.forEach { host ->
                            val settings = siteSettings.optJSONObject(host) ?: return@forEach
                            settings.remove("location")
                            if (settings.length() == 0) siteSettings.remove(host)
                        }
                        prefs[siteSettingsKey] = siteSettings.toString()
                    } catch (_: Exception) {
                        // Keep other app state intact if legacy JSON is malformed.
                    }
                }

                // Remove only shortcuts shipped by the old cybersecurity-
                // themed Power defaults; retain unrelated user-created sites.
                val powerSitesKey = stringPreferencesKey("quick_sites_hack")
                val rawPowerSites = prefs[powerSitesKey]
                if (!rawPowerSites.isNullOrBlank()) {
                    try {
                        val oldSites = JSONArray(rawPowerSites)
                        val retainedSites = JSONArray()
                        var removedRetiredShortcut = false
                        for (index in 0 until oldSites.length()) {
                            val site = oldSites.optJSONObject(index) ?: continue
                            val host = runCatching {
                                Uri.parse(site.optString("url")).host?.lowercase().orEmpty()
                            }.getOrDefault("")
                            if (retiredPowerShortcutHosts.any { host == it || host.endsWith(".$it") }) {
                                removedRetiredShortcut = true
                            } else {
                                retainedSites.put(site)
                            }
                        }
                        if (removedRetiredShortcut) {
                            if (retainedSites.length() == 0) prefs.remove(powerSitesKey)
                            else prefs[powerSitesKey] = retainedSites.toString()
                        }
                    } catch (_: Exception) {
                        // Preserve custom settings if legacy shortcut JSON is malformed.
                    }
                }

                val powerSearchKey = stringPreferencesKey("search_engine_hack")
                val savedPowerSearchEngine = prefs[powerSearchKey]
                if (savedPowerSearchEngine == "Ahmia" || savedPowerSearchEngine == "Ahmia Search") {
                    prefs[powerSearchKey] = "Brave Search"
                }
            }
        }
        return filesPurged
    }

    private fun purgeScriptFiles(context: Context): Boolean {
        var cleanupSucceeded = true
        listOf("userscripts", "userscripts_advanced").forEach { folder ->
            val directory = File(context.filesDir, folder)
            if (directory.exists() && !runCatching { directory.deleteRecursively() }.getOrDefault(false)) {
                cleanupSucceeded = false
            }
        }

        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        if (sharedPrefsDir.exists()) {
            val files = sharedPrefsDir.listFiles() ?: return false
            files.filter { it.name.startsWith("userscript_") && it.name.endsWith(".xml") }
                .forEach { file ->
                    val preferencesCleared = runCatching {
                        context.getSharedPreferences(file.name.removeSuffix(".xml"), Context.MODE_PRIVATE)
                            .edit()
                            .clear()
                            .commit()
                    }.getOrDefault(false)
                    val fileDeleted = runCatching { !file.exists() || file.delete() }.getOrDefault(false)
                    if (!preferencesCleared && !fileDeleted) cleanupSucceeded = false
                }
        }
        return cleanupSucceeded
    }
}
