package com.click.browser.engine

/** Whether the selected profile must be written when it already matches the process boot profile. */
internal fun shouldPersistNoRestartModeSelection(
    savedMode: BrowserMode,
    requestedMode: BrowserMode
): Boolean = savedMode != requestedMode

/** At least one relaunch route must have started before the old process is killed. */
internal fun hasEngineRestartPath(
    alarmScheduled: Boolean,
    directRelaunchStarted: Boolean
): Boolean = alarmScheduled || directRelaunchStarted
