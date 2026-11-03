package com.click.browser.extensions

import com.click.browser.R

/**
 * Small explicit allowlist of add-ons currently marked Recommended for Firefox Android on AMO.
 * Catalog membership is only a first gate: live AMO metadata, GeckoView's Mozilla signature and
 * blocklist checks, and Click's permission/origin policy are still required at install time.
 */
internal data class FirefoxAmoCatalogEntry(
    val slug: String,
    val extensionId: String = "",
    val name: String,
    val category: String = "Productivity",
    val summary: String,
    val version: String,
    val minimumAndroidVersion: String,
    val listingUrl: String,
    val iconUrl: String,
    val iconResource: Int,
    val isRecommended: Boolean = false,
    val requiredPermissions: List<String>,
    val requiredOrigins: List<String> = emptyList()
)

internal data class VerifiedAmoPackage(
    val version: String,
    val minimumAndroidVersion: String,
    val fileSize: Long,
    val downloadUrl: String
)

internal object FirefoxAmoCatalog {
    val categories: List<String> = listOf("All", "Productivity", "Notes & Study")

    val entries: List<FirefoxAmoCatalogEntry> by lazy {
        listOf(
            amoEntry(
                slug = "tomato-clock",
                extensionId = "jid1-Kt2kYYgi32zPuw@jetpack",
                name = "Tomato Clock",
                category = "Productivity",
                summary = "A simple Pomodoro timer with adjustable work and break intervals, pause/resume, and completion notifications.",
                version = "7.8.0",
                minAndroid = "109.0",
                iconUrl = "https://addons.mozilla.org/user-media/addon_icons/627/627490-64.png?modified=mcrushed",
                iconRes = R.drawable.amo_tomato_clock,
                perms = listOf("notifications", "storage", "alarms")
            ),
            amoEntry(
                slug = "websites-notes",
                extensionId = "websitesnotes@saveriomorelli.com",
                name = "Notefox: Websites Notes",
                category = "Notes & Study",
                summary = "Take structured per-page and domain study notes locally in your isolated extension profile without broad host access.",
                version = "5.0.0.5",
                minAndroid = "60.0",
                iconUrl = "https://addons.mozilla.org/user-media/addon_icons/2714/2714481-64.png?modified=e71325d8",
                iconRes = R.drawable.amo_websites_notes,
                perms = listOf("storage", "tabs", "unlimitedStorage", "activeTab", "menus")
            )
        )
    }

    private val approvedSlugs: Set<String> by lazy {
        entries.mapTo(HashSet(entries.size * 2)) { it.slug.lowercase() }
    }

    private val approvedExtensionIds: Set<String> by lazy {
        entries.mapNotNullTo(HashSet(entries.size * 2)) { entry ->
            entry.extensionId.trim().takeIf { it.isNotEmpty() }
        }
    }

    fun isApprovedAmoListing(url: String?): Boolean {
        if (!ExtensionInstallPolicy.isOfficialAmoListing(url)) return false
        val path = runCatching { java.net.URI(url!!).path.orEmpty() }.getOrDefault("")
        val slug = path.substringAfter("/firefox/addon/", "").trim('/').lowercase()
        return slug.isNotEmpty() && slug in approvedSlugs
    }

    fun isApprovedExtensionId(id: String?): Boolean {
        val trimmed = id?.trim().orEmpty()
        return trimmed.isNotEmpty() && trimmed in approvedExtensionIds
    }

    fun recommendedEntryForListing(url: String?): FirefoxAmoCatalogEntry? {
        if (!ExtensionInstallPolicy.isOfficialAmoListing(url)) return null
        val path = runCatching { java.net.URI(url!!).path.orEmpty() }.getOrDefault("")
        val slug = path.substringAfter("/firefox/addon/", "").trim('/').substringBefore('/').lowercase()
        return entries.firstOrNull { it.slug.equals(slug, ignoreCase = true) }
    }

    fun latestPackageUrl(entry: FirefoxAmoCatalogEntry): String? {
        val approved = entries.any {
            it.isRecommended && it.slug.equals(entry.slug, ignoreCase = true) &&
                it.extensionId == entry.extensionId
        }
        return if (approved) ExtensionInstallPolicy.officialAmoLatestXpiUrl(entry.slug) else null
    }

    fun verifyCurrentAndroidRecommendedPackage(
        entry: FirefoxAmoCatalogEntry,
        json: String
    ): VerifiedAmoPackage? {
        if (entries.none { it.slug == entry.slug && it.extensionId == entry.extensionId && it.isRecommended }) return null
        return runCatching {
            val addon = org.json.JSONObject(json)
            if (addon.optString("slug") != entry.slug || addon.optString("status") != "public" ||
                addon.optBoolean("is_disabled", true) || addon.optString("guid") != entry.extensionId
            ) return null

            val promotions = addon.optJSONArray("promoted") ?: return null
            val recommendedForAndroid = (0 until promotions.length()).any { index ->
                val promotion = promotions.optJSONObject(index) ?: return@any false
                if (promotion.optString("category") != "recommended") return@any false
                val apps = promotion.optJSONArray("apps") ?: return@any false
                (0 until apps.length()).any { apps.optString(it) == "android" }
            }
            if (!recommendedForAndroid) return null

            val current = addon.optJSONObject("current_version") ?: return null
            val android = current.optJSONObject("compatibility")?.optJSONObject("android") ?: return null
            val minimumAndroid = android.optString("min").takeIf { it.isNotBlank() && it != "null" } ?: return null
            val file = current.optJSONObject("file") ?: return null
            val downloadUrl = file.optString("url").takeIf(ExtensionInstallPolicy::isOfficialAmoXpiUrl) ?: return null
            val fileSize = file.optLong("size", -1L)
            if (fileSize <= 0L || fileSize > ExtensionInstallPolicy.MAX_XPI_BYTES) return null

            VerifiedAmoPackage(
                version = current.optString("version").takeIf { it.isNotBlank() && it != "null" } ?: return null,
                minimumAndroidVersion = minimumAndroid,
                fileSize = fileSize,
                downloadUrl = downloadUrl
            )
        }.getOrNull()
    }

    private fun amoEntry(
        slug: String,
        extensionId: String,
        name: String,
        category: String,
        summary: String,
        version: String,
        minAndroid: String,
        iconUrl: String,
        iconRes: Int,
        perms: List<String>
    ) = FirefoxAmoCatalogEntry(
        slug = slug,
        extensionId = extensionId,
        name = name,
        category = category,
        summary = summary,
        version = version,
        minimumAndroidVersion = minAndroid,
        listingUrl = "https://addons.mozilla.org/en-US/firefox/addon/$slug/",
        iconUrl = iconUrl,
        iconResource = iconRes,
        isRecommended = true,
        requiredPermissions = perms,
        requiredOrigins = emptyList()
    )
}
