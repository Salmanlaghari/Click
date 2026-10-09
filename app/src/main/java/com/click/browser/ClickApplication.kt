package com.click.browser

import android.app.Application
import com.click.browser.engine.V9Engine

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
    }
}
