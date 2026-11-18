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

/**
 * Profile-wide default for a website device permission, applied when a site
 * has no saved per-origin choice. Stored in BrowserRepository as the string
 * values "allow" | "ask" | "block"; anything unrecognized falls back to ASK,
 * which preserves Click's historic ask-first behaviour.
 */
enum class WebsitePermissionGlobalDefault {
    ALLOW,
    ASK,
    BLOCK;

    fun toStoredValue(): String = when (this) {
        ALLOW -> "allow"
        ASK -> "ask"
        BLOCK -> "block"
    }

    companion object {
        fun fromStoredValue(value: String?): WebsitePermissionGlobalDefault = when (value) {
            "allow" -> ALLOW
            "block" -> BLOCK
            else -> ASK
        }
    }
}

/**
 * Combines a site's saved choice with the matching Android permission grant.
 * Camera, microphone, and location all follow the same rule set: a blocked
 * site stays blocked, an allowed site still needs the Android grant, and no
 * saved choice means Click asks. Website notifications remain unsupported —
 * the engine gives Click no per-site notification control to enforce.
 */
internal fun websitePermissionStatus(
    capability: WebsitePermissionCapability,
    siteMicrophoneAllowed: Boolean? = null,
    siteCameraAllowed: Boolean? = null,
    siteLocationAllowed: Boolean? = null,
    androidPermissionGranted: Boolean = false
): WebsitePermissionStatus = when (capability) {
    WebsitePermissionCapability.CAMERA ->
        websitePermissionStatusFor(siteCameraAllowed, androidPermissionGranted)
    WebsitePermissionCapability.MICROPHONE ->
        websitePermissionStatusFor(siteMicrophoneAllowed, androidPermissionGranted)
    WebsitePermissionCapability.LOCATION ->
        websitePermissionStatusFor(siteLocationAllowed, androidPermissionGranted)
    WebsitePermissionCapability.NOTIFICATIONS -> WebsitePermissionStatus.UNSUPPORTED
}

private fun websitePermissionStatusFor(
    siteAllowed: Boolean?,
    androidPermissionGranted: Boolean
): WebsitePermissionStatus = when (
    websiteMicrophoneStatus(siteAllowed, androidPermissionGranted)
) {
    WebsiteMicrophoneStatus.ASK -> WebsitePermissionStatus.ASK
    WebsiteMicrophoneStatus.ALLOWED -> WebsitePermissionStatus.ALLOWED
    WebsiteMicrophoneStatus.BLOCKED_FOR_SITE -> WebsitePermissionStatus.BLOCKED_FOR_SITE
    WebsiteMicrophoneStatus.ANDROID_PERMISSION_REQUIRED ->
        WebsitePermissionStatus.ANDROID_PERMISSION_REQUIRED
}
