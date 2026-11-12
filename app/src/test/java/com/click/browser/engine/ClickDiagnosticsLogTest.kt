package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun targetProcessStartupRetainsPendingSwitchUntilActivityWindowFocus() {
        val pending = PendingClickModeSwitch(BrowserMode.SIMPLE, BrowserMode.ADVANCED)

        val startup = resolveClickModeSwitchOutcome(pending, BrowserMode.ADVANCED, bootBlocked = false)
        assertEquals(ClickCatLogCode.MODE_SWITCH_RELAUNCHED, startup)
        assertTrue(shouldRetainPendingModeSwitchUntilForeground(startup))
        assertFalse(
            shouldRetainPendingModeSwitchUntilForeground(
                ClickCatLogCode.MODE_SWITCH_BOOT_BLOCKED
            )
        )
        assertFalse(
            shouldRetainPendingModeSwitchUntilForeground(
                ClickCatLogCode.MODE_SWITCH_WRONG_PROFILE
            )
        )

        assertEquals(
            ClickCatLogCode.MODE_SWITCH_ACTIVITY_FOCUSED,
            resolveClickModeSwitchWindowFocusOutcome(
                pending,
                focusedMode = BrowserMode.ADVANCED,
                bootBlocked = false
            )
        )
        assertEquals(
            ClickCatLogCode.MODE_SWITCH_WRONG_PROFILE,
            resolveClickModeSwitchWindowFocusOutcome(
                pending,
                focusedMode = BrowserMode.SIMPLE,
                bootBlocked = false
            )
        )
        assertNull(resolveClickModeSwitchWindowFocusOutcome(null, BrowserMode.SIMPLE, bootBlocked = false))
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
                exceptionType = "java.lang.IllegalStateException",
                deviceModel = "Google Pixel 9",
                systemLowMemory = true,
                memoryThresholdMb = 1024,
                exceptionDetails = "java.lang.IllegalStateException\n  at com.click.browser.Session.retry(Session.kt:42)"
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
        assertEquals("Google Pixel 9", restored?.deviceModel)
        assertEquals(true, restored?.systemLowMemory)
        assertEquals(1024L, restored?.memoryThresholdMb)
        assertEquals("session reopen failed", restored?.eventDetails)
        assertEquals("java.lang.IllegalStateException", restored?.exceptionType)
        assertEquals(
            "java.lang.IllegalStateException\n  at com.click.browser.Session.retry(Session.kt:42)",
            restored?.exceptionDetails
        )
        assertEquals(
            CLICK_CAT_LOG_URL_MAX_CHARS,
            sanitizeClickRendererUrl("https://example.com/${"a".repeat(600)}")?.length
        )
    }

    @Test
    fun rendererEntriesFromOlderLogVersionsRemainReadable() {
        val legacyJson = """
            [{"t":1,"c":"GECKO_RENDERER_CRASHED","f":null,"to":null,"r":{
              "tab":"tab-1","url":"https://example.com","engine":"153",
              "android":"Android 16","memoryMb":512,"event":"renderer crashed","exception":null
            }}]
        """.trimIndent()

        val details = ClickDiagnosticsLog.decodeEvents(legacyJson).single().rendererDetails

        assertNotNull(details)
        assertEquals("unknown", details?.deviceModel)
        assertNull(details?.systemLowMemory)
        assertNull(details?.memoryThresholdMb)
        assertNull(details?.exceptionDetails)
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
                exceptionType = "x".repeat(120),
                deviceModel = "d".repeat(120),
                systemLowMemory = false,
                memoryThresholdMb = -10,
                exceptionDetails = "s".repeat(CLICK_DIAGNOSTIC_EXCEPTION_DETAILS_MAX_CHARS + 20)
            )
        )

        assertEquals(64, details.tabId.length)
        assertEquals("javascript:", details.url)
        assertEquals(80, details.geckoViewVersion.length)
        assertEquals(80, details.androidVersion.length)
        assertEquals(0L, details.availableMemoryMb)
        assertEquals(160, details.eventDetails.length)
        assertEquals(100, details.exceptionType?.length)
        assertEquals(100, details.deviceModel.length)
        assertEquals(false, details.systemLowMemory)
        assertEquals(0L, details.memoryThresholdMb)
        assertEquals(CLICK_DIAGNOSTIC_EXCEPTION_DETAILS_MAX_CHARS, details.exceptionDetails?.length)
    }

    @Test
    fun safeExceptionDetailsKeepCauseAndFramesButNeverMessages() {
        val message = "private https://example.com/path?token=secret#fragment"
        val details = safeRendererExceptionDetails(
            IllegalStateException(message, IllegalArgumentException("nested private value"))
        )

        assertNotNull(details)
        assertEquals(false, details?.contains(message))
        assertEquals(false, details?.contains("nested private value"))
        org.junit.Assert.assertTrue(details!!.contains("java.lang.IllegalStateException"))
        org.junit.Assert.assertTrue(details.contains("Caused by: java.lang.IllegalArgumentException"))
        org.junit.Assert.assertTrue(details.contains(" at "))
        org.junit.Assert.assertTrue(details.length <= CLICK_DIAGNOSTIC_EXCEPTION_DETAILS_MAX_CHARS)
    }

    @Test
    fun userControlledShareExportUsesRedactedUrlAndRendererContext() {
        val errorDetails = safeRendererExceptionDetails(IllegalStateException("secret message"))
        val export = formatClickDiagnosticsForSharing(
            listOf(
                ClickCatLogEntry(
                    timestampMillis = 42,
                    code = ClickCatLogCode.GECKO_RECOVERY_FAILED,
                    rendererDetails = ClickRendererDiagnosticDetails(
                        tabId = "tab-7",
                        url = "https://user:pass@example.com/path?q=secret#fragment",
                        geckoViewVersion = "153",
                        androidVersion = "Android 16",
                        availableMemoryMb = 512,
                        eventDetails = "retry failed",
                        exceptionType = "java.lang.IllegalStateException",
                        deviceModel = "Google Pixel",
                        systemLowMemory = true,
                        memoryThresholdMb = 900,
                        exceptionDetails = errorDetails
                    )
                )
            )
        )

        org.junit.Assert.assertTrue(export.contains("https://example.com/path"))
        org.junit.Assert.assertTrue(export.contains("Google Pixel"))
        org.junit.Assert.assertTrue(export.contains("system low memory: true"))
        org.junit.Assert.assertTrue(export.contains("Exception frames (messages omitted)"))
        org.junit.Assert.assertEquals(false, export.contains("q=secret"))
        org.junit.Assert.assertEquals(false, export.contains("secret message"))
        org.junit.Assert.assertEquals(false, export.contains("user:pass"))
        org.junit.Assert.assertTrue(export.length <= CLICK_DIAGNOSTIC_EXPORT_MAX_CHARS)
    }
}

