package com.click.browser.engine.vpn

/** A failed startup must remain visible when teardown runs after stopSelf(). */
internal fun shouldPreserveVpnFailureOnCleanup(state: VpnState): Boolean =
    state == VpnState.ERROR
