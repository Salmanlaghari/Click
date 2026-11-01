package com.click.browser.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionInstallPolicyTest {
    @Test
    fun onlyXpiPackagesAreAccepted() {
        assertTrue(ExtensionInstallPolicy.isSupportedPackageName("reader.xpi"))
        assertTrue(ExtensionInstallPolicy.isSupportedPackageName("READER.XPI"))
        assertFalse(ExtensionInstallPolicy.isSupportedPackageName("reader.crx"))
        assertFalse(ExtensionInstallPolicy.isSupportedPackageName("reader.zip"))
        assertFalse(ExtensionInstallPolicy.isSupportedPackageName("reader.xpi.exe"))
        assertFalse(ExtensionInstallPolicy.isSupportedPackageName(null))
        assertFalse(ExtensionInstallPolicy.isSupportedPackageName("  "))
    }

    @Test
    fun blocksNativeMessagingAndSensitiveBrowserApis() {
        assertEquals(
            listOf("cookies", "nativeMessaging", "proxy"),
            ExtensionInstallPolicy.blockedPermissionNames(
                listOf("storage", "proxy", "nativeMessaging", "cookies")
            )
        )
    }

    @Test
    fun blocksBrowsingHistoryIdentityAndClipboardApis() {
        assertEquals(
            listOf("bookmarks", "clipboardWrite", "identity", "webNavigation"),
            ExtensionInstallPolicy.blockedPermissionNames(
                listOf("webNavigation", "identity", "bookmarks", "clipboardWrite")
            )
        )
    }

    @Test
    fun keepsCommonContentPermissionsAvailableForExplicitUserReview() {
        assertTrue(
            ExtensionInstallPolicy.blockedPermissionNames(
                listOf("activeTab", "storage", "tabs", "scripting", "contextMenus")
            ).isEmpty()
        )
    }

    @Test
    fun acceptsOnlyNarrowHttpsOrigins() {
        assertTrue(
            ExtensionInstallPolicy.blockedOriginPatterns(
                listOf("https://example.com/*", "https://login.example.org/*")
            ).isEmpty()
        )
        assertEquals(1, ExtensionInstallPolicy.blockedOriginPatterns(listOf("<all_urls>")).size)
        assertEquals(1, ExtensionInstallPolicy.blockedOriginPatterns(listOf("https://*.example.com/*")).size)
        assertEquals(1, ExtensionInstallPolicy.blockedOriginPatterns(listOf("http://example.com/*")).size)
        assertEquals(1, ExtensionInstallPolicy.blockedOriginPatterns(listOf("https://router.local/*")).size)
        assertEquals(1, ExtensionInstallPolicy.blockedOriginPatterns(listOf("https://192.168.1.1/*")).size)
        listOf(
            "https://127.1/*",
            "https://10.0/*",
            "https://0x7f000001/*",
            "https://0177.0.0.1/*",
            "https://router.home.arpa/*",
            "https://home.arpa/*",
            "https://router.local./*",
            "https://[::1]/*"
        ).forEach { pattern ->
            assertEquals("Should reject $pattern", 1, ExtensionInstallPolicy.blockedOriginPatterns(listOf(pattern)).size)
        }
        assertEquals(1, ExtensionInstallPolicy.blockedOriginPatterns(listOf("https://example.com:8443/*")).size)
        assertEquals(
            1,
            ExtensionInstallPolicy.blockedOriginPatterns((1..11).map { "https://site$it.example.com/*" }).size
        )
    }

    @Test
    fun trustsOnlyOfficialHttpsAmoListingUrls() {
        assertTrue(ExtensionInstallPolicy.isOfficialAmoListing("https://addons.mozilla.org/en-US/firefox/addon/example/"))
        assertFalse(ExtensionInstallPolicy.isOfficialAmoListing("https://example.com/firefox/addon/example/"))
        assertFalse(ExtensionInstallPolicy.isOfficialAmoListing("http://addons.mozilla.org/en-US/firefox/addon/example/"))
        assertFalse(ExtensionInstallPolicy.isOfficialAmoListing(null))
    }

    @Test
    fun addressBarAllowsOnlyHttpAndHttpsSchemes() {
        assertEquals("https://example.com", ExtensionBrowserInput.resolve("example.com"))
        assertEquals("http://example.com/path", ExtensionBrowserInput.resolve("http://example.com/path"))
        assertEquals("https://duckduckgo.com/?q=hello+world", ExtensionBrowserInput.resolve("hello world"))
        assertNull(ExtensionBrowserInput.resolve("javascript:alert(1)"))
        assertNull(ExtensionBrowserInput.resolve("file:///etc/passwd"))
        assertNull(ExtensionBrowserInput.resolve("data:text/html,hi"))
    }
}
