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
 * V9 — "1 Browser, 4 Engines".
 *
 * Prince's signature feature: one Click Browser app that behaves as four
 * completely separate browsers (Simple / Developer / Hack / Advance). Each
 * engine has:
 *  - its own WebView data directory (separate cookies, cache, localStorage,
 *    history, permissions) via [WebView.setDataDirectorySuffix]
 *  - its own app-data profile (bookmarks, history, passwords, userscripts —
 *    see [profileDataStore]; Advance starts empty by design)
 *  - its own User-Agent + JS fingerprint profile, so websites (e.g. Google)
 *    see four different browsers/devices
 *
 * HONEST LIMIT: the renderer in every mode is the system WebView (Chromium).
 * "Advance" is an isolated, performance-tuned profile — not a new engine
 * technology. OS-level identifiers (ANDROID_ID, ro.build.fingerprint)
 * cannot be spoofed per-mode without root. Websites identify browsers via
 * cookies + User-Agent + JS fingerprint — all three ARE fully distinct
 * per engine here, which achieves the goal.
 *
 * Because the data-directory suffix is process-wide and can only be set once
 * before any WebView exists, switching engines restarts the app process
 * ([restartForEngineSwitch]). The suffix is applied in [ClickApplication.onCreate].
 */
object V9Engine {

    private const val TAG = "V9Engine"
    const val VERSION = "V9"
    const val BRAND_LINE = "V9 · 1 Browser · 4 Engines"
    const val TAGLINE = "First time in the World We Present A Superior Testing Future"

    /** The engine this process booted with (set in [applyDataDirectorySuffix]).
     *
     * SINGLE SOURCE OF TRUTH for the process's engine identity. Every
     * profile decision — the WebView data-directory suffix, [profileDataStore],
     * [UserscriptManager]'s script folder — keys off this value, so they can
     * never disagree with each other. It is pinned once in
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
     * ([profileDataStore], [UserscriptManager.dir]) must only run after this
     * is true; they fail fast otherwise instead of silently touching the
     * wrong profile.
     */
    @Volatile
    private var bootPinned = false

    /** See [bootPinned]. */
    val isBootPinned: Boolean get() = bootPinned

    data class EngineProfile(
        val mode: BrowserMode,
        val userAgent: String,
        val platform: String,
        val vendor: String,
        val languages: List<String>,
        val hardwareConcurrency: Int,
        val deviceMemory: Int,
        val screenW: Int,
        val screenH: Int,
        val devicePixelRatio: Double,
        val maxTouchPoints: Int,
        val webglVendor: String,
        val webglRenderer: String,
        /** Seed for canvas noise: stable within an engine, distinct across engines. */
        val canvasSeed: Long,
        /** Human-readable device identity shown in the V9 Shield screen. */
        val deviceLabel: String,
        /** Spoofed timezone (IANA name) — distinct per engine. */
        val timezone: String,
        /** Spoofed timezone offset in minutes (for Date.getTimezoneOffset). */
        val timezoneOffsetMinutes: Int,
    )

