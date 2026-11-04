package com.click.browser.extensions

/** Narrow guardrails for user-initiated, verified Mozilla AMO add-on installs. */
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
        if (uri.scheme != "https" || !uri.host.equals("addons.mozilla.org", ignoreCase = true) ||
            uri.userInfo != null || (uri.port != -1 && uri.port != 443)
        ) return false
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val storeIndex = segments.indexOfLast { it.equals("android", true) || it.equals("firefox", true) }
        if (storeIndex < 0 || storeIndex + 2 >= segments.size ||
            !segments[storeIndex + 1].equals("addon", true)
        ) return false
        val slug = segments[storeIndex + 2]
        if (!slug.matches(Regex("(?i)^[a-z0-9][a-z0-9_~-]{0,127}$"))) return false
        val trailing = segments.drop(storeIndex + 3)
        return trailing.size <= 1 && trailing.all { it in setOf("reviews", "versions", "support", "details") }
    }

    fun officialAmoLatestXpiUrl(slug: String?): String? {
        val normalized = slug?.trim()?.lowercase().orEmpty()
        if (!normalized.matches(Regex("^[a-z0-9][a-z0-9_~-]{0,127}$"))) return null
        return "https://addons.mozilla.org/firefox/downloads/latest/$normalized/latest.xpi"
    }

    fun isOfficialAmoXpiUrl(url: String?): Boolean {
        val uri = runCatching { url?.let { java.net.URI(it) } }.getOrNull() ?: return false
        if (uri.scheme != "https" || !uri.host.equals("addons.mozilla.org", ignoreCase = true) ||
            uri.userInfo != null || (uri.port != -1 && uri.port != 443)
        ) return false
        val path = uri.path.orEmpty()
        val latestPackage = Regex("^/firefox/downloads/latest/[a-z0-9_~-]+/[^/]+\\.xpi$", RegexOption.IGNORE_CASE)
        val versionedPackage = Regex("^/firefox/downloads/file/[0-9]+/[^/]+\\.xpi$", RegexOption.IGNORE_CASE)
        return latestPackage.matches(path) || versionedPackage.matches(path)
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
        val basePath = canonicalExtensionPath(base) ?: return null
        val optionsPath = canonicalExtensionPath(resolved) ?: return null
        if (!basePath.startsWith("/") || !optionsPath.startsWith("/")) return null
        val baseRoot = if (basePath.endsWith("/")) basePath else "$basePath/"
        val belongsToBasePath = optionsPath == basePath || optionsPath.startsWith(baseRoot)
        val staysOnExtensionOrigin = resolved.scheme.equals("moz-extension", ignoreCase = true) &&
            baseHost.equals(optionsHost, ignoreCase = true) &&
            resolved.userInfo == null &&
            resolved.port == -1
        return resolved.toASCIIString().takeIf { staysOnExtensionOrigin && belongsToBasePath }
    }

    private fun canonicalExtensionPath(uri: java.net.URI): String? {
        var decodedPath = uri.rawPath?.ifBlank { "/" } ?: "/"
        repeat(3) {
            val next = runCatching {
                java.net.URLDecoder.decode(
                    decodedPath.replace("+", "%2B"),
                    Charsets.UTF_8.name()
                )
            }.getOrNull() ?: return null
            if (next == decodedPath) {
                return runCatching {
                    java.net.URI(null, null, next.replace('\\', '/'), null).normalize().path
                }.getOrNull()
            }
            decodedPath = next
        }
        if (Regex("%[0-9a-fA-F]{2}").containsMatchIn(decodedPath)) return null
        return runCatching {
            java.net.URI(null, null, decodedPath.replace('\\', '/'), null).normalize().path
        }.getOrNull()
    }

    /** Allow regular-tab navigation only within the root of an add-on whose options were opened. */
    fun isSafeExtensionOptionsNavigation(baseUrl: String?, navigationUrl: String?): Boolean {
        val base = runCatching { baseUrl?.let { java.net.URI(it).normalize() } }.getOrNull() ?: return false
        val candidate = runCatching { navigationUrl?.let { java.net.URI(it).normalize() } }.getOrNull() ?: return false
        val baseHost = base.host ?: return false
        val candidateHost = candidate.host ?: return false
        val basePath = canonicalExtensionPath(base) ?: return false
        val candidatePath = canonicalExtensionPath(candidate) ?: return false
        if (!basePath.startsWith("/") || !candidatePath.startsWith("/")) return false
        val baseRoot = if (basePath.endsWith("/")) basePath else "$basePath/"
        val belongsToBasePath = candidatePath == basePath || candidatePath.startsWith(baseRoot)
        return base.scheme.equals("moz-extension", ignoreCase = true) &&
            base.userInfo == null && base.port == -1 &&
            candidate.scheme.equals("moz-extension", ignoreCase = true) &&
            baseHost.equals(candidateHost, ignoreCase = true) &&
            candidate.userInfo == null && candidate.port == -1 && belongsToBasePath
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
