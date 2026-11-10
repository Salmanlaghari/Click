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
