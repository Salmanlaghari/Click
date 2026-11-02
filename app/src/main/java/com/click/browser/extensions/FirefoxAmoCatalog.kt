package com.click.browser.extensions

import com.click.browser.R

/**
 * A deliberately small catalog: each item is a real AMO listing whose current
 * Firefox for Android package was checked against the pinned GeckoView flow.
 * Import still rechecks the actual package's signed/recommended/blocklist
 * metadata and all permissions; this static catalog is never an install bypass.
 */
internal data class FirefoxAmoCatalogEntry(
    val slug: String,
    val name: String,
    val summary: String,
    val version: String,
    val minimumAndroidVersion: String,
    val listingUrl: String,
    val iconUrl: String,
    val iconResource: Int,
    val requiredPermissions: List<String>,
    val requiredOrigins: List<String>
)

internal object FirefoxAmoCatalog {
    // Verified against AMO's public v5 add-on detail API and listing page.
    // Keep only entries whose current package fits Click's existing policy.
    val entries: List<FirefoxAmoCatalogEntry> = listOf(
        FirefoxAmoCatalogEntry(
            slug = "tomato-clock",
            name = "Tomato Clock",
            summary = "A simple Pomodoro timer with adjustable work and break intervals, pause/resume, and completion notifications.",
            version = "7.8.0",
            minimumAndroidVersion = "109.0",
            listingUrl = "https://addons.mozilla.org/en-US/firefox/addon/tomato-clock/",
            iconUrl = "https://addons.mozilla.org/user-media/addon_icons/627/627490-64.png?modified=mcrushed",
            iconResource = R.drawable.amo_tomato_clock,
            requiredPermissions = listOf("notifications", "storage", "alarms"),
            requiredOrigins = emptyList()
        )
    )
}
