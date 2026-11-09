package com.click.browser.ui.screens

import com.click.browser.engine.ConnectionSecurityInfo
import com.click.browser.engine.ConnectionSecurityPolicy
import com.click.browser.engine.ConnectionSecurityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyDashboardPolicyTest {
    @Test
    fun currentPageSummaryUsesEngineEvidenceAndExposesOnlyTheHost() {
        val url = "https://alice:secret@example.com/private/path?session=private#section"
        val evidence = ConnectionSecurityPolicy.fromWebView(
            url = url,
            pageFinished = true,
            certificatePresent = true,
            sslErrorObserved = false
        )

        val summary = PrivacyDashboardPolicy.currentPage(url, evidence)

        assertEquals("example.com", summary?.host)
        assertEquals(ConnectionSecurityStatus.SECURE_HTTPS, summary?.connectionStatus)
        assertTrue(summary?.connectionTitle.orEmpty().contains("HTTPS"))
        assertFalse(summary.toString().contains("alice"))
        assertFalse(summary.toString().contains("secret"))
        assertFalse(summary.toString().contains("private/path"))
        assertFalse(summary.toString().contains("session=private"))
    }

    @Test
    fun staleConnectionEvidenceFallsBackToUnknownForTheCurrentPage() {
        val oldUrl = "https://old.example/page"
        val oldEvidence = ConnectionSecurityPolicy.fromWebView(
            url = oldUrl,
            pageFinished = true,
            certificatePresent = true,
            sslErrorObserved = false
        )

        val summary = PrivacyDashboardPolicy.currentPage("https://new.example/", oldEvidence)

        assertEquals("new.example", summary?.host)
        assertEquals(ConnectionSecurityStatus.UNKNOWN, summary?.connectionStatus)
        assertEquals("Connection status unavailable", summary?.connectionTitle)
    }

    @Test
    fun internalAndMalformedPagesHaveNoWebsiteSecuritySummary() {
        assertNull(PrivacyDashboardPolicy.currentPage("about:blank", ConnectionSecurityInfo()))
        assertNull(PrivacyDashboardPolicy.currentPage("click://vpn", ConnectionSecurityInfo()))
        assertNull(PrivacyDashboardPolicy.currentPage("https:///missing-host", ConnectionSecurityInfo()))
    }

    @Test
    fun websitePermissionDisclosureKeepsMicrophoneOnlyScope() {
        val disclosure = PrivacyDashboardPolicy.WEBSITE_PERMISSION_SCOPE_DESCRIPTION

        assertTrue(disclosure.contains("per-site consent first"))
        assertTrue(disclosure.contains("RECORD_AUDIO"))
        assertTrue(disclosure.contains("Camera, location, and website notifications remain blocked"))
        assertFalse(disclosure.contains("camera permission"))
    }
}
