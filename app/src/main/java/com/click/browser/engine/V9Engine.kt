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
     * restarts the process rather than mutating it (see [restartForEngineSwitch];
     * the in-place fallback is a documented degraded mode with no isolation).
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
        try {
            val modeStr = runBlocking {
                context.dataStore.data.first()[ModeManager.MODE_KEY]
            } ?: BrowserMode.SIMPLE.name
            val mode = try {
                BrowserMode.valueOf(modeStr)
            } catch (_: Exception) {
                BrowserMode.SIMPLE
            }
            bootMode = mode
            WebView.setDataDirectorySuffix(suffixFor(mode))
            Log.i(TAG, "V9 browser profile online: ${suffixFor(mode)} (${profileFor(mode).deviceLabel})")
        } catch (t: Throwable) {
            Log.e(TAG, "V9 data-directory suffix failed; engines share storage", t)
            // Fall back consistently: the WebView is now on SHARED storage, so
            // the app-data profile must follow suit — otherwise cookies would
            // land in shared storage while bookmarks/history went per-profile.
            bootMode = BrowserMode.SIMPLE
        } finally {
            // Pin the flag even on failure: bootMode then holds the
            // best-known value (default SIMPLE) and every profile decision
            // consistently degrades to the shared store — never half-pinned.
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
     *  1. NEVER kills the process unless a restart is actually scheduled
     *     (returns false so the caller can apply the mode in-place instead).
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
     * @return true if a restart was triggered; false if we stayed alive
     *   (caller should apply the mode in-place without engine isolation).
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
            Log.e(TAG, "V9: no launch intent — staying alive, mode applied in-place")
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
        try {
            context.startActivity(launch)
        } catch (t: Throwable) {
            Log.e(TAG, "V9: direct relaunch failed, relying on alarm", t)
        }

        if (!scheduled) {
            // Last resort: do NOT kill — a live app on the old engine beats
            // a dead app. Caller applies the mode in-place.
            Log.e(TAG, "V9: restart not scheduled — staying alive")
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
