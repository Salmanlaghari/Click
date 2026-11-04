package com.click.browser.engine

/** Route chosen when the user opens Extensions for the selected tab. */
internal enum class ExtensionTabEntryAction {
    PRIVATE_TAB_UNAVAILABLE,
    OPEN_ADDON_MANAGER,
    CONFIRM_GECKOVIEW_SWITCH
}

internal fun extensionTabEntryAction(
    engine: BrowserTabEngine,
    isPrivate: Boolean
): ExtensionTabEntryAction = when {
    isPrivate -> ExtensionTabEntryAction.PRIVATE_TAB_UNAVAILABLE
    engine == BrowserTabEngine.GECKOVIEW -> ExtensionTabEntryAction.OPEN_ADDON_MANAGER
    else -> ExtensionTabEntryAction.CONFIRM_GECKOVIEW_SWITCH
}

/** Only pages the Gecko tab provider can safely load are eligible for in-place switching. */
internal fun isSupportedGeckoTabUrl(url: String): Boolean =
    url.equals("about:blank", ignoreCase = true) ||
        url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)
