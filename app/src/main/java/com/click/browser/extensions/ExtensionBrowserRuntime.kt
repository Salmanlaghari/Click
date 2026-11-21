package com.click.browser.extensions

import android.content.Context
import com.click.browser.engine.ClickAutocompleteStorageDelegate
import com.click.browser.engine.ProtectionRuntime
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/** GeckoView permits one runtime per process; the runtime owns the separate extension-browser profile. */
/** No extension update calls are made: GeckoView documents updates as embedder-triggered, and same-permission updates skip prompts. */
internal object ExtensionBrowserRuntime {
    @Volatile
    private var runtime: GeckoRuntime? = null

    /** The already-created runtime, or null — never creates one. Used when
     *  applying settings that must not force an eager runtime start. */
    fun peek(): GeckoRuntime? = runtime

    fun get(context: Context): GeckoRuntime = runtime ?: synchronized(this) {
        runtime ?: run {
            val appContext = context.applicationContext
            // Seed the runtime with the user's persisted Protection choices so
            // Gecko-engine tabs enforce the selected tracking-protection
            // level, cookie behavior and Safe Browsing categories from the
            // first page. Later changes go through
            // ProtectionRuntime.applyToRuntime (live setters, no restart).
            val protectionState = ProtectionRuntime.readPersisted(appContext)
            ProtectionRuntime.update(protectionState)
            val runtimeSettings = GeckoRuntimeSettings.Builder()
                .contentBlocking(ProtectionRuntime.buildContentBlockingSettings(protectionState))
                .build()
            GeckoRuntime.create(appContext, runtimeSettings).also {
                it.setAutocompleteStorageDelegate(ClickAutocompleteStorageDelegate)
                runtime = it
            }
        }
    }
}
