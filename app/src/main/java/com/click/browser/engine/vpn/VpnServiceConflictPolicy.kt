package com.click.browser.engine.vpn

internal enum class ClickVpnServiceKind {
    DNS_SHIELD,
    FULL_TUNNEL
}

/** Android permits only one active VpnService per user/profile. */
internal fun hasVpnServiceConflict(
    requested: ClickVpnServiceKind,
    dnsShieldActiveOrStarting: Boolean,
    fullTunnelActiveOrStarting: Boolean
): Boolean = when (requested) {
    ClickVpnServiceKind.DNS_SHIELD -> fullTunnelActiveOrStarting
    ClickVpnServiceKind.FULL_TUNNEL -> dnsShieldActiveOrStarting
}
