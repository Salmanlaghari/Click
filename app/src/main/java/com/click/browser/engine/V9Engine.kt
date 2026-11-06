package com.click.browser.engine

import android.app.AlarmManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import android.webkit.WebView
import androidx.annotation.RequiresApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal data class ProfileBootFailureDecision(
    val bootMode: BrowserMode,
    val blockBrowser: Boolean,
)

internal fun profileBootFailureDecision(
    requestedMode: BrowserMode?,
): ProfileBootFailureDecision = when (requestedMode) {
    null -> ProfileBootFailureDecision(BrowserMode.SIMPLE, blockBrowser = true)
    BrowserMode.ADVANCED -> ProfileBootFailureDecision(BrowserMode.ADVANCED, blockBrowser = true)
    else -> ProfileBootFailureDecision(BrowserMode.SIMPLE, blockBrowser = false)
}

/**
 * Manages browser-data profiles backed by Android System WebView.
 * Each profile has a separate WebView data directory for cookies/cache. Advance
 * also has separate app-level browsing data; Simple, Developer and Power share
 * app-level data. Profiles do not substitute the device's User-Agent or fingerprint.
 * Because WebView's data-directory suffix is process-wide and can only be set
 * before any WebView exists, switching profiles restarts the app process.
 */
object V9Engine {

    private const val TAG = "V9Engine"
    const val VERSION = "V9"
    const val BRAND_LINE = "V9 · 1 Browser · 4 Profiles"
    const val TAGLINE = "First time in the World We Present A Superior Testing Future"

    /** The profile this process booted with (set in [applyDataDirectorySuffix]).
     *
     * SINGLE SOURCE OF TRUTH for the active browser-data profile. The WebView
     * data-directory suffix and [profileDataStore] use this value. It is pinned once in
     * [ClickApplication.onCreate] and never changes afterwards: a mode switch
     * restarts the process rather than mutating it (see [restartForEngineSwitch]).
     * If Advanced isolation cannot be initialized, browser startup is blocked.
     */
    @Volatile
    var bootMode: BrowserMode = BrowserMode.SIMPLE
        private set

    /**
     * True once [applyDataDirectorySuffix] has run (called first thing in
     * [ClickApplication.onCreate]). Code that resolves per-profile state
     * ([profileDataStore]) must only run after this
     * is true; they fail fast otherwise instead of silently touching the
     * wrong profile.
     */
    @Volatile
    private var bootPinned = false

    /** See [bootPinned]. */
    val isBootPinned: Boolean get() = bootPinned

    @Volatile
    private var bootBlocked = false

    /** True when profile initialization failed and browser data must stay closed. */
    val isBootBlocked: Boolean get() = bootBlocked

    data class EngineProfile(
        val mode: BrowserMode,
        val deviceLabel: String,
    )

    fun profileFor(mode: BrowserMode): EngineProfile = EngineProfile(
        mode = mode,
        deviceLabel = if (mode == BrowserMode.ADVANCED) {
            "Android System WebView · separate cookies, cache and app data"
        } else {
            "Android System WebView · separate cookies/cache; shared app data"
        }
    )

    fun suffixFor(mode: BrowserMode): String = "v9_" + mode.name.lowercase()

    /**
     * Must be called from Application.onCreate, before any WebView is created.
     * Reads the saved mode synchronously (single DataStore read) and pins the
     * process to that engine's data directory.
     */
    fun applyDataDirectorySuffix(context: Context) {
        var requestedMode: BrowserMode? = null
        try {
            val modeStr = runBlocking {
                context.dataStore.data.first()[ModeManager.MODE_KEY]
            } ?: BrowserMode.SIMPLE.name
            val mode = try {
                BrowserMode.valueOf(modeStr)
            } catch (_: Exception) {
                throw IllegalStateException("Saved browser mode is not recognized")
            }
            requestedMode = mode
            bootMode = mode
            WebView.setDataDirectorySuffix(suffixFor(mode))
            Log.i(TAG, "V9 browser profile online: ${suffixFor(mode)} (${profileFor(mode).deviceLabel})")
        } catch (t: Throwable) {
            val decision = profileBootFailureDecision(requestedMode)
            bootMode = decision.bootMode
            bootBlocked = decision.blockBrowser
            if (decision.blockBrowser) {
                Log.e(TAG, "V9 profile initialization failed; browser startup is blocked to prevent cross-profile data access", t)
            } else {
                // Preserve the legacy fallback for known non-Advanced modes.
                // A failed mode read is different: without knowing the saved
                // mode, opening shared data could expose another profile.
                Log.e(TAG, "V9 data-directory suffix failed for ${requestedMode?.name}; using legacy Simple profile", t)
            }
        } finally {
            // Pin even on failure so accessors can reject profile data until
            // initialization is known to be safe.
            bootPinned = true
        }
    }

    /** True when switching to [mode] requires a process restart (profile change). */
    fun needsRestart(mode: BrowserMode): Boolean = mode != bootMode

