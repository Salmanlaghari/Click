package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class WebsitePermissionPolicyTest {
    @Test
    fun cameraMicrophoneAndLocationAreBlockedByClick() {
        assertEquals(WebsitePermissionStatus.BLOCKED_IN_CLICK, websitePermissionStatus(WebsitePermissionCapability.CAMERA))
        assertEquals(WebsitePermissionStatus.BLOCKED_IN_CLICK, websitePermissionStatus(WebsitePermissionCapability.MICROPHONE))
        assertEquals(WebsitePermissionStatus.BLOCKED_IN_CLICK, websitePermissionStatus(WebsitePermissionCapability.LOCATION))
    }

    @Test
    fun websiteNotificationsAreExplicitlyUnsupported() {
        assertEquals(WebsitePermissionStatus.UNSUPPORTED, websitePermissionStatus(WebsitePermissionCapability.NOTIFICATIONS))
    }

    @Test
    fun policyCoversEveryWebsitePermissionCapability() {
        val statuses = WebsitePermissionCapability.values().associateWith(::websitePermissionStatus)
        assertEquals(4, statuses.size)
        assertEquals(WebsitePermissionStatus.BLOCKED_IN_CLICK, statuses[WebsitePermissionCapability.CAMERA])
        assertEquals(WebsitePermissionStatus.BLOCKED_IN_CLICK, statuses[WebsitePermissionCapability.MICROPHONE])
        assertEquals(WebsitePermissionStatus.BLOCKED_IN_CLICK, statuses[WebsitePermissionCapability.LOCATION])
        assertEquals(WebsitePermissionStatus.UNSUPPORTED, statuses[WebsitePermissionCapability.NOTIFICATIONS])
    }
}
