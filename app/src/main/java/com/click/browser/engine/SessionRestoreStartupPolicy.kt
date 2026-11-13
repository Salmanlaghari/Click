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

/**
 * After startup, the first visible surface must be the mode's home
 * dashboard — not a restored web page.
 *
 * Root cause this fixes (1.7.0 device report): the per-mode dashboards are
 * composed only while the active tab is the home tab ("about:blank"), but
 * automatic session restore replaced the initial home tab with the saved
 * web tabs, and a home tab can never be part of a saved snapshot
 * (SessionRestore only persists http/https URLs). A returning user
 * therefore launched straight into a web page in every mode and never saw
 * a dashboard. Landing on home applies whenever Click itself decides the
 * first tab; an external link that explicitly opened Click
 * ([startViewUrl] != null) still lands in the foreground instead.
 */
internal fun shouldLandOnHomeDashboard(startViewUrl: String?): Boolean =
    startViewUrl == null
