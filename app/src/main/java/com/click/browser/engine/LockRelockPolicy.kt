package com.click.browser.engine

/**
 * Re-lock grace policy for the app lock and the private-tab lock
 * (Prince's device report, 2026-11-28: "Application ko smoothly wapas
 * open karna" — the app did not reopen smoothly).
 *
 * Before this policy, both locks re-engaged on EVERY onPause: a
 * one-second trip to Recents, a system permission dialog, or any other
 * brief interruption landed the user back on a lock screen with an
 * automatic biometric prompt. Genuine apps (WhatsApp, banking apps)
 * only re-lock after the app has genuinely been away for a while.
 *
 * Rule: when the app backgrounds from an UNLOCKED state, the moment is
 * remembered. If it returns within [GRACE_MS], the lock that engaged on
 * backgrounding is silently released — no prompt, no friction. A cold
 * start, a return after the grace window, or a backgrounding that
 * happened while already locked all keep the lock (fail closed).
 */
internal object LockRelockPolicy {

    /** How long the app may stay backgrounded before the locks bite. */
    const val GRACE_MS: Long = 30_000L

    /**
     * True when a lock engaged on backgrounding should STAY engaged on
     * return. [backgroundedAtMillis] is a SystemClock.elapsedRealtime()
     * stamp taken when the app backgrounded from an unlocked state, or
     * 0 when no such stamp exists (cold start / backgrounded while
     * locked) — both keep the lock.
     */
    fun shouldKeepLock(backgroundedAtMillis: Long, nowMillis: Long): Boolean {
        if (backgroundedAtMillis <= 0L) return true
        val elapsed = nowMillis - backgroundedAtMillis
        if (elapsed < 0L) return true // clock moved backwards — fail closed
        return elapsed >= GRACE_MS
    }
}
