package com.click.browser.engine

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
}
