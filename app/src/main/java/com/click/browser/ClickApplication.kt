package com.click.browser

import android.app.Application
import android.util.Log
import com.click.browser.engine.V9Engine
import com.google.android.gms.ads.MobileAds

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
    }
}
