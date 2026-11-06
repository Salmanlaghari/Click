package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClickDiagnosticsLogTest {
    @Test
    fun ringRetentionKeepsOnlyTheNewestEvents() {
        val entries = (1L..5L).map {
            ClickCatLogEntry(it, ClickCatLogCode.PROCESS_STARTED)
        }
        assertEquals(
            listOf(3L, 4L, 5L),
            appendClickCatLogEntry(entries.take(4), entries.last(), maxEntries = 3)
                .map(ClickCatLogEntry::timestampMillis)
        )
    }

    @Test
    fun startupClassifiesPendingSwitchWithoutSensitiveDetails() {
        val pending = PendingClickModeSwitch(BrowserMode.SIMPLE, BrowserMode.ADVANCED)
        assertEquals(
            ClickCatLogCode.MODE_SWITCH_RELAUNCHED,
            resolveClickModeSwitchOutcome(pending, BrowserMode.ADVANCED, bootBlocked = false)
        )
        assertEquals(
            ClickCatLogCode.MODE_SWITCH_BOOT_BLOCKED,
            resolveClickModeSwitchOutcome(pending, BrowserMode.ADVANCED, bootBlocked = true)
        )
        assertEquals(
            ClickCatLogCode.MODE_SWITCH_WRONG_PROFILE,
            resolveClickModeSwitchOutcome(pending, BrowserMode.SIMPLE, bootBlocked = false)
        )
        assertNull(resolveClickModeSwitchOutcome(null, BrowserMode.SIMPLE, bootBlocked = false))
        assertEquals(
            setOf("timestampMillis", "code", "fromMode", "toMode"),
            ClickCatLogEntry::class.java.declaredFields
                .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .map { it.name }
                .toSet()
        )
    }
}
