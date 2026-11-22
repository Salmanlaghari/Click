package com.click.browser.engine

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

/**
 * Real app theme control (1.8.7 overhaul, A-list #1). The user picks Light,
 * Dark or Follow system in Settings → Appearance; the choice is persisted and
 * resolved against the system night mode by [resolveDark]. The light palette
 * variants below only tune the light look — darkness is a separate axis.
 */
object ThemeController {
    const val MODE_AUTO = "auto"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"
    const val MODE_SCHEDULED = "scheduled"

    fun normalizeLightThemeVariant(id: String?): LightThemeVariant =
        LightThemeVariant.fromId(id)

    /** Advances exactly one palette in the stable enum order, wrapping at the end. */
    fun nextLightThemeVariant(currentId: String?): LightThemeVariant {
        val variants = LightThemeVariant.values()
        val currentIndex = variants.indexOf(normalizeLightThemeVariant(currentId))
        return variants[(currentIndex + 1) % variants.size]
    }

    /** Resolves the palette to persist for one app launch. */
    fun variantForLaunch(lastAppliedId: String?, autoRotate: Boolean): LightThemeVariant =
        if (autoRotate) nextLightThemeVariant(lastAppliedId)
        else normalizeLightThemeVariant(lastAppliedId)

    /**
     * Canonical stored value: [MODE_LIGHT], [MODE_DARK] or [MODE_AUTO].
     * The legacy "scheduled" value folds into [MODE_AUTO] (follow system);
     * anything unknown also resolves to [MODE_AUTO].
     */
    fun normalize(mode: String?): String = when (mode?.trim()?.lowercase()) {
        MODE_LIGHT -> MODE_LIGHT
        MODE_DARK -> MODE_DARK
        MODE_AUTO, MODE_SCHEDULED -> MODE_AUTO
        else -> MODE_AUTO
    }

    fun displayName(mode: String): String = when (normalize(mode)) {
        MODE_LIGHT -> "Light"
        MODE_DARK -> "Dark"
        else -> "Follow system"
    }

    /** Pure resolution: should the app UI render dark for this stored mode? */
    fun resolveDark(themeMode: String?, systemDark: Boolean): Boolean =
        when (normalize(themeMode)) {
            MODE_LIGHT -> false
            MODE_DARK -> true
            else -> systemDark
        }

    fun isSystemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    suspend fun themeMode(context: Context): String =
        normalize(context.dataStore.data.first()[AppSettings.THEME_MODE])

    suspend fun setThemeMode(context: Context, mode: String) {
        val normalized = normalize(mode)
        context.dataStore.edit { prefs ->
            prefs[AppSettings.THEME_MODE] = normalized
            prefs[AppSettings.APPEARANCE_MODE] = normalized
            prefs[AppSettings.UI_DARK_MODE] = normalized == MODE_DARK
            // The single app-wide choice replaces the legacy per-mode flags.
            BrowserMode.values().forEach { prefs[AppSettings.darkModeKey(it)] = false }
        }
    }

    suspend fun isDarkNow(context: Context): Boolean =
        isDarkForMode(context, themeMode(context))

    fun isDarkForMode(context: Context, themeMode: String): Boolean =
        resolveDark(themeMode, isSystemDark(context))

    /** Legacy scheduled mode behaved like follow-system; kept for stored values. */
    suspend fun isDarkScheduled(context: Context): Boolean {
        val stored = context.dataStore.data.first()[AppSettings.THEME_MODE]
        return stored?.trim()?.lowercase() == MODE_SCHEDULED && isSystemDark(context)
    }
}
