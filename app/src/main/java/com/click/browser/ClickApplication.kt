package com.click.browser

import android.app.Application
import android.util.Log
import com.click.browser.engine.AppSettings
import com.click.browser.engine.NewsNotificationScheduler
import com.click.browser.engine.V9Engine
import com.click.browser.engine.dataStore
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "AdMobAds"

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
        // Initialize Google AdMob (news-feed banner + native ads).
        // Initialization is async and safe on the main thread.
        MobileAds.initialize(this) { status ->
            Log.d(TAG, "MobileAds initialized: ${status.adapterStatusMap.keys}")
        }
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
