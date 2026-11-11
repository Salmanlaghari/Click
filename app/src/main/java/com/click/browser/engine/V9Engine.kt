package com.click.browser.engine

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.ResultReceiver
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

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

    /** Cancel the one-shot process-relaunch alarm used by older Click builds. */
    fun cancelLegacyModeSwitchRelaunch(context: Context) {
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ?: return
        try {
            val pendingIntent = PendingIntent.getActivity(
                context,
                LEGACY_RESTART_REQUEST_CODE,
                launch,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            ) ?: return
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent)
            pendingIntent.cancel()
        } catch (t: Throwable) {
            Log.w(TAG, "Could not cancel an old mode-switch relaunch alarm", t)
        }
    }

    /**
     * Persists [mode], then opens a dedicated visible transition Activity in a
     * separate process. The transition stays in front while this browser
     * process exits, then opens MainActivity only after the old process is gone.
     * This avoids delayed background alarms and background-launch privileges.
     *
     * @param hackIntro legacy flag name; when true, the new process shows the
     *   Power Mode Markhor intro animation once (see HackIntroOverlay).
     * @param advanceIntro when true, the new process shows the Advance Mode
     *   teal intro animation once (see AdvanceIntroOverlay).
     * @return true only after the bridge has window focus and this process is
     *   about to exit; false leaves the current profile running.
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
            restoreSourceModeAfterFailedSwitch(context, modeManager, sourceMode, mode)
            Log.e(TAG, "V9: target profile could not be saved", t)
            if (t is kotlinx.coroutines.CancellationException) throw t
            return false
        }
        ClickDiagnosticsLog.record(
            context, ClickCatLogCode.MODE_SWITCH_TARGET_PERSISTED, sourceMode, mode
        )
        val activity = context as? Activity
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Log.e(TAG, "V9: no usable Activity for the profile handoff — staying in the current profile")
            restoreSourceModeAfterFailedSwitch(context, modeManager, sourceMode, mode)
            return false
        }
        // The switch is confirmed from an AlertDialog, which is a separate
        // window: the Activity does not hold window focus while the dialog is
        // showing, and focus only returns asynchronously after dismissal.
        // Checking hasWindowFocus() once, immediately, therefore fails the
        // switch on slower devices even though the user just confirmed it.
        // Wait briefly for focus to return (the bridge handoff below already
        // gets an 8-second focus budget; the source Activity previously got
        // none) and only abandon the switch if focus never comes back.
        val focusGained = try {
            awaitActivityWindowFocus(activity)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) {
                restoreSourceModeAfterFailedSwitch(context, modeManager, sourceMode, mode)
                throw t
            }
            Log.e(TAG, "V9: window-focus wait for the profile handoff failed", t)
            false
        }
        if (!focusGained) {
            Log.e(TAG, "V9: Activity did not regain window focus for the profile handoff — staying in the current profile")
            restoreSourceModeAfterFailedSwitch(context, modeManager, sourceMode, mode)
            return false
        }

        val bridgeFocused = try {
            withTimeoutOrNull(MODE_SWITCH_BRIDGE_FOCUS_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val readyReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    resultCode == com.click.browser.ModeSwitchActivity.RESULT_BRIDGE_WINDOW_FOCUSED
                                )
                            }
                        }
                    }
                    val bridge = Intent(activity, com.click.browser.ModeSwitchActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        putExtra(com.click.browser.ModeSwitchActivity.EXTRA_SOURCE_PID, Process.myPid())
                        putExtra(com.click.browser.ModeSwitchActivity.EXTRA_BRIDGE_READY_RECEIVER, readyReceiver)
                        if (hackIntro) putExtra(EXTRA_HACK_INTRO, true)
                        if (advanceIntro) putExtra(EXTRA_ADVANCE_INTRO, true)
                    }
                    try {
                        activity.startActivity(bridge)
                    } catch (t: Throwable) {
                        Log.e(TAG, "V9: cannot open the visible profile handoff", t)
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            } == true
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) {
                restoreSourceModeAfterFailedSwitch(context, modeManager, sourceMode, mode)
                throw t
            }
            Log.e(TAG, "V9: profile handoff did not become visible", t)
            false
        }

        if (!shouldRequestSourceProcessExit(bridgeHasWindowFocus = bridgeFocused)) {
            runCatching {
                activity.sendBroadcast(
                    Intent(com.click.browser.ModeSwitchActivity.ACTION_CANCEL)
                        .setPackage(activity.packageName)
                )
            }
            Log.e(TAG, "V9: no visible profile handoff — staying in the current profile")
            restoreSourceModeAfterFailedSwitch(context, modeManager, sourceMode, mode)
            return false
        }

        ClickDiagnosticsLog.record(
            context, ClickCatLogCode.MODE_SWITCH_BRIDGE_FOCUSED, sourceMode, mode
        )
        withContext(NonCancellable) {
            ClickDiagnosticsLog.record(
                context, ClickCatLogCode.MODE_SWITCH_EXIT_REQUESTED, sourceMode, mode
            )
            Process.killProcess(Process.myPid())
        }
        return true // unreachable
    }

    /**
     * Polls until the Activity (re)gains window focus or the wait budget in
     * [MODE_SWITCH_SOURCE_FOCUS_WAIT_MS] is exhausted. Returns the final
     * focus state; see [shouldKeepWaitingForSourceWindowFocus].
     */
    private suspend fun awaitActivityWindowFocus(activity: Activity): Boolean {
        val startedAt = SystemClock.elapsedRealtime()
        while (true) {
            val hasFocus = activity.hasWindowFocus()
            if (hasFocus) return true
            // Bail out immediately if the Activity went away during the wait
            // instead of polling until the budget is exhausted.
            if (activity.isFinishing || activity.isDestroyed) return false
            val waitedMs = SystemClock.elapsedRealtime() - startedAt
            if (!shouldKeepWaitingForSourceWindowFocus(hasFocus, waitedMs, MODE_SWITCH_SOURCE_FOCUS_WAIT_MS)) {
                return false
            }
            delay(MODE_SWITCH_FOCUS_POLL_MS)
        }
    }

    private suspend fun restoreSourceModeAfterFailedSwitch(
        context: Context,
        modeManager: ModeManager,
        sourceMode: BrowserMode,
        targetMode: BrowserMode,
    ) {
        withContext(NonCancellable) {
            try {
                modeManager.setMode(sourceMode)
            } catch (t: Throwable) {
                Log.e(TAG, "V9: could not restore the source profile after a failed handoff", t)
            }
            ClickDiagnosticsLog.failModeSwitch(context, sourceMode, targetMode)
        }
    }

    /** Legacy intent extra: show the Power Mode intro animation on launch. */
    const val EXTRA_HACK_INTRO = "v9_hack_intro"

    /** Intent extra: show the Advance Mode intro animation on launch. */
    const val EXTRA_ADVANCE_INTRO = "v9_advance_intro"

    private const val MODE_SWITCH_BRIDGE_FOCUS_TIMEOUT_MS = 8_000L
    private const val MODE_SWITCH_SOURCE_FOCUS_WAIT_MS = 2_500L
    private const val MODE_SWITCH_FOCUS_POLL_MS = 50L
    private const val LEGACY_RESTART_REQUEST_CODE = 9001

}
