package com.click.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeNewsFeedPolicyTest {
    @Test
    fun missingPreferenceKeepsFeedOptedOut() {
        assertFalse(HomeNewsFeedPolicy.isEnabled(null))
    }

    @Test
    fun explicitPreferenceControlsFeedVisibility() {
        assertTrue(HomeNewsFeedPolicy.isEnabled(true))
        assertFalse(HomeNewsFeedPolicy.isEnabled(false))
    }
}
