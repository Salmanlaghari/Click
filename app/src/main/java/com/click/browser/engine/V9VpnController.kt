package com.click.browser.engine

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build

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

    fun start(context: Context) {
        val i = Intent(context, V9VpnService::class.java).setAction(V9VpnService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
        else context.startService(i)
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, V9VpnService::class.java).setAction(V9VpnService.ACTION_STOP)
        )
    }

    val isRunning get() = V9VpnService.running
    val queries get() = V9VpnService.queries
    val blocked get() = V9VpnService.blocked
}
