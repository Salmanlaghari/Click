package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRtcTestResultTest {
    @Test
    fun completedWithoutCandidatesIsOnlyNoAddressesObserved() {
        val result = WebRtcTestResult(WebRtcTestOutcome.COMPLETED)

        assertEquals(WebRtcTestPresentation.NO_ADDRESSES_OBSERVED, result.presentation)
    }

    @Test
    fun unsupportedTimeoutAndErrorAreInconclusive() {
        listOf(
            WebRtcTestOutcome.UNSUPPORTED,
            WebRtcTestOutcome.TIMED_OUT,
            WebRtcTestOutcome.ERROR
        ).forEach { outcome ->
            assertEquals(
                "$outcome must not be presented as a clean result",
                WebRtcTestPresentation.INCONCLUSIVE,
                WebRtcTestResult(outcome).presentation
            )
        }
    }

    @Test
    fun observedAddressesRemainVisibleEvenWhenGatheringDidNotComplete() {
        val result = WebRtcTestResult(
            outcome = WebRtcTestOutcome.TIMED_OUT,
            ipv4Addresses = listOf("192.0.2.12")
        )

        assertEquals(WebRtcTestPresentation.ADDRESSES_FOUND, result.presentation)
    }

    @Test
    fun parserRejectsMalformedOrInvalidPayloadAsInconclusive() {
        assertEquals(
            WebRtcTestOutcome.ERROR,
            WebRtcTestResult.parse("not-json").outcome
        )
        assertEquals(
            WebRtcTestOutcome.ERROR,
            WebRtcTestResult.parse("""{"status":"completed","ips":["999.1.1.1"]}""").outcome
        )
        assertEquals(
            WebRtcTestPresentation.INCONCLUSIVE,
            WebRtcTestResult.parse("not-json").presentation
        )
    }

    @Test
    fun parserPreservesCompletedEmptyAndObservedAddressOutcomes() {
        val empty = WebRtcTestResult.parse("""{"status":"completed","ips":[]}""")
        val found = WebRtcTestResult.parse("""{"status":"completed","ips":["192.0.2.12"]}""")

        assertEquals(WebRtcTestOutcome.COMPLETED, empty.outcome)
        assertEquals(WebRtcTestPresentation.NO_ADDRESSES_OBSERVED, empty.presentation)
        assertEquals(listOf("192.0.2.12"), found.ipv4Addresses)
        assertEquals(WebRtcTestPresentation.ADDRESSES_FOUND, found.presentation)
    }

    @Test
    fun scriptUsesOnlySafeRunIdsAndReplacesThePlaceholder() {
        val script = PrivacyGuards.webRtcLeakTestScript("run-42_abc")

        assertTrue(script.contains("var runId = 'run-42_abc';"))
        assertFalse(script.contains("__CLICK_WEBRTC_RUN_ID__"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun scriptRejectsRunIdsThatCouldChangeJavascript() {
        PrivacyGuards.webRtcLeakTestScript("bad'; alert(1);//")
    }

    @Test
    fun runGateAcceptsOnlyOneCallbackForTheCurrentRun() {
        WebRtcTestRunGate.begin("active-run")

        assertFalse(WebRtcTestRunGate.consume("stale-run"))
        assertTrue(WebRtcTestRunGate.consume("active-run"))
        assertFalse(WebRtcTestRunGate.consume("active-run"))
    }
}
