package com.click.browser.engine

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Minimal cancellation handle so request ownership can be tested without network traffic. */
internal fun interface AiCallCancellation {
    fun cancel()
}

/** Owns exactly one in-flight AI transport call and safely handles cancel/start races. */
internal class AiRequestCancellationController {
    private val activeCall = AtomicReference<AiCallCancellation?>(null)
    private val cancelled = AtomicBoolean(false)

    fun attach(call: AiCallCancellation) {
        val previous = activeCall.getAndSet(call)
        if (previous !== call) previous?.cancel()

        // If the user canceled before the transport call was created, cancel it immediately.
        if (cancelled.get() && activeCall.compareAndSet(call, null)) {
            call.cancel()
        }
    }

    fun cancel(): Boolean {
        val firstCancellation = cancelled.compareAndSet(false, true)
        val call = activeCall.getAndSet(null)
        call?.cancel()
        return firstCancellation || call != null
    }

    fun finish(call: AiCallCancellation) {
        // A late completion from an old request must not clear a newer request's handle.
        activeCall.compareAndSet(call, null)
    }

    fun isCancelled(): Boolean = cancelled.get()
}
