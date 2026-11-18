package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class WebsitePermissionPolicyTest {
    @Test
    fun cameraStatusReflectsSiteAndAndroidChoices() {
        val capability = WebsitePermissionCapability.CAMERA
        assertEquals(WebsitePermissionStatus.ASK, websitePermissionStatus(capability))
        assertEquals(
            WebsitePermissionStatus.BLOCKED_FOR_SITE,
            websitePermissionStatus(capability, siteCameraAllowed = false, androidPermissionGranted = true)
        )
        assertEquals(
            WebsitePermissionStatus.ANDROID_PERMISSION_REQUIRED,
            websitePermissionStatus(capability, siteCameraAllowed = true, androidPermissionGranted = false)
        )
        assertEquals(
            WebsitePermissionStatus.ALLOWED,
            websitePermissionStatus(capability, siteCameraAllowed = true, androidPermissionGranted = true)
        )
    }

    @Test
    fun locationStatusReflectsSiteAndAndroidChoices() {
        val capability = WebsitePermissionCapability.LOCATION
        assertEquals(WebsitePermissionStatus.ASK, websitePermissionStatus(capability))
        assertEquals(
            WebsitePermissionStatus.BLOCKED_FOR_SITE,
            websitePermissionStatus(capability, siteLocationAllowed = false, androidPermissionGranted = true)
        )
        assertEquals(
            WebsitePermissionStatus.ANDROID_PERMISSION_REQUIRED,
            websitePermissionStatus(capability, siteLocationAllowed = true, androidPermissionGranted = false)
        )
        assertEquals(
            WebsitePermissionStatus.ALLOWED,
            websitePermissionStatus(capability, siteLocationAllowed = true, androidPermissionGranted = true)
        )
    }

    @Test
    fun cameraAndLocationIgnoreMicrophoneChoice() {
        // A microphone decision must never leak into the camera/location status.
        assertEquals(
            WebsitePermissionStatus.ASK,
            websitePermissionStatus(
                WebsitePermissionCapability.CAMERA,
                siteMicrophoneAllowed = false,
                androidPermissionGranted = true
            )
        )
        assertEquals(
            WebsitePermissionStatus.ASK,
            websitePermissionStatus(
                WebsitePermissionCapability.LOCATION,
                siteMicrophoneAllowed = true,
                androidPermissionGranted = true
            )
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

    @Test
    fun globalDefaultParsesStoredValuesWithAskFallback() {
        assertEquals(
            WebsitePermissionGlobalDefault.ALLOW,
            WebsitePermissionGlobalDefault.fromStoredValue("allow")
        )
        assertEquals(
            WebsitePermissionGlobalDefault.ASK,
            WebsitePermissionGlobalDefault.fromStoredValue("ask")
        )
        assertEquals(
            WebsitePermissionGlobalDefault.BLOCK,
            WebsitePermissionGlobalDefault.fromStoredValue("block")
        )
        assertEquals(
            WebsitePermissionGlobalDefault.ASK,
            WebsitePermissionGlobalDefault.fromStoredValue(null)
        )
        assertEquals(
            WebsitePermissionGlobalDefault.ASK,
            WebsitePermissionGlobalDefault.fromStoredValue("nonsense")
        )
        assertEquals("allow", WebsitePermissionGlobalDefault.ALLOW.toStoredValue())
        assertEquals("ask", WebsitePermissionGlobalDefault.ASK.toStoredValue())
        assertEquals("block", WebsitePermissionGlobalDefault.BLOCK.toStoredValue())
    }
}
