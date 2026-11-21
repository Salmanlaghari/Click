package com.click.browser.engine

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime

/** Immutable snapshot of the user's Protection choices. */
data class ProtectionState(
    val level: String = ProtectionPolicy.LEVEL_STANDARD,
    val fingerprinting: Boolean = true,
    val blockThirdPartyCookies: Boolean = false,
    val safeBrowsing: Boolean = true
)

/**
 * Owns the live Protection state and pushes it into the Gecko engine.
 *
 * The Gecko runtime is created lazily (see ExtensionBrowserRuntime), so the
 * state is also kept here in memory: the runtime is SEEDED with these values
 * at creation time, and every later change is applied through the live
 * ContentBlocking.Settings setters, which commit the underlying Gecko
 * preferences immediately — no runtime restart needed.
 */
object ProtectionRuntime {

    @Volatile
    var current: ProtectionState = ProtectionState()
        private set

    fun update(state: ProtectionState) {
        current = state
    }

    /** Reads the persisted Protection choices (blocking; use at startup or
     *  from a background thread, never inside composition). */
    fun readPersisted(context: Context): ProtectionState = runBlocking {
        val prefs = runCatching { context.dataStore.data.first() }.getOrNull()
        ProtectionState(
            level = ProtectionPolicy.normalizeLevel(prefs?.get(AppSettings.PROTECTION_LEVEL)),
            fingerprinting = prefs?.get(AppSettings.FINGERPRINTING_PROTECTION) ?: true,
            blockThirdPartyCookies = prefs?.get(AppSettings.BLOCK_THIRD_PARTY_COOKIES) ?: false,
            safeBrowsing = prefs?.get(AppSettings.SAFE_BROWSING_ENABLED) ?: true
        )
    }

    /** ContentBlocking.Settings used to seed a freshly created runtime. */
    fun buildContentBlockingSettings(state: ProtectionState): ContentBlocking.Settings =
        ContentBlocking.Settings.Builder()
            .antiTracking(ProtectionPolicy.antiTrackingCategories(state.level, state.fingerprinting))
            .enhancedTrackingProtectionLevel(ProtectionPolicy.etpLevel(state.level))
            .enhancedTrackingProtectionCategory(ProtectionPolicy.etpCategory(state.level))
            .cookieBehavior(ProtectionPolicy.cookieBehavior(state.blockThirdPartyCookies))
            .cookieBehaviorPrivateMode(ProtectionPolicy.cookieBehavior(state.blockThirdPartyCookies))
            .safeBrowsing(ProtectionPolicy.safeBrowsingCategories(state.safeBrowsing))
            .build()

    /** Applies [state] to an already-running runtime via the live setters. */
    fun applyToRuntime(runtime: GeckoRuntime, state: ProtectionState) {
        runCatching {
            val blocking = runtime.settings.contentBlocking
            blocking.setAntiTracking(
                ProtectionPolicy.antiTrackingCategories(state.level, state.fingerprinting)
            )
            blocking.setEnhancedTrackingProtectionLevel(ProtectionPolicy.etpLevel(state.level))
            blocking.setEnhancedTrackingProtectionCategory(ProtectionPolicy.etpCategory(state.level))
            val cookies = ProtectionPolicy.cookieBehavior(state.blockThirdPartyCookies)
            blocking.setCookieBehavior(cookies)
            blocking.setCookieBehaviorPrivateMode(cookies)
            blocking.setSafeBrowsing(ProtectionPolicy.safeBrowsingCategories(state.safeBrowsing))
        }
    }
}