class RecentErrorSummariesTest {

    private fun entry(
        timestamp: Long,
        code: ClickCatLogCode,
        tabId: String? = null,
        details: String? = null
    ) = ClickCatLogEntry(
        timestampMillis = timestamp,
        code = code,
        rendererDetails = tabId?.let {
            ClickRendererDiagnosticDetails(
                tabId = it,
                url = null,
                geckoViewVersion = "153.0",
                androidVersion = "15",
                availableMemoryMb = 512L,
                eventDetails = details.orEmpty()
            )
        }
    )

    @Test
    fun onlyErrorCodesAreSummarizedNewestFirst() {
        val summaries = recentErrorSummaries(
            listOf(
                entry(100L, ClickCatLogCode.GECKO_RENDERER_CRASHED, tabId = "tab-1", details = "Renderer process gone code=42"),
                entry(200L, ClickCatLogCode.PROCESS_STARTED),
                entry(300L, ClickCatLogCode.EXTENSION_POPUP_FAILED, tabId = "tab-2", details = "Extension popup failed to render: uBlock Origin"),
                entry(400L, ClickCatLogCode.MODE_SWITCH_STARTED)
            )
        )
        assertEquals(2, summaries.size)
        assertEquals(300L, summaries[0].timestampMillis)
        assertEquals(
            "Extension popup failed to render: uBlock Origin",
            summaries[0].detail
        )
        assertEquals("ERR_RENDERER_GONE tab=tab-1", summaries[1].headline)
        assertEquals("Renderer process gone code=42", summaries[1].detail)
    }

    @Test
    fun recoveryFailuresAreErrorsButSuccessesAndNoiseAreNotSurfaced() {
        val summaries = recentErrorSummaries(
            listOf(
                entry(1L, ClickCatLogCode.GECKO_RECOVERY_SUCCEEDED, tabId = "t"),
                entry(2L, ClickCatLogCode.GECKO_RECOVERY_FAILED, tabId = "t", details = "Rebuilt session failed")
            )
        )
        assertEquals(1, summaries.size)
        assertEquals("ERR_RECOVERY_FAILED tab=t", summaries[0].headline)
    }

    @Test
    fun limitCapsTheList() {
        val entries = (1L..8L).map {
            entry(it, ClickCatLogCode.GECKO_RENDERER_KILLED, tabId = "t$it")
        }
        assertEquals(5, recentErrorSummaries(entries).size)
        assertEquals(8L, recentErrorSummaries(entries).first().timestampMillis)
    }
}
