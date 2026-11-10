package com.click.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualFullscreenBackPolicyTest {
    @Test
    fun manualFullscreenConsumesBackBeforeBrowserNavigation() {
        assertTrue(
            ManualFullscreenBackPolicy.shouldExitBeforeBrowserBack(
                manualFullscreen = true,
                overlayOpen = false,
                videoFullscreen = false,
                internalPageOpen = false,
                gamePlayerOpen = false
            )
        )
    }

    @Test
    fun higherPriorityOverlaysVideoAndNativeSurfacesKeepTheirBackAction() {
        val blockers = listOf(
            Triple(true, false, false),
            Triple(false, true, false),
            Triple(false, false, true)
        )
        blockers.forEach { (overlayOpen, videoFullscreen, internalPageOpen) ->
            assertFalse(
                ManualFullscreenBackPolicy.shouldExitBeforeBrowserBack(
                    manualFullscreen = true,
                    overlayOpen = overlayOpen,
                    videoFullscreen = videoFullscreen,
                    internalPageOpen = internalPageOpen,
                    gamePlayerOpen = false
                )
            )
        }
        assertFalse(
            ManualFullscreenBackPolicy.shouldExitBeforeBrowserBack(
                manualFullscreen = true,
                overlayOpen = false,
                videoFullscreen = false,
                internalPageOpen = false,
                gamePlayerOpen = true
            )
        )
    }

    @Test
    fun fullscreenExitIsUnavailableWhenManualImmersiveModeIsOff() {
        assertFalse(
            ManualFullscreenBackPolicy.shouldExitBeforeBrowserBack(
                manualFullscreen = false,
                overlayOpen = false,
                videoFullscreen = false,
                internalPageOpen = false,
                gamePlayerOpen = false
            )
        )
    }
}
