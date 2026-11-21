package com.click.browser.engine

import org.mozilla.geckoview.ContentBlocking

/**
 * Pure mapping from Click's user-facing Protection choices to the exact
 * GeckoView content-blocking values that enforce them. Kept free of
 * Android/Gecko runtime calls (the ContentBlocking constants are
 * compile-time ints) so the mapping is unit-testable on the JVM.
 *
 * Enforcement points:
 *  - Gecko-engine tabs: [ProtectionRuntime] pushes these values into the
 *    live GeckoRuntime content-blocking settings (and seeds them when the
 *    runtime is created), plus the per-session tracking-protection switch.
 *  - WebView tabs: the level drives Click's own filter-list blocker
 *    (off = no interception, strong = aggressive list) and the third-party
 *    cookie / Safe Browsing toggles feed the existing WebView settings
 *    application in MainActivity.
 */
object ProtectionPolicy {

    const val LEVEL_OFF = "off"
    const val LEVEL_STANDARD = "standard"
    const val LEVEL_STRONG = "strong"

    fun normalizeLevel(value: String?): String =
        when (value?.lowercase()) {
            LEVEL_OFF, LEVEL_STANDARD, LEVEL_STRONG -> value.lowercase()
            else -> LEVEL_STANDARD
        }

    fun levelLabel(level: String): String = when (normalizeLevel(level)) {
        LEVEL_OFF -> "Off"
        LEVEL_STRONG -> "Strong"
        else -> "Standard"
    }

    /**
     * Anti-tracking categories for the Gecko engine.
     *  - off: no categories at all.
     *  - standard: GeckoView's DEFAULT set (ads, analytics, social, test)
     *    plus the fingerprinting category when fingerprinting protection
     *    is on — the DEFAULT set alone does not include fingerprinting.
     *  - strong: GeckoView's STRICT set (adds content trackers,
     *    cryptomining, fingerprinting and email tracking); the
     *    fingerprinting category is removed again when the user turned
     *    fingerprinting protection off.
     */
    fun antiTrackingCategories(level: String, fingerprinting: Boolean): Int =
        when (normalizeLevel(level)) {
            LEVEL_OFF -> ContentBlocking.AntiTracking.NONE
            LEVEL_STRONG -> {
                val strict = ContentBlocking.AntiTracking.STRICT
                if (fingerprinting) strict
                else strict and ContentBlocking.AntiTracking.FINGERPRINTING.inv()
            }
            else -> {
                val base = ContentBlocking.AntiTracking.DEFAULT
                if (fingerprinting) base or ContentBlocking.AntiTracking.FINGERPRINTING
                else base
            }
        }

    /** Enhanced Tracking Protection level paired with the cookie behavior
     *  below (ETP levels only take effect with an isolating/non-tracker
     *  cookie behavior, which is what [cookieBehavior] always returns). */
    fun etpLevel(level: String): Int = when (normalizeLevel(level)) {
        LEVEL_OFF -> ContentBlocking.EtpLevel.NONE
        LEVEL_STRONG -> ContentBlocking.EtpLevel.STRICT
        else -> ContentBlocking.EtpLevel.DEFAULT
    }

    /** browser.contentblocking.category counterpart of the level. */
    fun etpCategory(level: String): Int = when (normalizeLevel(level)) {
        LEVEL_OFF -> ContentBlocking.EtpCategory.CUSTOM
        LEVEL_STRONG -> ContentBlocking.EtpCategory.STRICT
        else -> ContentBlocking.EtpCategory.STANDARD
    }

    /**
     * Cookie behavior for both normal and private Gecko sessions.
     * Blocking third-party cookies maps to ACCEPT_FIRST_PARTY; otherwise
     * GeckoView's own default (first-party + total cookie protection
     * isolation) is kept so ETP levels stay effective.
     */
    fun cookieBehavior(blockThirdPartyCookies: Boolean): Int =
        if (blockThirdPartyCookies) ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
        else ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS

    /** Safe Browsing categories: the full default set, or none. */
    fun safeBrowsingCategories(enabled: Boolean): Int =
        if (enabled) ContentBlocking.SafeBrowsing.DEFAULT
        else ContentBlocking.SafeBrowsing.NONE

    /** The click://flags aggressive-adblock experiment ORs with the level:
     *  Strong protection always uses the aggressive WebView filter list. */
    fun shouldUseAggressiveFilterList(level: String, flagsAggressive: Boolean): Boolean =
        flagsAggressive || normalizeLevel(level) == LEVEL_STRONG

    /** The Privacy & Safety toggle ORs with the click://flags experiment. */
    fun effectiveBlockThirdPartyCookies(userSetting: Boolean, flagsValue: Boolean): Boolean =
        userSetting || flagsValue

    /** Safe Browsing is on only when BOTH the user setting and the
     *  click://flags experiment allow it (either one can turn it off). */
    fun effectiveSafeBrowsing(userSetting: Boolean, flagsValue: Boolean): Boolean =
        userSetting && flagsValue
}
