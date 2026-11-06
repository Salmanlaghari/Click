package com.click.browser.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal enum class ClickCatLogCode(val label: String) {
    PROCESS_STARTED("Click process started"),
    PROFILE_BOOT_BLOCKED("Selected browser profile could not start safely"),
    MODE_SWITCH_STARTED("Profile switch started"),
    MODE_SWITCH_SESSION_SAVED("Source-profile session preparation completed"),
    MODE_SWITCH_TARGET_PERSISTED("Target profile selection was saved"),
    MODE_SWITCH_RELAUNCH_SCHEDULED("Relaunch alarm accepted; delivery not confirmed"),
    MODE_SWITCH_EXIT_REQUESTED("Old process exit was requested"),
    MODE_SWITCH_FAILED("Profile switch did not reach the relaunch step"),
    MODE_SWITCH_ROLLED_BACK("Profile selection was rolled back after relaunch failure"),
    MODE_SWITCH_RELAUNCHED("Target profile restarted successfully"),
    MODE_SWITCH_BOOT_BLOCKED("Target profile restarted but browser startup was blocked"),
    MODE_SWITCH_WRONG_PROFILE("Restart did not reach the requested profile"),
    MODE_SWITCH_CANCELLED("Profile switch was cancelled"),
    MODE_CHANGED_WITHOUT_RESTART("Browser mode changed without a profile restart"),
    GECKO_RENDERER_CRASHED("GeckoView renderer crashed"),
    GECKO_RENDERER_KILLED("GeckoView renderer was killed by the system")
}

internal data class ClickCatLogEntry(
    val timestampMillis: Long,
    val code: ClickCatLogCode,
    val fromMode: BrowserMode? = null,
    val toMode: BrowserMode? = null
)

internal data class PendingClickModeSwitch(
    val fromMode: BrowserMode,
    val toMode: BrowserMode
)

internal const val CLICK_CAT_LOG_MAX_ENTRIES = 120

internal fun appendClickCatLogEntry(
    existing: List<ClickCatLogEntry>,
    entry: ClickCatLogEntry,
    maxEntries: Int = CLICK_CAT_LOG_MAX_ENTRIES
): List<ClickCatLogEntry> = (existing + entry).takeLast(maxEntries.coerceAtLeast(1))

internal fun resolveClickModeSwitchOutcome(
    pending: PendingClickModeSwitch?,
    bootMode: BrowserMode,
    bootBlocked: Boolean
): ClickCatLogCode? = when {
    pending == null -> null
    bootBlocked -> ClickCatLogCode.MODE_SWITCH_BOOT_BLOCKED
    bootMode == pending.toMode -> ClickCatLogCode.MODE_SWITCH_RELAUNCHED
    else -> ClickCatLogCode.MODE_SWITCH_WRONG_PROFILE
}

/**
 * Local Click-authored troubleshooting events only. This is not Android logcat:
 * it does not read other apps/system logs, browser URLs, page contents, passwords,
 * cookies, crash stacks, or free-form exception messages. Storage is a bounded
 * private SharedPreferences ring and requires no new permissions.
 */
internal object ClickDiagnosticsLog {
    private const val PREFS_NAME = "click_local_catlog_v1"
    private const val EVENTS_KEY = "events"
    private const val PENDING_SWITCH_KEY = "pending_mode_switch"

    @Synchronized
    fun record(
        context: Context,
        code: ClickCatLogCode,
        fromMode: BrowserMode? = null,
        toMode: BrowserMode? = null
    ) {
        runCatching {
            val prefs = prefs(context)
            val current = decodeEvents(prefs.getString(EVENTS_KEY, null))
            val next = appendClickCatLogEntry(
                current,
                ClickCatLogEntry(System.currentTimeMillis(), code, fromMode, toMode)
            )
            prefs.edit().putString(EVENTS_KEY, encodeEvents(next)).commit()
        }
    }

    @Synchronized
    fun beginModeSwitch(context: Context, fromMode: BrowserMode, toMode: BrowserMode) {
        runCatching {
            prefs(context).edit().putString(
                PENDING_SWITCH_KEY,
                JSONObject().put("from", fromMode.name).put("to", toMode.name).toString()
            ).commit()
            record(context, ClickCatLogCode.MODE_SWITCH_STARTED, fromMode, toMode)
        }
    }

    @Synchronized
    fun failModeSwitch(context: Context, fromMode: BrowserMode, toMode: BrowserMode) {
        record(context, ClickCatLogCode.MODE_SWITCH_FAILED, fromMode, toMode)
        runCatching { prefs(context).edit().remove(PENDING_SWITCH_KEY).commit() }
    }

    @Synchronized
    fun resolveStartup(context: Context, bootMode: BrowserMode, bootBlocked: Boolean) {
        record(context, ClickCatLogCode.PROCESS_STARTED, toMode = bootMode)
        val pending = readPendingSwitch(context)
        val outcome = resolveClickModeSwitchOutcome(pending, bootMode, bootBlocked)
        if (pending != null && outcome != null) {
            record(context, outcome, pending.fromMode, pending.toMode)
            runCatching { prefs(context).edit().remove(PENDING_SWITCH_KEY).commit() }
        }
        if (bootBlocked) record(context, ClickCatLogCode.PROFILE_BOOT_BLOCKED, toMode = bootMode)
    }

    @Synchronized
    fun read(context: Context): List<ClickCatLogEntry> = runCatching {
        decodeEvents(prefs(context).getString(EVENTS_KEY, null)).takeLast(CLICK_CAT_LOG_MAX_ENTRIES)
    }.getOrDefault(emptyList())

    @Synchronized
    fun clear(context: Context) {
        runCatching {
            prefs(context).edit().remove(EVENTS_KEY).remove(PENDING_SWITCH_KEY).commit()
        }
    }

    private fun readPendingSwitch(context: Context): PendingClickModeSwitch? = runCatching {
        val raw = prefs(context).getString(PENDING_SWITCH_KEY, null) ?: return null
        val json = JSONObject(raw)
        PendingClickModeSwitch(
            fromMode = BrowserMode.valueOf(json.getString("from")),
            toMode = BrowserMode.valueOf(json.getString("to"))
        )
    }.getOrNull()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private fun encodeEvents(events: List<ClickCatLogEntry>): String = JSONArray().also { array ->
        events.takeLast(CLICK_CAT_LOG_MAX_ENTRIES).forEach { event ->
            array.put(
                JSONObject()
                    .put("t", event.timestampMillis)
                    .put("c", event.code.name)
                    .put("f", event.fromMode?.name ?: JSONObject.NULL)
                    .put("to", event.toMode?.name ?: JSONObject.NULL)
            )
        }
    }.toString()

    private fun decodeEvents(raw: String?): List<ClickCatLogEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val value = array.getJSONObject(index)
                ClickCatLogEntry(
                    timestampMillis = value.optLong("t"),
                    code = ClickCatLogCode.valueOf(value.getString("c")),
                    fromMode = optionalMode(value, "f"),
                    toMode = optionalMode(value, "to")
                )
            }.getOrNull()
        }.takeLast(CLICK_CAT_LOG_MAX_ENTRIES)
    }

    private fun optionalMode(value: JSONObject, key: String): BrowserMode? {
        val raw = value.opt(key)
        if (raw == null || raw == JSONObject.NULL) return null
        return runCatching { BrowserMode.valueOf(raw.toString()) }.getOrNull()
    }
}
