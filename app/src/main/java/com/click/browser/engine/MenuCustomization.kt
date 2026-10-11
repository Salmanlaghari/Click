package com.click.browser.engine

import androidx.datastore.preferences.core.stringPreferencesKey
import org.json.JSONArray

/**
 * Customizable browser menu: the user can reorder items and hide the ones
 * they never use. Order + hidden set are persisted in DataStore as JSON.
 *
 * Item IDs are stable strings (enum names) so a stored order survives
 * app updates even if new items are appended later — unknown IDs are
 * ignored and new items land at the end in default order.
 */
object MenuCustomization {

    val MENU_ORDER_JSON = stringPreferencesKey("menu_order_json")
    val MENU_HIDDEN_JSON = stringPreferencesKey("menu_hidden_json")

    enum class MenuItemId(val title: String) {
        NEW_TAB("New tab"),
        NEW_PRIVATE_TAB("New Private tab"),
        TABS("Tabs"),
        HISTORY("History"),
        DELETE_DATA("Delete browsing data"),
        DOWNLOADS("Downloads"),
        PLAYLIST("Playlist"),
        STREAM_PLAYER("Live Stream"),
        BOOKMARKS("Bookmarks"),
        GAMES("Games"),
        RECENT_TABS("Recent tabs"),
        EXTENSIONS("Extensions"),
        SHARE("Share…"),
        FIND_IN_PAGE("Find in page"),
        TRANSLATE("Translate"),
        DESKTOP_SITE("Desktop site"),
        VPN("Click VPN"),
        SETTINGS("Settings"),
    }

    val DEFAULT_ORDER: List<MenuItemId> = MenuItemId.values().toList()

    /** Items that must always stay visible (core navigation) — cannot be hidden. */
    val LOCKED_VISIBLE: Set<MenuItemId> = setOf(MenuItemId.SETTINGS)

    /**
     * Effective visible order: stored order filtered to known items,
     * minus hidden ones, plus any brand-new items appended at the end.
     */
    fun effectiveVisibleItems(
        storedOrder: List<MenuItemId>,
        hidden: Set<MenuItemId>
    ): List<MenuItemId> {
        val known = storedOrder.filter { it in MenuItemId.values().toSet() }
        val withNew = known + MenuItemId.values().filter { it !in known }
        return withNew.filter { it !in hidden }
    }

    /** Full editable order (including hidden) for the customize UI. */
    fun effectiveFullOrder(storedOrder: List<MenuItemId>): List<MenuItemId> {
        val known = storedOrder.filter { it in MenuItemId.values().toSet() }
        return known + MenuItemId.values().filter { it !in known }
    }

    fun loadOrder(json: String?): List<MenuItemId> {
        if (json.isNullOrBlank()) return DEFAULT_ORDER
        return try {
            val arr = JSONArray(json)
            val ids = (0 until arr.length()).mapNotNull { i ->
                runCatching { MenuItemId.valueOf(arr.optString(i)) }.getOrNull()
            }
            if (ids.isEmpty()) DEFAULT_ORDER else effectiveFullOrder(ids)
        } catch (_: Exception) {
            DEFAULT_ORDER
        }
    }

    fun loadHidden(json: String?): Set<MenuItemId> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                runCatching { MenuItemId.valueOf(arr.optString(i)) }.getOrNull()
            }.toSet() - LOCKED_VISIBLE
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun orderToJson(order: List<MenuItemId>): String =
        JSONArray(order.map { it.name }).toString()

    fun hiddenToJson(hidden: Set<MenuItemId>): String =
        JSONArray((hidden - LOCKED_VISIBLE).map { it.name }).toString()
}
