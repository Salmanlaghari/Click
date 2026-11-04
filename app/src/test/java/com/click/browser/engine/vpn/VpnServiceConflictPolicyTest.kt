package com.click.browser.engine.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnServiceConflictPolicyTest {
    @Test
    fun eachServiceRefusesToReplaceTheOtherActiveTunnel() {
        assertTrue(
            hasVpnServiceConflict(
                ClickVpnServiceKind.DNS_SHIELD,
                dnsShieldActiveOrStarting = false,
                fullTunnelActiveOrStarting = true
            )
        )
        assertTrue(
            hasVpnServiceConflict(
                ClickVpnServiceKind.FULL_TUNNEL,
                dnsShieldActiveOrStarting = true,
                fullTunnelActiveOrStarting = false
            )
        )
    }

    @Test
    fun eitherServiceCanStartWhenTheOtherServiceIsInactive() {
        assertFalse(
            hasVpnServiceConflict(
                ClickVpnServiceKind.DNS_SHIELD,
                dnsShieldActiveOrStarting = false,
                fullTunnelActiveOrStarting = false
            )
        )
        assertFalse(
            hasVpnServiceConflict(
                ClickVpnServiceKind.FULL_TUNNEL,
                dnsShieldActiveOrStarting = false,
                fullTunnelActiveOrStarting = false
            )
        )
    }
}
