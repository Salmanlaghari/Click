package com.click.browser.ui.screens

import com.click.browser.engine.FeatureId
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureCatalogCapabilityTest {
    @Test
    fun everyRegisteredFeatureHasExactlyOneSharedCatalogShortcut() {
        assertEquals(FeatureId.values().toSet(), ALL_FEATURES.map { it.id }.toSet())
        assertEquals(ALL_FEATURES.size, ALL_FEATURES.map { it.id }.distinct().size)
    }
}
