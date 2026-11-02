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
        // Public: V9Engine reads the saved mode synchronously in Application.onCreate
        // to pin the process to the correct engine data directory.
        val MODE_KEY = stringPreferencesKey("browser_mode")
    }

    val modeFlow: Flow<BrowserMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[MODE_KEY] ?: BrowserMode.SIMPLE.name
        try {
            BrowserMode.valueOf(modeStr)
        } catch (_: Exception) {
            BrowserMode.SIMPLE
        }
    }

    suspend fun setMode(mode: BrowserMode) {
        context.dataStore.edit { preferences ->
            preferences[MODE_KEY] = mode.name
        }
    }

    /**
     * Applies mode-specific WebSettings without changing the browser identity.
     * All modes use Android System WebView's actual User-Agent. Wide layout
     * changes only standard viewport settings, not the requested site identity.
     */
    fun applySettings(webView: WebView, mode: BrowserMode, forceDesktop: Boolean = false) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        val layout = DesktopLayoutPolicy.webViewLayout(
            DesktopLayoutPolicy.defaultEnabled(mode, forceDesktop)
        )
        settings.useWideViewPort = layout.useWideViewPort
        settings.loadWithOverviewMode = layout.loadWithOverviewMode
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

        if (mode == BrowserMode.ADVANCED) {
            // These are genuine WebSettings controls; gains are incremental.
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.loadsImagesAutomatically = true
            settings.blockNetworkImage = false
            settings.domStorageEnabled = true
        }
    }

    /**
     * Apply only the resolved per-site viewport setting. A layout toggle must
     * not reset unrelated JavaScript, playback, or privacy preferences.
     */
    @Suppress("UNUSED_PARAMETER")
    fun applyDesktopOverride(webView: WebView, mode: BrowserMode, desktop: Boolean) {
        val layout = DesktopLayoutPolicy.webViewLayout(desktop)
        webView.settings.useWideViewPort = layout.useWideViewPort
        webView.settings.loadWithOverviewMode = layout.loadWithOverviewMode
    }
}
