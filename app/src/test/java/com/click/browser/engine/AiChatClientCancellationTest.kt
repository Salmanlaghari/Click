package com.click.browser.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AiChatClientCancellationTest {

    @Test
    fun cancelBeforeTransportCallIsAttachedCancelsThatCallImmediately() {
        val controller = AiRequestCancellationController()
        val call = FakeCancellation()

        assertTrue(controller.cancel())
        controller.attach(call)

        assertTrue(controller.isCancelled())
        assertEquals(1, call.cancelCount)
    }

    @Test
    fun finishingOldRequestCannotClearNewRequestAndCancelOnlyStopsCurrent() {
        val controller = AiRequestCancellationController()
        val first = FakeCancellation()
        val second = FakeCancellation()

        controller.attach(first)
        controller.attach(second)
        controller.finish(first)

        assertEquals(1, first.cancelCount)
        assertTrue(controller.cancel())
        assertEquals(1, second.cancelCount)
        assertFalse(controller.cancel())
        assertEquals(1, second.cancelCount)
    }

    @Test
    fun cancelCurrentRequestCancelsGeminiOkHttpCallAndPropagatesCancellation() = runBlocking {
        assertCancellationFor(AiProviders.GEMINI)
    }

    @Test
    fun cancelCurrentRequestCancelsOpenAiCompatibleOkHttpCallAndPropagatesCancellation() = runBlocking {
        assertCancellationFor(AiProviders.GROQ)
    }

    private suspend fun assertCancellationFor(provider: AiProviderInfo) = coroutineScope {
        val factory = BlockingCallFactory()
        val client = AiChatClient(factory)
        val request = async(Dispatchers.Default) {
            client.sendRich(
                apiKey = "test-key",
                provider = provider,
                model = "test-model",
                history = listOf(ChatMessage("user", "Hello"))
            )
        }

        assertTrue("AI transport should start", factory.started.await(5, TimeUnit.SECONDS))
        client.cancelCurrentRequest()

        var wasCancelled = false
        try {
            request.await()
        } catch (_: CancellationException) {
            wasCancelled = true
        }

        assertTrue("Cancellation must not be returned as a normal Result.failure", wasCancelled)
        assertNotNull(factory.call)
        assertTrue("Cancel must reach the underlying OkHttp Call", factory.call!!.isCanceled())
    }

    private class FakeCancellation : AiCallCancellation {
        var cancelCount: Int = 0
            private set

        override fun cancel() {
            cancelCount += 1
        }
    }

    private class BlockingCallFactory : Call.Factory {
        val started = CountDownLatch(1)
        @Volatile var call: BlockingCall? = null
            private set

        override fun newCall(request: Request): Call =
            BlockingCall(request, started).also { call = it }
    }

    private class BlockingCall(
        private val request: Request,
        private val started: CountDownLatch
    ) : Call {
        private val cancelled = AtomicBoolean(false)
        private val executed = AtomicBoolean(false)
        private val release = CountDownLatch(1)

        override fun request(): Request = request

        override fun execute(): Response {
            if (!executed.compareAndSet(false, true)) throw IllegalStateException("Already executed")
            started.countDown()
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw IOException("Test call timed out")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Test call interrupted", e)
            }
            if (cancelled.get()) throw IOException("Canceled")
            throw IOException("Test call released without cancellation")
        }

        override fun enqueue(responseCallback: Callback) {
            throw UnsupportedOperationException("This test uses synchronous execute()")
        }

        override fun cancel() {
            cancelled.set(true)
            release.countDown()
        }

        override fun isExecuted(): Boolean = executed.get()

        override fun isCanceled(): Boolean = cancelled.get()

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = BlockingCall(request, started)
    }
}
