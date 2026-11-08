package com.click.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterListManagerTest {
    @Test
    fun cachedListWithZeroVersionIsNotUsableEvenWhenBundledVersionIsZero() {
        assertFalse(
            isUsableCachedFilterList(
                cachedVersion = 0,
                bundledVersion = 0,
                cachedDomains = setOf("ads.example")
            )
        )
    }

    @Test
    fun validCachedListAtLeastAsNewAsBundledListIsUsable() {
        assertTrue(
            isUsableCachedFilterList(
                cachedVersion = 5,
                bundledVersion = 4,
                cachedDomains = setOf("ads.example")
            )
        )
    }

    @Test
    fun staleOrEmptyCachedListIsNotUsable() {
        assertFalse(
            isUsableCachedFilterList(
                cachedVersion = 3,
                bundledVersion = 4,
                cachedDomains = setOf("ads.example")
            )
        )
        assertFalse(
            isUsableCachedFilterList(
                cachedVersion = 5,
                bundledVersion = 4,
                cachedDomains = emptySet()
            )
        )
    }
}
