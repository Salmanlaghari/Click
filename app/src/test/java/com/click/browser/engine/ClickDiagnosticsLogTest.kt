package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
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
            setOf("timestampMillis", "code", "fromMode", "toMode", "rendererDetails"),
            ClickCatLogEntry::class.java.declaredFields
                .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .map { it.name }
                .toSet()
        )
    }

    @Test
    fun rendererMetadataIsRedactedBoundedAndRoundTripsLocally() {
        val entry = ClickCatLogEntry(
            timestampMillis = 123L,
            code = ClickCatLogCode.GECKO_RECOVERY_FAILED,
            rendererDetails = ClickRendererDiagnosticDetails(
                tabId = "tab-123",
                url = "https://user:password@example.com/search?q=private#result",
                geckoViewVersion = "153.0.20260810162159",
                androidVersion = "Android 16 (API 36)",
                availableMemoryMb = 812,
                eventDetails = "session reopen failed",
                exceptionType = "java.lang.IllegalStateException"
            )
        )

        val restored = ClickDiagnosticsLog.decodeEvents(
            ClickDiagnosticsLog.encodeEvents(listOf(entry))
        ).single().rendererDetails

        assertNotNull(restored)
        assertEquals("tab-123", restored?.tabId)
        assertEquals("https://example.com/search", restored?.url)
        assertEquals("153.0.20260810162159", restored?.geckoViewVersion)
        assertEquals("Android 16 (API 36)", restored?.androidVersion)
        assertEquals(812L, restored?.availableMemoryMb)
        assertEquals("session reopen failed", restored?.eventDetails)
        assertEquals("java.lang.IllegalStateException", restored?.exceptionType)
        assertEquals(
            CLICK_CAT_LOG_URL_MAX_CHARS,
            sanitizeClickRendererUrl("https://example.com/${"a".repeat(600)}")?.length
        )
    }

    @Test
    fun rendererDiagnosticFieldsAreCappedAndNonHttpSchemesDoNotStoreContent() {
        val details = boundedClickRendererDetails(
            ClickRendererDiagnosticDetails(
                tabId = "t".repeat(100),
                url = "javascript:document.body.innerText='private'",
                geckoViewVersion = "g".repeat(100),
                androidVersion = "a".repeat(100),
                availableMemoryMb = -10,
                eventDetails = "e".repeat(200),
                exceptionType = "x".repeat(120)
            )
        )

        assertEquals(64, details.tabId.length)
        assertEquals("javascript:", details.url)
        assertEquals(80, details.geckoViewVersion.length)
        assertEquals(80, details.androidVersion.length)
        assertEquals(0L, details.availableMemoryMb)
        assertEquals(160, details.eventDetails.length)
        assertEquals(100, details.exceptionType?.length)
    }
}
