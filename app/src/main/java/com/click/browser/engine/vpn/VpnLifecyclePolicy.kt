package com.click.browser.engine.vpn

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** A failed startup must remain visible when teardown runs after stopSelf(). */
internal fun shouldPreserveVpnFailureOnCleanup(state: VpnState): Boolean =
    state == VpnState.ERROR

/** Wait for the requested shield state; return null if it is not observed in time. */
internal suspend fun awaitVpnRunningState(
    running: StateFlow<Boolean>,
    expectedOn: Boolean,
    timeoutMillis: Long = 10_000L
): Boolean? = withTimeoutOrNull(timeoutMillis) {
    running.first { it == expectedOn }
}
