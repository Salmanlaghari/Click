package com.click.browser.engine

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Default-browser detection and the system "set as default" request flow.
 *
 * Detection uses [RoleManager] on API 29+ (the supported way to check the
 * BROWSER role); on older devices it falls back to resolving the http VIEW
 * intent and comparing the winning package. The request flow likewise uses
 * [RoleManager.createRequestRoleIntent] where available, and falls back to
 * the system's Default-Apps settings page where it is not.
 *
 * Note: for Android to offer Click as a default browser at all, MainActivity
 * must declare the BROWSABLE http/https VIEW intent-filter (see
 * AndroidManifest.xml) — without it the role request cannot succeed.
 */
object DefaultBrowserHelper {

    private const val TAG = "DefaultBrowser"

    /**
     * True when Click currently holds the system BROWSER role (i.e. it is
     * the user's default browser). Never throws — returns false on any
     * failure so callers can treat "unknown" as "not default".
     */
    fun isDefaultBrowser(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                    ?: return false
                roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
            } else {
                // Pre-Q fallback: resolve a generic http VIEW intent and see
                // which package the system would send it to.
                val probe = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"))
                val resolved = context.packageManager.resolveActivity(probe, 0)
                resolved?.activityInfo?.packageName == context.packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * True when the in-app system role-request UI can be used on this
     * device (API 29+ and the BROWSER role is available).
     */
    fun canRequestRole(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
            val roleManager = context.getSystemService(RoleManager::class.java)
                ?: return false
            roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Builds the system "set Click as default browser" intent for use with
     * an ActivityResultLauncher. Returns null when the role request cannot
     * be built on this device — callers should then use
     * [openDefaultAppsSettings] instead.
     */
    fun requestRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val roleManager = context.getSystemService(RoleManager::class.java)
                ?: return null
            roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fallback for devices where the role request is unavailable: opens the
     * system's Default-Apps settings page so the user can pick Click
     * manually. Every branch is guarded — this never throws.
     */
    fun openDefaultAppsSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // Last resort: the generic Settings screen. Still better than a
            // dead button.
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                Log.w(TAG, "Could not open any system settings screen")
            }
        }
    }
}
