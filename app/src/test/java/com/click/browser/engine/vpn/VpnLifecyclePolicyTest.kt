package com.click.browser.engine.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnLifecyclePolicyTest {
    @Test
    fun preservesErrorStateDuringServiceCleanup() {
        assertTrue(shouldPreserveVpnFailureOnCleanup(VpnState.ERROR))
    }

    @Test
    fun ordinaryDisconnectAndConnectedCleanupDoNotPreserveFailure() {
        assertFalse(shouldPreserveVpnFailureOnCleanup(VpnState.DISCONNECTED))
        assertFalse(shouldPreserveVpnFailureOnCleanup(VpnState.CONNECTED))
    }
}
