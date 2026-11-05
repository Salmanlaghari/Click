package com.click.browser.extensions

/** Plain-language explanations for WebExtension behavior and Click policy outcomes. */
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

    const val installPermissionConsent =
        "Tap Add to grant all browser permissions and site patterns listed below for this add-on. Cancel denies the installation. Optional permissions are not granted now; GeckoView will show a separate request if the add-on asks for them later. Add-ons may read or change listed pages and may process or send data under their own code and privacy terms."

    const val optionalPermissionConsent =
        "This is a separate optional request shown when the add-on asks for access. Allow grants every item listed in this request; Cancel denies it. GeckoView makes this request all-or-nothing, so multiple sites in one request cannot be split into individual site choices. Future requests will be shown separately."

    private val highImpactPermissions = setOf(
        "cookies", "history", "browsingdata", "webrequest", "webrequestblocking",
        "webrequestfilterresponse", "webrequestauthprovider", "declarativenetrequest",
        "declarativenetrequestfeedback", "declarativenetrequestwithhostaccess", "proxy", "tabs",
        "webnavigation", "clipboardread", "management", "privacy", "bookmarks",
        "downloads", "identity", "scripting", "debugger", "pkcs11", "geolocation",
        "nativemessaging", "nativemessagingfromcontent", "geckoviewaddons"
    )

    fun permissionDisclosure(permission: String): String {
        val explanation = when (permission.trim().substringAfterLast('.').lowercase()) {
            "activetab" -> "temporary access to the current tab after you invoke the add-on"
            "tabs" -> "read details about open tabs, including their URLs and titles"
            "scripting" -> "run the add-on's scripts on sites it is allowed to access"
            "storage" -> "store this add-on's settings or data in its browser profile"
            "unlimitedstorage" -> "store a larger amount of this add-on's data in its browser profile"
            "cookies" -> "read or change cookies for the sites listed under Website access"
            "history" -> "read browsing history"
            "bookmarks" -> "read or change bookmarks"
            "downloads" -> "view or manage downloads"
            "webrequest" -> "observe network requests for permitted sites"
            "webrequestblocking" -> "block or change network requests for permitted sites"
            "webrequestfilterresponse" -> "inspect or filter network responses for permitted sites"
            "webrequestauthprovider" -> "handle authentication requests from permitted sites"
            "declarativenetrequest" -> "apply network-request rules, such as blocking or redirecting requests"
            "declarativenetrequestfeedback" -> "receive feedback about network requests matched by rules"
            "declarativenetrequestwithhostaccess" -> "apply network-request rules with access to permitted sites"
            "proxy" -> "change browser proxy settings"
            "browsingdata" -> "view or clear browsing data such as history and cookies"
            "management" -> "view or manage other browser add-ons"
            "privacy" -> "read or change browser privacy settings"
            "clipboardread" -> "read clipboard contents"
            "clipboardwrite" -> "write to the clipboard"
            "notifications" -> "show browser notifications"
            "identity" -> "use browser identity and sign-in features"
            "webnavigation" -> "observe browser navigation events"
            "topsites" -> "read frequently visited sites"
            "browsersettings" -> "read or change browser settings"
            "contentsettings" -> "read or change site content settings"
            "contextmenus", "menus" -> "add items to browser context menus"
            "alarms" -> "schedule background alarms for this add-on"
            "sessions" -> "access recently closed tabs or windows"
            "find" -> "search text in open pages"
            "pagecapture" -> "capture page content"
            "devtools" -> "integrate with developer tools"
            "debugger" -> "debug or inspect browser and page activity"
            "pkcs11" -> "use cryptographic module and key functions where supported"
            "geolocation" -> "request device location; this capability is blocked in Click"
            "nativemessaging", "nativemessagingfromcontent" -> "communicate with native app code; this capability is blocked in Click"
            "geckoviewaddons" -> "install or manage more add-ons; this capability is blocked in Click"
            else -> "browser capability requested by the add-on"
        }
        return "$permission — $explanation"
    }

    fun originDisclosure(pattern: String): String {
        val normalized = pattern.trim().lowercase()
        if (normalized == "<all_urls>") {
            return "$pattern — all URLs supported by GeckoView; this is very broad and may include insecure or local pages"
        }
        val scheme = normalized.substringBefore("://", "")
        val host = normalized.substringAfter("://", "").substringBefore('/')
        val detail = when {
            scheme == "*" && host == "*" -> "all HTTP and HTTPS websites; HTTP connections are unencrypted"
            host == "*" && scheme == "http" -> "all hosts over unencrypted HTTP"
            host == "*" -> "all hosts using $scheme"
            scheme == "*" -> "the specified host and path over both HTTP and HTTPS"
            host.startsWith("*.") -> "the named domain and its subdomains using $scheme"
            scheme == "file" -> "local file URLs, if supported by GeckoView"
            else -> "the specified host and path using $scheme"
        }
        val qualifiers = buildList {
            if (scheme == "http") add("the connection is unencrypted")
            if (isLocalOrPrivateSiteHost(host.removePrefix("*."))) add("the host appears to be on a local or private network")
        }
        val qualifierText = qualifiers.takeIf { it.isNotEmpty() }?.joinToString("; ")?.let { " ($it)" }.orEmpty()
        return "$pattern — $detail$qualifierText"
    }

    private fun isLocalOrPrivateSiteHost(host: String): Boolean {
        val normalized = host.trimEnd('.').removeSurrounding("[", "]")
        val labels = normalized.split('.').mapNotNull(String::toIntOrNull)
        val privateIpv4 = labels.size == 4 && labels.all { it in 0..255 } && (
            labels[0] == 10 || labels[0] == 127 || labels[0] == 169 && labels[1] == 254 ||
                labels[0] == 192 && labels[1] == 168 || labels[0] == 172 && labels[1] in 16..31
            )
        val localIpv6 = normalized.startsWith("::1") || normalized.startsWith("fc", ignoreCase = true) ||
            normalized.startsWith("fd", ignoreCase = true) || normalized.startsWith("fe80", ignoreCase = true)
        return normalized.equals("localhost", ignoreCase = true) ||
            normalized.endsWith(".localhost", ignoreCase = true) ||
            normalized.endsWith(".local", ignoreCase = true) ||
            normalized.endsWith(".lan", ignoreCase = true) ||
            normalized.endsWith(".internal", ignoreCase = true) ||
            normalized.endsWith(".home", ignoreCase = true) ||
            normalized.endsWith(".home.arpa", ignoreCase = true) ||
            normalized.endsWith(".arpa", ignoreCase = true) ||
            normalized.endsWith(".localdomain", ignoreCase = true) || privateIpv4 || localIpv6
    }

    fun permissionRiskWarning(permissions: List<String>): String? {
        val highImpact = permissions.filter {
            it.trim().substringAfterLast('.').lowercase() in highImpactPermissions
        }.distinct().sorted()
        if (highImpact.isEmpty()) return null
        return "Sensitive browser access requested: ${highImpact.joinToString()}. If you Add or Allow, these capabilities are granted with the other items in this request; review each description first."
    }

    fun siteAccessRiskWarning(origins: List<String>): String? {
        val distinct = origins.map(String::trim).filter(String::isNotEmpty).distinct()
        val broad = ExtensionInstallPolicy.broadOriginPatterns(distinct)
        val elevated = distinct.filter { pattern ->
            val normalized = pattern.lowercase()
            val scheme = normalized.substringBefore("://", "")
            val host = normalized.substringAfter("://", "").substringBefore('/')
            pattern in broad || scheme == "http" || scheme == "file" ||
                isLocalOrPrivateSiteHost(host.removePrefix("*."))
        }.distinct().sorted()
        if (elevated.isEmpty()) return null
        val cautions = buildList {
            if (elevated.any { it.lowercase().startsWith("http://") || it.lowercase().startsWith("*://") || it.equals("<all_urls>", true) }) {
                add("HTTP access may be unencrypted")
            }
            if (elevated.any { it.lowercase().startsWith("file://") }) add("file access can expose local documents")
            if (elevated.any { pattern ->
                    val host = pattern.lowercase().substringAfter("://", "").substringBefore('/').removePrefix("*.")
                    isLocalOrPrivateSiteHost(host)
                }
            ) add("local/private-network sites may include routers or intranet pages")
            if (broad.isNotEmpty()) add("wildcard patterns may cover many sites")
        }.joinToString("; ")
        return "Elevated site access requested: ${elevated.joinToString()}. $cautions. Add or Allow grants access to the listed patterns; the add-on may read or change those pages and process or send their contents under its own code and privacy terms."
    }

    fun blockedCatalogInstallStatus(
        blockedPermissions: List<String>,
        blockedOrigins: List<String>
    ): String {
        val requestedAccess = buildList {
            blockedPermissions.takeIf { it.isNotEmpty() }?.let {
                add("unsupported browser capabilities: ${it.joinToString()}")
            }
            blockedOrigins.takeIf { it.isNotEmpty() }?.let {
                add("site access patterns Click cannot review: ${it.joinToString()}")
            }
        }.joinToString("; ").ifBlank { "a capability Click does not expose" }

        return "Install stopped by Click's safety checks. This add-on requests $requestedAccess. " +
            "Nothing was installed; this capability cannot be approved in Click."
    }
}
