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
    fun findInPageVisibilityMatchesTheModeCapabilityRegistry() {
        val defaultItems = MenuCustomization.effectiveVisibleItems(
            MenuCustomization.DEFAULT_ORDER,
            emptySet()
        )

        BrowserMode.values().forEach { mode ->
            val visibleItems = MenuCustomization.filterItemsForMode(defaultItems, mode)
            assertEquals(
                "Find in page visibility in $mode",
                ModeCapabilityRegistry.supportsMode(FeatureId.FIND_IN_PAGE, mode),
                MenuCustomization.MenuItemId.FIND_IN_PAGE in visibleItems
            )
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
