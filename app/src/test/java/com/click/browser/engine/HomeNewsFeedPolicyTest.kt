package com.click.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeNewsFeedPolicyTest {
    @Test
    fun missingPreferenceDefaultsFeedOn() {
        // UC-style homepage (2026-11-28): the news feed is the homepage's
        // main scroll content, so no stored choice means ON.
        assertTrue(HomeNewsFeedPolicy.isEnabled(null))
    }

    @Test
    fun explicitPreferenceControlsFeedVisibility() {
        assertTrue(HomeNewsFeedPolicy.isEnabled(true))
        assertFalse(HomeNewsFeedPolicy.isEnabled(false))
    }
}
