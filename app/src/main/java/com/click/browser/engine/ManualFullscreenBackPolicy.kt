package com.click.browser.engine

/** Keeps browser navigation and exit actions behind the manual-fullscreen escape. */
internal object ManualFullscreenBackPolicy {
    fun shouldExitBeforeBrowserBack(
        manualFullscreen: Boolean,
        overlayOpen: Boolean,
        videoFullscreen: Boolean,
        internalPageOpen: Boolean,
        gamePlayerOpen: Boolean
    ): Boolean = manualFullscreen &&
        !overlayOpen &&
        !videoFullscreen &&
        !internalPageOpen &&
        !gamePlayerOpen
}
