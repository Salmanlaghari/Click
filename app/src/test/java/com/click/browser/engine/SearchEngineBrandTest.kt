package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineBrandTest {
    @Test
    fun everyOfferedSearchProviderHasAVisibleBrand() {
        BrowserMode.values().flatMap(ModePersonalization::searchEngines).distinct().forEach { engine ->
            val brand = SearchEngineBrands.forEngine(engine)
            assertTrue("Missing mark for $engine", brand.monogram.isNotBlank())
            assertTrue("Missing label for $engine", brand.shortName.isNotBlank())
        }
    }

    @Test
    fun providerMarkIdentifiesSelectedEngine() {
        assertEquals("Google", SearchEngineBrands.forEngine("Google").shortName)
        assertEquals("DuckDuckGo", SearchEngineBrands.forEngine("DuckDuckGo").shortName)
        assertEquals("Brave", SearchEngineBrands.forEngine("Brave Search").shortName)
        assertEquals("Я", SearchEngineBrands.forEngine("Yandex").monogram)
    }

    @Test
    fun eachOfferedProviderRoutesToItsOwnSearchHost() {
        val expectedHosts = mapOf(
            "Google" to "www.google.com",
            "Bing" to "www.bing.com",
            "Yahoo" to "search.yahoo.com",
            "DuckDuckGo" to "duckduckgo.com",
            "Yandex" to "yandex.com",
            "Baidu" to "www.baidu.com",
            "Brave Search" to "search.brave.com",
            "Startpage" to "www.startpage.com",
            "Perplexity" to "www.perplexity.ai"
        )
        BrowserMode.values().flatMap(ModePersonalization::searchEngines).distinct().forEach { engine ->
            val url = ModePersonalization.searchUrl(engine, "click+browser")
            val expectedHost = expectedHosts[engine]
            assertNotNull("Missing expected host mapping for $engine", expectedHost)
            assertTrue("$engine did not route to its provider host: $url", url.contains(expectedHost!!))
        }
    }
}
