package com.click.browser.extensions

import android.content.Context
import com.click.browser.engine.ClickAutocompleteStorageDelegate
import org.mozilla.geckoview.GeckoRuntime

/** GeckoView permits one runtime per process; the runtime owns the separate extension-browser profile. */
/** No extension update calls are made: GeckoView documents updates as embedder-triggered, and same-permission updates skip prompts. */
internal object ExtensionBrowserRuntime {
    @Volatile
    private var runtime: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime = runtime ?: synchronized(this) {
        runtime ?: GeckoRuntime.create(context.applicationContext).also {
            it.setAutocompleteStorageDelegate(ClickAutocompleteStorageDelegate)
            runtime = it
        }
    }
}
