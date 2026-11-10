package com.click.browser.engine

/** The UI action to take for the current profile's saved session at startup. */
internal enum class SessionRestoreStartupAction {
    NONE,
    AUTOMATIC_RESTORE
}

/** Restore the current profile's saved tabs; explicit clear-on-exit remains authoritative. */
internal fun sessionRestoreStartupAction(
    savedTabCount: Int,
    clearOnExit: Boolean
): SessionRestoreStartupAction =
    if (clearOnExit || savedTabCount <= 0) {
        SessionRestoreStartupAction.NONE
    } else {
        SessionRestoreStartupAction.AUTOMATIC_RESTORE
    }
