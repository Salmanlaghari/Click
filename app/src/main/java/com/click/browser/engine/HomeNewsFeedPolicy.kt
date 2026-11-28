package com.click.browser.engine

/**
 * Home news feed visibility (Prince's professional-browser round,
 * 2026-11-28): the UC-style homepage carries the news feed as its main
 * scroll content, so the feed is ON by default. A user who explicitly
 * turned it off (stored value `false` — via the section's dismiss or
 * Settings) stays off; the choice is always respected.
 */
internal object HomeNewsFeedPolicy {
    fun isEnabled(storedValue: Boolean?): Boolean = storedValue != false
}
