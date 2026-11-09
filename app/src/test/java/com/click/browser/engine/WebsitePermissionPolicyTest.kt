package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class WebsitePermissionPolicyTest {
    @Test
    fun cameraAndLocationRemainBlockedByClick() {
        assertEquals(
            WebsitePermissionStatus.BLOCKED_IN_CLICK,
            websitePermissionStatus(WebsitePermissionCapability.CAMERA)
        )
        assertEquals(
            WebsitePermissionStatus.BLOCKED_IN_CLICK,
            websitePermissionStatus(WebsitePermissionCapability.LOCATION)
        )
    }

    @Test
    fun microphoneStatusReflectsSiteAndAndroidChoices() {
        val capability = WebsitePermissionCapability.MICROPHONE
        assertEquals(WebsitePermissionStatus.ASK, websitePermissionStatus(capability))
        assertEquals(
            WebsitePermissionStatus.BLOCKED_FOR_SITE,
            websitePermissionStatus(capability, siteMicrophoneAllowed = false, androidPermissionGranted = true)
        )
        assertEquals(
            WebsitePermissionStatus.ANDROID_PERMISSION_REQUIRED,
            websitePermissionStatus(capability, siteMicrophoneAllowed = true, androidPermissionGranted = false)
        )
        assertEquals(
            WebsitePermissionStatus.ALLOWED,
            websitePermissionStatus(capability, siteMicrophoneAllowed = true, androidPermissionGranted = true)
        )
    }

    @Test
    fun websiteNotificationsRemainUnsupported() {
        assertEquals(
            WebsitePermissionStatus.UNSUPPORTED,
            websitePermissionStatus(WebsitePermissionCapability.NOTIFICATIONS)
        )
    }
}
