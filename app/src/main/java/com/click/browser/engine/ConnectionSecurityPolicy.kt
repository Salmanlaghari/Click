package com.click.browser.engine

import java.net.URI
import java.util.Locale

enum class ConnectionSecurityStatus {
    CHECKING,
    SECURE_HTTPS,
    SECURE_WITH_MIXED_CONTENT,
    UNENCRYPTED_HTTP,
    CERTIFICATE_WARNING,
    UNKNOWN
}

data class ConnectionSecurityInfo(
    val pageKey: String? = null,
    val status: ConnectionSecurityStatus = ConnectionSecurityStatus.UNKNOWN,
    val certificateSubject: String? = null,
    val certificateIssuer: String? = null,
    val detail: String? = null
)

/** Maps engine-reported facts to UI state; URL scheme alone never produces a secure state. */
object ConnectionSecurityPolicy {
    fun checking(url: String?): ConnectionSecurityInfo =
        ConnectionSecurityInfo(pageKey(url), ConnectionSecurityStatus.CHECKING)

    fun unknown(url: String?, detail: String? = null): ConnectionSecurityInfo =
        ConnectionSecurityInfo(
            pageKey = pageKey(url),
            status = ConnectionSecurityStatus.UNKNOWN,
            detail = detail
        )

    /** Do not reuse evidence from another page after navigation, except for fragment-only changes. */
    fun forPage(url: String?, info: ConnectionSecurityInfo): ConnectionSecurityInfo {
        val currentPageKey = pageKey(url)
        return if (currentPageKey != null && currentPageKey == info.pageKey) info else unknown(url)
    }

    /** Gecko reports an origin, not a path; reject security callbacks from a different active origin. */
    fun originMatchesPage(pageUrl: String?, reportedOrigin: String?): Boolean {
        val pageOrigin = originKey(pageUrl) ?: return false
        return pageOrigin == originKey(reportedOrigin)
    }

    /** A later generic load failure must not erase an SSL/certificate warning for that same page. */
    fun afterWebViewLoadError(
        pageUrl: String?,
        current: ConnectionSecurityInfo
    ): ConnectionSecurityInfo {
        val currentPageKey = pageKey(pageUrl)
        return if (current.status == ConnectionSecurityStatus.CERTIFICATE_WARNING &&
            currentPageKey != null && current.pageKey == currentPageKey
        ) {
            current
        } else {
            unknown(
                pageUrl,
                "The page failed to load, so its connection security could not be confirmed."
            )
        }
    }

    fun fromWebView(
        url: String?,
        pageFinished: Boolean,
        certificatePresent: Boolean,
        sslErrorObserved: Boolean,
        certificateSubject: String? = null,
        certificateIssuer: String? = null
    ): ConnectionSecurityInfo {
        val scheme = scheme(url)
        val status = when {
            sslErrorObserved -> ConnectionSecurityStatus.CERTIFICATE_WARNING
            !pageFinished -> ConnectionSecurityStatus.CHECKING
            scheme == "http" -> ConnectionSecurityStatus.UNENCRYPTED_HTTP
            scheme == "https" && certificatePresent -> ConnectionSecurityStatus.SECURE_HTTPS
            else -> ConnectionSecurityStatus.UNKNOWN
        }
        return ConnectionSecurityInfo(
            pageKey = pageKey(url),
            status = status,
            certificateSubject = certificateSubject.takeUnless { status != ConnectionSecurityStatus.SECURE_HTTPS },
            certificateIssuer = certificateIssuer.takeUnless { status != ConnectionSecurityStatus.SECURE_HTTPS },
            detail = if (sslErrorObserved) "WebView reported a TLS certificate error." else null
        )
    }

