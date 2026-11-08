package com.click.browser.ui.screens

/** User-facing privacy disclosures tied to the behavior of the underlying DNS controls. */
internal object PrivacyDisclosurePolicy {

    fun aiChatDnsDescription(enabled: Boolean): String = if (enabled) {
        "AI chat DNS lookups use Cloudflare DNS-over-HTTPS when the resolver initializes; " +
            "if setup fails, they use the system DNS resolver. WebView page loads always use system DNS."
    } else {
        "AI chat DNS lookups use the system DNS resolver. Turn this on to use " +
            "Cloudflare DNS-over-HTTPS when available. WebView page loads always use system DNS."
    }

    fun v9ShieldDnsDisclosure(isRunning: Boolean, strictDns: Boolean): String = when {
        !isRunning -> "V9 Shield is OFF; its DNS filtering and DoH are inactive. When it runs, " +
            "Strict DNS controls whether a DoH failure blocks the lookup or may fall back to " +
            "plain DNS. V9 Shield handles DNS only and does not tunnel general website traffic."

        strictDns -> "V9 Shield is ON. It filters DNS and uses the configured DoH provider. " +
            "With Strict DNS ON, a DoH failure blocks that lookup; it does not retry with plain DNS. " +
            "V9 Shield handles DNS only and does not tunnel general website traffic."

        else -> "V9 Shield is ON. It filters DNS and uses the configured DoH provider. " +
            "With Strict DNS OFF, a DoH failure may fall back to plain DNS, which may be " +
            "unencrypted. V9 Shield handles DNS only and does not tunnel general website traffic."
    }

    const val V9_BLOCK_COUNT_LABEL = "DNS queries blocked"

    const val V9_SHIELD_BLOCKLIST_SCOPE =
        "The built-in DNS blocklist covers a limited set of advertising, tracker and malware " +
            "domains. It is not a full phishing filter or a general web-protection service."

    const val V9_SHIELD_RESPONSIBLE_USE =
        "V9 Shield is a privacy tool, not intended for illegal activity; it does not hide " +
            "wrongdoing from the law. Click applies no political censorship."

    const val VPN_DNS_LEAK_TEST_GUIDANCE =
        "After connecting, check whether the detected resolver is consistent with this tunnel's " +
            "DNS-over-HTTPS path. An unexpected ISP resolver may indicate a leak. A single test " +
            "is only a spot check; it cannot guarantee that every DNS request is leak-free."
}
