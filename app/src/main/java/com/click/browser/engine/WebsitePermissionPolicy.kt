package com.click.browser.engine

/** Website device capabilities surfaced by the per-site settings sheet. */
internal enum class WebsitePermissionCapability {
    CAMERA,
    MICROPHONE,
    LOCATION,
    NOTIFICATIONS
}

/** This build deliberately exposes no website-device-permission grant controls. */
internal enum class WebsitePermissionStatus {
    BLOCKED_IN_CLICK,
    UNSUPPORTED
}

internal fun websitePermissionStatus(
    capability: WebsitePermissionCapability
): WebsitePermissionStatus = when (capability) {
    WebsitePermissionCapability.CAMERA,
    WebsitePermissionCapability.MICROPHONE,
    WebsitePermissionCapability.LOCATION -> WebsitePermissionStatus.BLOCKED_IN_CLICK
    WebsitePermissionCapability.NOTIFICATIONS -> WebsitePermissionStatus.UNSUPPORTED
}
