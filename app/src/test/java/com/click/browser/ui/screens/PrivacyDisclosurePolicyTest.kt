package com.click.browser.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyDisclosurePolicyTest {

    @Test
    fun aiChatSecureDnsDescriptionMatchesToggleAndKeepsWebViewDnsSeparate() {
        val disabled = PrivacyDisclosurePolicy.aiChatDnsDescription(enabled = false)
        assertTrue(disabled.contains("AI chat DNS lookups use the system DNS resolver"))
        assertTrue(disabled.contains("Turn this on to use Cloudflare DNS-over-HTTPS"))
        assertTrue(disabled.contains("WebView page loads always use system DNS"))

        val enabled = PrivacyDisclosurePolicy.aiChatDnsDescription(enabled = true)
        assertTrue(enabled.contains("use Cloudflare DNS-over-HTTPS"))
        assertTrue(enabled.contains("if setup fails, they use the system DNS resolver"))
        assertTrue(enabled.contains("WebView page loads always use system DNS"))
    }

    @Test
    fun v9ShieldOffStateDoesNotClaimDnsProtectionIsActive() {
        val disclosure = PrivacyDisclosurePolicy.v9ShieldDnsDisclosure(
            isRunning = false,
            strictDns = false
        )

        assertTrue(disclosure.contains("V9 Shield is OFF"))
        assertTrue(disclosure.contains("DNS filtering and DoH are inactive"))
        assertTrue(disclosure.contains("does not tunnel general website traffic"))
    }

    @Test
    fun strictDnsDisclosureMatchesFailClosedBehavior() {
        val disclosure = PrivacyDisclosurePolicy.v9ShieldDnsDisclosure(
            isRunning = true,
            strictDns = true
        )

        assertTrue(disclosure.contains("a DoH failure blocks that lookup"))
        assertTrue(disclosure.contains("does not retry with plain DNS"))
        assertFalse(disclosure.contains("may fall back to plain DNS"))
    }

    @Test
    fun nonStrictDnsDisclosureWarnsAboutPossibleUnencryptedFallback() {
        val disclosure = PrivacyDisclosurePolicy.v9ShieldDnsDisclosure(
            isRunning = true,
            strictDns = false
        )

        assertTrue(disclosure.contains("may fall back to plain DNS"))
        assertTrue(disclosure.contains("may be unencrypted"))
    }

    @Test
    fun v9ShieldBlocklistDisclosureDoesNotOverstateItsScope() {
        val disclosure = PrivacyDisclosurePolicy.V9_SHIELD_BLOCKLIST_SCOPE

        assertTrue(disclosure.contains("limited set of advertising, tracker and malware domains"))
        assertTrue(disclosure.contains("not a full phishing filter"))
        assertFalse(disclosure.contains("phishing and adult content where you enable it"))
        assertEquals("DNS queries blocked", PrivacyDisclosurePolicy.V9_BLOCK_COUNT_LABEL)
    }

    @Test
    fun vpnDnsLeakTestIsDescribedAsASpotCheckNotAProof() {
        val guidance = PrivacyDisclosurePolicy.VPN_DNS_LEAK_TEST_GUIDANCE

        assertTrue(guidance.contains("An unexpected ISP resolver may indicate a leak"))
        assertTrue(guidance.contains("only a spot check"))
        assertTrue(guidance.contains("cannot guarantee"))
        assertFalse(guidance.contains("proves there is no DNS leak"))
    }
}
