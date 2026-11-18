package com.click.browser.engine

/** Sections of the Chrome-style Site settings hub, in display order. */
internal enum class SiteSettingsHubSection {
    PERMISSIONS,
    CONTENT
}

/** What a hub row actually opens — a real control, or a truthful info screen. */
internal enum class SiteSettingsRowKind {
    /** Real per-site device permission with a category screen (camera / location / microphone). */
    PERMISSION,

    /** Content setting enforced through per-site overrides, optionally with a global control. */
    CONTENT_OVERRIDES,

    /** Content setting whose only control is a real global toggle (desktop site / on-device site data). */
    CONTENT_GLOBAL,

    /** Per-host saved text-zoom list. */
    SAVED_ZOOM,

    /** Stored website data (WebStorage origins) with per-origin delete. */
    DATA_STORED,

    /** Capability the browser engine does not provide at all — info screen only, never a fake toggle. */
    UNSUPPORTED
}

internal data class SiteSettingsHubRow(
    val id: String,
    val title: String,
    val section: SiteSettingsHubSection,
    val kind: SiteSettingsRowKind
)

/**
 * The hub's row catalog, mirroring the order of Chrome's Site settings page
 * (Prince's screenshots, 18 Nov 2026). The hub UI renders exactly these rows;
 * [SiteSettingsCatalogTest] pins the id list so a row can never silently
 * disappear or duplicate.
 */
internal object SiteSettingsCatalog {

    const val ALL_SITES_ID = "all_sites"

    val permissionRows: List<SiteSettingsHubRow> = listOf(
        SiteSettingsHubRow("location", "Location", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.PERMISSION),
        SiteSettingsHubRow("camera", "Camera", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.PERMISSION),
        SiteSettingsHubRow("microphone", "Microphone", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.PERMISSION),
        SiteSettingsHubRow("notifications", "Notifications", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("embedded_content", "Embedded content", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("motion_sensors", "Motion sensors", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("nfc_devices", "NFC devices", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("usb", "USB", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("serial_port", "Serial port", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("file_editing", "File editing", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("clipboard", "Clipboard", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("virtual_reality", "Virtual reality", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("augmented_reality", "Augmented reality", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("your_device_use", "Your device use", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("local_network", "Local network", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("apps_on_device", "Apps on device", SiteSettingsHubSection.PERMISSIONS, SiteSettingsRowKind.UNSUPPORTED)
    )

    val contentRows: List<SiteSettingsHubRow> = listOf(
        SiteSettingsHubRow("third_party_cookies", "Third-party cookies", SiteSettingsHubSection.CONTENT, SiteSettingsRowKind.CONTENT_OVERRIDES),
        SiteSettingsHubRow("javascript", "JavaScript", SiteSettingsHubSection.CONTENT, SiteSettingsRowKind.CONTENT_OVERRIDES),
        SiteSettingsHubRow("pop_ups", "Pop-ups and redirects", SiteSettingsHubSection.CONTENT, SiteSettingsRowKind.CONTENT_OVERRIDES),
        SiteSettingsHubRow("sound", "Sound", SiteSettingsHubSection.CONTENT, SiteSettingsRowKind.CONTENT_OVERRIDES),
        SiteSettingsHubRow("intrusive_ads", "Intrusive ads", SiteSettingsHubSection.CONTENT, SiteSettingsRowKind.CONTENT_OVERRIDES),
        SiteSettingsHubRow("protected_content", "Protected content", SiteSettingsHubSection.CONTENT, SiteSettingsRowKind.CONTENT_OVERRIDES),
        SiteSettingsHubRow("third_party_sign_in", "Third-party sign-in", SiteSettingsHubSection.CONTENT, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("auto_verify", "Auto-verify", SiteSettingsHubSection.CONTENT, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("on_device_site_data", "On-device site data", SiteSettingsHubSection.CONTENT, SiteSettingsRowKind.CONTENT_GLOBAL),
        SiteSettingsHubRow("desktop_site", "Desktop site", SiteSettingsHubSection.CONTENT, SiteSettingsRowKind.CONTENT_GLOBAL),
        SiteSettingsHubRow("background_sync", "Background sync", SiteSettingsHubSection.CONTENT, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("automatic_downloads", "Automatic downloads", SiteSettingsHubSection.CONTENT, SiteSettingsRowKind.CONTENT_OVERRIDES),
        SiteSettingsHubRow("javascript_optimisation", "JavaScript optimisation and security", SiteSettingsHubSection.CONTENT, SiteSettingsRowKind.UNSUPPORTED),
        SiteSettingsHubRow("saved_zoom", "Saved zoom for sites", SiteSettingsHubSection.CONTENT, SiteSettingsRowKind.SAVED_ZOOM),
        SiteSettingsHubRow("data_stored", "Data stored", SiteSettingsHubSection.CONTENT, SiteSettingsRowKind.DATA_STORED)
    )

    val allRows: List<SiteSettingsHubRow> = permissionRows + contentRows

    fun row(id: String): SiteSettingsHubRow? = allRows.firstOrNull { it.id == id }
}
