package com.click.browser.engine

/** Whether the selected profile must be written when it already matches the process boot profile. */
internal fun shouldPersistNoRestartModeSelection(
    savedMode: BrowserMode,
    requestedMode: BrowserMode
): Boolean = savedMode != requestedMode

internal enum class RelaunchBackgroundActivityStartMode {
    PLATFORM_DEFAULT,
    ALLOW_BACKGROUND,
    ALLOW_ALWAYS,
}

/** Use the narrow API 34/35 opt-in and API 36's replacement for process-death relaunch. */
internal fun relaunchPendingIntentCreatorBackgroundStartMode(apiLevel: Int): RelaunchBackgroundActivityStartMode = when {
    apiLevel >= 36 -> RelaunchBackgroundActivityStartMode.ALLOW_ALWAYS
    apiLevel >= 34 -> RelaunchBackgroundActivityStartMode.ALLOW_BACKGROUND
    else -> RelaunchBackgroundActivityStartMode.PLATFORM_DEFAULT
}

/** A direct activity launch is not a cold-process restart; only a scheduled relaunch qualifies. */
internal fun hasEngineRestartPath(
    alarmScheduled: Boolean
): Boolean = alarmScheduled
