package com.click.browser.extensions

import android.content.Context
import com.click.browser.R
import org.json.JSONArray
import org.json.JSONObject

/** A catalog card or a live-verified entry from the official Firefox-for-Android AMO store. */
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
    val slug: String,
    val extensionId: String,
    val version: String,
    val minimumAndroidVersion: String,
    val fileSize: Long,
    val downloadUrl: String
)

internal data class VerifiedAmoCatalogRecord(
    val entry: FirefoxAmoCatalogEntry,
    val packageInfo: VerifiedAmoPackage
)

/** The parsed bundled catalog asset: browsable entries plus its category list. */
internal data class BundledAmoCatalog(
    val entries: List<FirefoxAmoCatalogEntry>,
    val categories: List<String>
)

/**
 * Parses the bundled AMO catalog asset into browsable entries. An entry is
 * only kept when it can pass the same official-listing gate the installer
 * applies (valid slug, official AMO listing whose slug round-trips, complete
 * display fields), so every card shown can actually reach the live AMO
 * verification in `ExtensionBrowserActivity.beginCatalogInstall`.
 * [iconFor] resolves the card icon from the entry category and the optional
 * bundled-icon name stored in the asset (`fallbackIcon`).
 */
internal fun parseBundledAmoCatalog(
    json: String,
    iconFor: (category: String, bundledIconName: String?) -> Int
): BundledAmoCatalog = runCatching {
    val root = JSONObject(json)
    val categories = FirefoxAmoCatalog.jsonStrings(root.optJSONArray("categories")).filter(String::isNotBlank)
    val rawEntries = root.optJSONArray("entries") ?: JSONArray()
    val parsed = buildList {
        for (index in 0 until rawEntries.length()) {
            val item = rawEntries.optJSONObject(index) ?: continue
            val slug = item.optString("slug").trim().lowercase()
            val name = item.optString("name").trim()
            val summary = item.optString("summary").trim()
            val version = item.optString("version").trim()
            val minimumAndroidVersion = item.optString("minimumAndroidVersion").trim()
            val listingUrl = item.optString("listingUrl").trim()
            val category = item.optString("category").trim().ifBlank { "Utilities" }
            if (!slug.matches(Regex("^[a-z0-9][a-z0-9_~-]{0,127}$")) ||
                name.isBlank() || summary.isBlank() || version.isBlank() ||
                minimumAndroidVersion.isBlank() ||
                FirefoxAmoCatalog.slugFromOfficialListing(listingUrl) != slug
            ) continue
            val iconUrl = item.optString("iconUrl").trim()
                .takeIf { it.startsWith("https://addons.mozilla.org/") }.orEmpty()
            val bundledIconName = item.optString("fallbackIcon")
                .substringAfterLast('/').substringBeforeLast('.')
                .takeIf(String::isNotBlank)
            add(
                FirefoxAmoCatalogEntry(
                    slug = slug,
                    extensionId = item.optString("guid").trim(),
                    name = name,
                    category = category,
                    summary = summary,
                    version = version,
                    minimumAndroidVersion = minimumAndroidVersion,
                    listingUrl = listingUrl,
                    iconUrl = iconUrl,
                    iconResource = iconFor(category, bundledIconName),
                    isRecommended = item.optBoolean("isRecommended", false),
                    requiredPermissions = FirefoxAmoCatalog.jsonStrings(item.optJSONArray("requiredPermissions")),
                    requiredOrigins = FirefoxAmoCatalog.jsonStrings(item.optJSONArray("requiredOrigins"))
                )
            )
        }
    }
    BundledAmoCatalog(entries = parsed, categories = categories)
}.getOrElse { BundledAmoCatalog(emptyList(), emptyList()) }

