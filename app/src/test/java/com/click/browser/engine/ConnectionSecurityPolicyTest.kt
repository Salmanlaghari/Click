package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionSecurityPolicyTest {
    @Test
    fun webViewDoesNotCallAnHttpsUrlSecureWithoutALoadedCertificate() {
        val loading = ConnectionSecurityPolicy.fromWebView(
            url = "https://example.test/page",
            pageFinished = false,
            certificatePresent = false,
            sslErrorObserved = false
        )
        val missingCertificate = ConnectionSecurityPolicy.fromWebView(
            url = "https://example.test/page",
            pageFinished = true,
            certificatePresent = false,
            sslErrorObserved = false
        )

        assertEquals(ConnectionSecurityStatus.CHECKING, loading.status)
        assertEquals(ConnectionSecurityStatus.UNKNOWN, missingCertificate.status)
    }

    @Test
    fun webViewRequiresACompletedHttpsPageAndCertificateForSecureState() {
        val result = ConnectionSecurityPolicy.fromWebView(
            url = "https://example.test/path",
            pageFinished = true,
            certificatePresent = true,
            sslErrorObserved = false,
            certificateSubject = "CN=example.test",
            certificateIssuer = "CN=Example CA"
        )

        assertEquals(ConnectionSecurityStatus.SECURE_HTTPS, result.status)
        assertEquals("https://example.test/path", result.pageKey)
        assertEquals("CN=example.test", result.certificateSubject)
        assertEquals("CN=Example CA", result.certificateIssuer)
    }

    @Test
    fun webViewCertificateErrorTakesPrecedenceOverCertificatePresence() {
        val result = ConnectionSecurityPolicy.fromWebView(
            url = "https://example.test/",
            pageFinished = true,
            certificatePresent = true,
            sslErrorObserved = true
        )

        assertEquals(ConnectionSecurityStatus.CERTIFICATE_WARNING, result.status)
        assertNull(result.certificateSubject)
    }

    @Test
    fun laterWebViewLoadErrorPreservesCertificateWarningForTheSamePage() {
        val warning = ConnectionSecurityPolicy.fromWebView(
            url = "https://example.test/page",
            pageFinished = false,
            certificatePresent = false,
            sslErrorObserved = true
        )

        val afterError = ConnectionSecurityPolicy.afterWebViewLoadError(
            "https://example.test/page",
            warning
        )

        assertEquals(ConnectionSecurityStatus.CERTIFICATE_WARNING, afterError.status)
        assertEquals(warning.detail, afterError.detail)
    }

    @Test
    fun oldCertificateWarningIsNotCarriedToANewPage() {
        val warning = ConnectionSecurityPolicy.fromWebView(
            url = "https://example.test/old",
            pageFinished = false,
            certificatePresent = false,
            sslErrorObserved = true
        )

        val afterError = ConnectionSecurityPolicy.afterWebViewLoadError(
            "https://example.test/new",
            warning
        )

        assertEquals(ConnectionSecurityStatus.UNKNOWN, afterError.status)
    }

    @Test
    fun completedHttpPageIsReportedAsUnencrypted() {
        val result = ConnectionSecurityPolicy.fromWebView(
            url = "http://example.test/",
            pageFinished = true,
            certificatePresent = false,
            sslErrorObserved = false
        )

        assertEquals(ConnectionSecurityStatus.UNENCRYPTED_HTTP, result.status)
    }

    @Test
    fun geckoSecurityEvidenceDistinguishesVerifiedMixedAndExceptionStates() {
        val secure = ConnectionSecurityPolicy.fromGeckoView(
            url = "https://example.test/",
            isSecure = true,
            isException = false,
            mixedActiveContentLoaded = false,
            mixedPassiveContentLoaded = false
        )
        val mixed = ConnectionSecurityPolicy.fromGeckoView(
            url = "https://example.test/",
            isSecure = true,
            isException = false,
            mixedActiveContentLoaded = true,
            mixedPassiveContentLoaded = false
        )
        val exception = ConnectionSecurityPolicy.fromGeckoView(
            url = "https://example.test/",
            isSecure = true,
            isException = true,
            mixedActiveContentLoaded = false,
            mixedPassiveContentLoaded = false
        )

        assertEquals(ConnectionSecurityStatus.SECURE_HTTPS, secure.status)
        assertEquals(ConnectionSecurityStatus.SECURE_WITH_MIXED_CONTENT, mixed.status)
        assertEquals(ConnectionSecurityStatus.CERTIFICATE_WARNING, exception.status)
    }

    @Test
    fun geckoNeverLabelsNonHttpsPagesAsSecureHttps() {
        val http = ConnectionSecurityPolicy.fromGeckoView(
            url = "http://example.test/",
            isSecure = true,
            isException = false,
            mixedActiveContentLoaded = false,
            mixedPassiveContentLoaded = false
        )

        assertEquals(ConnectionSecurityStatus.UNENCRYPTED_HTTP, http.status)
    }

    @Test
    fun geckoHttpAndUnknownHttpsDoNotBecomeSecure() {
        val http = ConnectionSecurityPolicy.fromGeckoView(
            url = "http://example.test/",
            isSecure = false,
            isException = false,
            mixedActiveContentLoaded = false,
            mixedPassiveContentLoaded = false
        )
        val unknownHttps = ConnectionSecurityPolicy.fromGeckoView(
            url = "https://example.test/",
            isSecure = false,
            isException = false,
            mixedActiveContentLoaded = false,
            mixedPassiveContentLoaded = false
        )

        assertEquals(ConnectionSecurityStatus.UNENCRYPTED_HTTP, http.status)
        assertEquals(ConnectionSecurityStatus.UNKNOWN, unknownHttps.status)
    }

    @Test
    fun geckoSecurityCallbacksMustMatchTheActivePageOrigin() {
        assertTrue(
            ConnectionSecurityPolicy.originMatchesPage(
                "https://example.test/current/page",
                "https://example.test"
            )
        )
        assertFalse(
            ConnectionSecurityPolicy.originMatchesPage(
                "https://example.test/current/page",
                "https://other.test"
            )
        )
        assertFalse(
            ConnectionSecurityPolicy.originMatchesPage(
                "http://example.test/current/page",
                "https://example.test"
            )
        )
    }

    @Test
    fun oldPageEvidenceIsDiscardedAfterNavigationToAnotherPage() {
        val previous = ConnectionSecurityPolicy.fromWebView(
            url = "https://example.test/old",
            pageFinished = true,
            certificatePresent = true,
            sslErrorObserved = false
        )

        val current = ConnectionSecurityPolicy.forPage("https://example.test/new", previous)

        assertEquals(ConnectionSecurityStatus.UNKNOWN, current.status)
        assertEquals("https://example.test/new", current.pageKey)
    }

    @Test
    fun fragmentOnlyNavigationCanReuseTheSameDocumentEvidence() {
        val previous = ConnectionSecurityPolicy.fromWebView(
            url = "https://example.test/page?mode=full",
            pageFinished = true,
            certificatePresent = true,
            sslErrorObserved = false
        )

        val current = ConnectionSecurityPolicy.forPage(
            "https://example.test/page?mode=full#section",
            previous
        )

        assertEquals(ConnectionSecurityStatus.SECURE_HTTPS, current.status)
    }
}