    /**
     * Persists [mode] then restarts the app process so the new profile's data
     * directory takes effect. setMode() is suspend and returns after the
     * DataStore write completes, so the new mode is durable before we die.
     *
     * A process restart is needed because WebView's data-directory suffix is
     * process-wide. Use a permission-free inexact alarm to launch the Activity
     * after this process exits. Inexact delivery may be delayed by Android; do
     * not claim an exact restart time or add exact-alarm permissions. A direct
     * Activity launch in the still-running singleTask process is not a cold
     * restart and must never be used to justify killing that process.
     *
     * @param hackIntro legacy flag name; when true, the relaunched process shows
     *   the Power Mode Markhor intro animation once (see HackIntroOverlay).
     * @param advanceIntro when true, the relaunched process shows the Advance
     *   Mode teal intro animation once (see AdvanceIntroOverlay).
     * @return true if a cold relaunch alarm was scheduled; false if the caller
     *   must keep the current profile selected.
     */
    suspend fun restartForEngineSwitch(
        context: Context,
        modeManager: ModeManager,
        mode: BrowserMode,
        hackIntro: Boolean = false,
        advanceIntro: Boolean = false,
    ): Boolean {
        val sourceMode = bootMode
        try {
            modeManager.setMode(mode)
        } catch (t: Throwable) {
            ClickDiagnosticsLog.failModeSwitch(context, sourceMode, mode)
            Log.e(TAG, "V9: target profile could not be saved", t)
            if (t is kotlinx.coroutines.CancellationException) throw t
            return false
        }
        ClickDiagnosticsLog.record(
            context, ClickCatLogCode.MODE_SWITCH_TARGET_PERSISTED, sourceMode, mode
        )

        // 1. Build the relaunch intent. If this fails we MUST NOT kill the
        // process — better to stay alive on the old engine than to die.
        val launch: Intent? = try {
            context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                ?.also {
                    if (hackIntro) it.putExtra(EXTRA_HACK_INTRO, true)
                    if (advanceIntro) it.putExtra(EXTRA_ADVANCE_INTRO, true)
                }
        } catch (t: Throwable) {
            Log.e(TAG, "V9: cannot build relaunch intent", t)
            null
        }
        if (launch == null) {
            Log.e(TAG, "V9: no launch intent — staying in the current profile")
            ClickDiagnosticsLog.failModeSwitch(context, sourceMode, mode)
            return false
        }

        // 2. Schedule an inexact RTC_WAKEUP relaunch. setAlarmClock is an exact
        // alarm and can throw SecurityException for this app (target SDK 36,
        // no exact-alarm permission). A direct startActivity here would reuse
        // the current process and is not a safe substitute.
        var scheduled = false
        try {
            val pi = createModeSwitchRelaunchPendingIntent(context, launch)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + RELAUNCH_DELAY_MS,
                pi
            )
            scheduled = true
        } catch (t: Throwable) {
            Log.e(TAG, "V9 restart schedule failed", t)
        }

        if (!hasEngineRestartPath(scheduled)) {
            // Last resort: do NOT kill or pretend the process-pinned profile
            // changed. The caller restores the saved selection to bootMode.
            Log.e(TAG, "V9: no restart path available — staying in the current profile")
            ClickDiagnosticsLog.failModeSwitch(context, sourceMode, mode)
            return false
        }
        ClickDiagnosticsLog.record(
            context, ClickCatLogCode.MODE_SWITCH_RELAUNCH_SCHEDULED, sourceMode, mode
        )

        // 3. Let the Activity finish its current UI work, then exit so the
        // scheduled PendingIntent can create a fresh process using the target
        // profile. This remains non-cancellable because it is called from the
        // Activity's coroutine scope.
        withContext(NonCancellable) {
            delay(PROCESS_EXIT_DELAY_MS)
            ClickDiagnosticsLog.record(
                context, ClickCatLogCode.MODE_SWITCH_EXIT_REQUESTED, sourceMode, mode
            )
            Process.killProcess(Process.myPid())
        }
        return true // unreachable
    }

    /**
     * Grant only this one-shot, explicit launcher PendingIntent the creator
     * background-start mode needed to reopen after this user-requested process exit.
     */
    private fun createModeSwitchRelaunchPendingIntent(
        context: Context,
        launch: Intent,
    ): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        val options = when (relaunchPendingIntentCreatorBackgroundStartMode(Build.VERSION.SDK_INT)) {
            RelaunchBackgroundActivityStartMode.PLATFORM_DEFAULT -> null
            RelaunchBackgroundActivityStartMode.ALLOW_BACKGROUND ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    backgroundActivityStartAllowedOptions()
                } else {
                    null
                }
            RelaunchBackgroundActivityStartMode.ALLOW_ALWAYS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    backgroundActivityStartAllowAlwaysOptions()
                } else {
                    null
                }
        }
        return if (options == null) {
            PendingIntent.getActivity(context, RESTART_REQUEST_CODE, launch, flags)
        } else {
            PendingIntent.getActivity(
                context, RESTART_REQUEST_CODE, launch, flags, options.toBundle()
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Suppress("DEPRECATION") // ALLOWED is the correct mode on API 34-35.
    private fun backgroundActivityStartAllowedOptions(): ActivityOptions =
        ActivityOptions.makeBasic()
            .setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun backgroundActivityStartAllowAlwaysOptions(): ActivityOptions =
        ActivityOptions.makeBasic()
            .setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            )

    /** Legacy intent extra: show the Power Mode intro animation on launch. */
    const val EXTRA_HACK_INTRO = "v9_hack_intro"

    /** Intent extra: show the Advance Mode intro animation on launch. */
    const val EXTRA_ADVANCE_INTRO = "v9_advance_intro"

    private const val RESTART_REQUEST_CODE = 9001
    private const val PROCESS_EXIT_DELAY_MS = 700L
    private const val RELAUNCH_DELAY_MS = 3_000L

}
