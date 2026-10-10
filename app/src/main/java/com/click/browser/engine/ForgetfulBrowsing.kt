package com.click.browser.engine

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Forgetful Browsing (Brave-inspired): when the last tab showing a site is
 * closed, that site's cookies and web storage are wiped automatically —
 * the site "forgets" you the moment you leave it.
 *
 * - Opt-in global toggle ([shouldForget] gate); per-site exceptions keep
 *   chosen sites' data ("Remember me" logins keep working).
 * - Only ever touches the CURRENT engine mode's storage (cookies and
 *   WebStorage are per-mode via the v9_* WebView data dirs) — mode
 *   isolation is preserved by construction.
 * - Incognito tab closes never trigger forgetting (separate concern).
 */
object ForgetfulBrowsing {

    /** Normalized host of a URL, or null when it has none. */
    fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val h = Uri.parse(url).host?.lowercase()?.trim()
            if (h.isNullOrBlank()) null else h
        } catch (_: Exception) {
            null
        }
    }

    /**
     * True when closing [closedHost] should wipe its data: the global toggle
     * is on, the host isn't excepted, and no [remainingHosts] still show it.
     */
    fun shouldForget(
        globalEnabled: Boolean,
        exceptions: Set<String>,
        closedHost: String?,
        remainingHosts: Collection<String?>
    ): Boolean {
        if (!globalEnabled) return false
        if (closedHost.isNullOrBlank()) return false
        if (closedHost in exceptions) return false
        return remainingHosts.none { it == closedHost }
    }

    /**
     * Wipe a site's cookies + DOM storage for the current engine mode.
     * Cookie expiration must run on the main thread (WebView requirement);
     * WebStorage.deleteOrigin is also safest there.
     */
    suspend fun forgetHost(host: String) {
        withContext(Dispatchers.Main) {
            try {
                CookieStore.clearSite(host)
            } catch (_: Exception) {
            }
            try {
                val ws = WebStorage.getInstance()
                ws.deleteOrigin("https://$host")
                ws.deleteOrigin("http://$host")
            } catch (_: Exception) {
            }
            try {
                CookieManager.getInstance().flush()
            } catch (_: Exception) {
            }
        }
    }

    /** Normalize a user-typed exception host ("https://Example.com/" -> "example.com"). */
    fun normalizeException(raw: String): String? = hostOf(
        if (raw.contains("://")) raw else "https://$raw"
    )
}
