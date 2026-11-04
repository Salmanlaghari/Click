package com.click.browser.extensions

/** Plain-language explanations for normal WebExtension behavior and Click policy outcomes. */
internal object ExtensionUiGuidance {
    const val noOptionsPage =
        "No settings page is provided. That is normal: some add-ons work only on supported websites and have no separate settings window. If an add-on offers a toolbar action, open it from the Extensions button in a regular GeckoView tab."

    const val noActionOnCurrentPage =
        "No action is available on this page. Some add-ons work only on supported websites; page actions appear only where enabled."

    const val noActionOrOptions =
        "This add-on has no toolbar action or settings page. That can be normal: some add-ons work automatically only on supported websites, with no separate window."

    const val enabledDoesNotMeanPopup =
        "Enabled means installed and permitted by Click—not that an add-on has a popup or settings page. Some work only on supported websites; an action appears only when available on this tab."

    const val webViewTabNotice =
        "Add-ons are not active in this WebView tab; they run in regular GeckoView tabs. Switching this page reloads it, and WebView cookies, sign-ins, history, and unsaved page state do not transfer."

    fun blockedCatalogInstallStatus(
        blockedPermissions: List<String>,
        blockedOrigins: List<String>
    ): String {
        val requestedAccess = buildList {
            blockedPermissions.takeIf { it.isNotEmpty() }?.let {
                add("sensitive browser permissions: ${it.joinToString()}")
            }
            blockedOrigins.takeIf { it.isNotEmpty() }?.let {
                add("broad or insecure site access: ${it.joinToString()}")
            }
        }.joinToString("; ").ifBlank { "access that Click cannot safely allow" }

        return "Install stopped by Click's safety policy. This add-on requests $requestedAccess, " +
            "which can expose sensitive browser data or grant broad browser/site control. " +
            "Nothing was installed. Choose an alternative that needs less access."
    }
}
