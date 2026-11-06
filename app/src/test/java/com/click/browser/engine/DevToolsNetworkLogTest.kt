package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevToolsNetworkLogTest {
    @Test
    fun httpErrorIsStandaloneEvenWhenSimilarRequestsExist() {
        val requests = listOf(
            NetworkRequest("GET", "https://example.com/data", false, 1, timestamp = 10L),
            NetworkRequest("GET", "https://example.com/data", false, 1, timestamp = 11L),
            NetworkRequest("GET", "https://example.com/data", true, 1, timestamp = 12L)
        )

        val updated = DevToolsNetworkLog.httpError(
            existing = requests,
            method = "GET",
            url = "https://example.com/data",
            isMainFrame = false,
            webViewKey = 1,
            statusCode = 404,
            reason = "Not Found",
            timestamp = 20L
        )

        assertEquals(4, updated.size)
        assertTrue(updated.take(3).all { it.outcome == NetworkRequestOutcome.OBSERVED })
        assertEquals(NetworkRequestOutcome.HTTP_ERROR, updated.last().outcome)
        assertEquals(404, updated.last().statusCode ?: -1)
        assertEquals("Not Found", updated.last().detail)
        assertEquals(20L, updated.last().timestamp)
    }

    @Test
    fun loadErrorIsStandaloneEvenWhenMatchingRequestWasObserved() {
        val observed = DevToolsNetworkLog.observed(
            existing = emptyList(),
            method = "GET",
            url = "https://example.com/offline",
            isMainFrame = true,
            webViewKey = 4,
            timestamp = 29L
        )
        val updated = DevToolsNetworkLog.loadError(
            existing = observed,
            method = "GET",
            url = "https://example.com/offline",
            isMainFrame = true,
            webViewKey = 4,
            description = "net::ERR_NAME_NOT_RESOLVED",
            timestamp = 30L
        )

        assertEquals(2, updated.size)
        assertEquals(NetworkRequestOutcome.OBSERVED, updated[0].outcome)
        assertEquals(NetworkRequestOutcome.LOAD_ERROR, updated[1].outcome)
        assertTrue(updated.last().isMainFrame)
        assertEquals("net::ERR_NAME_NOT_RESOLVED", updated.last().detail)
        assertEquals(30L, updated.last().timestamp)
    }

    @Test
    fun requestHistoryIsCappedAndDropsOldestEntry() {
        var requests = emptyList<NetworkRequest>()
        repeat(DevToolsNetworkLog.MAX_ENTRIES + 1) { index ->
            requests = DevToolsNetworkLog.observed(
                existing = requests,
                method = "GET",
                url = "https://example.com/$index",
                isMainFrame = false,
                webViewKey = 7,
                timestamp = index.toLong()
            )
        }

        assertEquals(DevToolsNetworkLog.MAX_ENTRIES, requests.size)
        assertFalse(requests.any { it.url.endsWith("/0") })
        assertTrue(requests.last().url.endsWith("/500"))
    }

    @Test
    fun blockedRequestIsMarkedWithoutPretendingItReceivedAResponse() {
        val blocked = DevToolsNetworkLog.blocked(
            existing = emptyList(),
            method = "GET",
            url = "https://ads.example/ad.js",
            isMainFrame = false,
            webViewKey = 3,
            timestamp = 40L
        ).single()

        assertEquals(NetworkRequestOutcome.BLOCKED_BY_CLICK, blocked.outcome)
        assertNull(blocked.statusCode)
        assertTrue(blocked.detail.orEmpty().contains("content filter"))
    }
}
