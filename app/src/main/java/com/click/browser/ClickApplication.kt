package com.click.browser

import android.app.Application
import android.util.Log
import com.click.browser.engine.AppSettings
import com.click.browser.engine.NewsNotificationScheduler
import com.click.browser.engine.V9Engine
import com.click.browser.engine.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Application entry point.
 *
 * The FIRST thing we do is pin this process to its V9 engine data directory
 * ([V9Engine.applyDataDirectorySuffix]) — this must happen before any WebView
 * is instantiated anywhere, otherwise the per-engine isolation silently fails.
 */
class ClickApplication : Application() {
    override fun onCreate() {
        V9Engine.applyDataDirectorySuffix(this)
        super.onCreate()
        // Finish the local policy-remediation migration before any Activity can
        // observe legacy shortcuts or settings during its first composition.
        runBlocking {
            com.click.browser.engine.LegacyPolicyFeatureCleanup.run(this@ClickApplication)
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
}
