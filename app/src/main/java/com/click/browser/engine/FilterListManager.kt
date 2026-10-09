package com.click.browser.engine

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Bundled + updatable ad/tracker filter lists.
 *
 * - Ships a curated EasyList-subset (assets/adblock/filters.json) INSIDE the
 *   APK with a version number — works fully offline from first launch.
 * - Checks for an updated list at most once a week (GitHub raw file in this
 *   repo); a newer version is cached to internal storage and used instead.
 * - Matching is suffix-based on a HashSet: O(segments) per request.
 */
object FilterListManager {

    private const val PREFS = "click_filter_lists"
    private const val KEY_VERSION = "list_version"
    private const val KEY_UPDATED_AT = "list_updated_at"
    private const val UPDATE_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    private const val ASSET_PATH = "adblock/filters.json"
    private const val CACHED_FILE = "adblock-filters.json"

    /**
     * Raw GitHub file in THIS repo — the single source of truth maintainers
     * update; the app pulls it weekly. No third-party server involved.
     */
    private const val REMOTE_URL =
        "https://raw.githubusercontent.com/Salmanlaghari/Click/main/app/src/main/assets/adblock/filters.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var domains: Set<String> = emptySet()

    @Volatile
    private var listVersion: Int = 0

    @Volatile
    private var initialized = false

    @Volatile
    private var prefs: SharedPreferences? = null
    @Volatile
    private var filesDir: File? = null

    /** Call once from Application/Activity onCreate. Safe to call repeatedly. */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        filesDir = app.filesDir
        scope.launch {
            loadBestAvailable(app)
            maybeUpdateFromRemote()
        }
    }

    /**
     * Releases the background scope and HTTP client. The manager lives for the
     * app process lifetime, so this is only needed in tests — but the leak is
     * real without it, so the API exists.
     */
    fun shutdown() {
        try { scope.cancel() } catch (e: Exception) { }
        try { http.dispatcher.executorService.shutdown() } catch (e: Exception) { }
        try { http.connectionPool.evictAll() } catch (e: Exception) { }
        prefs = null
        filesDir = null
        initialized = false
    }

    /** Synchronous snapshot for the request path — never blocks on network. */
    fun currentDomains(): Set<String> = domains

    fun currentVersion(): Int = listVersion

    /** Suffix match: any parent domain of [host] in the set blocks it. */
    fun isBlockedHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val set = domains
        if (set.isEmpty()) return false
        val h = host.lowercase()
        var idx = 0
        while (true) {
            if (set.contains(h.substring(idx))) return true
            val dot = h.indexOf('.', idx)
            if (dot < 0) return false
            idx = dot + 1
        }
    }

    private fun loadBestAvailable(app: Context) {
        // 1) Cached remote copy (newer) wins if present and valid.
        val cached = filesDir?.let { File(it, CACHED_FILE) }
        val cachedDomains = cached?.takeIf { it.exists() }?.let { parseFile(it) }
        // 2) Bundled asset always exists as fallback.
        val bundled = try {
            app.assets.open(ASSET_PATH).use { ins ->
                parseJson(ins.readBytes().toString(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            null
        }
        val cachedVersion = prefs?.getInt(KEY_VERSION, 0) ?: 0
        val bundledVersion = bundled?.first ?: 0
        if (cachedDomains != null && cachedVersion >= bundledVersion && cachedDomains.isNotEmpty()) {
            domains = cachedDomains
            listVersion = cachedVersion
        } else if (bundled != null) {
            domains = bundled.second
            listVersion = bundledVersion
            prefs?.edit()?.putInt(KEY_VERSION, bundledVersion)?.apply()
        }
    }

    private fun maybeUpdateFromRemote() {
        val last = prefs?.getLong(KEY_UPDATED_AT, 0L) ?: 0L
        if (System.currentTimeMillis() - last < UPDATE_INTERVAL_MS) return
        try {
            val req = Request.Builder().url(REMOTE_URL).get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val body = resp.body?.string() ?: return
                val (version, set) = parseJson(body) ?: return
                if (version > listVersion && set.isNotEmpty()) {
                    filesDir?.let { File(it, CACHED_FILE).writeText(body) }
                    prefs?.edit()
                        ?.putInt(KEY_VERSION, version)
                        ?.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                        ?.apply()
                    domains = set
                    listVersion = version
                } else {
                    // Still fresh — don't hammer the server next launch.
                    prefs?.edit()?.putLong(KEY_UPDATED_AT, System.currentTimeMillis())?.apply()
                }
            }
        } catch (e: Exception) {
            // Offline or unreachable: keep using the local list.
            android.util.Log.w("FilterListManager", "Remote filter-list update failed", e)
        }
    }

    private fun parseFile(f: File): Set<String>? = try {
        parseJson(f.readText())?.second
    } catch (e: Exception) {
        null
    }

    /** Returns (version, domains) or null when invalid. */
    private fun parseJson(json: String): Pair<Int, Set<String>>? = try {
        val obj = JSONObject(json)
        val version = obj.optInt("version", 0)
        val arr = obj.optJSONArray("domains")
        if (arr == null) {
            null
        } else {
            val set = HashSet<String>(arr.length())
            for (i in 0 until arr.length()) {
                val d = arr.optString(i).trim().lowercase()
                // Sanity: real domains only — never match empty/TLD-only junk.
                if (d.isNotEmpty() && d.contains('.') && !d.contains(' ') && !d.contains('/')) {
                    set.add(d)
                }
            }
            if (version <= 0 || set.isEmpty()) null else version to set
        }
    } catch (e: Exception) {
        null
    }
}
