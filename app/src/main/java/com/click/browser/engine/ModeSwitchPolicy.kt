package com.click.browser.engine

/** Whether a no-restart mode choice must be persisted for the next process. */
internal fun shouldPersistNoRestartModeSelection(
    savedMode: BrowserMode,
    requestedMode: BrowserMode
): Boolean = savedMode != requestedMode

/** The source process may exit only after the visible handoff owns window focus. */
internal fun shouldRequestSourceProcessExit(bridgeHasWindowFocus: Boolean): Boolean =
    bridgeHasWindowFocus

/** The target profile may start only after the source process is no longer alive. */
internal fun shouldOpenTargetProfile(sourceProcessExited: Boolean): Boolean =
    sourceProcessExited

/**
 * The mode-switch confirmation dialog is a separate window: while it is
 * showing, the source Activity does not hold window focus, and focus only
 * returns asynchronously after the dialog is dismissed. A switch confirmed
 * from that dialog must therefore WAIT briefly for focus to return instead
 * of failing on the first instantaneous check; it may only be abandoned
 * once the wait budget is exhausted without focus.
 */
internal fun shouldKeepWaitingForSourceWindowFocus(
    hasWindowFocus: Boolean,
    waitedMs: Long,
    timeoutMs: Long
): Boolean = !hasWindowFocus && waitedMs < timeoutMs

/** True only when the focus wait ended without the Activity regaining focus. */
internal fun shouldFailSwitchForMissingWindowFocus(
    hasWindowFocus: Boolean,
    waitedMs: Long,
    timeoutMs: Long
): Boolean = !hasWindowFocus && waitedMs >= timeoutMs
