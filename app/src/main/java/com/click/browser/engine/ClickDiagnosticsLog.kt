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
    GECKO_RENDERER_KILLED("GeckoView renderer was killed by the system"),
    GECKO_RECOVERY_STARTED("GeckoView session recovery started"),
    GECKO_RECOVERY_SUCCEEDED("GeckoView renderer produced a recovery frame"),
    GECKO_RECOVERY_FAILED("GeckoView session recovery failed")
}

internal data class ClickRendererDiagnosticDetails(
    val tabId: String,
    val url: String?,
    val geckoViewVersion: String,
    val androidVersion: String,
    val availableMemoryMb: Long?,
    val eventDetails: String,
    val exceptionType: String? = null
)

internal data class ClickCatLogEntry(
    val timestampMillis: Long,
    val code: ClickCatLogCode,
    val fromMode: BrowserMode? = null,
    val toMode: BrowserMode? = null,
    val rendererDetails: ClickRendererDiagnosticDetails? = null
)

internal data class PendingClickModeSwitch(
    val fromMode: BrowserMode,
    val toMode: BrowserMode
)

internal const val CLICK_CAT_LOG_MAX_ENTRIES = 120
internal const val CLICK_CAT_LOG_URL_MAX_CHARS = 512

/** Keep renderer diagnostics useful while dropping credentials, query strings, and fragments. */
internal fun sanitizeClickRendererUrl(rawUrl: String?): String? {
    val raw = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val parsed = runCatching { java.net.URI(raw) }.getOrNull() ?: return "unparseable-url"
    val scheme = parsed.scheme?.lowercase() ?: return null
    val safe = when (scheme) {
        "http", "https", "moz-extension" -> {
            val authority = parsed.rawAuthority?.substringAfterLast('@').orEmpty()
            if (authority.isBlank()) return "$scheme:"
            "$scheme://$authority${parsed.rawPath.orEmpty()}"
        }
        "about" -> "about:${parsed.rawSchemeSpecificPart.orEmpty().substringBefore('?')}"
        else -> "$scheme:"
    }
    return safe.take(CLICK_CAT_LOG_URL_MAX_CHARS)
}

internal fun boundedClickRendererDetails(
    details: ClickRendererDiagnosticDetails
): ClickRendererDiagnosticDetails = details.copy(
    tabId = details.tabId.take(64),
    url = sanitizeClickRendererUrl(details.url),
    geckoViewVersion = details.geckoViewVersion.take(80),
    androidVersion = details.androidVersion.take(80),
    availableMemoryMb = details.availableMemoryMb?.coerceAtLeast(0),
    eventDetails = details.eventDetails.take(160),
    exceptionType = details.exceptionType?.take(100)
)

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
 * renderer events may store a sanitized URL and tab ID, but never page contents,
 * passwords, cookies, crash stacks, or free-form exception messages. Storage is
 * a bounded private SharedPreferences ring and requires no new permissions.
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
        toMode: BrowserMode? = null,
        rendererDetails: ClickRendererDiagnosticDetails? = null
    ) {
        runCatching {
            val prefs = prefs(context)
            val current = decodeEvents(prefs.getString(EVENTS_KEY, null))
            val next = appendClickCatLogEntry(
                current,
                ClickCatLogEntry(
                    System.currentTimeMillis(),
                    code,
                    fromMode,
                    toMode,
                    rendererDetails?.let(::boundedClickRendererDetails)
                )
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

    internal fun encodeEvents(events: List<ClickCatLogEntry>): String = JSONArray().also { array ->
        events.takeLast(CLICK_CAT_LOG_MAX_ENTRIES).forEach { event ->
            val value = JSONObject()
                    .put("t", event.timestampMillis)
                    .put("c", event.code.name)
                    .put("f", event.fromMode?.name ?: JSONObject.NULL)
                    .put("to", event.toMode?.name ?: JSONObject.NULL)
            event.rendererDetails?.let { rawDetails ->
                val details = boundedClickRendererDetails(rawDetails)
                value.put(
                    "r",
                    JSONObject()
                        .put("tab", details.tabId)
                        .put("url", details.url ?: JSONObject.NULL)
                        .put("engine", details.geckoViewVersion)
                        .put("android", details.androidVersion)
                        .put("memoryMb", details.availableMemoryMb ?: JSONObject.NULL)
                        .put("event", details.eventDetails)
                        .put("exception", details.exceptionType ?: JSONObject.NULL)
                )
            }
            array.put(value)
        }
    }.toString()

    internal fun decodeEvents(raw: String?): List<ClickCatLogEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val value = array.getJSONObject(index)
                ClickCatLogEntry(
                    timestampMillis = value.optLong("t"),
                    code = ClickCatLogCode.valueOf(value.getString("c")),
                    fromMode = optionalMode(value, "f"),
                    toMode = optionalMode(value, "to"),
                    rendererDetails = value.optJSONObject("r")?.let { renderer ->
                        boundedClickRendererDetails(
                            ClickRendererDiagnosticDetails(
                                tabId = renderer.optString("tab"),
                                url = nullableString(renderer, "url"),
                                geckoViewVersion = renderer.optString("engine"),
                                androidVersion = renderer.optString("android"),
                                availableMemoryMb = nullableLong(renderer, "memoryMb"),
                                eventDetails = renderer.optString("event"),
                                exceptionType = nullableString(renderer, "exception")
                            )
                        )
                    }
                )
            }.getOrNull()
        }.takeLast(CLICK_CAT_LOG_MAX_ENTRIES)
    }

    private fun optionalMode(value: JSONObject, key: String): BrowserMode? {
        val raw = value.opt(key)
        if (raw == null || raw == JSONObject.NULL) return null
        return runCatching { BrowserMode.valueOf(raw.toString()) }.getOrNull()
    }

    private fun nullableString(value: JSONObject, key: String): String? =
        value.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()

    private fun nullableLong(value: JSONObject, key: String): Long? =
        value.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.toLongOrNull()
}
