package com.click.browser.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.WebExtension

class FirefoxAmoCatalogTest {
    @Test
    fun nativeQuickPicksRemainDistinctButTheStoreIsTheFullOfficialAndroidCatalog() {
        assertEquals(listOf("tomato-clock", "websites-notes"), FirefoxAmoCatalog.quickPicks.map { it.slug })
        assertEquals(FirefoxAmoCatalog.quickPicks.map { it.slug }.distinct().size, FirefoxAmoCatalog.quickPicks.size)
        assertEquals("https://addons.mozilla.org/en-US/android/", FirefoxAmoCatalog.ANDROID_STORE_URL)

        FirefoxAmoCatalog.quickPicks.forEach { entry ->
            assertTrue("Quick pick must be a real AMO recommendation", entry.isRecommended)
            assertTrue("Listing must be on official AMO: ${entry.listingUrl}", ExtensionInstallPolicy.isOfficialAmoListing(entry.listingUrl))
            assertEquals(entry.slug, FirefoxAmoCatalog.slugFromOfficialListing(entry.listingUrl))
            assertTrue(entry.iconUrl.startsWith("https://addons.mozilla.org/user-media/addon_icons/"))
            assertTrue(entry.iconResource != 0)
            assertTrue(entry.version.isNotBlank())
            assertTrue(entry.minimumAndroidVersion.isNotBlank())
        }
        assertEquals("websitesnotes@saveriomorelli.com", FirefoxAmoCatalog.quickPicks.single { it.slug == "websites-notes" }.extensionId)
    }

    @Test
    fun bundledCatalogParserKeepsOnlyInstallableEntries() {
        val parsed = parseBundledAmoCatalog(sampleBundledCatalogJson()) { _, _ -> 7 }
        assertEquals(listOf("All", "Productivity", "Utilities"), parsed.categories)
        // The underscore-slug entry and the off-AMO listing entry are dropped:
        // neither can pass the installer's official-listing gate.
        assertEquals(listOf("sample-addon", "second-addon"), parsed.entries.map { it.slug })
        val first = parsed.entries.first()
        assertEquals("sample-addon@example.org", first.extensionId)
        assertEquals("Sample Add-on", first.name)
        assertEquals("Productivity", first.category)
        assertEquals(listOf("storage"), first.requiredPermissions)
        assertEquals(7, first.iconResource)
        assertTrue(first.isRecommended)
    }

    @Test
    fun mergedCatalogPutsQuickPicksFirstAndDedupesBySlug() {
        val bundled = parseBundledAmoCatalog(sampleBundledCatalogJson()) { _, _ -> 7 }
        val merged = mergeAmoCatalogEntries(FirefoxAmoCatalog.quickPicks, bundled.entries)
        assertEquals(
            listOf("tomato-clock", "websites-notes", "sample-addon", "second-addon"),
            merged.map { it.slug }
        )
        assertEquals(merged.map { it.slug }.distinct().size, merged.size)
    }

    @Test
    fun bundledCatalogAssetRestoresTheFullBrowsableCatalog() {
        val asset = listOf(
            "src/main/assets/extensions/amo_safe_catalog.json",
            "app/src/main/assets/extensions/amo_safe_catalog.json"
        ).map { java.io.File(it) }.firstOrNull { it.isFile }
            ?: error("Bundled AMO catalog asset not found from the test working directory")
        val parsed = parseBundledAmoCatalog(asset.readText()) { _, _ -> 1 }
        // The asset ships 520 entries; the two underscore-slug entries
        // (_pacman, _pikachu) can never pass the official-listing gate and
        // are excluded, leaving 518 installable entries.
        assertEquals(518, parsed.entries.size)
        assertEquals(518, parsed.entries.map { it.slug }.distinct().size)
        assertEquals(
            listOf("All", "Productivity", "Developer & Design", "Image & Research", "Notes & Study", "Writing & Language", "Utilities"),
            parsed.categories
        )
        parsed.entries.forEach { entry ->
            assertEquals(entry.slug, FirefoxAmoCatalog.slugFromOfficialListing(entry.listingUrl))
            assertTrue(entry.extensionId.isNotBlank())
            assertTrue(entry.name.isNotBlank())
            assertTrue(entry.iconResource != 0)
        }
        val merged = mergeAmoCatalogEntries(FirefoxAmoCatalog.quickPicks, parsed.entries)
        assertEquals(518, merged.size)
        assertEquals(listOf("tomato-clock", "websites-notes"), merged.take(2).map { it.slug })
    }

