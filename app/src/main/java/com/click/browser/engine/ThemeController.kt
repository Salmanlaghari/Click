package com.click.browser.engine

import android.content.Context
import androidx.datastore.preferences.core.edit

/** Legacy theme API retained for compatibility; Click's appearance is light-only. */
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

    @Suppress("UNUSED_PARAMETER")
    suspend fun themeMode(context: Context): String = MODE_LIGHT

    @Suppress("UNUSED_PARAMETER")
    suspend fun setThemeMode(context: Context, mode: String) {
        context.dataStore.edit { prefs ->
            prefs[AppSettings.THEME_MODE] = MODE_LIGHT
            prefs[AppSettings.APPEARANCE_MODE] = MODE_LIGHT
            prefs[AppSettings.UI_DARK_MODE] = false
            BrowserMode.values().forEach { prefs[AppSettings.darkModeKey(it)] = false }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun normalize(mode: String?): String = MODE_LIGHT

    @Suppress("UNUSED_PARAMETER")
    fun displayName(mode: String): String = "Light"

    @Suppress("UNUSED_PARAMETER")
    suspend fun isDarkNow(context: Context, mode: BrowserMode): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun isDarkForMode(context: Context, themeMode: String): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    suspend fun isDarkScheduled(context: Context): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun isSystemDark(context: Context): Boolean = false
}
