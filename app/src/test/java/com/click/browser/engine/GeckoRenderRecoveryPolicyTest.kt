package com.click.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoRenderRecoveryPolicyTest {
    @Test
    fun crashAndKillRequireVisibleRecoveryUntilNavigationOrFirstPaint() {
        assertTrue(geckoRendererRecoveryRequired(GeckoRenderEvent.PROCESS_CRASHED))
        assertTrue(geckoRendererRecoveryRequired(GeckoRenderEvent.PROCESS_KILLED))
        assertFalse(geckoRendererRecoveryRequired(GeckoRenderEvent.LOAD_STARTED))
        assertFalse(geckoRendererRecoveryRequired(GeckoRenderEvent.FIRST_COMPOSITE))
    }
}
