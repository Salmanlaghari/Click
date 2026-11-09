package com.click.browser.engine

/** Website device capabilities surfaced by the per-site settings sheet. */
internal enum class WebsitePermissionCapability {
    CAMERA,
    MICROPHONE,
    LOCATION,
    NOTIFICATIONS
}

internal enum class WebsitePermissionStatus {
    BLOCKED_IN_CLICK,
    UNSUPPORTED,
    ASK,
    ALLOWED,
    BLOCKED_FOR_SITE,
    ANDROID_PERMISSION_REQUIRED
}

internal fun websitePermissionStatus(
    capability: WebsitePermissionCapability,
    siteMicrophoneAllowed: Boolean? = null,
    androidPermissionGranted: Boolean = false
): WebsitePermissionStatus = when (capability) {
    WebsitePermissionCapability.CAMERA,
    WebsitePermissionCapability.LOCATION -> WebsitePermissionStatus.BLOCKED_IN_CLICK
    WebsitePermissionCapability.MICROPHONE -> when (
        websiteMicrophoneStatus(siteMicrophoneAllowed, androidPermissionGranted)
    ) {
        WebsiteMicrophoneStatus.ASK -> WebsitePermissionStatus.ASK
        WebsiteMicrophoneStatus.ALLOWED -> WebsitePermissionStatus.ALLOWED
        WebsiteMicrophoneStatus.BLOCKED_FOR_SITE -> WebsitePermissionStatus.BLOCKED_FOR_SITE
        WebsiteMicrophoneStatus.ANDROID_PERMISSION_REQUIRED ->
            WebsitePermissionStatus.ANDROID_PERMISSION_REQUIRED
    }
    WebsitePermissionCapability.NOTIFICATIONS -> WebsitePermissionStatus.UNSUPPORTED
}
