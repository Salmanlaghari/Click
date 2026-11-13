package com.click.browser.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Install flow for APK files the user downloaded with Click itself
 * (Downloads screen, Open-With sheet, completion notification landing).
 *
 * Click never installs silently: the system package installer shows its
 * own confirmation, and on Android 8+ the user must first have allowed
 * "install unknown apps" for Click — when that is missing, Click sends
 * the user to exactly that settings page instead of failing quietly.
 *
 * Play policy note: this requires the REQUEST_INSTALL_PACKAGES manifest
 * permission, which Google Play treats as restricted — a declaration may
 * be required before a release containing it can be submitted.
 */
object ApkInstaller {

    const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    fun isApk(mimeType: String?, fileName: String): Boolean =
        mimeType?.lowercase() == APK_MIME_TYPE ||
            fileName.lowercase().endsWith(".apk")

    /** True when Android will let Click hand an APK to the installer. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Opens Settings → "Install unknown apps" for Click's own package. */
    fun openUnknownAppSourcesSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Fires the system package installer for [uri] (content:// only). */
    fun launchInstaller(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