/** Quick picks first, then bundled entries whose slug is not already a quick pick. */
internal fun mergeAmoCatalogEntries(
    quickPicks: List<FirefoxAmoCatalogEntry>,
    bundled: List<FirefoxAmoCatalogEntry>
): List<FirefoxAmoCatalogEntry> {
    if (bundled.isEmpty()) return quickPicks
    val quickSlugs = quickPicks.mapTo(mutableSetOf()) { it.slug.lowercase() }
    return quickPicks + bundled.filterNot { it.slug.lowercase() in quickSlugs }
}

/**
 * The two curated quick picks stay hardcoded (with their real bundled icons).
 * The full browsable catalog ships inside the APK as
 * `assets/extensions/amo_safe_catalog.json` and is loaded once via
 * [initialize]; every install still live-checks the official AMO record
 * before GeckoView sees the package, exactly like the quick picks.
 */
internal object FirefoxAmoCatalog {
    const val ANDROID_STORE_URL = "https://addons.mozilla.org/en-US/android/"
    /** Keep aligned with the GeckoView 153 runtime pin in gradle/libs.versions.toml. */
    const val GECKOVIEW_ANDROID_COMPAT_VERSION = "153.0"
    const val CATALOG_ASSET_PATH = "extensions/amo_safe_catalog.json"

    @Volatile
    private var bundledCatalog: BundledAmoCatalog = BundledAmoCatalog(emptyList(), emptyList())

    /** Loads the bundled catalog asset once; safe to call again (no-op after success). */
    fun initialize(context: Context) {
        if (bundledCatalog.entries.isNotEmpty()) return
        val parsed = runCatching {
            val json = context.assets.open(CATALOG_ASSET_PATH).bufferedReader().use { it.readText() }
            parseBundledAmoCatalog(json, ::bundledIconResource)
        }.getOrNull() ?: return
        if (parsed.entries.isNotEmpty()) bundledCatalog = parsed
    }

    val categories: List<String>
        get() {
            val bundled = bundledCatalog.categories
            return when {
                bundled.isEmpty() -> listOf("All", "Productivity", "Notes & Study")
                bundled.first().equals("All", ignoreCase = true) -> bundled
                else -> listOf("All") + bundled
            }
        }

    /** All browsable catalog entries: curated quick picks first, then the bundled catalog. */
    val entries: List<FirefoxAmoCatalogEntry>
        get() = mergeAmoCatalogEntries(quickPicks, bundledCatalog.entries)

