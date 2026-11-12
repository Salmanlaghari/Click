package com.click.browser.ui.screens

import com.click.browser.data.Bookmark
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.ClickDownloadRecord
import com.click.browser.engine.ClickDownloadState
import com.click.browser.engine.DownloadSettings
import com.click.browser.engine.ModePersonalization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the per-mode dashboard compositions (2026-11-12 four-mode redesign):
 * each mode's home is its own approved dashboard, not one shared list.
 */
class HomeContentSectionOrderTest {

    @Test
    fun simpleIsJustShieldSpeedDialAndContinue() {
        // Simple mockup: address bar (header) -> shield card -> big speed
        // dial -> Continue browsing. Nothing else on the dashboard.
        assertEquals(
            listOf(
                HomeContentSection.PRIVACY_STATUS,
                HomeContentSection.QUICK_SITES,
                HomeContentSection.CONTINUE_BROWSING
            ),
            homeContentSections(BrowserMode.SIMPLE)
        )
    }

    @Test
    fun advanceLeadsWithShieldAndPromotesNewsAboveContinue() {
        val sections = homeContentSections(BrowserMode.ADVANCED)
        assertEquals(HomeContentSection.PRIVACY_STATUS, sections.first())
        assertTrue(
            sections.indexOf(HomeContentSection.OPTIONAL_NEWS_FEED) <
                sections.indexOf(HomeContentSection.CONTINUE_BROWSING)
        )
        assertFalse(sections.contains(HomeContentSection.DEVELOPER_STATUS))
        assertFalse(sections.contains(HomeContentSection.DOWNLOADS))
    }

    @Test
    fun powerIsDesktopWidgetsInMockupOrder() {
        assertEquals(
            listOf(
                HomeContentSection.NEWS_WIDGET,
                HomeContentSection.DOWNLOADS,
                HomeContentSection.CONTINUE_BROWSING,
                HomeContentSection.QUICK_SITES
            ),
            homeContentSections(BrowserMode.HACK)
        )
    }

    @Test
    fun developerIsTheControlPanelStack() {
        assertEquals(
            listOf(
                HomeContentSection.DEVELOPER_STATUS,
                HomeContentSection.DEVELOPER_TOOLS,
                HomeContentSection.RECENT_ERRORS,
                HomeContentSection.PRIVACY_STATUS
            ),
            homeContentSections(BrowserMode.DEVELOPER)
        )
    }
}

class DeveloperStatusLineTest {

    @Test
    fun statusLineUsesMajorVersionAndRealValues() {
        assertEquals(
            "GeckoView 153  ·  Tabs: 3  ·  RAM 212 MB  ·  WebRTC Guard: ON",
            developerStatusLine("153.0.20251101000000", tabCount = 3, usedMemoryMb = 212, webrtcBlocked = true)
        )
        assertEquals(
            "GeckoView 153  ·  Tabs: 1  ·  RAM 96 MB  ·  WebRTC Guard: OFF",
            developerStatusLine("153.0", tabCount = 1, usedMemoryMb = 96, webrtcBlocked = false)
        )
    }

    @Test
    fun majorVersionDegradesGracefully() {
        assertEquals("153", geckoMajorVersion("153.0.1"))
        assertEquals("153", geckoMajorVersion("153"))
        assertEquals("", geckoMajorVersion(""))
    }
}

class PowerDashboardWidgetsTest {

    private fun record(
        state: ClickDownloadState,
        downloaded: Long = 0L,
        total: Long = -1L
    ) = ClickDownloadRecord(
        id = "id-$state-$downloaded",
        mode = BrowserMode.HACK,
        url = "https://example.com/file.bin",
        fileName = "file.bin",
        mimeType = "application/octet-stream",
        destination = DownloadSettings.LOCATION_APP,
        state = state,
        bytesDownloaded = downloaded,
        totalBytes = total,
        createdAt = 1L,
        updatedAt = 1L
    )

    @Test
    fun activeDownloadsAreOnlyQueuedAndRunning() {
        val active = activeDownloadRecords(
            listOf(
                record(ClickDownloadState.RUNNING),
                record(ClickDownloadState.QUEUED),
                record(ClickDownloadState.PAUSED),
                record(ClickDownloadState.COMPLETED),
                record(ClickDownloadState.FAILED)
            )
        )
        assertEquals(
            listOf(ClickDownloadState.RUNNING, ClickDownloadState.QUEUED),
            active.map { it.state }
        )
    }

    @Test
    fun progressFractionClampsAndRejectsUnknownSize() {
        assertEquals(0.5f, downloadProgressFraction(record(ClickDownloadState.RUNNING, 50, 100))!!, 0.001f)
        assertEquals(1f, downloadProgressFraction(record(ClickDownloadState.RUNNING, 150, 100))!!, 0.001f)
        assertNull(downloadProgressFraction(record(ClickDownloadState.RUNNING, 10, -1)))
        assertNull(downloadProgressFraction(record(ClickDownloadState.RUNNING, 10, 0)))
    }

    @Test
    fun percentLabelMatchesFractionOrEllipsis() {
        assertEquals("50%", downloadPercentLabel(record(ClickDownloadState.RUNNING, 1, 2)))
        assertEquals("…", downloadPercentLabel(record(ClickDownloadState.RUNNING, 1, -1)))
    }
}

class BookmarksBarItemsTest {

    @Test
    fun userBookmarksWinOverFallbackSites() {
        val items = bookmarksBarItems(
            bookmarks = listOf(
                Bookmark("Docs", "https://docs.example", "folder"),
                Bookmark("", "https://bare.example", "folder")
            ),
            fallbackSites = ModePersonalization.defaultQuickSites(BrowserMode.HACK)
        )
        assertEquals(2, items.size)
        assertEquals("Docs", items[0].label)
        // A bookmark with no title falls back to its URL as the label.
        assertEquals("https://bare.example", items[1].label)
    }

    @Test
    fun fallbackSitesUsedWhenNoBookmarksAndCapped() {
        val items = bookmarksBarItems(
            bookmarks = emptyList(),
            fallbackSites = ModePersonalization.defaultQuickSites(BrowserMode.HACK),
            limit = 3
        )
        assertEquals(3, items.size)
        assertTrue(items.all { it.url.startsWith("http") })
    }
}
