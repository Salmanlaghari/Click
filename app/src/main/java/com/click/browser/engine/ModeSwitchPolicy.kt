package com.click.browser.engine

/** Whether the selected profile must be written when it already matches the process boot profile. */
internal fun shouldPersistNoRestartModeSelection(
    savedMode: BrowserMode,
    requestedMode: BrowserMode
): Boolean = savedMode != requestedMode

/** A direct activity launch is not a cold-process restart; only a scheduled relaunch qualifies. */
internal fun hasEngineRestartPath(
    alarmScheduled: Boolean
): Boolean = alarmScheduled
