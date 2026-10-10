package com.click.browser.engine

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** One name=value cookie. */
data class CookieEntry(val name: String, val value: String)

/** All cookies belonging to one site host. */
data class SiteCookies(val host: String, val cookies: List<CookieEntry>)

/**
 * Cookie Manager backend: reads/writes the WebView cookie jar through
 * android.webkit.CookieManager.
 *
 * Honest notes (kept in UI copy too):
 * - WebView exposes NO per-cookie delete API. Deletion works by expiring the
 *   cookie (setCookie with an Expires date in the past) — the standard trick.
 * - Cookies live in the per-engine WebView data directory
 *   (v9_simple / v9_developer / v9_hack), so this manager shows the cookies of
 *   the CURRENT engine mode only.
 *
 * Threading: getCookie() blocks and runs on Dispatchers.IO. setCookie() /
 * removeAllCookies() are async and must be invoked from a thread with a
 * Looper (the main thread); their callbacks trigger flush() so deletions hit
 * disk immediately instead of waiting for the next automatic sync.
 */
object CookieStore {

    private const val EXPIRED_SUFFIX = "; Expires=Wed, 31 Dec 2000 23:59:59 GMT; Path=/"

    /** Parse a "a=1; b=2" cookie header. Values may contain '=', so split on the first one. */
    fun parseCookieHeader(header: String?): List<CookieEntry> {
        if (header.isNullOrBlank()) return emptyList()
        return header.split(";").mapNotNull { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val eq = trimmed.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val name = trimmed.substring(0, eq).trim()
            // Skip RFC 2965 attribute cookies ($Version, $Path, ...).
            if (name.isEmpty() || name.startsWith("$")) return@mapNotNull null
            CookieEntry(name, trimmed.substring(eq + 1))
        }
    }

    /**
     * Cookies for one host. Queries the given sample URLs (actual visited URLs,
     * so path-scoped cookies are included) plus the https/http roots, merging
     * by cookie name. Blocking getCookie() calls run on Dispatchers.IO.
     */
    suspend fun cookiesForHost(
        host: String,
        sampleUrls: List<String> = emptyList()
    ): List<CookieEntry> = withContext(Dispatchers.IO) {
        val cm = CookieManager.getInstance()
        val urls = (sampleUrls + listOf("https://$host", "http://$host")).distinct()
        urls.flatMap { url -> parseCookieHeader(cm.getCookie(url)) }
            // Same name can exist on different paths with different values —
            // dedupe by name+value so both remain visible.
            .distinctBy { it.name to it.value }
    }

    /** Expire one cookie. Call from the main thread. Result via [onDone]. */
    fun deleteCookie(host: String, name: String, onDone: (Boolean) -> Unit = {}) {
        val cm = CookieManager.getInstance()
        expire(cm, host, name) { ok ->
            cm.flush()
            onDone(ok)
        }
    }

    /**
     * Expire every cookie for a host. Returns the names that failed to expire
     * (empty = all good). Names are collected on IO; expirations run together
     * on Main with a single dispatcher switch and parallel callbacks.
     */
    suspend fun clearSite(host: String): List<String> {
        val names = cookiesForHost(host).map { it.name }
        if (names.isEmpty()) return emptyList()
        val cm = CookieManager.getInstance()
        return try {
            withContext(Dispatchers.Main) {
                coroutineScope {
                    names.map { name ->
                        async {
                            val ok = suspendCoroutine<Boolean> { cont ->
                                expire(cm, host, name) { cont.resume(it) }
                            }
                            if (ok) null else name
                        }
                    }.awaitAll().filterNotNull()
                }
            }
        } finally {
            // Persist even if an expiration threw midway.
            cm.flush()
        }
    }

    /** Remove ALL cookies (all sites, current engine mode). Call from the main thread. */
    fun clearAll(onDone: () -> Unit = {}) {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies {
            cm.flush()
            onDone()
        }
    }

    /**
     * Result of a full per-site data wipe.
     */
    data class SiteDataClearResult(
        val cookiesCleared: Int,
        val cookiesFailed: List<String>,
        val storageOriginsCleared: Int
    )

    /**
     * Wipes ALL data for one host: cookies (via expiry) + DOM storage /
     * localStorage origins (via WebStorage.deleteOrigin). Runs the cookie
     * expiry on Main (WebView requirement) and origin deletion wherever.
     * Returns counts for honest UI copy.
     */
    suspend fun clearSiteData(host: String): SiteDataClearResult {
        val failed = clearSite(host)
        val cookieCount = cookiesForHost(host).size + failed.size
        val originsCleared = clearStorageOrigins(host)
        return SiteDataClearResult(
            cookiesCleared = (cookieCount - failed.size).coerceAtLeast(0),
            cookiesFailed = failed,
            storageOriginsCleared = originsCleared
        )
    }

    /**
     * Deletes WebStorage (DOM storage / localStorage / IndexedDB) origins
     * belonging to [host]. Matches the origin host suffix-aware, same as
     * the cookie logic. Returns the number of origins deleted.
     */
    suspend fun clearStorageOrigins(host: String): Int =
        suspendCoroutine { cont ->
            try {
                val ws = WebStorage.getInstance()
                ws.getOrigins { origins ->
                    var cleared = 0
                    try {
                        val map = origins ?: emptyMap<String, WebStorage.Origin>()
                        for ((origin, _) in map) {
                            val originHost = try {
                                Uri.parse(origin).host?.lowercase()
                            } catch (_: Exception) {
                                null
                            }
                            if (originHost != null &&
                                (originHost == host || originHost.endsWith(".$host"))
                            ) {
                                ws.deleteOrigin(origin)
                                cleared++
                            }
                        }
                    } catch (_: Exception) {
                    }
                    cont.resume(cleared)
                }
            } catch (_: Exception) {
                cont.resume(0)
            }
        }

    /**
     * Expire a cookie on both schemes; [done] receives true only if both
     * writes reported success (setCookie's ValueCallback<Boolean>).
     */
    private fun expire(cm: CookieManager, host: String, name: String, done: (Boolean) -> Unit) {
        val expired = "$name$EXPIRED_SUFFIX"
        cm.setCookie("https://$host", expired) { okHttps ->
            cm.setCookie("http://$host", expired) { okHttp ->
                done(okHttps && okHttp)
            }
        }
    }

    /** Distinct, normalized hosts from a list of URLs. */
    fun hostsFromUrls(urls: List<String>): List<String> {
        return urls.mapNotNull { url ->
            try {
                val host = Uri.parse(url).host?.lowercase()?.trim()
                if (host.isNullOrBlank()) null else host
            } catch (_: Exception) {
                null
            }
        }.distinct().sorted()
    }

    /**
     * Map each host to up to [maxPerHost] sample URLs (actual visited URLs —
     * used so path-scoped cookies are included when querying).
     */
    fun sampleUrlsByHost(urls: List<String>, maxPerHost: Int = 3): Map<String, List<String>> {
        val map = linkedMapOf<String, MutableList<String>>()
        for (url in urls) {
            val host = try {
                Uri.parse(url).host?.lowercase()?.trim()
            } catch (_: Exception) {
                null
            }
            if (host.isNullOrBlank()) continue
            val list = map.getOrPut(host) { mutableListOf() }
            if (list.size < maxPerHost && url !in list) list.add(url)
        }
        return map
    }
}
