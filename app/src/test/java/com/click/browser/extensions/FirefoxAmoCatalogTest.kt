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
        assertEquals(listOf("tomato-clock", "websites-notes"), FirefoxAmoCatalog.entries.map { it.slug })
        assertEquals(FirefoxAmoCatalog.entries.map { it.slug }.distinct().size, FirefoxAmoCatalog.entries.size)
        assertEquals("https://addons.mozilla.org/en-US/android/", FirefoxAmoCatalog.ANDROID_STORE_URL)

        FirefoxAmoCatalog.entries.forEach { entry ->
            assertTrue("Quick pick must be a real AMO recommendation", entry.isRecommended)
            assertTrue("Listing must be on official AMO: ${entry.listingUrl}", ExtensionInstallPolicy.isOfficialAmoListing(entry.listingUrl))
            assertEquals(entry.slug, FirefoxAmoCatalog.slugFromOfficialListing(entry.listingUrl))
            assertTrue(entry.iconUrl.startsWith("https://addons.mozilla.org/user-media/addon_icons/"))
            assertTrue(entry.iconResource != 0)
            assertTrue(entry.version.isNotBlank())
            assertTrue(entry.minimumAndroidVersion.isNotBlank())
        }
        assertEquals("websitesnotes@saveriomorelli.com", FirefoxAmoCatalog.entries.single { it.slug == "websites-notes" }.extensionId)
    }

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
        FirefoxAmoCatalog.entries.forEach { entry ->
            assertTrue("Quick pick ${entry.slug} requests an unsupported capability", ExtensionInstallPolicy.unsupportedPermissionNames(entry.requiredPermissions).isEmpty())
            assertTrue("Quick pick ${entry.slug} requests an unreviewable origin", ExtensionInstallPolicy.blockedOriginPatterns(entry.requiredOrigins).isEmpty())
            assertTrue("Quick pick ${entry.slug} must not request site access", entry.requiredOrigins.isEmpty())
        }
        val tomato = FirefoxAmoCatalog.entries.single { it.slug == "tomato-clock" }
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

        val expected = FirefoxAmoCatalog.entries.single { it.slug == "tomato-clock" }.copy(
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
