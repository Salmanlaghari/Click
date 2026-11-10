package com.click.browser

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.click.browser.engine.AppSettings
import com.click.browser.engine.ClickDiagnosticsLog
import com.click.browser.engine.NewsNotificationScheduler
import com.click.browser.engine.V9Engine
import com.click.browser.engine.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Application entry point. The browser process pins itself to its V9 engine
 * data directory before creating a WebView. The temporary mode-switch process
 * displays only a handoff screen and deliberately creates no WebView.
 */
class ClickApplication : Application() {
    override fun onCreate() {
        val isModeSwitchProcess = isModeSwitchProcess()
        if (!isModeSwitchProcess) {
            V9Engine.applyDataDirectorySuffix(this)
            V9Engine.cancelLegacyModeSwitchRelaunch(this)
        }
        super.onCreate()
        if (isModeSwitchProcess) return
        ClickDiagnosticsLog.resolveStartup(this, V9Engine.bootMode, V9Engine.isBootBlocked)
        if (V9Engine.isBootBlocked) {
            Log.e("ClickApplication", "Skipping browsing startup tasks because the selected profile could not be initialized safely")
            return
        }
        // Run policy-remediation cleanup before any Activity can observe legacy
        // state, but skip coroutine startup after its versioned marker is written.
        if (!com.click.browser.engine.LegacyPolicyFeatureCleanup.isComplete(this)) {
            runBlocking {
                com.click.browser.engine.LegacyPolicyFeatureCleanup.run(this@ClickApplication)
            }
        }
        // Crash + OOM reports: capture to files dir, user sends them from
        // Settings → Reports. Never uploads silently.
        com.click.browser.engine.CrashReporter.install(this)
        // Background news alerts: re-schedule the battery-friendly periodic
        // check if the user previously opted in (default OFF).
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = dataStore.data.first()[AppSettings.NEWS_NOTIFICATIONS_ENABLED] == true
                if (enabled) NewsNotificationScheduler.schedule(this@ClickApplication)
            } catch (e: Exception) {
                Log.w("NewsNotifications", "startup schedule check failed", e)
            }
        }
    }

    private fun isModeSwitchProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }
                ?.processName
        }
        return processName == "$packageName${ModeSwitchActivity.PROCESS_SUFFIX}"
    }
}