    fun profileFor(mode: BrowserMode): EngineProfile = when (mode) {
        BrowserMode.SIMPLE -> EngineProfile(
            mode = mode,
            userAgent = ModeManager.UA_SIMPLE,
            platform = "Linux armv8l",
            vendor = "Google Inc.",
            languages = listOf("en-US", "en"),
            hardwareConcurrency = 8,
            deviceMemory = 8,
            screenW = 1080,
            screenH = 2400,
            devicePixelRatio = 2.625,
            maxTouchPoints = 5,
            webglVendor = "Google Inc. (ARM)",
            webglRenderer = "ANGLE (ARM, Mali-G715 MC7, OpenGL ES 3.2)",
            canvasSeed = 5101151L,
            deviceLabel = "Pixel 8 · Android 14 · Chrome Mobile",
            timezone = "America/New_York",
            timezoneOffsetMinutes = 300,
        )
        BrowserMode.DEVELOPER -> EngineProfile(
            mode = mode,
            userAgent = ModeManager.UA_DEVELOPER,
            platform = "Linux armv8l",
            vendor = "Google Inc.",
            languages = listOf("en-GB", "en"),
            hardwareConcurrency = 8,
            deviceMemory = 12,
            screenW = 1080,
            screenH = 2340,
            devicePixelRatio = 3.0,
            maxTouchPoints = 5,
            webglVendor = "Google Inc. (Qualcomm)",
            webglRenderer = "ANGLE (Qualcomm, Adreno 750, OpenGL ES 3.2)",
            canvasSeed = 90231117L,
            deviceLabel = "Galaxy S24 · Android 14 · Chrome Mobile",
            timezone = "Europe/London",
            timezoneOffsetMinutes = 0,
        )
        BrowserMode.HACK -> EngineProfile(
            mode = mode,
            userAgent = ModeManager.UA_HACK,
            platform = "Win32",
            vendor = "Google Inc.",
            languages = listOf("en-US", "en"),
            hardwareConcurrency = 16,
            deviceMemory = 16,
            screenW = 1920,
            screenH = 1080,
            devicePixelRatio = 1.0,
            maxTouchPoints = 0,
            webglVendor = "Google Inc. (NVIDIA)",
            webglRenderer = "ANGLE (NVIDIA, NVIDIA GeForce RTX 4070 Ti/PCIe/SSE2, OpenGL 4.5)",
            canvasSeed = 90031991L,
            deviceLabel = "Windows 11 · Chrome Desktop",
            timezone = "America/Los_Angeles",
            // UTC-8 (standard time); getTimezoneOffset() counts minutes west of UTC.
            timezoneOffsetMinutes = 480,
        )
        // Click Advance: desktop-class like Hack, but a DISTINCT identity
        // (newer Chrome build, different GPU/timezone/seed) so sites see a
        // fourth, separate browser. Performance-tuned profile — the renderer
        // is still the system WebView; this is isolation + tuning, not a new
        // engine.
        BrowserMode.ADVANCED -> EngineProfile(
            mode = mode,
            userAgent = ModeManager.UA_ADVANCED,
            platform = "Win32",
            vendor = "Google Inc.",
            languages = listOf("en-US", "en"),
            hardwareConcurrency = 12,
            deviceMemory = 16,
            screenW = 1920,
            screenH = 1080,
            devicePixelRatio = 1.0,
            maxTouchPoints = 0,
            webglVendor = "Google Inc. (AMD)",
            webglRenderer = "ANGLE (AMD, AMD Radeon RX 7800 XT Direct3D11 vs_5_0 ps_5_0, D3D11)",
            canvasSeed = 77120408L,
            deviceLabel = "Click Advance · Desktop-class isolated profile",
            timezone = "Asia/Dubai",
            // Sign convention: JS Date.getTimezoneOffset() returns minutes WEST of
            // UTC, so UTC+4 (Asia/Dubai) = -240. (America/Los_Angeles above is
            // UTC-8 in standard time = +480 for the same reason.)
            timezoneOffsetMinutes = -240,
        )
    }

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
            Log.i(TAG, "V9 engine online: ${suffixFor(mode)} (${profileFor(mode).deviceLabel})")
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

    /** True when switching to [mode] requires a process restart (engine change). */
    fun needsRestart(mode: BrowserMode): Boolean = mode != bootMode

    /**
     * Persists [mode] then restarts the app process so the new engine's data
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
     * @param hackIntro when true, the relaunched process shows the Hack Mode
     *   Markhor intro animation once (see HackIntroOverlay).
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

    /** Intent extra: show the Hack Mode intro animation on launch. */
    const val EXTRA_HACK_INTRO = "v9_hack_intro"

    /** Intent extra: show the Advance Mode intro animation on launch. */
    const val EXTRA_ADVANCE_INTRO = "v9_advance_intro"

    private const val RESTART_REQUEST_CODE = 9001
    private const val RESTART_DELAY_MS = 1000L

