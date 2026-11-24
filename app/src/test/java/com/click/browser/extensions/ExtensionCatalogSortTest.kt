package com.click.browser.extensions

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionCatalogSortTest {

    private fun entry(name: String, recommended: Boolean = false) = FirefoxAmoCatalogEntry(
        slug = name.lowercase().replace(' ', '-'),
        name = name,
        summary = "summary",
        version = "1.0",
        minimumAndroidVersion = "10",
        listingUrl = "https://addons.mozilla.org/firefox/addon/x/",
        iconUrl = "",
        iconResource = 0,
        isRecommended = recommended,
        requiredPermissions = emptyList()
    )

    @Test
    fun recommendedFloatsFirstThenAlphabetical() {
        val sorted = ExtensionCatalogSort.RECOMMENDED.apply(
            listOf(entry("Zebra"), entry("alpha", recommended = true), entry("Beta", recommended = true), entry("apple"))
        )
        assertEquals(listOf("alpha", "Beta", "apple", "Zebra"), sorted.map { it.name })
    }

    @Test
    fun nameSortsAreCaseInsensitive() {
        val entries = listOf(entry("zebra"), entry("Apple"), entry("mango"))
        assertEquals(listOf("Apple", "mango", "zebra"), ExtensionCatalogSort.NAME_ASC.apply(entries).map { it.name })
        assertEquals(listOf("zebra", "mango", "Apple"), ExtensionCatalogSort.NAME_DESC.apply(entries).map { it.name })
    }
}
