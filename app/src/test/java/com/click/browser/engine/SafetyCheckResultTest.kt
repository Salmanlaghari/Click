package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyCheckResultTest {
    @Test
    fun informationalItemsAreNotCountedAsProtectionChecks() {
        val actualCheck = SafetyCheck.Check(
            id = "https",
            title = "HTTPS protection",
            description = "Standard mode is enabled.",
            passed = true,
            fixLabel = null
        )
        val updateLink = SafetyCheck.Check(
            id = "updates",
            title = "App updates",
            description = "Open the store to check.",
            passed = true,
            fixLabel = null,
            actionLabel = "Open Play Store",
            informational = true
        )

        assertEquals(listOf(actualCheck), SafetyCheck.protectionChecks(listOf(actualCheck, updateLink)))
    }
}