    val quickPicks: List<FirefoxAmoCatalogEntry> by lazy {
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
                summary = "Take structured per-page and domain study notes locally in its GeckoView profile without broad host access.",
                version = "5.0.0.5",
                minAndroid = "60.0",
                iconUrl = "https://addons.mozilla.org/user-media/addon_icons/2714/2714481-64.png?modified=e71325d8",
                iconRes = R.drawable.amo_websites_notes,
                perms = listOf("storage", "tabs", "unlimitedStorage", "activeTab", "menus")
            )
        )
    }

    fun slugFromOfficialListing(url: String?): String? {
        if (!ExtensionInstallPolicy.isOfficialAmoListing(url)) return null
        val segments = runCatching {
            java.net.URI(url!!).path.orEmpty().split('/').filter(String::isNotBlank)
        }.getOrNull() ?: return null
        val storeIndex = segments.indexOfLast { it.equals("android", true) || it.equals("firefox", true) }
        if (storeIndex < 0 || storeIndex + 2 >= segments.size ||
            !segments[storeIndex + 1].equals("addon", true)
        ) return null
        val slug = segments[storeIndex + 2].lowercase()
        if (!slug.matches(Regex("^[a-z0-9][a-z0-9_~-]{0,127}$"))) return null
        val trailing = segments.drop(storeIndex + 3)
        if (trailing.any { it !in setOf("reviews", "versions", "support", "details") }) return null
        if (trailing.size > 1) return null
        return slug
    }

    fun isApprovedAmoListing(url: String?): Boolean = slugFromOfficialListing(url) != null

    /** The exact ID-to-listing relationship is established from the live AMO record at install time. */
    fun isApprovedExtensionId(id: String?): Boolean = !id.isNullOrBlank()

    fun recommendedEntryForListing(url: String?): FirefoxAmoCatalogEntry? {
        val slug = slugFromOfficialListing(url) ?: return null
        return entries.firstOrNull { it.slug.equals(slug, ignoreCase = true) }
    }

    fun latestPackageUrl(entry: FirefoxAmoCatalogEntry): String? {
        val listingSlug = slugFromOfficialListing(entry.listingUrl)
        return if (listingSlug == entry.slug.lowercase()) {
            ExtensionInstallPolicy.officialAmoLatestXpiUrl(entry.slug)
        } else null
    }

    fun verifyCurrentAndroidCompatiblePackage(
        entry: FirefoxAmoCatalogEntry,
        json: String
    ): VerifiedAmoCatalogRecord? {
        val record = verifyCurrentAndroidCompatiblePackage(entry.slug, json) ?: return null
        if (entry.extensionId.isNotBlank() && record.entry.extensionId != entry.extensionId) return null
        return record
    }

    fun verifyCurrentAndroidCompatiblePackage(
        expectedSlug: String,
        json: String
    ): VerifiedAmoCatalogRecord? = runCatching {
        val slug = expectedSlug.trim().lowercase()
        if (!slug.matches(Regex("^[a-z0-9][a-z0-9_~-]{0,127}$"))) return null
        val addon = JSONObject(json)
        if (addon.optString("slug") != slug || addon.optString("status") != "public" ||
            addon.optBoolean("is_disabled", true)
        ) return null

        val extensionId = addon.optString("guid").takeIf { it.isNotBlank() && it != "null" } ?: return null
        val current = addon.optJSONObject("current_version") ?: return null
        val compatibility = current.optJSONObject("compatibility") ?: return null
        val android = compatibility.optJSONObject("android") ?: return null
        val minimumAndroid = android.optString("min").takeIf { it.isNotBlank() && it != "null" } ?: return null
        val maximumAndroid = android.optString("max").takeIf { it.isNotBlank() && it != "null" } ?: "*"
        if (!versionInRange(GECKOVIEW_ANDROID_COMPAT_VERSION, minimumAndroid, maximumAndroid)) return null

        val file = current.optJSONObject("file") ?: return null
        val downloadUrl = file.optString("url").takeIf(ExtensionInstallPolicy::isOfficialAmoXpiUrl) ?: return null
        val fileSize = file.optLong("size", -1L)
        if (fileSize <= 0L || fileSize > ExtensionInstallPolicy.MAX_XPI_BYTES) return null
        val version = current.optString("version").takeIf { it.isNotBlank() && it != "null" } ?: return null

        val rawPermissions = jsonStrings(file.optJSONArray("permissions"))
        val hostPermissions = jsonStrings(file.optJSONArray("host_permissions"))
        val requiredOrigins = (rawPermissions.filter(::looksLikeOrigin) + hostPermissions).distinct().sorted()
        val requiredPermissions = rawPermissions.filterNot(::looksLikeOrigin).distinct().sorted()
        val name = localizedText(addon.opt("name"), slug)
        val summary = localizedText(addon.opt("summary"), "No summary provided by AMO.")
        val category = jsonStrings(addon.optJSONArray("categories"))
            .firstOrNull()?.replace('-', ' ')?.split(' ')?.joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }?.takeIf(String::isNotBlank) ?: "Firefox for Android"
        val listingUrl = "https://addons.mozilla.org/en-US/android/addon/$slug/"
        val recommended = isRecommendedForAndroid(addon.optJSONArray("promoted"))
        val iconUrl = addon.optString("icon_url").takeIf { it.startsWith("https://addons.mozilla.org/") }.orEmpty()

        val entry = FirefoxAmoCatalogEntry(
            slug = slug,
            extensionId = extensionId,
            name = name,
            category = category,
            summary = summary,
            version = version,
            minimumAndroidVersion = minimumAndroid,
            listingUrl = listingUrl,
            iconUrl = iconUrl,
            iconResource = 0,
            isRecommended = recommended,
            requiredPermissions = requiredPermissions,
            requiredOrigins = requiredOrigins
        )
        val packageInfo = VerifiedAmoPackage(
            slug = slug,
            extensionId = extensionId,
            version = version,
            minimumAndroidVersion = minimumAndroid,
            fileSize = fileSize,
            downloadUrl = downloadUrl
        )
        VerifiedAmoCatalogRecord(entry, packageInfo)
    }.getOrNull()

    private fun versionInRange(current: String, minimum: String, maximum: String): Boolean {
        val currentParts = versionParts(current) ?: return false
        val minParts = versionParts(minimum) ?: return false
        if (compareVersions(currentParts, minParts) < 0) return false
        if (maximum == "*") return true
        val maxParts = versionParts(maximum) ?: return false
        return compareVersions(currentParts, maxParts) <= 0
    }

    private fun versionParts(version: String): List<Int>? {
        if (!version.matches(Regex("^\\d+(?:\\.\\d+){0,3}$"))) return null
        return version.split('.').map(String::toIntOrNull).takeIf { parts -> parts.all { it != null } }?.filterNotNull()
    }

    private fun compareVersions(left: List<Int>, right: List<Int>): Int {
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    internal fun jsonStrings(array: JSONArray?): List<String> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            array.optString(index).takeIf { it.isNotBlank() && it != "null" }?.let(::add)
        }
    }

    private fun looksLikeOrigin(permission: String): Boolean =
        permission == "<all_urls>" || permission.contains("://") || permission.startsWith("file:")

    private fun localizedText(value: Any?, fallback: String): String {
        if (value is String) return value.takeIf(String::isNotBlank) ?: fallback
        if (value is JSONObject) {
            value.optString("en-US").takeIf { it.isNotBlank() && it != "null" }?.let { return it }
            val keys = value.keys()
            while (keys.hasNext()) {
                value.optString(keys.next()).takeIf { it.isNotBlank() && it != "null" }?.let { return it }
            }
        }
        return fallback
    }

    private fun isRecommendedForAndroid(promotions: JSONArray?): Boolean {
        if (promotions == null) return false
        return (0 until promotions.length()).any { index ->
            val promotion = promotions.optJSONObject(index) ?: return@any false
            if (promotion.optString("category") != "recommended") return@any false
            val apps = promotion.optJSONArray("apps") ?: return@any false
            (0 until apps.length()).any { apps.optString(it) == "android" }
        }
    }

    /**
     * Card icon for a bundled-catalog entry: the entry's own bundled icon when
     * the asset names one that ships in the APK, otherwise the category icon —
     * the same fallback scheme the original in-code catalog used.
     */
    private fun bundledIconResource(category: String, bundledIconName: String?): Int =
        when (bundledIconName) {
            "amo_tomato_clock" -> R.drawable.amo_tomato_clock
            "amo_websites_notes" -> R.drawable.amo_websites_notes
            "amo_tabliss" -> R.drawable.amo_tabliss
            "amo_tineye_search" -> R.drawable.amo_tineye_search
            "amo_emoji_sav" -> R.drawable.amo_emoji_sav
            else -> when (category) {
                "Productivity" -> R.drawable.ic_amo_cat_productivity
                "Developer & Design" -> R.drawable.ic_amo_cat_dev
                "Image & Research" -> R.drawable.ic_amo_cat_research
                "Notes & Study" -> R.drawable.ic_amo_cat_notes
                "Writing & Language" -> R.drawable.ic_amo_cat_writing
                else -> R.drawable.ic_amo_cat_utilities
            }
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
        listingUrl = "https://addons.mozilla.org/en-US/android/addon/$slug/",
        iconUrl = iconUrl,
        iconResource = iconRes,
        isRecommended = true,
        requiredPermissions = perms,
        requiredOrigins = emptyList()
    )
}
