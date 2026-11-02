package com.click.browser.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirefoxAmoCatalogTest {
    @Test
    fun catalogContainsOnlyOfficialFirefoxAmoListingsAndIcons() {
        assertTrue(FirefoxAmoCatalog.entries.size >= 500)
        assertEquals(
            FirefoxAmoCatalog.entries.map { it.slug }.distinct().size,
            FirefoxAmoCatalog.entries.size
        )

        FirefoxAmoCatalog.entries.forEach { entry ->
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
}
