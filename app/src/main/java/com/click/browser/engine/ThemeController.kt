package com.click.browser.engine

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Day/Night theme control: Auto (follow system) | Light | Dark | Scheduled.
 *
 * Replaces the old boolean UI_DARK_MODE toggle (migrated on first read).
 * Per-mode overrides ([AppSettings.darkModeKey]) still win over the global
 * mode — they are explicit user choices per engine.
 */
object ThemeController {

    const val MODE_AUTO = "auto"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"
    const val MODE_SCHEDULED = "scheduled"

    private const val TAG = "ThemeController"

    /** Reads the theme mode, migrating the legacy boolean on first use. */
    suspend fun themeMode(context: Context): String {
        val prefs = context.dataStore.data.first()
        prefs[AppSettings.THEME_MODE]?.let { return normalize(it) }
        // Migrate legacy boolean: UI_DARK_MODE=false -> light, else dark.
        val legacyDark = prefs[AppSettings.UI_DARK_MODE] ?: true
        val migrated = if (legacyDark) MODE_DARK else MODE_LIGHT
        try {
            context.dataStore.edit { it[AppSettings.THEME_MODE] = migrated }
        } catch (e: Exception) {
            Log.w(TAG, "theme migration write failed", e)
        }
        return migrated
    }

    suspend fun setThemeMode(context: Context, mode: String) {
        val normalized = normalize(mode)
        context.dataStore.edit { it[AppSettings.THEME_MODE] = normalized }
    }

    fun normalize(mode: String?): String = when (mode?.lowercase()) {
        MODE_AUTO, MODE_LIGHT, MODE_DARK, MODE_SCHEDULED -> mode.lowercase()
        else -> MODE_DARK // historical default was Dark
    }

    fun displayName(mode: String): String = when (normalize(mode)) {
        MODE_AUTO -> "Auto (system)"
        MODE_LIGHT -> "Light"
        MODE_DARK -> "Dark"
        else -> "Scheduled"
    }

    /**
     * Effective dark flag for [mode] right now: per-mode override wins,
     * otherwise the global theme mode (auto = system, scheduled = clock).
     */
    suspend fun isDarkNow(context: Context, mode: BrowserMode): Boolean {
        val prefs = context.dataStore.data.first()
        prefs[AppSettings.darkModeKey(mode)]?.let { return it }
        val themeMode = normalize(prefs[AppSettings.THEME_MODE])
        return if (themeMode == MODE_SCHEDULED) isDarkScheduled(context)
        else isDarkForMode(context, themeMode)
    }

    /** Pure function — easy to unit-test the schedule logic. */
    fun isDarkForMode(context: Context, themeMode: String): Boolean = when (themeMode) {
        MODE_LIGHT -> false
        MODE_DARK -> true
        // Scheduled needs DataStore hours — use isDarkScheduled() instead.
        MODE_SCHEDULED -> isSystemDark(context)
        else -> isSystemDark(context)
    }

    /** Scheduled variant with user-configured hours. */
    suspend fun isDarkScheduled(context: Context): Boolean {
        val prefs = context.dataStore.data.first()
        val start = (prefs[AppSettings.THEME_SCHEDULE_START_HOUR] ?: 6).coerceIn(0, 23)
        val end = (prefs[AppSettings.THEME_SCHEDULE_END_HOUR] ?: 19).coerceIn(0, 23)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val light = if (start < end) hour in start until end else hour >= start || hour < end
        return !light
    }

    fun isSystemDark(context: Context): Boolean {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
    }
}
