package com.click.browser.engine

/** Route chosen when the user taps Extensions for the selected tab. */
internal enum class ExtensionTabEntryAction {
    PRIVATE_TAB_UNAVAILABLE,
    OPEN_AMO_STORE
}

internal fun extensionTabEntryAction(
    engine: BrowserTabEngine,
    isPrivate: Boolean
): ExtensionTabEntryAction =
    if (isPrivate) ExtensionTabEntryAction.PRIVATE_TAB_UNAVAILABLE
    else ExtensionTabEntryAction.OPEN_AMO_STORE

/**
 * A WebView-to-GeckoView conversion is offered only after a verified add-on install returns to the
 * same selected, non-private tab. The helper never performs the switch; UI must obtain consent.
 */
internal fun shouldOfferPostInstallGeckoSwitch(
    originTabIsStillSelected: Boolean,
    hasEnabledInstalledExtension: Boolean,
    engine: BrowserTabEngine,
    isPrivate: Boolean,
    url: String
): Boolean = originTabIsStillSelected && hasEnabledInstalledExtension &&
    engine == BrowserTabEngine.WEBVIEW && !isPrivate && isSupportedGeckoTabUrl(url)

/** Only pages the Gecko tab provider can safely load are eligible for in-place switching. */
internal fun isSupportedGeckoTabUrl(url: String): Boolean =
    url.equals("about:blank", ignoreCase = true) ||
        url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)
