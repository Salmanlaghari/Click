package com.click.browser.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionInstallPolicyTest {
    @Test
    fun blocksNativeMessagingAndUnexposedGeckoViewCapabilities() {
        assertEquals(
            listOf("geckoViewAddons", "geolocation", "nativeMessaging", "nativeMessagingFromContent"),
            ExtensionInstallPolicy.unsupportedPermissionNames(
                listOf("geolocation", "nativeMessagingFromContent", "storage", "geckoViewAddons", "nativeMessaging")
            )
        )
    }

    @Test
    fun sensitiveBrowserPermissionsReachTheExplicitReviewPrompt() {
        assertTrue(
            ExtensionInstallPolicy.unsupportedPermissionNames(
                listOf("proxy", "webRequest", "webRequestBlocking", "cookies", "history", "clipboardRead", "clipboardWrite", "browsingData", "management", "downloads")
            ).isEmpty()
        )
    }

    @Test
    fun keepsCommonContentPermissionsAvailableForExplicitUserReview() {
        assertTrue(
            ExtensionInstallPolicy.unsupportedPermissionNames(
                listOf("activeTab", "storage", "tabs", "scripting", "contextMenus")
            ).isEmpty()
        )
    }

    @Test
    fun broadAndInsecureOriginsReachTheExplicitReviewPrompt() {
        assertTrue(
            ExtensionInstallPolicy.blockedOriginPatterns(
                listOf(
                    "https://example.com/*",
                    "https://login.example.org/*",
                    "<all_urls>",
                    "https://*.example.net/*",
                    "https://*/*",
                    "*://*/*",
                    "http://example.com/*",
                    "https://router.local/*",
                    "https://192.168.1.1/*"
                )
            ).isEmpty()
        )
        assertEquals(
            listOf("*://*/*", "<all_urls>", "https://*.example.net/*", "https://*/*"),
            ExtensionInstallPolicy.broadOriginPatterns(
                listOf("https://example.com/*", "<all_urls>", "https://*.example.net/*", "https://*/*", "*://*/*")
            )
        )
    }

    @Test
    fun rejectsMalformedOrUnreviewablyLargeOriginRequests() {
        assertEquals(1, ExtensionInstallPolicy.blockedOriginPatterns(listOf("not-a-match-pattern")).size)
        listOf(
            "https://example.com:8443/*",
            "https://user@example.com/*",
            "https://*.example.*/*",
            "javascript://example.com/*"
        ).forEach { pattern -> assertEquals("Should reject $pattern", 1, ExtensionInstallPolicy.blockedOriginPatterns(listOf(pattern)).size) }
        assertEquals(
            1,
            ExtensionInstallPolicy.blockedOriginPatterns((1..101).map { "https://site$it.example.com/*" }).size
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
    fun onlyOfficialHttpsAmoXpiDownloadUrlsAreAccepted() {
        assertEquals(
            "https://addons.mozilla.org/firefox/downloads/latest/tomato-clock/latest.xpi",
            ExtensionInstallPolicy.officialAmoLatestXpiUrl("tomato-clock")
        )
        assertNull(ExtensionInstallPolicy.officialAmoLatestXpiUrl("../other-addon"))
        assertTrue(
            ExtensionInstallPolicy.isOfficialAmoXpiUrl(
                "https://addons.mozilla.org/firefox/downloads/latest/tomato-clock/latest.xpi"
            )
        )
        assertTrue(
            ExtensionInstallPolicy.isOfficialAmoXpiUrl(
                "https://addons.mozilla.org/firefox/downloads/file/4810430/tomato_clock-7.8.0.xpi?src=addon-manager"
            )
        )
        assertFalse(ExtensionInstallPolicy.isOfficialAmoXpiUrl("http://addons.mozilla.org/firefox/downloads/latest/tomato-clock/latest.xpi"))
        assertFalse(ExtensionInstallPolicy.isOfficialAmoXpiUrl("https://addons.mozilla.org.evil.example/firefox/downloads/latest/tomato-clock/latest.xpi"))
        assertFalse(ExtensionInstallPolicy.isOfficialAmoXpiUrl("https://addons.mozilla.org/firefox/downloads/latest/tomato-clock/latest.crx"))
        assertFalse(ExtensionInstallPolicy.isOfficialAmoXpiUrl("https://addons.mozilla.org/firefox/addon/tomato-clock/"))
    }

    @Test
    fun optionsPagesMustStayOnTheirOwnMozExtensionOrigin() {
        val baseUrl = "moz-extension://click-addon-id/addon-root/"
        assertEquals(
            "moz-extension://click-addon-id/addon-root/options.html",
            ExtensionInstallPolicy.safeExtensionOptionsPageUrl(baseUrl, "options.html")
        )
        assertEquals(
            "moz-extension://click-addon-id/addon-root/options.html",
            ExtensionInstallPolicy.safeExtensionOptionsPageUrl(
                baseUrl,
                "moz-extension://click-addon-id/addon-root/options.html"
            )
        )
        assertNull(
            ExtensionInstallPolicy.safeExtensionOptionsPageUrl(
                baseUrl,
                "moz-extension://click-addon-id/addon-root-elsewhere/options.html"
            )
        )
        assertNull(
            ExtensionInstallPolicy.safeExtensionOptionsPageUrl(
                baseUrl,
                "moz-extension://click-addon-id/addon-root/%2e%2e/escape.html"
            )
        )
        assertNull(
            ExtensionInstallPolicy.safeExtensionOptionsPageUrl(
                baseUrl,
                "moz-extension://another-addon-id/addon-root/options.html"
            )
        )
        assertNull(ExtensionInstallPolicy.safeExtensionOptionsPageUrl(baseUrl, "https://example.com/options"))
        assertNull(ExtensionInstallPolicy.safeExtensionOptionsPageUrl(baseUrl, "javascript:alert(1)"))
        assertNull(
            ExtensionInstallPolicy.safeExtensionOptionsPageUrl(
                baseUrl,
                "moz-extension://user@click-addon-id/addon-root/options.html"
            )
        )
        assertNull(ExtensionInstallPolicy.safeExtensionOptionsPageUrl(baseUrl, null))
    }

    @Test
    fun optionsNavigationStaysWithinTheOpenedAddonRoot() {
        val baseUrl = "moz-extension://click-addon-id/addon-root/"
        assertTrue(
            ExtensionInstallPolicy.isSafeExtensionOptionsNavigation(
                baseUrl,
                "moz-extension://click-addon-id/addon-root/options.html"
            )
        )
        assertTrue(
            ExtensionInstallPolicy.isSafeExtensionOptionsNavigation(
                baseUrl,
                "moz-extension://click-addon-id/addon-root/subpage.html"
            )
        )
        assertFalse(
            ExtensionInstallPolicy.isSafeExtensionOptionsNavigation(
                baseUrl,
                "moz-extension://click-addon-id/addon-root-elsewhere/options.html"
            )
        )
        assertFalse(
            ExtensionInstallPolicy.isSafeExtensionOptionsNavigation(
                baseUrl,
                "moz-extension://click-addon-id/addon-root/%2e%2e/escape.html"
            )
        )
        assertFalse(
            ExtensionInstallPolicy.isSafeExtensionOptionsNavigation(
                baseUrl,
                "moz-extension://click-addon-id/addon-root/%252e%252e/escape.html"
            )
        )
        assertFalse(
            ExtensionInstallPolicy.isSafeExtensionOptionsNavigation(
                baseUrl,
                "moz-extension://another-addon-id/addon-root/options.html"
            )
        )
        assertFalse(
            ExtensionInstallPolicy.isSafeExtensionOptionsNavigation(
                baseUrl,
                "https://example.com/options.html"
            )
        )
        assertFalse(
            ExtensionInstallPolicy.isSafeExtensionOptionsNavigation(
                baseUrl,
                "moz-extension://user@click-addon-id/addon-root/options.html"
            )
        )
    }

    @Test
    fun addressBarAllowsOnlyHttpAndHttpsSchemes() {
        assertEquals("https://example.com", ExtensionBrowserInput.resolve("example.com"))
        assertEquals("http://example.com/path", ExtensionBrowserInput.resolve("http://example.com/path"))
        assertEquals("https://example.com:8443", ExtensionBrowserInput.resolve("example.com:8443"))
        assertEquals("https://duckduckgo.com/?q=hello+world", ExtensionBrowserInput.resolve("hello world"))
        assertNull(ExtensionBrowserInput.resolve("javascript:alert(1)"))
        assertNull(ExtensionBrowserInput.resolve("mailto:hello@example.com"))
        assertNull(ExtensionBrowserInput.resolve("file:///etc/passwd"))
        assertNull(ExtensionBrowserInput.resolve("data:text/html,hi"))
    }

    @Test
    fun storePageNavigationRejectsNonWebSchemes() {
        assertTrue(isAllowedExtensionBrowserNavigation("https://addons.mozilla.org/android/"))
        assertTrue(isAllowedExtensionBrowserNavigation("http://example.com/"))
        assertFalse(isAllowedExtensionBrowserNavigation("javascript:alert(1)"))
        assertFalse(isAllowedExtensionBrowserNavigation("file:///etc/passwd"))
        assertFalse(isAllowedExtensionBrowserNavigation("data:text/html,hi"))
        assertFalse(isAllowedExtensionBrowserNavigation("intent://open"))
        assertFalse(isAllowedExtensionBrowserNavigation("about:blank"))
    }
}
