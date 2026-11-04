package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GeckoTabMigrationPolicyTest {
    @Test
    fun restorableSnapshotPreservesEngineAndActiveIndexAfterFilteringPrivateTabs() {
        val (tabs, activeIndex) = buildRestorableSnapshot(
            tabStates = listOf(
                RestorableTabState(
                    url = "https://example.com/",
                    title = "WebView tab",
                    isIncognito = false,
                    engine = BrowserTabEngine.WEBVIEW
                ),
                RestorableTabState(
                    url = "https://private.example/",
                    title = "Private tab",
                    isIncognito = true,
                    engine = BrowserTabEngine.WEBVIEW
                ),
                RestorableTabState(
                    url = "https://gecko.example/",
                    title = "Gecko tab",
                    isIncognito = false,
                    engine = BrowserTabEngine.GECKOVIEW
                ),
                RestorableTabState(
                    url = "about:blank",
                    title = "Blank",
                    isIncognito = false,
                    engine = BrowserTabEngine.GECKOVIEW
                )
            ),
            activeIdx = 2
        )

        assertEquals(2, tabs.size)
        assertEquals("https://example.com/", tabs[0].url)
        assertEquals(BrowserTabEngine.WEBVIEW, tabs[0].engine)
        assertEquals("https://gecko.example/", tabs[1].url)
        assertEquals(BrowserTabEngine.GECKOVIEW, tabs[1].engine)
        assertEquals(1, activeIndex)
    }

    @Test
    fun oldSavedTabRecordsDefaultToGeckoView() {
        assertEquals(
            BrowserTabEngine.GECKOVIEW,
            SavedTab(url = "https://legacy.example/", title = "Legacy").engine
        )
    }

    @Test
    fun regularTabsDefaultToGeckoAndPrivateTabsRemainWebView() {
        assertEquals(BrowserTabEngine.GECKOVIEW, defaultBrowserTabEngine(isIncognito = false))
        assertEquals(BrowserTabEngine.WEBVIEW, defaultBrowserTabEngine(isIncognito = true))
    }

    @Test
    fun legacyNormalWebViewSessionRestoresInGeckoView() {
        assertEquals(
            BrowserTabEngine.GECKOVIEW,
            migrateRestoredRegularTabEngine(BrowserTabEngine.WEBVIEW)
        )
        assertEquals(
            BrowserTabEngine.GECKOVIEW,
            migrateRestoredRegularTabEngine(BrowserTabEngine.GECKOVIEW)
        )
    }

    @Test
    fun geckoContextsMatchExistingProfileSharingAndIsolation() {
        val defaultProfile = geckoContextIdFor(BrowserMode.SIMPLE)
        assertEquals(defaultProfile, geckoContextIdFor(BrowserMode.DEVELOPER))
        assertEquals(defaultProfile, geckoContextIdFor(BrowserMode.HACK))
        assertEquals(defaultProfile, geckoContextIdFor(BrowserMode.SIMPLE))
        assertNotEquals(defaultProfile, geckoContextIdFor(BrowserMode.ADVANCED))
    }
}
