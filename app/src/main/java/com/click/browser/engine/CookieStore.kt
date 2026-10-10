package com.click.browser.engine

import android.net.Uri
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
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

    /** Cookies for one host (https first, http fallback). Safe to call from any thread. */
    suspend fun cookiesForHost(host: String): List<CookieEntry> = withContext(Dispatchers.IO) {
        val cm = CookieManager.getInstance()
        val header = cm.getCookie("https://$host") ?: cm.getCookie("http://$host")
        parseCookieHeader(header)
    }

    /** Expire one cookie. Call from the main thread. */
    fun deleteCookie(host: String, name: String, onDone: () -> Unit = {}) {
        val cm = CookieManager.getInstance()
        expire(cm, host, name) {
            cm.flush()
            onDone()
        }
    }

    /** Expire every cookie for a host. Safe to call from any thread. */
    suspend fun clearSite(host: String) {
        val names = cookiesForHost(host).map { it.name }
        if (names.isEmpty()) return
        val cm = CookieManager.getInstance()
        for (name in names) {
            withContext(Dispatchers.Main) {
                suspendCoroutine { cont ->
                    expire(cm, host, name) { cont.resume(Unit) }
                }
            }
        }
        cm.flush()
    }

    /** Remove ALL cookies (all sites, current engine mode). Call from the main thread. */
    fun clearAll(onDone: () -> Unit = {}) {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies {
            cm.flush()
            onDone()
        }
    }

    /** Expire a cookie on both schemes; invokes [done] after both writes complete. */
    private fun expire(cm: CookieManager, host: String, name: String, done: () -> Unit) {
        val expired = "$name$EXPIRED_SUFFIX"
        cm.setCookie("https://$host", expired) {
            cm.setCookie("http://$host", expired) { done() }
        }
    }

    /** Distinct, normalized hosts from a list of URLs. */
    fun hostsFromUrls(urls: List<String>): List<String> {
        return urls.mapNotNull { url ->
            try {
                val host = Uri.parse(url).host?.lowercase()?.trim()
                if (host.isNullOrBlank()) null
                else host.removePrefix("www.")
            } catch (_: Exception) {
                null
            }
        }.distinct().sorted()
    }
}
