package com.click.browser.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRecordingPolicyTest {

    @Test
    fun recordsHttpAndHttpsVisitsFromRegularTabs() {
        assertTrue(HistoryRecordingPolicy.shouldRecord(false, "http://example.com/page"))
        assertTrue(HistoryRecordingPolicy.shouldRecord(false, "HTTPS://example.com/page"))
    }

    @Test
    fun rejectsPrivateTabVisitsAtTheRecordingBoundary() {
        assertFalse(HistoryRecordingPolicy.shouldRecord(true, "https://example.com/private"))
    }

    @Test
    fun rejectsNonWebAndMalformedUrls() {
        assertFalse(HistoryRecordingPolicy.shouldRecord(false, "about:blank"))
        assertFalse(HistoryRecordingPolicy.shouldRecord(false, "file:///private/page.html"))
        assertFalse(HistoryRecordingPolicy.shouldRecord(false, "ftp://example.com/file"))
        assertFalse(HistoryRecordingPolicy.shouldRecord(false, "https:///missing-host"))
        assertFalse(HistoryRecordingPolicy.shouldRecord(false, "not a URL"))
    }
}
