package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuCustomizationTest {

    @Test
    fun addBookmarkIsVisibleByDefaultAndAppendedForOlderSavedOrders() {
        val addBookmark = MenuCustomization.MenuItemId.ADD_BOOKMARK
        assertTrue(MenuCustomization.DEFAULT_ORDER.contains(addBookmark))

        val legacyOrder = MenuCustomization.DEFAULT_ORDER.filterNot { it == addBookmark }
        assertTrue(MenuCustomization.effectiveVisibleItems(legacyOrder, emptySet()).contains(addBookmark))
        assertFalse(MenuCustomization.effectiveVisibleItems(legacyOrder, setOf(addBookmark)).contains(addBookmark))
    }

    @Test
    fun modeScopedMenuItemsFollowTheirDeclaredCapability() {
        val modeScopedItems = MenuCustomization.MenuItemId.values()
            .filter { it.modeFeature != null }
        val findInPageFeature = requireNotNull(MenuCustomization.MenuItemId.FIND_IN_PAGE.modeFeature)
        assertEquals(FeatureId.FIND_IN_PAGE, findInPageFeature)
        assertTrue(modeScopedItems.contains(MenuCustomization.MenuItemId.FIND_IN_PAGE))

        val defaultItems = MenuCustomization.effectiveVisibleItems(
            MenuCustomization.DEFAULT_ORDER,
            emptySet()
        )

        BrowserMode.values().forEach { mode ->
            val visibleItems = MenuCustomization.filterItemsForMode(defaultItems, mode)
            assertEquals(
                "Find in page visibility in $mode",
                ModeCapabilityRegistry.supportsMode(findInPageFeature, mode),
                MenuCustomization.MenuItemId.FIND_IN_PAGE in visibleItems
            )

            modeScopedItems.forEach { item ->
                val feature = requireNotNull(item.modeFeature)
                assertEquals(
                    "$item visibility in $mode",
                    ModeCapabilityRegistry.supportsMode(feature, mode),
                    item in MenuCustomization.filterItemsForMode(listOf(item), mode)
                )
            }
        }
    }

    @Test
    fun modeFilteringPreservesCustomizedOrderAndHiddenSelections() {
        val storedOrder = listOf(
            MenuCustomization.MenuItemId.SHARE,
            MenuCustomization.MenuItemId.FIND_IN_PAGE,
            MenuCustomization.MenuItemId.SETTINGS,
            MenuCustomization.MenuItemId.NEW_TAB
        )
        val hidden = setOf(MenuCustomization.MenuItemId.SHARE)
        val customizedItems = MenuCustomization.effectiveVisibleItems(storedOrder, hidden)
        val powerItems = MenuCustomization.filterItemsForMode(customizedItems, BrowserMode.HACK)
        val simpleItems = MenuCustomization.filterItemsForMode(customizedItems, BrowserMode.SIMPLE)

        assertEquals(customizedItems, powerItems)
        assertEquals(
            customizedItems.filterNot { it == MenuCustomization.MenuItemId.FIND_IN_PAGE },
            simpleItems
        )
        assertFalse(simpleItems.contains(MenuCustomization.MenuItemId.SHARE))
    }
}
