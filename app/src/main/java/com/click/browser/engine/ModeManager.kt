package com.click.browser.engine

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "browser_settings")

class ModeManager(private val context: Context) {

    companion object {
        private val MODE_KEY = stringPreferencesKey("browser_mode")

        const val UA_SIMPLE = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        const val UA_DEVELOPER = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36 DevTools"
        const val UA_HACK = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }

    val modeFlow: Flow<BrowserMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[MODE_KEY] ?: BrowserMode.SIMPLE.name
        try {
            BrowserMode.valueOf(modeStr)
        } catch (e: Exception) {
            BrowserMode.SIMPLE
        }
    }

    suspend fun setMode(mode: BrowserMode) {
        context.dataStore.edit { preferences ->
            preferences[MODE_KEY] = mode.name
        }
    }

    fun applySettings(webView: WebView, mode: BrowserMode, forceDesktop: Boolean = false) {
        val settings = webView.settings

        // General always-on configs as requested
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false // Enabled media playback
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        when (mode) {
            BrowserMode.SIMPLE -> {
                settings.userAgentString = UA_SIMPLE
                // Wide viewport and overview mode disabled
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = false
                // Normal layout
                settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            }
            BrowserMode.DEVELOPER -> {
                settings.userAgentString = UA_DEVELOPER
                // Optional toggle for desktop mode, if requested, we can handle via forceDesktop parameter
                if (forceDesktop) {
                    settings.userAgentString = UA_HACK // Spoof desktop if emulator wants desktop
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                } else {
                    settings.useWideViewPort = false
                    settings.loadWithOverviewMode = false
                }
            }
            BrowserMode.HACK -> {
                settings.userAgentString = UA_HACK
                // Force desktop mode enabled
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                // Hack mode viewport specs (1920x1080) can also be controlled on layout / JS injection side
            }
        }
    }
}
