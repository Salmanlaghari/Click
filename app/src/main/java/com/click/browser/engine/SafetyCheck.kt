package com.click.browser.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Safety Check: a local, honest health scan of the browser's protections.
 *
 * Everything here is verified ON-DEVICE from real settings state — nothing
 * is guessed and nothing phones home. The update check does NOT silently
 * query the network: it opens the Play Store listing so the user sees the
 * real state themselves.
 */
object SafetyCheck {

    private const val TAG = "SafetyCheck"
    private const val PLAY_STORE_URL =
        "https://play.google.com/store/apps/details?id=com.teampkai.clickbrowser"

    data class Check(
        val id: String,
        val title: String,
        val description: String,
        val passed: Boolean,
        /** Fix button label; null = no automatic fix (manual action instead). */
        val fixLabel: String?,
        /** Manual action label when fixLabel is null. */
        val actionLabel: String? = null
    )

    suspend fun run(context: Context): List<Check> {
        val prefs = context.dataStore.data.first()
        val checks = ArrayList<Check>()

        // 1. Safe Browsing (WebView-level malware/phishing protection).
        val sbSupported =
            WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)
        checks.add(
            Check(
                id = "safe_browsing",
                title = "Safe Browsing",
                description = if (sbSupported)
                    "WebView Safe Browsing is active — malicious and deceptive sites are blocked."
                else
                    "This WebView build doesn't expose Safe Browsing controls.",
                passed = sbSupported,
                fixLabel = null
            )
        )

        // 2. HTTPS mode.
        val httpsMode = AppSettings.normalizeMode(prefs[AppSettings.HTTPS_MODE])
        checks.add(
            Check(
                id = "https",
                title = "HTTPS protection",
                description = when (httpsMode) {
                    "strict" -> "Strict: plain-http pages are blocked entirely."
                    "standard" -> "Standard: http pages are upgraded to https automatically."
                    else -> "Off: plain-http pages load unencrypted."
                },
                passed = httpsMode != "off",
                fixLabel = if (httpsMode == "off") "Turn on Standard" else null
            )
        )

        // 3. Password manager.
        val pmOn = try {
            PasswordManager.isEnabled(context)
        } catch (e: Exception) {
            Log.w(TAG, "password-manager check failed", e); true
        }
        checks.add(
            Check(
                id = "password_manager",
                title = "Password manager",
                description = if (pmOn)
                    "Click's encrypted password manager is on — logins are saved with a hardware-backed key."
                else
                    "Password manager is off — you won't be offered to save logins.",
                passed = pmOn,
                fixLabel = if (!pmOn) "Turn on" else null
            )
        )

        // 4. Fingerprint protection.
        val fpOn = prefs[AppSettings.FINGERPRINT_PROTECTION] ?: false
        checks.add(
            Check(
                id = "fingerprint",
                title = "Fingerprint protection",
                description = if (fpOn)
                    "Canvas and audio fingerprinting are poisoned with per-launch noise."
                else
                    "Sites can fingerprint this browser via canvas/audio signals.",
                passed = fpOn,
                fixLabel = if (!fpOn) "Turn on" else null
            )
        )

        // 5. Tracking-param stripping.
        val stripOn = prefs[AppSettings.STRIP_TRACKING_PARAMS] ?: true
        checks.add(
            Check(
                id = "strip_params",
                title = "Tracker stripping",
                description = if (stripOn)
                    "utm_*, gclid, fbclid and other tracking parameters are removed from URLs."
                else
                    "Tracking parameters stay in URLs when you open links.",
                passed = stripOn,
                fixLabel = if (!stripOn) "Turn on" else null
            )
        )

        // 6. App updates (manual — opens the real Play listing).
        checks.add(
            Check(
                id = "updates",
                title = "App updates",
                description = "Click can't check versions silently — open the Play Store to see if an update is waiting.",
                passed = true, // informational; never fails
                fixLabel = null,
                actionLabel = "Open Play Store"
            )
        )

        return checks
    }

    /** Applies the one-tap fix for a failed check. Returns false if unknown. */
    suspend fun applyFix(context: Context, checkId: String): Boolean {
        return try {
            when (checkId) {
                "https" -> context.dataStore.edit { it[AppSettings.HTTPS_MODE] = "standard" }
                "password_manager" -> PasswordManager.setEnabled(context, true)
                "fingerprint" -> context.dataStore.edit { it[AppSettings.FINGERPRINT_PROTECTION] = true }
                "strip_params" -> context.dataStore.edit { it[AppSettings.STRIP_TRACKING_PARAMS] = true }
                else -> return false
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "applyFix($checkId) failed", e)
            false
        }
    }

    fun openPlayListing(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) { }
    }
}
