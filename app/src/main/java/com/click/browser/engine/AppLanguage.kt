package com.click.browser.engine

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * App language: English | اردو (Urdu) | Roman Urdu.
 *
 * Applied by wrapping the base context in [MainActivity.attachBaseContext].
 * The choice is dual-written: DataStore (source of truth for Compose) and a
 * plain SharedPreferences file (synchronous read in attachBaseContext, which
 * runs before coroutines are available). "Roman Urdu" uses the Latin script,
 * so the framework locale stays English while [UiStrings] swaps the
 * user-facing labels of the new Settings-suite screens to Roman Urdu.
 */
object AppLanguage {

    const val ENGLISH = "en"
    const val URDU = "ur"
    const val ROMAN = "roman"

    private const val TAG = "AppLanguage"
    private const val SYNC_PREFS = "click_app_prefs"
    private const val SYNC_LANG_KEY = "app_language_sync"

    fun normalize(code: String?): String = when (code?.lowercase()) {
        URDU -> URDU
        ROMAN -> ROMAN
        else -> ENGLISH
    }

    fun displayName(code: String): String = when (normalize(code)) {
        URDU -> "اردو (Urdu)"
        ROMAN -> "Roman Urdu"
        else -> "English"
    }

    /** Framework locale for the setting (Roman Urdu -> English/Latin). */
    fun localeFor(code: String): Locale = when (normalize(code)) {
        URDU -> Locale("ur")
        else -> Locale.ENGLISH
    }

    suspend fun current(context: Context): String {
        return try {
            normalize(context.dataStore.data.first()[AppSettings.APP_LANGUAGE])
        } catch (e: Exception) {
            Log.w(TAG, "language read failed", e); ENGLISH
        }
    }

    suspend fun set(context: Context, code: String) {
        val normalized = normalize(code)
        try {
            context.dataStore.edit { it[AppSettings.APP_LANGUAGE] = normalized }
        } catch (e: Exception) {
            Log.w(TAG, "language DataStore write failed", e)
        }
        // Synchronous mirror for attachBaseContext.
        try {
            context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                .edit().putString(SYNC_LANG_KEY, normalized).apply()
        } catch (e: Exception) {
            Log.w(TAG, "language sync-prefs write failed", e)
        }
    }

    /** Synchronous read for attachBaseContext. */
    fun currentSync(context: Context): String {
        return try {
            normalize(
                context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                    .getString(SYNC_LANG_KEY, ENGLISH)
            )
        } catch (_: Exception) { ENGLISH }
    }

    /** Wraps [base] with the saved locale. Call from attachBaseContext. */
    fun wrapContext(base: Context): Context =
        applyLocale(base, localeFor(currentSync(base)))

    fun applyLocale(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }
        return context.createConfigurationContext(config)
    }
}

/**
 * Roman-Urdu string overlay for the new Settings-suite screens.
 * English is the source of truth; Urdu uses the framework locale; Roman
 * Urdu swaps these key labels at render time.
 */
object UiStrings {
    private val romanMap = mapOf(
        // Privacy & Security
        "Privacy & Security" to "Privacy aur Security",
        "Click Account" to "Click Account",
        "Sign in with Google" to "Google se Sign in karein",
        "Sign out" to "Sign out karein",
        "Passwords" to "Passwords",
        "Save password" to "Password save karein",
        "Use saved" to "Saved istemal karein",
        "Safety Check" to "Safety Check",
        "Run Safety Check" to "Safety Check chalao",
        "Autofill" to "Autofill",
        "Payment methods" to "Payment methods",
        // Settings suite
        "Tab Groups" to "Tab Groups",
        "Accessibility" to "Accessibility",
        "Language" to "Zubaan",
        "Downloads" to "Downloads",
        "Reports" to "Reports",
        "Appearance" to "Appearance",
        "Feedback" to "Feedback",
        "Bug Report" to "Bug Report",
        "Crash Reports" to "Crash Reports",
        "Done" to "Ho gaya",
        "Cancel" to "Cancel",
        "Save" to "Save karein",
        "Delete" to "Delete karein",
        "Close" to "Band karein",
        "Back" to "Wapas"
    )

    /** Returns the Roman-Urdu label when [romanUrdu] is true. */
    fun s(english: String, romanUrdu: Boolean): String =
        if (romanUrdu) romanMap[english] ?: english else english
}
