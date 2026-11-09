package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickInternalPagesTest {

    @Test
    fun vpnCatalogAndDestinationDescribeTheFullTunnelVpn() {
        val vpnPage = ClickInternalPages.PAGES.single { it.key == "vpn" }

        assertEquals("Click VPN", vpnPage.title)
        assertTrue(vpnPage.desc.contains("full-tunnel", ignoreCase = true))
        assertEquals(
            ClickInternalPages.SecurityPageDestination.CLICK_VPN,
            ClickInternalPages.securityPageDestination("vpn")
        )
    }

    @Test
    fun dnsPageKeepsItsDistinctV9ShieldDestination() {
        val dnsPage = ClickInternalPages.PAGES.single { it.key == "dns" }

        assertEquals("DNS", dnsPage.title)
        assertEquals(
            ClickInternalPages.SecurityPageDestination.V9_SHIELD,
            ClickInternalPages.securityPageDestination("dns")
        )
        assertNull(ClickInternalPages.securityPageDestination("settings"))
    }
}
