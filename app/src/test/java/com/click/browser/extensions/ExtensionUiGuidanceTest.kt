package com.click.browser.extensions

import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionUiGuidanceTest {
    @Test
    fun installReviewExplainsAffirmativeConsentAndSeparateOptionalRequests() {
        assertTrue(ExtensionUiGuidance.installPermissionConsent.contains("Tap Add"))
        assertTrue(ExtensionUiGuidance.installPermissionConsent.contains("Cancel denies"))
        assertTrue(ExtensionUiGuidance.installPermissionConsent.contains("Optional permissions are not granted now"))
        assertTrue(ExtensionUiGuidance.installPermissionConsent.contains("process or send data"))
    }

    @Test
    fun optionalRequestExplainsBatchApprovalAndNoPerSiteSplit() {
        assertTrue(ExtensionUiGuidance.optionalPermissionConsent.contains("separate optional request"))
        assertTrue(ExtensionUiGuidance.optionalPermissionConsent.contains("all-or-nothing"))
        assertTrue(ExtensionUiGuidance.optionalPermissionConsent.contains("cannot be split into individual site choices"))
        assertTrue(ExtensionUiGuidance.optionalPermissionConsent.contains("Future requests will be shown separately"))
    }

    @Test
    fun permissionAndOriginRowsKeepExactRequestedValuesAndExplainBroadAccess() {
        val permission = ExtensionUiGuidance.permissionDisclosure("webRequestBlocking")
        assertTrue(permission.startsWith("webRequestBlocking —"))
        assertTrue(permission.contains("block or change network requests"))

        val origin = ExtensionUiGuidance.originDisclosure("<all_urls>")
        assertTrue(origin.startsWith("<all_urls> —"))
        assertTrue(origin.contains("all URLs supported by GeckoView"))
        assertTrue(ExtensionUiGuidance.originDisclosure("*://*/*").contains("all HTTP and HTTPS websites"))

        val warning = ExtensionUiGuidance.siteAccessRiskWarning(listOf("<all_urls>"))
        assertTrue(warning.orEmpty().contains("Elevated site access requested"))
        assertTrue(warning.orEmpty().contains("read or change pages"))
        assertTrue(warning.orEmpty().contains("wildcard patterns may cover many sites"))

        val permissionWarning = ExtensionUiGuidance.permissionRiskWarning(listOf("cookies", "storage"))
        assertTrue(permissionWarning.orEmpty().contains("Sensitive browser access requested: cookies"))
        assertTrue(ExtensionInstallPolicy.unsupportedPermissionNames(listOf("cookies")).isEmpty())

        assertTrue(ExtensionUiGuidance.originDisclosure("http://example.com/*").contains("unencrypted"))
        assertTrue(ExtensionUiGuidance.originDisclosure("https://192.168.1.1/*").contains("local or private network"))
        assertTrue(ExtensionUiGuidance.siteAccessRiskWarning(listOf("http://example.com/*")).orEmpty().contains("HTTP access may be unencrypted"))
        assertTrue(ExtensionUiGuidance.siteAccessRiskWarning(listOf("https://192.168.1.1/*")).orEmpty().contains("local/private-network sites"))
    }

    @Test
    fun blockedInstallMessageNamesUnsupportedCapabilityAndSaysNothingWasInstalled() {
        val message = ExtensionUiGuidance.blockedCatalogInstallStatus(
            blockedPermissions = listOf("nativeMessaging"),
            blockedOrigins = listOf("not-a-match-pattern")
        )
        assertTrue(message.contains("nativeMessaging"))
        assertTrue(message.contains("not-a-match-pattern"))
        assertTrue(message.contains("Nothing was installed"))
    }

    @Test
    fun noOptionsAndNoActionAreExplainedAsPotentiallyNormal() {
        assertTrue(ExtensionUiGuidance.noOptionsPage.contains("normal"))
        assertTrue(ExtensionUiGuidance.noOptionsPage.contains("supported websites"))
        assertTrue(ExtensionUiGuidance.noActionOnCurrentPage.contains("page"))
    }
}
