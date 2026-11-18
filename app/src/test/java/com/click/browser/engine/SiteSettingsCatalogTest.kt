package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Site settings hub catalog to Prince's Chrome screenshots
 * (18 Nov 2026): every row he listed must exist exactly once, in order.
 */
class SiteSettingsCatalogTest {

    private val expectedPermissionIds = listOf(
        "location",
        "camera",
        "microphone",
        "notifications",
        "embedded_content",
        "motion_sensors",
        "nfc_devices",
        "usb",
        "serial_port",
        "file_editing",
        "clipboard",
        "virtual_reality",
        "augmented_reality",
        "your_device_use",
        "local_network",
        "apps_on_device"
    )

    private val expectedContentIds = listOf(
        "third_party_cookies",
        "javascript",
        "pop_ups",
        "sound",
        "intrusive_ads",
        "protected_content",
        "third_party_sign_in",
        "auto_verify",
        "on_device_site_data",
        "desktop_site",
        "background_sync",
        "automatic_downloads",
        "javascript_optimisation",
        "saved_zoom",
        "data_stored"
    )

    @Test
    fun permissionRowsMatchPrinceListExactlyInOrder() {
        assertEquals(expectedPermissionIds, SiteSettingsCatalog.permissionRows.map { it.id })
    }

    @Test
    fun contentRowsMatchPrinceListExactlyInOrder() {
        assertEquals(expectedContentIds, SiteSettingsCatalog.contentRows.map { it.id })
    }

    @Test
    fun everyRowIdExistsExactlyOnce() {
        val ids = SiteSettingsCatalog.allRows.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(SiteSettingsCatalog.ALL_SITES_ID !in ids)
    }

    @Test
    fun sectionsAndKindsAreConsistent() {
        SiteSettingsCatalog.permissionRows.forEach { row ->
            assertEquals(SiteSettingsHubSection.PERMISSIONS, row.section)
        }
        SiteSettingsCatalog.contentRows.forEach { row ->
            assertEquals(SiteSettingsHubSection.CONTENT, row.section)
        }
        // Only camera / location / microphone may claim a real permission screen.
        val realPermissions = SiteSettingsCatalog.allRows
            .filter { it.kind == SiteSettingsRowKind.PERMISSION }
            .map { it.id }
        assertEquals(listOf("location", "camera", "microphone"), realPermissions)
        // Data stored is its own destination kind.
        assertEquals(
            SiteSettingsRowKind.DATA_STORED,
            SiteSettingsCatalog.row("data_stored")?.kind
        )
    }
}
