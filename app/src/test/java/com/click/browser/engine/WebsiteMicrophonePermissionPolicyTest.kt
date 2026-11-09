package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteMicrophonePermissionPolicyTest {
    @Test
    fun canonicalOriginDropsPathAndNormalizesHostAndDefaultPort() {
        assertEquals(
            "https://example.com",
            canonicalWebsiteMicrophoneOrigin("HTTPS://Example.COM:443/meet/room?q=1#top")
        )
        assertEquals(
            "https://example.com:8443",
            canonicalWebsiteMicrophoneOrigin("https://Example.COM:8443/meet")
        )
    }

    @Test
    fun microphoneConsentRejectsInsecureOrMalformedOrigins() {
        assertNull(canonicalWebsiteMicrophoneOrigin("http://example.com"))
        assertNull(canonicalWebsiteMicrophoneOrigin("file:///tmp/page.html"))
        assertNull(canonicalWebsiteMicrophoneOrigin("https://user@example.com"))
        assertFalse(isMicrophoneOnlyMediaRequest("http://example.com", false, true))
    }

    @Test
    fun mediaPolicyAllowsOnlyMicrophoneWithoutVideo() {
        assertTrue(isMicrophoneOnlyMediaRequest("https://example.com/path", false, true))
        assertFalse(isMicrophoneOnlyMediaRequest("https://example.com", true, true))
        assertFalse(isMicrophoneOnlyMediaRequest("https://example.com", false, false))
    }

    @Test
    fun decisionsArePerExactOriginAndAndroidGrantIsRequired() {
        val permissions = mapOf("https://example.com" to true)
        assertEquals(WebsiteMicrophoneStatus.ANDROID_PERMISSION_REQUIRED,
            websiteMicrophoneStatus(permissions["https://example.com"], false))
        assertEquals(WebsiteMicrophoneStatus.ALLOWED,
            websiteMicrophoneStatus(permissions["https://example.com"], true))
        assertEquals(WebsiteMicrophoneStatus.ASK,
            websiteMicrophoneStatus(permissions["https://www.example.com"], true))
        assertEquals(WebsiteMicrophoneStatus.BLOCKED_FOR_SITE,
            websiteMicrophoneStatus(false, true))
    }

    @Test
    fun cameraAndLocationRemainBlockedAndWebsiteNotificationsUnsupported() {
        assertEquals(WebsitePermissionStatus.BLOCKED_IN_CLICK,
            websitePermissionStatus(WebsitePermissionCapability.CAMERA))
        assertEquals(WebsitePermissionStatus.BLOCKED_IN_CLICK,
            websitePermissionStatus(WebsitePermissionCapability.LOCATION))
        assertEquals(WebsitePermissionStatus.UNSUPPORTED,
            websitePermissionStatus(WebsitePermissionCapability.NOTIFICATIONS))
    }
}