    /**
     * Per-engine JS fingerprint: overrides navigator/screen/WebGL values to
     * match the engine profile and poisons canvas readback with seeded noise
     * (stable within an engine, distinct across engines).
     *
     * Injected at onPageStarted so sites see a consistent identity from the
     * first script they run.
     */
    fun fingerprintJs(mode: BrowserMode): String {
        val p = profileFor(mode)
        val langs = p.languages.joinToString(",") { "'$it'" }
        // NOTE: single-quoted JS strings — UA/profile strings contain no quotes.
        return """
        (function(){
          if (window.__v9fp) return; window.__v9fp = true;
          var seed = ${p.canvasSeed};
          function rng(){ seed|=0; seed = seed + 0x6D2B79F5 | 0; var t = Math.imul(seed ^ seed >>> 15, 1 | seed); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }
          function def(o,k,v){ try { Object.defineProperty(o,k,{ get:function(){ return v; }, configurable:true }); } catch(e){} }
          var UA = '${p.userAgent}';
          def(navigator,'userAgent',UA); def(navigator,'appVersion',UA);
          def(navigator,'platform','${p.platform}'); def(navigator,'vendor','${p.vendor}');
          def(navigator,'language','${p.languages.first()}'); def(navigator,'languages',[${langs}]);
          def(navigator,'hardwareConcurrency',${p.hardwareConcurrency});
          def(navigator,'deviceMemory',${p.deviceMemory});
          def(navigator,'maxTouchPoints',${p.maxTouchPoints});
          // Timezone spoofing: distinct per engine (IANA name + offset).
          // Honest limit: this spoofs JS-visible timezone only; OS timezone
          // and IP geolocation are unchanged (would need root/VPN to alter).
          try {
            var tzName = '${p.timezone}';
            var tzOffset = ${p.timezoneOffsetMinutes};
            Date.prototype.getTimezoneOffset = function(){ return tzOffset; };
            if (typeof Intl !== 'undefined' && Intl.DateTimeFormat) {
              var origResolved = Intl.DateTimeFormat.prototype.resolvedOptions;
              Intl.DateTimeFormat.prototype.resolvedOptions = function(){
                var o = origResolved.apply(this, arguments);
                try { o.timeZone = tzName; } catch(e){}
                return o;
              };
            }
          } catch(e){}
          def(window.screen,'width',${p.screenW}); def(window.screen,'height',${p.screenH});
          def(window.screen,'availWidth',${p.screenW}); def(window.screen,'availHeight',${p.screenH - 40});
          def(window,'devicePixelRatio',${p.devicePixelRatio});
          if (${p.maxTouchPoints} === 0) { try { delete window.ontouchstart; delete window.ontouchend; } catch(e){} }
          try {
            var proto = WebGLRenderingContext.prototype, orig = proto.getParameter;
            var hook = function(param){
              if (param === 37445) return '${p.webglVendor}';
              if (param === 37446) return '${p.webglRenderer}';
              return orig.apply(this, arguments);
            };
            proto.getParameter = hook;
            if (window.WebGL2RenderingContext) WebGL2RenderingContext.prototype.getParameter = hook;
          } catch(e){}
          try {
            var origToDataURL = HTMLCanvasElement.prototype.toDataURL;
            HTMLCanvasElement.prototype.toDataURL = function(){
              try { var c = this.getContext('2d'); if (c) { var im = c.getImageData(0,0,this.width,this.height), d = im.data; for (var i=0;i<d.length;i+=4){ var n=(rng()-0.5)*3; d[i]+=n; d[i+1]+=n; d[i+2]+=n; } c.putImageData(im,0,0); } } catch(e){}
              return origToDataURL.apply(this, arguments);
            };
            var origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
            CanvasRenderingContext2D.prototype.getImageData = function(){
              var im = origGetImageData.apply(this, arguments), d = im.data;
              for (var i=0;i<d.length;i+=4){ var n=(rng()-0.5)*3; d[i]+=n; d[i+1]+=n; d[i+2]+=n; }
              return im;
            };
          } catch(e){}
        })();
        """.trimIndent()
    }
}
