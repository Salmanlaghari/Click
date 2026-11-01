package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileBootFailurePolicyTest {
    @Test
    fun advancedSetupFailureKeepsAdvancedSelectedAndBlocksBrowser() {
        val decision = profileBootFailureDecision(BrowserMode.ADVANCED)

        assertEquals(BrowserMode.ADVANCED, decision.bootMode)
        assertTrue(decision.blockBrowser)
    }

    @Test
    fun knownNonAdvancedSetupFailuresKeepLegacySimpleFallback() {
        BrowserMode.values()
            .filter { it != BrowserMode.ADVANCED }
            .forEach { requestedMode ->
                val decision = profileBootFailureDecision(requestedMode)

                assertEquals(requestedMode.name, BrowserMode.SIMPLE, decision.bootMode)
                assertFalse(requestedMode.name, decision.blockBrowser)
            }
    }

    @Test
    fun unknownModeFailsClosedInsteadOfGuessingSharedStorage() {
        val decision = profileBootFailureDecision(null)

        assertEquals(BrowserMode.SIMPLE, decision.bootMode)
        assertTrue(decision.blockBrowser)
    }
}
