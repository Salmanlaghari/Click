package com.click.browser.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import android.webkit.WebView
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
     * RELIABILITY: the old code called Process.killProcess() UNCONDITIONALLY,
     * even when the restart alarm was never scheduled (null launch intent or
     * exception) — the app died and never came back. It also used an inexact
     * 400ms alarm that MIUI/OEM battery optimizers routinely swallow after a
     * kill. This version:
     *  1. NEVER kills the process unless a restart alarm is scheduled or the
     *     relaunch Activity was started successfully.
     *  2. Uses AlarmManager.setAlarmClock (alarm-clock alarms are delivered
     *     at the highest priority and are NOT swallowed by MIUI/Oppo/Vivo
     *     battery optimizers after a kill — the classic OEM-proof restart
     *     trick; needs no SCHEDULE_EXACT_ALARM permission) with a 1s delay.
     *  3. Also starts the relaunch intent directly as a backup before dying.
     *
     * @param hackIntro legacy flag name; when true, the relaunched process shows
     *   the Power Mode Markhor intro animation once (see HackIntroOverlay).
     * @param advanceIntro when true, the relaunched process shows the Advance
     *   Mode teal intro animation once (see AdvanceIntroOverlay).
     * @return true if a restart path was started; false if neither the alarm
     *   nor direct relaunch could be started. The caller must keep the current
     *   profile selected when false is returned.
     */
    suspend fun restartForEngineSwitch(
        context: Context,
        modeManager: ModeManager,
        mode: BrowserMode,
        hackIntro: Boolean = false,
        advanceIntro: Boolean = false,
    ): Boolean {
        modeManager.setMode(mode)

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
            return false
        }

        // 2. Schedule the relaunch via AlarmManager.setAlarmClock. Alarm-clock
        // alarms are the OEM-proof restart mechanism: unlike
        // setAndAllowWhileIdle (which MIUI's battery optimizer swallows after
        // the process is killed), alarm-clock alarms are delivered at the
        // highest priority on all skins — Xiaomi/Oppo/Vivo treat them as
        // user-visible and do not suppress them. No SCHEDULE_EXACT_ALARM
        // permission is needed for setAlarmClock. The showIntent is null —
        // tapping the transient status-bar alarm icon does nothing; the
        // operation PendingIntent is what relaunches the app.
        var scheduled = false
        try {
            val pi = PendingIntent.getActivity(
                context, RESTART_REQUEST_CODE, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(
                    System.currentTimeMillis() + RESTART_DELAY_MS,
                    null
                ),
                pi
            )
            scheduled = true
        } catch (t: Throwable) {
            Log.e(TAG, "V9 restart schedule failed", t)
        }

        // 3. Backup: start the relaunch intent directly NOW. With
        // CLEAR_TASK|NEW_TASK the old stack is torn down. If an OEM skin
        // swallows the alarm after killProcess, the system still has a live
        // activity record to keep the app visible.
        var directRelaunchStarted = false
        try {
            context.startActivity(launch)
            directRelaunchStarted = true
        } catch (t: Throwable) {
            Log.e(TAG, "V9: direct relaunch failed, relying on alarm", t)
        }

        if (!hasEngineRestartPath(scheduled, directRelaunchStarted)) {
            // Last resort: do NOT kill or pretend the process-pinned profile
            // changed. The caller restores the saved selection to bootMode.
            Log.e(TAG, "V9: no restart path available — staying in the current profile")
            return false
        }

        // 4. Give the alarm + the directly-started activity a moment, then die.
        // The alarm fires in a FRESH process where ClickApplication pins the
        // new engine's WebView data directory.
        //
        // RACE FIX (mode switch "not working", reported in Simple mode): this
        // MUST run non-cancellably. The caller invokes us from the Activity's
        // coroutine scope (rememberCoroutineScope), and step 3's CLEAR_TASK
        // destroys that Activity -> its scope is cancelled -> a plain delay()
        // throws CancellationException and killProcess() NEVER runs. The app
        // would then survive on the OLD engine while the UI shows the NEW
        // mode (the DataStore was already updated in step 0) — the exact
        // "mode switch doesn't work" symptom. NonCancellable guarantees the
        // kill happens no matter what the Activity does.
        withContext(NonCancellable) {
            delay(RESTART_DELAY_MS)
            Process.killProcess(Process.myPid())
        }
        return true // unreachable
    }

    /** Legacy intent extra: show the Power Mode intro animation on launch. */
    const val EXTRA_HACK_INTRO = "v9_hack_intro"

    /** Intent extra: show the Advance Mode intro animation on launch. */
    const val EXTRA_ADVANCE_INTRO = "v9_advance_intro"

    private const val RESTART_REQUEST_CODE = 9001
    private const val RESTART_DELAY_MS = 1000L

}
