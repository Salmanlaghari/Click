package com.click.browser.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.click.browser.BuildConfig
import java.security.MessageDigest

/**
 * Runtime tamper detection — the user-visible part of the "decompile guard".
 *
 * On app start, RELEASE builds compare the APK's actual signing certificate
 * (SHA-256) against the known-good fingerprints below. On mismatch the app
 * shows a blocking warning and disables the AI chat entry point.
 *
 * Honest notes:
 * - This does NOT stop a determined reverser: they can patch this check out
 *   or re-sign and the app can't tell. It raises the bar and, most usefully,
 *   warns honest users who installed a repackaged copy from an untrusted
 *   source.
 * - Debug builds always pass (BuildConfig.DEBUG) — developers are not "tamperers".
 * - IMPORTANT — Play App Signing: Google Play re-signs the AAB on upload, so
 *   the runtime certificate of a Play-installed copy is PLAY'S key, not the
 *   upload keystore's. Add your Play App Signing SHA-256 below
 *   (Play Console → Setup → App signing) or Play installs will false-positive
 *   and AI chat will stay disabled for real users.
 */
object TamperCheck {

    /**
     * Accepted signing-certificate SHA-256 fingerprints (hex, colons optional).
     */
    private val ACCEPTED_SHA256 = setOf(
        // Prince's release upload keystore (click-browser.p12, alias "click").
        "c8c42cd7f04e0b38719f2a0276c124cdce41349ed70a53c8f9307b736c4dda2c"
        // TODO(Prince): add your Play App Signing SHA-256 here once the app
        // is enrolled, otherwise Play-installed copies will trip the warning.
    )

    /**
     * True when the running APK's signature matches a known-good certificate
     * (or this is a debug build, which is never treated as tampered).
     */
    fun isReleaseSignatureValid(context: Context): Boolean {
        if (BuildConfig.DEBUG) return true
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            val signatures = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)?.signatures
            } ?: return false
            val digest = MessageDigest.getInstance("SHA-256")
            signatures.any { sig ->
                val hex = digest.digest(sig.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                    .lowercase()
                ACCEPTED_SHA256.any { accepted ->
                    accepted.lowercase().replace(":", "") == hex
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
