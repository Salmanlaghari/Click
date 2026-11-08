package com.click.browser.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

internal const val PAGE_CONTEXT_CAPTURE_TIMEOUT_MILLIS = 12_000L

/**
 * Waits for a callback-based page-context read without leaving callers stuck
 * when the WebView callback never arrives. Cancellation and timeout discard
 * any late result so it cannot be used by a later AI request.
 */
internal suspend fun capturePageContextWithTimeout(
    timeoutMillis: Long = PAGE_CONTEXT_CAPTURE_TIMEOUT_MILLIS,
    request: (onCaptured: (String) -> Unit) -> Unit
): String? {
    require(timeoutMillis > 0L)
    val capture = CompletableDeferred<String>()
    return try {
        try {
            request { value -> capture.complete(value) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        withTimeoutOrNull(timeoutMillis) { capture.await() }
    } finally {
        capture.cancel()
    }
}
