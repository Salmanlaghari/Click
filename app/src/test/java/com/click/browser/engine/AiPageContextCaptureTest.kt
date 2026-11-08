package com.click.browser.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPageContextCaptureTest {
    @Test
    fun returnsTextWhenCallbackArrives() = runBlocking {
        val result = capturePageContextWithTimeout(timeoutMillis = 1_000L) { onCaptured ->
            onCaptured("visible page text")
        }

        assertEquals("visible page text", result)
    }

    @Test
    fun timesOutWhenCallbackNeverArrivesAndDiscardsLateText() = runBlocking {
        var lateCallback: ((String) -> Unit)? = null
        val result = capturePageContextWithTimeout(timeoutMillis = 30L) { onCaptured ->
            lateCallback = onCaptured
        }

        lateCallback?.invoke("late page text")
        assertNull(result)
    }

    @Test
    fun returnsNullWhenCaptureCannotStart() = runBlocking {
        val result = capturePageContextWithTimeout(timeoutMillis = 100L) {
            throw IllegalStateException("WebView is no longer available")
        }

        assertNull(result)
    }

    @Test
    fun callerCanCancelPendingCapture() = runBlocking {
        val requestStarted = CompletableDeferred<Unit>()
        val job = launch {
            capturePageContextWithTimeout(timeoutMillis = 10_000L) {
                requestStarted.complete(Unit)
            }
        }

        requestStarted.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
    }
}
