package com.click.browser.engine.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test
    fun clampsTunnelMtuToMobileSafeBounds() {
        assertEquals(1400, VpnConfigBuilder.clampTunnelMtu(null))
        assertEquals(1400, VpnConfigBuilder.clampTunnelMtu(9000))
        assertEquals(1280, VpnConfigBuilder.clampTunnelMtu(1200))
        assertEquals(1360, VpnConfigBuilder.clampTunnelMtu(1360))
    }
}
