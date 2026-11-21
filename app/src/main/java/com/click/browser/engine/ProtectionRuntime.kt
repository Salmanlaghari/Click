package com.click.browser.engine

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 *
 * Threading: the persisted choices are read on [Dispatchers.IO] only (see
 * [ensurePersistedLoaded]). Nothing on the runtime-creation path may block
 * waiting for the disk read — the runtime is seeded from the last-known (or
 * default) state and the persisted values are applied live as soon as the
 * read lands. (The previous synchronous read inside runtime creation ran on
 * the main thread and contributed to the cold-start/resume ANR fixed in
 * 1.8.5.)
 */
object ProtectionRuntime {

    @Volatile
    var current: ProtectionState = ProtectionState()
        private set

    /** True once [current] reflects the persisted choices (or newer edits
     *  made on top of them). Lets runtime creation skip a redundant disk
     *  read when the state was already loaded this process. */
    @Volatile
    var persistedLoaded: Boolean = false
        private set

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val loadLock = Any()
    private var loadInFlight = false
    private val pendingLoadCallbacks = mutableListOf<(ProtectionState) -> Unit>()

    fun update(state: ProtectionState) {
        current = state
        persistedLoaded = true
    }

    /** Maps one DataStore snapshot to a [ProtectionState]. */
    internal fun stateFrom(prefs: Preferences?): ProtectionState = ProtectionState(
        level = ProtectionPolicy.normalizeLevel(prefs?.get(AppSettings.PROTECTION_LEVEL)),
        fingerprinting = prefs?.get(AppSettings.FINGERPRINTING_PROTECTION) ?: true,
        blockThirdPartyCookies = prefs?.get(AppSettings.BLOCK_THIRD_PARTY_COOKIES) ?: false,
        safeBrowsing = prefs?.get(AppSettings.SAFE_BROWSING_ENABLED) ?: true
    )

    /** Reads the persisted Protection choices from disk (suspending; IO). */
    suspend fun loadPersisted(context: Context): ProtectionState {
        val prefs = runCatching { context.dataStore.data.first() }.getOrNull()
        return stateFrom(prefs)
    }

    /**
     * Makes sure the persisted choices are loaded, without ever blocking the
     * caller. If they are already loaded, [onLoaded] runs immediately with
     * [current]. Otherwise a single IO read is started (concurrent callers
     * coalesce) and every registered [onLoaded] runs with the loaded state
     * once it lands. Callbacks run on an IO thread — hop to the main thread
     * before touching UI or Gecko state.
     */
    fun ensurePersistedLoaded(context: Context, onLoaded: (ProtectionState) -> Unit = {}) {
        if (persistedLoaded) {
            onLoaded(current)
            return
        }
        val shouldStart = synchronized(loadLock) {
            pendingLoadCallbacks += onLoaded
            if (loadInFlight) {
                false
            } else {
                loadInFlight = true
                true
            }
        }
        if (!shouldStart) return
        val appContext = context.applicationContext
        ioScope.launch {
            val state = runCatching { loadPersisted(appContext) }.getOrElse { current }
            // A settings edit made while the read was in flight is newer
            // than the disk snapshot and must not be overwritten by it.
            if (!persistedLoaded) update(state)
            val callbacks = synchronized(loadLock) {
                loadInFlight = false
                pendingLoadCallbacks.toList().also { pendingLoadCallbacks.clear() }
            }
            val effective = current
            callbacks.forEach { callback ->
                runCatching { callback(effective) }
            }
        }
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
