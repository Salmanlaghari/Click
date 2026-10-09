package com.click.browser.engine

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Experimental feature flags backing `click://flags`.
 *
 * EVERY flag is a REAL working toggle wired into MainActivity / WebView
 * settings — no fake UI. Flags are persisted in DataStore, loaded once at
 * startup into `MainActivity.liveFlags`, applied idempotently via
 * `applyExperimentalFlags()`.
 */
data class ExperimentalFlags(
    val desktopDefault: Boolean = false,
    val aggressiveAdblock: Boolean = false,
    val clearOnExit: Boolean = false,
    val clearHistoryOnExit: Boolean = false,
    val bottomAddressBar: Boolean = false,
    val pullToRefreshEnabled: Boolean = true,
    val dntHeader: Boolean = false,
    val blockThirdPartyCookies: Boolean = false,
    val blockImages: Boolean = false,
    val blockPopups: Boolean = true,
    val autoplayBlock: Boolean = false,
    val forceZoom: Boolean = false,
    val blockScreenshots: Boolean = false,
    val safeBrowsing: Boolean = true,
    val tabAnimations: Boolean = true,
    val confirmExit: Boolean = false,
    val textZoomLarge: Boolean = false,
    val mixedContentAllow: Boolean = false,
    val cookieAccept: Boolean = true,
    val overscrollGlow: Boolean = true,
    val customUserAgent: String = "",
    val customHomepage: String = "",
) {
    companion object {
        val K_DESKTOP_DEFAULT = booleanPreferencesKey("flag_desktop_default")
        val K_AGGRESSIVE_ADBLOCK = booleanPreferencesKey("flag_aggressive_adblock")
        val K_CLEAR_ON_EXIT = booleanPreferencesKey("flag_clear_on_exit")
        val K_CLEAR_HISTORY_ON_EXIT = booleanPreferencesKey("flag_clear_history_on_exit")
        val K_BOTTOM_ADDRESS_BAR = booleanPreferencesKey("flag_bottom_address_bar")
        val K_PULL_TO_REFRESH = booleanPreferencesKey("flag_pull_to_refresh")
        val K_DNT_HEADER = booleanPreferencesKey("flag_dnt_header")
        val K_BLOCK_3P_COOKIES = booleanPreferencesKey("flag_block_3p_cookies")
        val K_BLOCK_IMAGES = booleanPreferencesKey("flag_block_images")
        val K_BLOCK_POPUPS = booleanPreferencesKey("flag_block_popups")
        val K_AUTOPLAY_BLOCK = booleanPreferencesKey("flag_autoplay_block")
        val K_FORCE_ZOOM = booleanPreferencesKey("flag_force_zoom")
        val K_BLOCK_SCREENSHOTS = booleanPreferencesKey("flag_block_screenshots")
        val K_SAFE_BROWSING = booleanPreferencesKey("flag_safe_browsing")
        val K_TAB_ANIMATIONS = booleanPreferencesKey("flag_tab_animations")
        val K_CONFIRM_EXIT = booleanPreferencesKey("flag_confirm_exit")
        val K_TEXT_ZOOM_LARGE = booleanPreferencesKey("flag_text_zoom_large")
        val K_MIXED_CONTENT_ALLOW = booleanPreferencesKey("flag_mixed_content_allow")
        val K_COOKIE_ACCEPT = booleanPreferencesKey("flag_cookie_accept")
        val K_OVERSCROLL_GLOW = booleanPreferencesKey("flag_overscroll_glow")
        val K_CUSTOM_UA = stringPreferencesKey("flag_custom_ua")
        val K_CUSTOM_HOMEPAGE = stringPreferencesKey("flag_custom_homepage")

        fun load(prefs: Preferences): ExperimentalFlags = ExperimentalFlags(
            desktopDefault = prefs[K_DESKTOP_DEFAULT] == true,
            aggressiveAdblock = prefs[K_AGGRESSIVE_ADBLOCK] == true,
            clearOnExit = prefs[K_CLEAR_ON_EXIT] == true,
            clearHistoryOnExit = prefs[K_CLEAR_HISTORY_ON_EXIT] == true,
            bottomAddressBar = prefs[K_BOTTOM_ADDRESS_BAR] == true,
            pullToRefreshEnabled = prefs[K_PULL_TO_REFRESH] != false,
            dntHeader = prefs[K_DNT_HEADER] == true,
            blockThirdPartyCookies = prefs[K_BLOCK_3P_COOKIES] == true,
            blockImages = prefs[K_BLOCK_IMAGES] == true,
            blockPopups = prefs[K_BLOCK_POPUPS] != false,
            autoplayBlock = prefs[K_AUTOPLAY_BLOCK] == true,
            forceZoom = prefs[K_FORCE_ZOOM] == true,
            blockScreenshots = prefs[K_BLOCK_SCREENSHOTS] == true,
            safeBrowsing = prefs[K_SAFE_BROWSING] != false,
            tabAnimations = prefs[K_TAB_ANIMATIONS] != false,
            confirmExit = prefs[K_CONFIRM_EXIT] == true,
            textZoomLarge = prefs[K_TEXT_ZOOM_LARGE] == true,
            mixedContentAllow = prefs[K_MIXED_CONTENT_ALLOW] == true,
            cookieAccept = prefs[K_COOKIE_ACCEPT] != false,
            overscrollGlow = prefs[K_OVERSCROLL_GLOW] != false,
            customUserAgent = prefs[K_CUSTOM_UA].orEmpty(),
            customHomepage = prefs[K_CUSTOM_HOMEPAGE].orEmpty(),
        )
    }

    /**
     * Metadata for the flags lab UI — BOOLEAN flags only.
     * The two string flags ([K_CUSTOM_UA], [K_CUSTOM_HOMEPAGE]) are intentionally
     * NOT in this enum; they use the separate [ClickPageHost.onStringFlag] callback
     * which is typed `(Preferences.Key<String>, String) -> Unit`. This keeps the
     * boolean/string key types from ever mixing (the enum's [key] is always
     * `Key<Boolean>`, the string callback's key is always `Key<String>`).
     */
    enum class Meta(
        val title: String,
        val desc: String,
        val get: (ExperimentalFlags) -> Boolean,
        val set: (ExperimentalFlags, Boolean) -> ExperimentalFlags,
        val key: Preferences.Key<Boolean>,
    ) {
        DESKTOP_DEFAULT("Desktop by default", "Always request desktop sites",
            { it.desktopDefault }, { f, v -> f.copy(desktopDefault = v) }, K_DESKTOP_DEFAULT),
        AGGRESSIVE_ADBLOCK("Aggressive ad-block", "Block extra trackers & analytics",
            { it.aggressiveAdblock }, { f, v -> f.copy(aggressiveAdblock = v) }, K_AGGRESSIVE_ADBLOCK),
        CLEAR_ON_EXIT("Clear data on exit", "Wipe cache & cookies when app closes",
            { it.clearOnExit }, { f, v -> f.copy(clearOnExit = v) }, K_CLEAR_ON_EXIT),
        CLEAR_HISTORY_ON_EXIT("Clear history on exit", "Wipe browsing history when app closes",
            { it.clearHistoryOnExit }, { f, v -> f.copy(clearHistoryOnExit = v) }, K_CLEAR_HISTORY_ON_EXIT),
        BOTTOM_ADDRESS_BAR("Bottom address bar", "Move the URL bar below the page",
            { it.bottomAddressBar }, { f, v -> f.copy(bottomAddressBar = v) }, K_BOTTOM_ADDRESS_BAR),
        PULL_TO_REFRESH("Pull to refresh", "Swipe down on pages to reload",
            { it.pullToRefreshEnabled }, { f, v -> f.copy(pullToRefreshEnabled = v) }, K_PULL_TO_REFRESH),
        DNT_HEADER("Do-Not-Track header", "Send DNT: 1 with every request",
            { it.dntHeader }, { f, v -> f.copy(dntHeader = v) }, K_DNT_HEADER),
        BLOCK_3P_COOKIES("Block 3rd-party cookies", "Stop cross-site tracking cookies",
            { it.blockThirdPartyCookies }, { f, v -> f.copy(blockThirdPartyCookies = v) }, K_BLOCK_3P_COOKIES),
        BLOCK_IMAGES("Block images", "Hide all images to save data",
            { it.blockImages }, { f, v -> f.copy(blockImages = v) }, K_BLOCK_IMAGES),
        BLOCK_POPUPS("Block pop-ups", "Stop sites opening new windows",
            { it.blockPopups }, { f, v -> f.copy(blockPopups = v) }, K_BLOCK_POPUPS),
        AUTOPLAY_BLOCK("Block autoplay", "Videos need a tap to play",
            { it.autoplayBlock }, { f, v -> f.copy(autoplayBlock = v) }, K_AUTOPLAY_BLOCK),
        FORCE_ZOOM("Force zoom", "Pinch-zoom on every site",
            { it.forceZoom }, { f, v -> f.copy(forceZoom = v) }, K_FORCE_ZOOM),
        BLOCK_SCREENSHOTS("Block screenshots", "Prevent screen capture in the app",
            { it.blockScreenshots }, { f, v -> f.copy(blockScreenshots = v) }, K_BLOCK_SCREENSHOTS),
        SAFE_BROWSING("Safe Browsing", "Warn about dangerous sites",
            { it.safeBrowsing }, { f, v -> f.copy(safeBrowsing = v) }, K_SAFE_BROWSING),
        TAB_ANIMATIONS("Tab animations", "Animated tab switcher transitions",
            { it.tabAnimations }, { f, v -> f.copy(tabAnimations = v) }, K_TAB_ANIMATIONS),
        CONFIRM_EXIT("Confirm exit", "Ask before closing the browser",
            { it.confirmExit }, { f, v -> f.copy(confirmExit = v) }, K_CONFIRM_EXIT),
        TEXT_ZOOM_LARGE("Large text", "125% text size on pages",
            { it.textZoomLarge }, { f, v -> f.copy(textZoomLarge = v) }, K_TEXT_ZOOM_LARGE),
        MIXED_CONTENT_ALLOW("Allow mixed content", "Load http resources on https pages (less safe)",
            { it.mixedContentAllow }, { f, v -> f.copy(mixedContentAllow = v) }, K_MIXED_CONTENT_ALLOW),
        COOKIE_ACCEPT("Accept cookies", "Master switch for all cookies",
            { it.cookieAccept }, { f, v -> f.copy(cookieAccept = v) }, K_COOKIE_ACCEPT),
        OVERSCROLL_GLOW("Overscroll glow", "Edge glow effect when scrolling past ends",
            { it.overscrollGlow }, { f, v -> f.copy(overscrollGlow = v) }, K_OVERSCROLL_GLOW),
    }
}
