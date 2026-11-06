package com.click.browser.engine.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun awaitsRequestedVpnStateAndReturnsNullWhenItIsNotObserved() = runBlocking {
        val running = MutableStateFlow(false)
        val start = launch { running.value = true }
        assertEquals(true, awaitVpnRunningState(running, expectedOn = true, timeoutMillis = 1_000L))
        start.join()

        val stop = launch { running.value = false }
        assertEquals(false, awaitVpnRunningState(running, expectedOn = false, timeoutMillis = 1_000L))
        stop.join()

        assertNull(awaitVpnRunningState(running, expectedOn = true, timeoutMillis = 50L))
    }

    @Test
    fun clampsTunnelMtuToMobileSafeBounds() {
        assertEquals(VpnConfigBuilder.DEFAULT_TUN_MTU, VpnConfigBuilder.clampTunnelMtu(null))
        assertEquals(VpnConfigBuilder.DEFAULT_TUN_MTU, VpnConfigBuilder.clampTunnelMtu(9000))
        assertEquals(VpnConfigBuilder.DEFAULT_TUN_MTU, VpnConfigBuilder.clampTunnelMtu(0))
        assertEquals(VpnConfigBuilder.DEFAULT_TUN_MTU, VpnConfigBuilder.clampTunnelMtu(-1))
        assertEquals(1280, VpnConfigBuilder.clampTunnelMtu(1200))
        assertEquals(1280, VpnConfigBuilder.clampTunnelMtu(1280))
        assertEquals(1360, VpnConfigBuilder.clampTunnelMtu(1360))
    }
}
