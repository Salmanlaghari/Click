package com.click.browser.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirefoxAmoCatalogTest {
    @Test
    fun catalogContainsOnlyTheCurrentAndroidRecommendedAllowlist() {
        assertEquals(listOf("tomato-clock", "websites-notes"), FirefoxAmoCatalog.entries.map { it.slug })
        assertEquals(
            FirefoxAmoCatalog.entries.map { it.slug }.distinct().size,
            FirefoxAmoCatalog.entries.size
        )

        FirefoxAmoCatalog.entries.forEach { entry ->
            assertTrue("Only Firefox Android Recommended entries may be shown", entry.isRecommended)
            assertTrue(
                "Listing must be on official AMO: ${entry.listingUrl}",
                ExtensionInstallPolicy.isOfficialAmoListing(entry.listingUrl)
            )
            assertTrue(
                "Listing path must match its catalog slug",
                entry.listingUrl.endsWith("/firefox/addon/${entry.slug}/")
            )
            assertTrue(entry.iconUrl.startsWith("https://addons.mozilla.org/user-media/addon_icons/"))
            assertTrue(entry.iconResource != 0)
            assertTrue(entry.version.isNotBlank())
            assertTrue(entry.minimumAndroidVersion.isNotBlank())
        }

        assertEquals("websitesnotes@saveriomorelli.com", FirefoxAmoCatalog.entries.single { it.slug == "websites-notes" }.extensionId)
    }

    @Test
    fun catalogPermissionsAndOriginsPassTheExistingSafeInstallPolicy() {
        FirefoxAmoCatalog.entries.forEach { entry ->
            assertTrue(
                "Catalog entry ${entry.slug} requests a blocked API",
                ExtensionInstallPolicy.blockedPermissionNames(entry.requiredPermissions).isEmpty()
            )
            assertTrue(
                "Catalog entry ${entry.slug} requests a blocked origin",
                ExtensionInstallPolicy.blockedOriginPatterns(entry.requiredOrigins).isEmpty()
            )
            assertTrue("Catalog entry ${entry.slug} must not request site access", entry.requiredOrigins.isEmpty())
        }

        val tomatoClock = FirefoxAmoCatalog.entries.single { it.slug == "tomato-clock" }
        assertEquals(listOf("notifications", "storage", "alarms"), tomatoClock.requiredPermissions)
        assertTrue(tomatoClock.iconResource != 0)
    }

    @Test
    fun signedAllowlistedButNonRecommendedExtensionIsRejected() {
        val entry = FirefoxAmoCatalog.entries.single { it.slug == "tomato-clock" }

        assertTrue(
            isEligibleMozillaCatalogExtension(
                isSigned = true,
                isNotBlocked = true,
                isRecommended = true,
                amoListingUrl = entry.listingUrl,
                extensionId = entry.extensionId
            )
        )
        assertFalse(
            isEligibleMozillaCatalogExtension(
                isSigned = true,
                isNotBlocked = true,
                isRecommended = false,
                amoListingUrl = entry.listingUrl,
                extensionId = entry.extensionId
            )
        )
        assertFalse(
            isEligibleMozillaCatalogExtension(
                isSigned = true,
                isNotBlocked = false,
                isRecommended = true,
                amoListingUrl = entry.listingUrl,
                extensionId = entry.extensionId
            )
        )
    }

    @Test
    fun currentAmORecordMustStillBePublicEnabledRecommendedForAndroidAndHaveAnOfficialXpi() {
        val entry = FirefoxAmoCatalog.entries.single { it.slug == "tomato-clock" }
        val currentRecord = """
            {"slug":"tomato-clock","status":"public","is_disabled":false,
             "guid":"jid1-Kt2kYYgi32zPuw@jetpack",
             "promoted":[{"apps":["firefox","android"],"category":"recommended"}],
             "current_version":{"version":"7.8.0","compatibility":{"android":{"min":"109.0","max":"*"}},
             "file":{"url":"https://addons.mozilla.org/firefox/downloads/file/4810430/tomato_clock-7.8.0.xpi","size":2471994}}}
        """.trimIndent()
        val verified = FirefoxAmoCatalog.verifyCurrentAndroidRecommendedPackage(entry, currentRecord)
        assertEquals("7.8.0", verified?.version)
        assertEquals("109.0", verified?.minimumAndroidVersion)

        assertNull(
            FirefoxAmoCatalog.verifyCurrentAndroidRecommendedPackage(
                entry,
                currentRecord.replace("[\"firefox\",\"android\"]", "[\"firefox\"]")
            )
        )
        assertNull(
            FirefoxAmoCatalog.verifyCurrentAndroidRecommendedPackage(
                entry,
                currentRecord.replace("jid1-Kt2kYYgi32zPuw@jetpack", "another-id@example")
            )
        )
        assertNull(
            FirefoxAmoCatalog.verifyCurrentAndroidRecommendedPackage(
                entry,
                currentRecord.replace("addons.mozilla.org/firefox/downloads/file/4810430", "example.com/firefox/downloads/file/4810430")
            )
        )
    }
}