    fun fromGeckoView(
        url: String?,
        isSecure: Boolean,
        isException: Boolean,
        mixedActiveContentLoaded: Boolean,
        mixedPassiveContentLoaded: Boolean,
        certificateSubject: String? = null,
        certificateIssuer: String? = null
    ): ConnectionSecurityInfo {
        val scheme = scheme(url)
        val mixedContentLoaded = mixedActiveContentLoaded || mixedPassiveContentLoaded
        val status = when {
            isException -> ConnectionSecurityStatus.CERTIFICATE_WARNING
            scheme == "https" && isSecure && mixedContentLoaded ->
                ConnectionSecurityStatus.SECURE_WITH_MIXED_CONTENT
            scheme == "https" && isSecure -> ConnectionSecurityStatus.SECURE_HTTPS
            scheme == "http" -> ConnectionSecurityStatus.UNENCRYPTED_HTTP
            else -> ConnectionSecurityStatus.UNKNOWN
        }
        val hasCertificateStatus = status == ConnectionSecurityStatus.SECURE_HTTPS ||
            status == ConnectionSecurityStatus.SECURE_WITH_MIXED_CONTENT ||
            status == ConnectionSecurityStatus.CERTIFICATE_WARNING
        return ConnectionSecurityInfo(
            pageKey = pageKey(url),
            status = status,
            certificateSubject = certificateSubject.takeIf { hasCertificateStatus },
            certificateIssuer = certificateIssuer.takeIf { hasCertificateStatus },
            detail = when {
                isException -> "GeckoView reported a certificate exception."
                status == ConnectionSecurityStatus.UNKNOWN -> "The engine did not confirm a secure connection."
                else -> null
            }
        )
    }

    fun title(status: ConnectionSecurityStatus): String = when (status) {
        ConnectionSecurityStatus.CHECKING -> "Checking connection security"
        ConnectionSecurityStatus.SECURE_HTTPS -> "Secure HTTPS connection"
        ConnectionSecurityStatus.SECURE_WITH_MIXED_CONTENT -> "HTTPS with mixed content"
        ConnectionSecurityStatus.UNENCRYPTED_HTTP -> "HTTP — not encrypted"
        ConnectionSecurityStatus.CERTIFICATE_WARNING -> "Certificate warning"
        ConnectionSecurityStatus.UNKNOWN -> "Connection status unavailable"
    }

    fun description(status: ConnectionSecurityStatus): String = when (status) {
        ConnectionSecurityStatus.CHECKING ->
            "The browser is still checking this page's connection."
        ConnectionSecurityStatus.SECURE_HTTPS ->
            "The browser reports a secure HTTPS connection for this page. This does not assess every third-party resource."
        ConnectionSecurityStatus.SECURE_WITH_MIXED_CONTENT ->
            "The top-level page uses HTTPS, but the engine reports that mixed HTTP content was loaded."
        ConnectionSecurityStatus.UNENCRYPTED_HTTP ->
            "This page was loaded over HTTP; data sent to the site is not encrypted in transit."
        ConnectionSecurityStatus.CERTIFICATE_WARNING ->
            "The browser reported a certificate problem or exception. Do not treat this connection as verified."
        ConnectionSecurityStatus.UNKNOWN ->
            "The browser could not confirm the connection security for this page."
    }

    private fun scheme(url: String?): String? = runCatching {
        URI(url ?: return null).scheme?.lowercase(Locale.ROOT)
    }.getOrNull()

    private fun pageKey(url: String?): String? = runCatching {
        val uri = URI(url ?: return null)
        val origin = originKey(uri) ?: return null
        val path = uri.rawPath?.ifEmpty { "/" } ?: "/"
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        "$origin$path$query"
    }.getOrNull()

    private fun originKey(url: String?): String? = runCatching {
        originKey(URI(url ?: return null))
    }.getOrNull()

    private fun originKey(uri: URI): String? {
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = (uri.host ?: uri.rawAuthority?.substringAfterLast('@'))
            ?.lowercase(Locale.ROOT)
            ?: return null
        val defaultPort = when (scheme) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
        val port = uri.port
        val portPart = if (port == -1 || port == defaultPort) "" else ":$port"
        return "$scheme://$host$portPart"
    }
}
