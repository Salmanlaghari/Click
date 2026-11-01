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

    /** Appends a standalone error event: WebView callbacks do not expose request IDs. */
    fun httpError(
        existing: List<NetworkRequest>,
        method: String,
        url: String,
        isMainFrame: Boolean,
        webViewKey: Int,
        statusCode: Int,
        reason: String?,
        timestamp: Long = System.currentTimeMillis()
    ): List<NetworkRequest> = append(
        existing,
        newEntry(
            method, url, isMainFrame, webViewKey,
            outcome = NetworkRequestOutcome.HTTP_ERROR,
            statusCode = statusCode,
            detail = reason,
            timestamp = timestamp
        )
    )

    /** Keeps load failures as standalone events instead of guessing a matching request. */
    fun loadError(
        existing: List<NetworkRequest>,
        method: String,
        url: String,
        isMainFrame: Boolean,
        webViewKey: Int,
        description: String,
        timestamp: Long = System.currentTimeMillis()
    ): List<NetworkRequest> = append(
        existing,
        newEntry(
            method, url, isMainFrame, webViewKey,
            outcome = NetworkRequestOutcome.LOAD_ERROR,
            detail = description,
            timestamp = timestamp
        )
    )

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
