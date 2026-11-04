package com.click.browser.extensions

import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionUiGuidanceTest {
    @Test
    fun blockedInstallMessageNamesRequestedRiskAndSaysNothingWasInstalled() {
        val message = ExtensionUiGuidance.blockedCatalogInstallStatus(
            blockedPermissions = listOf("webRequest", "webRequestBlocking"),
            blockedOrigins = listOf("<all_urls>")
        )
        assertTrue(message.contains("webRequest"))
        assertTrue(message.contains("<all_urls>"))
        assertTrue(message.contains("Nothing was installed"))
    }

    @Test
    fun noOptionsAndNoActionAreExplainedAsPotentiallyNormal() {
        assertTrue(ExtensionUiGuidance.noOptionsPage.contains("normal"))
        assertTrue(ExtensionUiGuidance.noOptionsPage.contains("supported websites"))
        assertTrue(ExtensionUiGuidance.noActionOnCurrentPage.contains("page"))
    }
}
