package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeCapabilityRegistryTest {
    @Test
    fun developerOnlyDevToolsIsHiddenOutsideDeveloperMode() {
        BrowserMode.values().filterNot { it == BrowserMode.DEVELOPER }.forEach { mode ->
            val access = ModeCapabilityRegistry.availability(
                FeatureId.DEVTOOLS,
                mode,
                BrowserTabEngine.WEBVIEW
            )
            assertFalse("DevTools should be hidden in $mode", access.visible)
            assertFalse("DevTools must not be enabled in $mode", access.enabled)
        }

        val developer = ModeCapabilityRegistry.availability(
            FeatureId.DEVTOOLS,
            BrowserMode.DEVELOPER,
            BrowserTabEngine.WEBVIEW
        )
        assertTrue(developer.visible)
        assertTrue(developer.enabled)
    }

    @Test
    fun developerDevToolsOnRegularGeckoTabIsVisibleButDisabledWithReason() {
        val access = ModeCapabilityRegistry.availability(
            FeatureId.DEVTOOLS,
            BrowserMode.DEVELOPER,
            BrowserTabEngine.GECKOVIEW
        )

        assertTrue(access.visible)
        assertFalse(access.enabled)
        assertNotNull(access.explanation)
        assertTrue(access.explanation!!.contains("WebView"))
        assertTrue(access.explanation.contains("GeckoView"))
    }

    @Test
    fun developerDesktopShortcutIsEnabledOnGeckoView() {
        val access = ModeCapabilityRegistry.availability(
            FeatureId.DESKTOP,
            BrowserMode.DEVELOPER,
            BrowserTabEngine.GECKOVIEW
        )

        assertTrue(access.visible)
        assertTrue(access.enabled)
        assertEquals(null, access.explanation)
    }

    @Test
    fun allWebViewOnlyActionsAreDisabledForEveryVisibleGeckoMode() {
        ModeCapabilityRegistry.webViewOnlyFeatures.forEach { feature ->
            BrowserMode.values().forEach { mode ->
                val access = ModeCapabilityRegistry.availability(
                    feature,
                    mode,
                    BrowserTabEngine.GECKOVIEW
                )
                if (access.visible) {
                    assertFalse("$feature must be disabled on GeckoView in $mode", access.enabled)
                    assertNotNull("$feature needs a visible explanation", access.explanation)
                }
                if (ModeCapabilityRegistry.supportsMode(feature, mode)) {
                    val webView = ModeCapabilityRegistry.availability(
                        feature,
                        mode,
                        BrowserTabEngine.WEBVIEW
                    )
                    assertTrue("$feature should be enabled on WebView in $mode", webView.enabled)
                }
            }
        }
    }

    @Test
    fun modeSpecificShortcutsMatchTheFourModeProductScopes() {
        assertEquals(
            setOf(BrowserMode.DEVELOPER),
            BrowserMode.values().filter { ModeCapabilityRegistry.supportsMode(FeatureId.DEVTOOLS, it) }.toSet()
        )
        assertEquals(
            setOf(BrowserMode.DEVELOPER, BrowserMode.HACK),
            BrowserMode.values().filter { ModeCapabilityRegistry.supportsMode(FeatureId.DESKTOP, it) }.toSet()
        )
        assertEquals(
            setOf(BrowserMode.DEVELOPER, BrowserMode.HACK),
            BrowserMode.values().filter { ModeCapabilityRegistry.supportsMode(FeatureId.FIND_IN_PAGE, it) }.toSet()
        )
        assertEquals(
            setOf(BrowserMode.HACK),
            BrowserMode.values().filter { ModeCapabilityRegistry.supportsMode(FeatureId.RECENT_TABS, it) }.toSet()
        )
        assertEquals(
            setOf(BrowserMode.ADVANCED),
            BrowserMode.values().filter { ModeCapabilityRegistry.supportsMode(FeatureId.COOKIES, it) }.toSet()
        )
    }

    @Test
    fun devToolsRequiresARealLoadedPageAndDoesNotAdvertiseEngineFallback() {
        val blank = ModeCapabilityRegistry.availability(
            FeatureId.DEVTOOLS,
            BrowserMode.DEVELOPER,
            BrowserTabEngine.WEBVIEW,
            hasLoadedPage = false
        )
        assertTrue(blank.visible)
        assertFalse(blank.enabled)
        assertEquals("Load a web page before opening DevTools.", blank.explanation)

        val loaded = ModeCapabilityRegistry.availability(
            FeatureId.DEVTOOLS,
            BrowserMode.DEVELOPER,
            BrowserTabEngine.WEBVIEW,
            hasLoadedPage = true
        )
        assertTrue(loaded.enabled)
    }

    @Test
    fun everyFeatureIdAppearsInAtLeastOneMode() {
        FeatureId.values().forEach { feature ->
            assertTrue(
                "$feature must be in at least one mode catalog",
                BrowserMode.values().any { ModeCapabilityRegistry.supportsMode(feature, it) }
            )
        }
    }

    @Test
    fun everyFeatureModeAndEngineCombinationHasConsistentVisibilityAndSupport() {
        FeatureId.values().forEach { feature ->
            BrowserMode.values().forEach { mode ->
                BrowserTabEngine.values().forEach { engine ->
                    val access = ModeCapabilityRegistry.availability(
                        feature = feature,
                        mode = mode,
                        engine = engine,
                        hasLoadedPage = true
                    )
                    val expectedVisible = ModeCapabilityRegistry.supportsMode(feature, mode)
                    assertEquals("$feature visibility in $mode on $engine", expectedVisible, access.visible)
                    if (!expectedVisible) {
                        assertFalse("Hidden $feature must not be enabled", access.enabled)
                    } else if (engine == BrowserTabEngine.GECKOVIEW &&
                        feature in ModeCapabilityRegistry.webViewOnlyFeatures
                    ) {
                        assertFalse("$feature must be disabled in GeckoView/$mode", access.enabled)
                        assertNotNull("$feature needs an explanation in GeckoView/$mode", access.explanation)
                    } else {
                        assertTrue("$feature should be enabled in $engine/$mode", access.enabled)
                    }
                }
            }
        }
    }
}
