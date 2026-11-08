package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRequestRetryPolicyTest {
    @Test
    fun retryIsAvailableOnlyForSavedErrorRowsWhenNoRequestIsRunning() {
        assertTrue(
            AiRequestRetryPolicy.canRetry(
                messageRole = "error",
                hasSavedRequest = true,
                requestInProgress = false
            )
        )
        assertFalse(AiRequestRetryPolicy.canRetry("error", hasSavedRequest = false, requestInProgress = false))
        assertFalse(AiRequestRetryPolicy.canRetry("assistant", hasSavedRequest = true, requestInProgress = false))
        assertFalse(AiRequestRetryPolicy.canRetry("error", hasSavedRequest = true, requestInProgress = true))
    }

    @Test
    fun contextBearingRetryRequiresCurrentPageContextConsent() {
        assertFalse(
            AiRequestRetryPolicy.canRetry(
                messageRole = "error",
                hasSavedRequest = true,
                requestInProgress = false,
                requiresPageContextConsent = true,
                pageContextConsentGranted = false
            )
        )
        assertTrue(
            AiRequestRetryPolicy.canRetry(
                messageRole = "error",
                hasSavedRequest = true,
                requestInProgress = false,
                requiresPageContextConsent = true,
                pageContextConsentGranted = true
            )
        )
    }

    @Test
    fun retryWithoutPageContextDoesNotNeedPageSharingConsent() {
        assertTrue(
            AiRequestRetryPolicy.canRetry(
                messageRole = "error",
                hasSavedRequest = true,
                requestInProgress = false,
                requiresPageContextConsent = false,
                pageContextConsentGranted = false
            )
        )
    }

    @Test
    fun removingOneFailedRowKeepsOtherRetryRequestsAligned() {
        val earlier = "earlier request"
        val retried = "request being retried"
        val later = "later request"
        val requests = mapOf(2 to earlier, 3 to retried, 5 to later)

        assertEquals(
            mapOf(2 to earlier, 4 to later),
            AiRequestRetryPolicy.afterErrorRemoved(requests, removedErrorIndex = 3)
        )
    }
}
