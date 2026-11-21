package com.click.browser.extensions

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.click.browser.engine.ClickAutocompleteStorageDelegate
import com.click.browser.engine.ProtectionRuntime
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/** GeckoView permits one runtime per process; the runtime owns the separate extension-browser profile. */
/** No extension update calls are made: GeckoView documents updates as embedder-triggered, and same-permission updates skip prompts. */
internal object ExtensionBrowserRuntime {
    @Volatile
    private var runtime: GeckoRuntime? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /** The already-created runtime, or null — never creates one. Used when
     *  applying settings that must not force an eager runtime start. */
    fun peek(): GeckoRuntime? = runtime

    fun get(context: Context): GeckoRuntime = runtime ?: synchronized(this) {
        runtime ?: run {
            val appContext = context.applicationContext
            // Seed the runtime with the last-known Protection state (the
            // persisted choices when they were already loaded this process,
            // otherwise the defaults) so Gecko-engine tabs enforce the
            // selected tracking-protection level, cookie behavior and Safe
            // Browsing categories from the first page. This NEVER waits for
            // the disk: get() is called on the main thread (e.g. from the
            // AndroidView factory that creates the active tab's Gecko
            // session), and the previous synchronous DataStore read here
            // blocked that thread on cold starts after process death —
            // black screen + "isn't responding" (fixed in 1.8.5). When the
            // persisted state has not been loaded yet, it is read on a
            // background thread and applied to this runtime through
            // ProtectionRuntime.applyToRuntime (live setters, no restart)
            // as soon as it lands; later changes take the same live path.
            val runtimeSettings = GeckoRuntimeSettings.Builder()
                .contentBlocking(
                    ProtectionRuntime.buildContentBlockingSettings(ProtectionRuntime.current)
                )
                .build()
            GeckoRuntime.create(appContext, runtimeSettings).also { created ->
                created.setAutocompleteStorageDelegate(ClickAutocompleteStorageDelegate(appContext))
                runtime = created
                if (!ProtectionRuntime.persistedLoaded) {
                    ProtectionRuntime.ensurePersistedLoaded(appContext) { state ->
                        mainHandler.post {
                            // Apply only if this runtime is still the live
                            // one; a settings edit made in the meantime is
                            // already reflected in ProtectionRuntime.current.
                            if (runtime === created) {
                                ProtectionRuntime.applyToRuntime(created, state)
                            }
                        }
                    }
                }
            }
        }
    }
}
