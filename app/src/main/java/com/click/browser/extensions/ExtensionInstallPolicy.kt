package com.click.browser.extensions

/** Narrow guardrails for user-initiated GeckoView add-on imports. */
internal object ExtensionInstallPolicy {
    const val MAX_XPI_BYTES: Long = 30L * 1024L * 1024L

    private val blockedPermissions = setOf(
        "nativemessaging",
        "nativemessagingfromcontent",
        "geckoviewaddons",
        "proxy",
        "webrequest",
        "webrequestblocking",
        "webrequestfilterresponse",
        "declarativenetrequest",
        "declarativenetrequestfeedback",
        "declarativenetrequestwithhostaccess",
        "management",
        "privacy",
        "browsingdata",
        "cookies",
        "downloads",
        "history",
        "sessions",
        "debugger",
        "pkcs11",
        "clipboardread",
        "clipboardwrite",
        "geolocation",
        "webnavigation",
        "bookmarks",
        "identity",
        "topsites",
        "browsersettings",
        "contentsettings",
        "webrequestauthprovider",
        "devtools"
    )

    // Browsers accept several non-dotted-decimal IPv4 forms (for example 127.1 or 0x7f000001).
    // Treat any 1-4-part numeric/hex host as an IP literal; a false positive is safer than local access.
    private val browserIpv4Part = Regex("(?i)^(?:0x[0-9a-f]+|[0-9]+)$")

    private fun isNumericIpv4Literal(host: String): Boolean {
        val parts = host.split('.')
        return parts.size in 1..4 && parts.all { browserIpv4Part.matches(it) }
    }

    fun isSupportedPackageName(displayName: String?): Boolean =
        !displayName.isNullOrBlank() && displayName.trim().endsWith(".xpi", ignoreCase = true)

    fun blockedPermissionNames(requested: Iterable<String>): List<String> =
        requested
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .filter { permission ->
                permission.substringAfterLast('.').lowercase() in blockedPermissions
            }
            .distinct()
            .sorted()

    fun blockedOriginPatterns(requested: Iterable<String>): List<String> {
        val origins = requested.map(String::trim).filter(String::isNotEmpty).distinct()
        if (origins.size > 10) return listOf("More than 10 website origins requested")

        return origins.filter { pattern ->
            val uri = runCatching { java.net.URI(pattern) }.getOrNull()
            val host = uri?.host?.trimEnd('.')?.lowercase().orEmpty()
            val localOrIpHost = host == "localhost" ||
                host.endsWith(".localhost") || host.endsWith(".local") ||
                host.endsWith(".lan") || host.endsWith(".internal") || host.endsWith(".home") ||
                host == "home.arpa" || host.endsWith(".home.arpa") || host.endsWith(".arpa") ||
                host.endsWith(".localdomain") || isNumericIpv4Literal(host) || host.contains(':')
            uri == null || uri.scheme != "https" || host.isBlank() || host.contains('*') ||
                !host.contains('.') || localOrIpHost || uri.userInfo != null ||
                (uri.port != -1 && uri.port != 443)
        }.distinct().sorted()
    }

    fun isOfficialAmoListing(url: String?): Boolean {
        val uri = runCatching { url?.let { java.net.URI(it) } }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host.equals("addons.mozilla.org", ignoreCase = true) &&
            uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
            uri.path.orEmpty().contains("/firefox/addon/")
    }

    /** Resolve a manifest options page, but only within that add-on's own GeckoView base URL. */
    fun safeExtensionOptionsPageUrl(baseUrl: String?, optionsPageUrl: String?): String? {
        val base = runCatching { baseUrl?.let { java.net.URI(it).normalize() } }.getOrNull() ?: return null
        val candidate = runCatching { optionsPageUrl?.let { java.net.URI(it) } }.getOrNull() ?: return null
        if (!base.scheme.equals("moz-extension", ignoreCase = true) || base.userInfo != null || base.port != -1) {
            return null
        }
        val resolved = runCatching { base.resolve(candidate).normalize() }.getOrNull() ?: return null
        val baseHost = base.host ?: return null
        val optionsHost = resolved.host ?: return null
        val basePath = base.path.orEmpty().ifBlank { "/" }
        val optionsPath = resolved.path.orEmpty()
        if (!basePath.startsWith("/") || !optionsPath.startsWith("/")) return null
        val baseRoot = if (basePath.endsWith("/")) basePath else "$basePath/"
        val belongsToBasePath = optionsPath == basePath || optionsPath.startsWith(baseRoot)
        val staysOnExtensionOrigin = resolved.scheme.equals("moz-extension", ignoreCase = true) &&
            baseHost.equals(optionsHost, ignoreCase = true) &&
            resolved.userInfo == null &&
            resolved.port == -1
        return resolved.toASCIIString().takeIf { staysOnExtensionOrigin && belongsToBasePath }
    }
}

/** Address-bar parsing deliberately rejects non-web schemes instead of loading local or script URLs. */
internal object ExtensionBrowserInput {
    private val hostLike = Regex("^[A-Za-z0-9.-]+(?::[0-9]{1,5})?(?:/[^\\s]*)?$")

    fun resolve(rawInput: String): String? {
        val input = rawInput.trim()
        if (input.isEmpty()) return null
        if (input.startsWith("https://", ignoreCase = true) || input.startsWith("http://", ignoreCase = true)) {
            return input
        }
        // Reject javascript:, file:, data:, intent:, and other non-HTTP schemes.
        val isDottedHostWithPort = input.contains(':') &&
            input.substringBefore(':').contains('.') && hostLike.matches(input)
        val hasNonWebScheme = input.contains("://") ||
            (input.contains(':') && !isDottedHostWithPort &&
                input.substringBefore(':').matches(Regex("^[A-Za-z][A-Za-z0-9+.-]*$")))
        if (hasNonWebScheme) {
            return null
        }
        if (hostLike.matches(input) && input.contains('.')) return "https://$input"
        val query = java.net.URLEncoder.encode(input, Charsets.UTF_8.name())
        return "https://duckduckgo.com/?q=$query"
    }
}
