package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
                    engine = BrowserTabEngine.GECKOVIEW,
                    geckoSessionState = "serialized-gecko-state"
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
        assertEquals("serialized-gecko-state", tabs[1].geckoSessionState)
        assertEquals(null, tabs[0].geckoSessionState)
        assertEquals(1, activeIndex)
    }

    @Test
    fun oversizedGeckoStateFallsBackToUrlAndTitleOnly() {
        val oversized = "x".repeat(SessionRestore.MAX_GECKO_STATE_CHARS + 1)
        val (tabs, _) = buildRestorableSnapshot(
            tabStates = listOf(
                RestorableTabState(
                    url = "https://example.com/",
                    title = "Example",
                    isIncognito = false,
                    engine = BrowserTabEngine.GECKOVIEW,
                    geckoSessionState = oversized
                )
            ),
            activeIdx = 0
        )

        assertEquals(1, tabs.size)
        assertEquals(null, tabs.single().geckoSessionState)
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

    @Test
    fun targetBlankAllowsWebPagesAndBlankButRejectsExecutableOrExternalSchemes() {
        assertTrue(isAllowedGeckoNewWindowUri("https://example.com/path"))
        assertTrue(isAllowedGeckoNewWindowUri("http://example.org/"))
        assertTrue(isAllowedGeckoNewWindowUri("about:blank"))
        assertFalse(isAllowedGeckoNewWindowUri("javascript:alert(1)"))
        assertFalse(isAllowedGeckoNewWindowUri("file:///etc/passwd"))
        assertFalse(isAllowedGeckoNewWindowUri("intent://open"))
    }

    @Test
    fun targetBlankMustNotBypassNavigationPolicyUrlChanges() {
        assertTrue(shouldAcceptGeckoNewWindowUri("https://example.com/", "https://example.com/"))
        assertTrue(shouldAcceptGeckoNewWindowUri("about:blank", "about:blank"))
        assertFalse(shouldAcceptGeckoNewWindowUri("http://example.com/", "https://example.com/"))
        assertFalse(shouldAcceptGeckoNewWindowUri("https://example.com/?utm_source=x", "https://example.com/"))
        assertFalse(shouldAcceptGeckoNewWindowUri("javascript:alert(1)", "javascript:alert(1)"))
    }

    @Test
    fun adoptedChildSessionIsOpenedAndNavigatedOnlyByGecko() {
        assertEquals(
            GeckoSessionStartupPlan(openLocally = false, restoreSavedState = false, loadInitialUrl = false),
            geckoSessionStartupPlan(
                adoptedChildSession = true,
                hasRestorableState = true,
                initialUrl = "https://child.example/"
            )
        )
    }

    @Test
    fun ordinarySessionOpensOnceAndChoosesRestoreStateOrInitialUrl() {
        assertEquals(
            GeckoSessionStartupPlan(openLocally = true, restoreSavedState = true, loadInitialUrl = false),
            geckoSessionStartupPlan(false, true, "https://restored.example/")
        )
        assertEquals(
            GeckoSessionStartupPlan(openLocally = true, restoreSavedState = false, loadInitialUrl = true),
            geckoSessionStartupPlan(false, false, "https://new.example/")
        )
        assertEquals(
            GeckoSessionStartupPlan(openLocally = true, restoreSavedState = false, loadInitialUrl = false),
            geckoSessionStartupPlan(false, false, "about:blank")
        )
    }
}
