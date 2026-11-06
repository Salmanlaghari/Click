package com.click.browser.engine

/** Whether the selected profile must be written when it already matches the process boot profile. */
internal fun shouldPersistNoRestartModeSelection(
    savedMode: BrowserMode,
    requestedMode: BrowserMode
): Boolean = savedMode != requestedMode

internal enum class RelaunchPendingIntentBalMode {
    PLATFORM_DEFAULT,
    ALLOW_BACKGROUND,
    ALLOW_ALWAYS,
}

/** Use the narrow API 34/35 opt-in and API 36's replacement for process-death relaunch. */
internal fun relaunchPendingIntentCreatorBalMode(apiLevel: Int): RelaunchPendingIntentBalMode = when {
    apiLevel >= 36 -> RelaunchPendingIntentBalMode.ALLOW_ALWAYS
    apiLevel >= 34 -> RelaunchPendingIntentBalMode.ALLOW_BACKGROUND
    else -> RelaunchPendingIntentBalMode.PLATFORM_DEFAULT
}

/** A direct activity launch is not a cold-process restart; only a scheduled relaunch qualifies. */
internal fun hasEngineRestartPath(
    alarmScheduled: Boolean
): Boolean = alarmScheduled
