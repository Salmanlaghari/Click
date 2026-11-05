package com.click.browser.engine

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import com.click.browser.engine.vpn.ClickVpnServiceKind
import com.click.browser.engine.vpn.SingBoxVpnService
import com.click.browser.engine.vpn.VpnState
import com.click.browser.engine.vpn.awaitVpnRunningState
import com.click.browser.engine.vpn.hasVpnServiceConflict

/**
 * UI-facing control for the V9 Shield VPN.
 *
 * Permission flow: [prepareIntent] returns null when the user already granted
 * VPN permission — then [start] can be called directly. Otherwise the caller
 * must launch the returned intent (VpnService.prepare) and call [start] on
 * RESULT_OK.
 */
object V9VpnController {

    /** Null = permission already granted, safe to [start]. */
    fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)

    /** Check before opening Android's VPN authorization UI, then check again in [start]. */
    fun isBlockedByClickVpn(): Boolean {
        val fullTunnelState = SingBoxVpnService.state.value
        return hasVpnServiceConflict(
            requested = ClickVpnServiceKind.DNS_SHIELD,
            dnsShieldActiveOrStarting = V9VpnService.activeOrStarting,
            fullTunnelActiveOrStarting = fullTunnelState == VpnState.CONNECTING ||
                fullTunnelState == VpnState.CONNECTED
        )
    }

    fun start(context: Context): Boolean {
        if (isBlockedByClickVpn()) return false
        val i = Intent(context, V9VpnService::class.java).setAction(V9VpnService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
        else context.startService(i)
        return true
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, V9VpnService::class.java).setAction(V9VpnService.ACTION_STOP)
        )
    }

    val isRunning get() = V9VpnService.running
    val queries get() = V9VpnService.queries
    val blocked get() = V9VpnService.blocked

    /** Observe actual service state instead of sampling it after a fixed delay. */
    internal suspend fun awaitRunningState(expectedOn: Boolean): Boolean? =
        awaitVpnRunningState(isRunning, expectedOn)
}