    private fun sampleBundledCatalogJson(): String = """
        {"version":"test","totalCount":4,
         "categories":["All","Productivity","Utilities"],
         "entries":[
          {"slug":"sample-addon","guid":"sample-addon@example.org","name":"Sample Add-on",
           "category":"Productivity","summary":"A sample.","version":"1.0",
           "minimumAndroidVersion":"120.0",
           "listingUrl":"https://addons.mozilla.org/en-US/firefox/addon/sample-addon/",
           "iconUrl":"https://addons.mozilla.org/user-media/addon_icons/1/sample.png",
           "isRecommended":true,"requiredPermissions":["storage"],"requiredOrigins":[]},
          {"slug":"second-addon","guid":"second@example.org","name":"Second Add-on",
           "category":"Utilities","summary":"Another sample.","version":"2.0",
           "minimumAndroidVersion":"109.0",
           "listingUrl":"https://addons.mozilla.org/en-US/android/addon/second-addon/",
           "iconUrl":"","isRecommended":false,"requiredPermissions":[],"requiredOrigins":[]},
          {"slug":"_underscore","guid":"bad@example.org","name":"Bad Slug",
           "category":"Utilities","summary":"Underscore slugs fail the listing gate.","version":"1.0",
           "minimumAndroidVersion":"109.0",
           "listingUrl":"https://addons.mozilla.org/en-US/android/addon/_underscore/",
           "iconUrl":"","isRecommended":false,"requiredPermissions":[],"requiredOrigins":[]},
          {"slug":"off-store","guid":"off@example.org","name":"Off Store",
           "category":"Utilities","summary":"Listing is not on AMO.","version":"1.0",
           "minimumAndroidVersion":"109.0",
           "listingUrl":"https://example.com/addon/off-store/",
           "iconUrl":"","isRecommended":false,"requiredPermissions":[],"requiredOrigins":[]}
         ]}
    """.trimIndent()

    @Test
    fun officialListingRecognizesBothAndroidAndFirefoxRoutesOnlyOnMozillaHost() {
        assertEquals("some-addon", FirefoxAmoCatalog.slugFromOfficialListing("https://addons.mozilla.org/en-US/android/addon/some-addon/"))
        assertEquals("some-addon", FirefoxAmoCatalog.slugFromOfficialListing("https://addons.mozilla.org/en-US/firefox/addon/some-addon/"))
        assertEquals("some-addon", FirefoxAmoCatalog.slugFromOfficialListing("https://addons.mozilla.org/en-US/android/addon/some-addon/reviews/"))
        assertNull(FirefoxAmoCatalog.slugFromOfficialListing("https://addons.mozilla.org.evil.example/en-US/android/addon/some-addon/"))
        assertNull(FirefoxAmoCatalog.slugFromOfficialListing("http://addons.mozilla.org/en-US/android/addon/some-addon/"))
        assertNull(FirefoxAmoCatalog.slugFromOfficialListing("https://addons.mozilla.org/en-US/android/addon/../"))
    }

    @Test
    fun quickPickPermissionsAndOriginsPassClickReviewPolicy() {
        FirefoxAmoCatalog.quickPicks.forEach { entry ->
            assertTrue("Quick pick ${entry.slug} requests an unsupported capability", ExtensionInstallPolicy.unsupportedPermissionNames(entry.requiredPermissions).isEmpty())
            assertTrue("Quick pick ${entry.slug} requests an unreviewable origin", ExtensionInstallPolicy.blockedOriginPatterns(entry.requiredOrigins).isEmpty())
            assertTrue("Quick pick ${entry.slug} must not request site access", entry.requiredOrigins.isEmpty())
        }
        val tomato = FirefoxAmoCatalog.quickPicks.single { it.slug == "tomato-clock" }
        assertEquals(listOf("notifications", "storage", "alarms"), tomato.requiredPermissions)
    }

    @Test
    fun runtimeTrustGateAcceptsMozillaReviewSignaturesAndOfficialAmoButNotUntrustedStates() {
        val listing = "https://addons.mozilla.org/en-US/android/addon/another-addon/"
        assertTrue(isEligibleMozillaCatalogExtension(WebExtension.SignedStateFlags.SIGNED, true, listing, "another-addon@example.org"))
        assertTrue(isEligibleMozillaCatalogExtension(WebExtension.SignedStateFlags.PRELIMINARY, true, listing, "another-addon@example.org"))
        listOf(
            WebExtension.SignedStateFlags.MISSING,
            WebExtension.SignedStateFlags.UNKNOWN,
            WebExtension.SignedStateFlags.PRIVILEGED,
            WebExtension.SignedStateFlags.SYSTEM
        ).forEach { state ->
            assertFalse(isEligibleMozillaCatalogExtension(state, true, listing, "another-addon@example.org"))
        }
        assertFalse(isEligibleMozillaCatalogExtension(WebExtension.SignedStateFlags.SIGNED, false, listing, "another-addon@example.org"))
        assertFalse(isEligibleMozillaCatalogExtension(WebExtension.SignedStateFlags.SIGNED, true, "https://example.com/addon/another-addon/", "another-addon@example.org"))
    }

