package com.click.browser.engine

/** The UI action to take for the current profile's saved session at startup. */
internal enum class SessionRestoreStartupAction {
    NONE,
    AUTOMATIC_MODE_RESTORE,
    CRASH_RECOVERY_PROMPT
}

/**
 * Resolve startup restoration without crossing profiles or overriding an
 * explicit external link. A pending marker is stored in the current profile's
 * own DataStore and is set only when intentionally switching away from it.
 */
internal fun sessionRestoreStartupAction(
    previousExitWasClean: Boolean,
    modeSwitchRestorePending: Boolean,
    savedTabCount: Int,
    clearOnExit: Boolean,
    externalStartUrlPresent: Boolean
): SessionRestoreStartupAction {
    if (clearOnExit || externalStartUrlPresent || savedTabCount <= 0) {
        return SessionRestoreStartupAction.NONE
    }
    if (modeSwitchRestorePending) {
        return SessionRestoreStartupAction.AUTOMATIC_MODE_RESTORE
    }
    if (!previousExitWasClean) {
        return SessionRestoreStartupAction.CRASH_RECOVERY_PROMPT
    }
    return SessionRestoreStartupAction.NONE
}
