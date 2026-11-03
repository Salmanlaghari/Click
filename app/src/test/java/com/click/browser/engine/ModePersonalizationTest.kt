package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModePersonalizationTest {
    @Test
    fun firstRunSearchDefaultsAreDistinctAndOfferedInEachMode() {
        val modes = BrowserMode.values().toList()
        val defaults = modes.map(ModePersonalization::defaultSearchEngine)

        assertEquals("Each mode should have its own first-run search provider", modes.size, defaults.distinct().size)
        modes.forEach { mode ->
            assertEquals(ModePersonalization.defaultSearchEngine(mode), ModePersonalization.searchEngines(mode).first())
        }
        assertEquals("Google", ModePersonalization.defaultSearchEngine(BrowserMode.SIMPLE))
        assertEquals("DuckDuckGo", ModePersonalization.defaultSearchEngine(BrowserMode.DEVELOPER))
        assertEquals("Brave Search", ModePersonalization.defaultSearchEngine(BrowserMode.HACK))
        assertEquals("Perplexity", ModePersonalization.defaultSearchEngine(BrowserMode.ADVANCED))
    }

    @Test
    fun advanceDefaultsEmphasizeResearchWithoutRemovingCustomizationSupport() {
        val sites = ModePersonalization.defaultQuickSites(BrowserMode.ADVANCED)

        assertTrue(sites.any { it.label == "arXiv" && it.url == "https://arxiv.org" })
        assertTrue(sites.any { it.label == "Perplexity" && it.url == "https://www.perplexity.ai" })
        assertEquals(
            "https://www.perplexity.ai/search?q=AI+research",
            ModePersonalization.searchUrl("Perplexity", "AI+research")
        )
    }
}
