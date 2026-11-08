package com.click.browser.engine

/** Compatibility seam for DevTools capture call sites; capability truth lives in the registry. */
internal object DevToolsAvailabilityPolicy {
    fun canCapture(mode: BrowserMode, engine: BrowserTabEngine): Boolean =
        ModeCapabilityRegistry.availability(
            feature = FeatureId.DEVTOOLS,
            mode = mode,
            engine = engine
        ).enabled
}
