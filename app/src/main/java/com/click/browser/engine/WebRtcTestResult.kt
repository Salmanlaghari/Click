package com.click.browser.engine

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

enum class WebRtcTestOutcome {
    COMPLETED,
    UNSUPPORTED,
    TIMED_OUT,
    ERROR
}

enum class WebRtcTestPresentation {
    ADDRESSES_FOUND,
    NO_ADDRESSES_OBSERVED,
    INCONCLUSIVE
}

data class WebRtcTestResult(
    val outcome: WebRtcTestOutcome,
    val ipv4Addresses: List<String> = emptyList()
) {
    /** An empty result is positive only when ICE gathering reported completion. */
    val presentation: WebRtcTestPresentation
        get() = when {
            ipv4Addresses.isNotEmpty() -> WebRtcTestPresentation.ADDRESSES_FOUND
            outcome == WebRtcTestOutcome.COMPLETED -> WebRtcTestPresentation.NO_ADDRESSES_OBSERVED
            else -> WebRtcTestPresentation.INCONCLUSIVE
        }

    companion object {
        /** Parse the bridge's status-bearing JSON payload; malformed data is never treated as a clean result. */
        fun parse(payload: String?): WebRtcTestResult {
            return try {
                val json = JSONObject(payload ?: throw IllegalArgumentException("Missing payload"))
                val outcome = when (json.optString("status")) {
                    "completed" -> WebRtcTestOutcome.COMPLETED
                    "unsupported" -> WebRtcTestOutcome.UNSUPPORTED
                    "timeout" -> WebRtcTestOutcome.TIMED_OUT
                    "error" -> WebRtcTestOutcome.ERROR
                    else -> throw IllegalArgumentException("Unknown outcome")
                }
                val addresses = json.optJSONArray("ips")
                    ?: throw IllegalArgumentException("Missing address list")
                val parsedAddresses = buildList {
                    for (index in 0 until addresses.length()) {
                        val value = addresses.opt(index) as? String
                            ?: throw IllegalArgumentException("Invalid address value")
                        if (!isIpv4Address(value)) {
                            throw IllegalArgumentException("Invalid IPv4 address")
                        }
                        add(value)
                    }
                }.distinct()
                WebRtcTestResult(outcome, parsedAddresses)
            } catch (_: Exception) {
                WebRtcTestResult(WebRtcTestOutcome.ERROR)
            }
        }

        private fun isIpv4Address(value: String): Boolean {
            val parts = value.split('.')
            if (parts.size != 4) return false
            return parts.all { part ->
                if (part.isEmpty() || (part.length > 1 && part.startsWith('0'))) return@all false
                val octet = part.toIntOrNull() ?: return@all false
                octet in 0..255
            }
        }
    }
}

/** One-shot gate so a loaded page cannot inject a fabricated result for another test run. */
object WebRtcTestRunGate {
    private val activeRunId = AtomicReference<String?>(null)

    fun begin(runId: String) {
        activeRunId.set(runId)
    }

    fun consume(runId: String): Boolean = activeRunId.compareAndSet(runId, null)

    fun cancel(runId: String): Boolean = activeRunId.compareAndSet(runId, null)
}
