package com.click.browser.engine

data class LogEntry(
    val type: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class NetworkRequest(
    val method: String,
    val url: String,
    val isMainFrame: Boolean,
    val webViewKey: Int,
    val outcome: NetworkRequestOutcome = NetworkRequestOutcome.OBSERVED,
    val statusCode: Int? = null,
    val detail: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class NetworkRequestOutcome {
    OBSERVED,
    BLOCKED_BY_CLICK,
    HTTP_ERROR,
    LOAD_ERROR
}

/** Pure, bounded state transitions for the Developer-mode native WebView request log. */
object DevToolsNetworkLog {
    const val MAX_ENTRIES = 500
    private const val MAX_URL_LENGTH = 2048
    private const val MAX_METHOD_LENGTH = 16
    private const val MAX_DETAIL_LENGTH = 200

    fun observed(
        existing: List<NetworkRequest>,
        method: String,
        url: String,
        isMainFrame: Boolean,
        webViewKey: Int,
        timestamp: Long = System.currentTimeMillis()
    ): List<NetworkRequest> = append(
        existing,
        newEntry(method, url, isMainFrame, webViewKey, NetworkRequestOutcome.OBSERVED, timestamp = timestamp)
    )

    fun blocked(
        existing: List<NetworkRequest>,
        method: String,
        url: String,
        isMainFrame: Boolean,
        webViewKey: Int,
        timestamp: Long = System.currentTimeMillis()
    ): List<NetworkRequest> = append(
        existing,
        newEntry(
            method, url, isMainFrame, webViewKey,
            outcome = NetworkRequestOutcome.BLOCKED_BY_CLICK,
            detail = "Blocked by Click's existing content filter",
            timestamp = timestamp
        )
    )

    fun httpError(
        existing: List<NetworkRequest>,
        method: String,
        url: String,
        isMainFrame: Boolean,
        webViewKey: Int,
        statusCode: Int,
        reason: String?,
        timestamp: Long = System.currentTimeMillis()
    ): List<NetworkRequest> = completeLatestObserved(
        existing, method, url, isMainFrame, webViewKey,
        outcome = NetworkRequestOutcome.HTTP_ERROR,
        statusCode = statusCode,
        detail = reason,
        timestamp = timestamp
    )

    fun loadError(
        existing: List<NetworkRequest>,
        method: String,
        url: String,
        isMainFrame: Boolean,
        webViewKey: Int,
        description: String,
        timestamp: Long = System.currentTimeMillis()
    ): List<NetworkRequest> = completeLatestObserved(
        existing, method, url, isMainFrame, webViewKey,
        outcome = NetworkRequestOutcome.LOAD_ERROR,
        detail = description,
        timestamp = timestamp
    )

    private fun completeLatestObserved(
        existing: List<NetworkRequest>,
        method: String,
        url: String,
        isMainFrame: Boolean,
        webViewKey: Int,
        outcome: NetworkRequestOutcome,
        statusCode: Int? = null,
        detail: String?,
        timestamp: Long
    ): List<NetworkRequest> {
        val safeMethod = method.take(MAX_METHOD_LENGTH).ifBlank { "GET" }
        val safeUrl = url.take(MAX_URL_LENGTH)
        // WebViewClient exposes no request ID; include frame type in the best-effort match.
        val pendingIndex = existing.indexOfLast {
            it.webViewKey == webViewKey &&
                it.method == safeMethod &&
                it.url == safeUrl &&
                it.isMainFrame == isMainFrame &&
                it.outcome == NetworkRequestOutcome.OBSERVED
        }
        if (pendingIndex < 0) {
            return append(
                existing,
                newEntry(
                    safeMethod, safeUrl, isMainFrame, webViewKey,
                    outcome = outcome,
                    statusCode = statusCode,
                    detail = detail,
                    timestamp = timestamp
                )
            )
        }

        val updated = existing.toMutableList()
        updated[pendingIndex] = updated[pendingIndex].copy(
            outcome = outcome,
            statusCode = statusCode,
            detail = detail?.take(MAX_DETAIL_LENGTH)
        )
        return updated.takeLast(MAX_ENTRIES)
    }

    private fun newEntry(
        method: String,
        url: String,
        isMainFrame: Boolean,
        webViewKey: Int,
        outcome: NetworkRequestOutcome,
        statusCode: Int? = null,
        detail: String? = null,
        timestamp: Long
    ) = NetworkRequest(
        method = method.take(MAX_METHOD_LENGTH).ifBlank { "GET" },
        url = url.take(MAX_URL_LENGTH),
        isMainFrame = isMainFrame,
        webViewKey = webViewKey,
        outcome = outcome,
        statusCode = statusCode,
        detail = detail?.take(MAX_DETAIL_LENGTH),
        timestamp = timestamp
    )

    private fun append(
        existing: List<NetworkRequest>,
        entry: NetworkRequest
    ): List<NetworkRequest> = (existing + entry).takeLast(MAX_ENTRIES)
}