    @Test
    fun anyLivePublicAndroidCompatibleAmoRecordCanBeConsideredRegardlessOfRecommendation() {
        val json = validNonRecommendedRecord()
        val verified = FirefoxAmoCatalog.verifyCurrentAndroidCompatiblePackage("sample-addon", json)
        assertEquals("sample-addon@example.org", verified?.packageInfo?.extensionId)
        assertEquals("1.2.3", verified?.packageInfo?.version)
        assertEquals("120.0", verified?.packageInfo?.minimumAndroidVersion)
        assertEquals("Sample Add-on", verified?.entry?.name)
        assertEquals(listOf("storage"), verified?.entry?.requiredPermissions)
        assertEquals(listOf("https://example.org/*"), verified?.entry?.requiredOrigins)
        assertFalse(verified?.entry?.isRecommended ?: true)

        val expected = FirefoxAmoCatalog.quickPicks.single { it.slug == "tomato-clock" }.copy(
            slug = "sample-addon",
            extensionId = "different-id@example.org"
        )
        assertNull(FirefoxAmoCatalog.verifyCurrentAndroidCompatiblePackage(expected, json))
    }

    @Test
    fun currentAmoRecordMustBePublicEnabledAndroidCompatibleAndRespectAmoXpiSizeLimit() {
        val good = validNonRecommendedRecord()
        assertTrue(FirefoxAmoCatalog.verifyCurrentAndroidCompatiblePackage("sample-addon", good) != null)
        assertNull(FirefoxAmoCatalog.verifyCurrentAndroidCompatiblePackage("sample-addon", good.replace("\"status\":\"public\"", "\"status\":\"unreviewed\"")))
        assertNull(FirefoxAmoCatalog.verifyCurrentAndroidCompatiblePackage("sample-addon", good.replace("\"is_disabled\":false", "\"is_disabled\":true")))
        assertNull(FirefoxAmoCatalog.verifyCurrentAndroidCompatiblePackage("sample-addon", good.replace("\"android\":{\"min\":\"120.0\",\"max\":\"*\"}", "\"firefox\":{\"min\":\"120.0\",\"max\":\"*\"}")))
        assertNull(FirefoxAmoCatalog.verifyCurrentAndroidCompatiblePackage("sample-addon", good.replace("\"min\":\"120.0\"", "\"min\":\"154.0\"")))
        assertNull(FirefoxAmoCatalog.verifyCurrentAndroidCompatiblePackage("sample-addon", good.replace("addons.mozilla.org/firefox/downloads/file/55", "example.com/firefox/downloads/file/55")))
        assertTrue(FirefoxAmoCatalog.verifyCurrentAndroidCompatiblePackage("sample-addon", good.replace("\"size\":1200", "\"size\":40000000")) != null)
        assertTrue(FirefoxAmoCatalog.verifyCurrentAndroidCompatiblePackage("sample-addon", good.replace("\"size\":1200", "\"size\":${ExtensionInstallPolicy.MAX_XPI_BYTES}")) != null)
        assertNull(FirefoxAmoCatalog.verifyCurrentAndroidCompatiblePackage("sample-addon", good.replace("\"size\":1200", "\"size\":${ExtensionInstallPolicy.MAX_XPI_BYTES + 1L}")))
        assertNull(FirefoxAmoCatalog.verifyCurrentAndroidCompatiblePackage("other-addon", good))
    }

    private fun validNonRecommendedRecord(): String = """
        {"slug":"sample-addon","status":"public","is_disabled":false,
         "guid":"sample-addon@example.org","name":{"en-US":"Sample Add-on"},
         "summary":{"en-US":"A safe sample."},"categories":["productivity"],
         "icon_url":"https://addons.mozilla.org/user-media/addon_icons/1/sample.png",
         "promoted":[],
         "current_version":{"version":"1.2.3","compatibility":{"android":{"min":"120.0","max":"*"}},
         "file":{"url":"https://addons.mozilla.org/firefox/downloads/file/55/sample_addon-1.2.3.xpi","size":1200,
         "permissions":["storage","https://example.org/*"],"host_permissions":[]}}}
    """.trimIndent()
}
