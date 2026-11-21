package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.WebRequestError

class GeckoNavigationErrorPolicyTest {
    @Test
    fun failedFirstNavigationShowsRecoveryWithoutMarkingContentCommitted() {
        val url = "https://example.test/search?q=click"
        val tracker = GeckoNavigationTracker()
        tracker.begin(url, geckoSafeRetryUrl("GET", url))

        // A genuine failure is Gecko's onLoadError followed by the failed stop.
        assertTrue(tracker.markLoadError(url))
        val completion = tracker.complete(success = false)!!

        assertFalse(completion.outcome.commitsContent)
        assertEquals(url, completion.outcome.failure?.failedUrl)
        assertEquals(url, completion.outcome.failure?.retryUrl)
        assertFalse(completion.wasCancelled)
        assertTrue(completion.isCurrent)
    }

    @Test
    fun supersededLoadStopWithoutLoadErrorShowsNoFailure() {
        val tracker = GeckoNavigationTracker()
        val older = tracker.begin("https://example.test/older", null)
        val newer = tracker.begin("https://example.test/newer", null)

        // The older load was replaced by the newer one: Gecko stops it with
        // success=false but never reported a load error for it.
        val olderCompletion = tracker.complete(success = false)!!
        assertEquals(older.generation, olderCompletion.generation)
        assertFalse(olderCompletion.outcome.commitsContent)
        assertNull(olderCompletion.outcome.failure)
        assertFalse(olderCompletion.isCurrent)

        val newerCompletion = tracker.complete(success = true)!!
        assertEquals(newer.generation, newerCompletion.generation)
        assertTrue(newerCompletion.outcome.commitsContent)
        assertNull(newerCompletion.outcome.failure)
    }

    @Test
    fun interruptedCurrentLoadStopWithoutLoadErrorShowsNoFailure() {
        // The only in-flight load is interrupted (e.g. its next hop was a
        // denied/replaced navigation, so no newer start follows): still not
        // a page failure, and nothing is marked committed.
        val tracker = GeckoNavigationTracker()
        tracker.begin("https://example.test/interrupted", null)

        val completion = tracker.complete(success = false)!!

        assertTrue(completion.isCurrent)
        assertFalse(completion.outcome.commitsContent)
        assertNull(completion.outcome.failure)
    }

    @Test
    fun successfulStopAfterEarlierInterruptedStopLeavesNoStaleFailure() {
        val tracker = GeckoNavigationTracker()
        tracker.begin("https://example.test/first", null)
        tracker.begin("https://example.test/second", null)

        val interrupted = tracker.complete(success = false)!!
        assertNull(interrupted.outcome.failure)

        val successful = tracker.complete(success = true)!!
        assertTrue(successful.isCurrent)
        assertTrue(successful.outcome.commitsContent)
        assertNull(successful.outcome.failure)
    }

    @Test
    fun loadErrorUriMatchingNoPendingAttemptMarksNothing() {
        val tracker = GeckoNavigationTracker()
        tracker.begin("https://example.test/start", null)
        tracker.updateCurrentUrl("https://example.test/redirected")

        // Gecko reports the error against the original hop's URI, which no
        // pending attempt carries any more. With no exact match the error is
        // NOT attributed: guessing the single in-flight load is exactly what
        // poisoned healthy navigations into false failures.
        assertFalse(tracker.markLoadError("https://example.test/start"))
        val completion = tracker.complete(success = false)!!

        assertFalse(completion.outcome.commitsContent)
        assertNull(completion.outcome.failure)
    }

    @Test
    fun strayUnmatchedLoadErrorDoesNotPoisonSuccessfulLoad() {
        val url = "https://example.test/page"
        val tracker = GeckoNavigationTracker()
        tracker.begin(url, null)

        // A stray/late error for some other resource (a previous page's
        // subresource, a subframe) arrives while this load is in flight. It
        // matches no pending attempt and must be ignored entirely.
        assertFalse(tracker.markLoadError("https://example.test/other-resource"))
        val completion = tracker.complete(success = true)!!

        assertTrue(completion.outcome.commitsContent)
        assertNull(completion.outcome.failure)
    }

    @Test
    fun matchedLoadErrorStillCommitsWhenPageStopReportsSuccess() {
        val url = "https://example.test/page"
        val tracker = GeckoNavigationTracker()
        tracker.begin(url, geckoSafeRetryUrl("GET", url))

        assertTrue(tracker.markLoadError(url))
        val completion = tracker.complete(success = true)!!

        // Gecko said the page loaded: a successful page-stop always wins
        // over an earlier error attribution for the same attempt.
        assertTrue(completion.outcome.commitsContent)
        assertNull(completion.outcome.failure)
    }

    @Test
    fun onlySuccessfulPageStopCommitsContentAndClearsFailure() {
        val url = "https://example.test/"
        val tracker = GeckoNavigationTracker()
        tracker.begin(url, null)

        val completion = tracker.complete(success = true)!!

        assertTrue(completion.outcome.commitsContent)
        assertNull(completion.outcome.failure)
        assertTrue(completion.isCurrent)
    }

    @Test
    fun firstContentfulPaintRevealsOnlyTheCurrentStillLoadingGeneration() {
        assertTrue(
            shouldCommitGeckoContentOnFirstPaint(
                activeGeneration = 12L,
                paintedGeneration = 12L,
                isLoading = true
            )
        )
        assertFalse(
            shouldCommitGeckoContentOnFirstPaint(
                activeGeneration = 13L,
                paintedGeneration = 12L,
                isLoading = true
            )
        )
        assertFalse(
            shouldCommitGeckoContentOnFirstPaint(
                activeGeneration = 12L,
                paintedGeneration = 12L,
                isLoading = false
            )
        )
    }

    @Test
    fun nullLoadErrorUriMarksNothingAndSuccessfulStopCommits() {
        val url = "https://example.test/loaded"
        val tracker = GeckoNavigationTracker()
        tracker.begin(url, geckoSafeRetryUrl("GET", url))

        // A null error URI can no longer be attributed to any attempt.
        assertFalse(tracker.markLoadError(uri = null))
        val completion = tracker.complete(success = true)!!

        assertTrue(completion.outcome.commitsContent)
        assertNull(completion.outcome.failure)
    }

    @Test
    fun loadErrorLabelMapsKnownGeckoErrors() {
        assertEquals(
            "Server address could not be found (DNS)",
            geckoLoadErrorLabel(
                WebRequestError.ERROR_CATEGORY_URI,
                WebRequestError.ERROR_UNKNOWN_HOST
            )
        )
        assertEquals(
            "The connection timed out",
            geckoLoadErrorLabel(
                WebRequestError.ERROR_CATEGORY_NETWORK,
                WebRequestError.ERROR_NET_TIMEOUT
            )
        )
        assertEquals(
            "The server refused the connection",
            geckoLoadErrorLabel(
                WebRequestError.ERROR_CATEGORY_NETWORK,
                WebRequestError.ERROR_CONNECTION_REFUSED
            )
        )
        assertEquals(
            "Secure connection failed (bad certificate)",
            geckoLoadErrorLabel(
                WebRequestError.ERROR_CATEGORY_SECURITY,
                WebRequestError.ERROR_SECURITY_BAD_CERT
            )
        )
    }

    @Test
    fun loadErrorLabelKeepsRawNumbersForUnmappedErrors() {
        val label = geckoLoadErrorLabel(category = 99, error = 424242)

        assertEquals("Load error (category 99, code 424242)", label)
    }

    @Test
    fun matchedLoadErrorLabelReachesTheFailure() {
        val url = "https://example.test/missing"
        val tracker = GeckoNavigationTracker()
        tracker.begin(url, geckoSafeRetryUrl("GET", url))

        val label = geckoLoadErrorLabel(
            WebRequestError.ERROR_CATEGORY_URI,
            WebRequestError.ERROR_UNKNOWN_HOST
        )
        assertTrue(tracker.markLoadError(url, label))
        val completion = tracker.complete(success = false)!!

        assertFalse(completion.outcome.commitsContent)
        assertEquals(url, completion.outcome.failure?.failedUrl)
        assertEquals(label, completion.outcome.failure?.errorLabel)
    }

    @Test
    fun staleStopIsAttributedToItsOwnStartAndCannotBeCurrent() {
        val tracker = GeckoNavigationTracker()
        val olderUrl = "https://example.test/older"
        val newerUrl = "https://example.test/newer"
        val older = tracker.begin(olderUrl, geckoSafeRetryUrl("GET", olderUrl))
        val newer = tracker.begin(newerUrl, geckoSafeRetryUrl("GET", newerUrl))

        assertNull(tracker.progressGeneration())
        assertTrue(tracker.markLoadError(olderUrl))
        val olderCompletion = tracker.complete(success = false)!!
        assertEquals(older.generation, olderCompletion.generation)
        assertEquals(olderUrl, olderCompletion.url)
        assertFalse(olderCompletion.isCurrent)
        assertEquals(olderUrl, olderCompletion.outcome.failure?.retryUrl)
        assertEquals(newer.generation, tracker.progressGeneration())

        val newerCompletion = tracker.complete(success = true)!!
        assertEquals(newer.generation, newerCompletion.generation)
        assertEquals(newerUrl, newerCompletion.url)
        assertTrue(newerCompletion.isCurrent)
        assertTrue(newerCompletion.outcome.commitsContent)
    }

    @Test
    fun contentPaintDuringOverlappingStartsTargetsLatestNavigationWithoutChangingProgressRules() {
        val tracker = GeckoNavigationTracker()
        val older = tracker.begin("https://example.test/older", null)
        val newer = tracker.begin("https://example.test/newer", null)

        assertNull(tracker.progressGeneration())
        assertEquals(newer.generation, tracker.contentPaintGeneration())
        assertTrue(
            shouldCommitGeckoContentOnFirstPaint(
                activeGeneration = newer.generation,
                paintedGeneration = tracker.contentPaintGeneration()!!,
                isLoading = true
            )
        )

        val olderCompletion = tracker.complete(success = true)!!
        assertEquals(older.generation, olderCompletion.generation)
        assertFalse(olderCompletion.isCurrent)
        assertEquals(newer.generation, tracker.contentPaintGeneration())
    }

    @Test
    fun cancellationRemainsBoundToTheLoadThatWasStopped() {
        val tracker = GeckoNavigationTracker()
        val stopped = tracker.begin("https://example.test/stopped", null)
        assertEquals(stopped.generation, tracker.cancelActive())
        val newer = tracker.begin("https://example.test/newer", null)

        val stoppedCompletion = tracker.complete(success = false)!!
        assertEquals(stopped.generation, stoppedCompletion.generation)
        assertTrue(stoppedCompletion.wasCancelled)
        assertTrue(stoppedCompletion.outcome.failure?.wasCancelled == true)
        assertFalse(stoppedCompletion.isCurrent)

        val newerCompletion = tracker.complete(success = true)!!
        assertEquals(newer.generation, newerCompletion.generation)
        assertFalse(newerCompletion.wasCancelled)
        assertTrue(newerCompletion.isCurrent)
    }

    @Test
    fun restoreLoadTransientDnsFailureIsSuppressedAndRequestsAutoRetry() {
        // App-start restore: the already-open page is re-displayed from saved
        // state while DNS is still coming back. The failure must NOT become
        // the sticky card; the session silently reloads instead.
        val url = "https://example.test/restored"
        val tracker = GeckoNavigationTracker()
        tracker.markNextLoadAsRestore()
        tracker.begin(url, geckoSafeRetryUrl("GET", url))

        val label = geckoLoadErrorLabel(
            WebRequestError.ERROR_CATEGORY_URI,
            WebRequestError.ERROR_UNKNOWN_HOST
        )
        assertTrue(
            tracker.markLoadError(
                url,
                label,
                errorCode = WebRequestError.ERROR_UNKNOWN_HOST
            )
        )
        val completion = tracker.complete(success = false)!!

        assertTrue(completion.isCurrent)
        assertFalse(completion.outcome.commitsContent)
        assertNull(completion.outcome.failure)
        assertEquals(url, completion.autoRetryUrl)
    }

    @Test
    fun backgroundedLoadTransientFailureIsSuppressedAndRequestsAutoRetry() {
        // The load was in flight when the user left the app; the suspended
        // network stack killed it. Its timeout around resume is an
        // interruption, not a page failure.
        val url = "https://example.test/in-flight"
        val tracker = GeckoNavigationTracker()
        tracker.begin(url, null)

        tracker.setHostResumed(false)
        tracker.setHostResumed(true)
        assertTrue(
            tracker.markLoadError(
                url,
                "The connection timed out",
                errorCode = WebRequestError.ERROR_NET_TIMEOUT
            )
        )
        val completion = tracker.complete(success = false)!!

        assertNull(completion.outcome.failure)
        assertEquals(url, completion.autoRetryUrl)
    }

    @Test
    fun errorWhileBackgroundedThenSuccessOnResumeShowsNoFailure() {
        val url = "https://example.test/slow"
        val tracker = GeckoNavigationTracker()
        tracker.begin(url, null)

        tracker.setHostResumed(false)
        assertTrue(
            tracker.markLoadError(
                url,
                "Server address could not be found (DNS)",
errorCode = WebRequestError.ERROR_UNKNOWN_HOST
            )
        )
        tracker.setHostResumed(true)
        val completion = tracker.complete(success = true)!!

        assertTrue(completion.outcome.commitsContent)
        assertNull(completion.outcome.failure)
        assertNull(completion.autoRetryUrl)
    }

    @Test
    fun autoRetryChainIsBoundedAndEventuallySurfacesTheRealFailure() {
        val url = "https://example.test/restored"
        val label = "Server address could not be found (DNS)"
        val tracker = GeckoNavigationTracker()

        tracker.markNextLoadAsRestore()
        tracker.begin(url, geckoSafeRetryUrl("GET", url))
        assertTrue(
            tracker.markLoadError(
                url, label,
                errorCode = WebRequestError.ERROR_UNKNOWN_HOST
            )
        )
        val first = tracker.complete(success = false)!!
        assertEquals(url, first.autoRetryUrl)
        assertNull(first.outcome.failure)

        tracker.markNextLoadAsAutoRetry()
        tracker.begin(url, geckoSafeRetryUrl("GET", url))
        assertTrue(
            tracker.markLoadError(
                url, label,
                errorCode = WebRequestError.ERROR_UNKNOWN_HOST
            )
        )
        val second = tracker.complete(success = false)!!
        assertEquals(url, second.autoRetryUrl)
        assertNull(second.outcome.failure)

        // Budget exhausted: the genuine failure now reaches the card with
        // its real reason, exactly as a normal failed navigation would.
        tracker.markNextLoadAsAutoRetry()
        tracker.begin(url, geckoSafeRetryUrl("GET", url))
        assertTrue(
            tracker.markLoadError(
                url, label,
                errorCode = WebRequestError.ERROR_UNKNOWN_HOST
            )
        )
        val third = tracker.complete(success = false)!!
        assertNull(third.autoRetryUrl)
        assertEquals(url, third.outcome.failure?.failedUrl)
        assertEquals(label, third.outcome.failure?.errorLabel)
    }

    @Test
    fun foregroundUserNavigationTransientFailureStillShowsCard() {
        // A navigation the user just made, in the foreground, failing with
        // the very same DNS error is a genuine failure: no suppression.
        val url = "https://example.test/typed-by-user"
        val tracker = GeckoNavigationTracker()
        tracker.begin(url, geckoSafeRetryUrl("GET", url))

        assertTrue(
            tracker.markLoadError(
                url,
                "Server address could not be found (DNS)",
errorCode = WebRequestError.ERROR_UNKNOWN_HOST
            )
        )
        val completion = tracker.complete(success = false)!!

        assertNull(completion.autoRetryUrl)
        assertEquals(url, completion.outcome.failure?.failedUrl)
    }

    @Test
    fun restoreLoadNonTransientFailureShowsCardImmediately() {
        // A bad certificate on a restore load would fail identically on any
        // retry: it reaches the card at once, reason intact.
        val url = "https://example.test/bad-cert"
        val tracker = GeckoNavigationTracker()
        tracker.markNextLoadAsRestore()
        tracker.begin(url, geckoSafeRetryUrl("GET", url))

        assertTrue(
            tracker.markLoadError(
                url,
                "Secure connection failed (bad certificate)",
errorCode = WebRequestError.ERROR_SECURITY_BAD_CERT
            )
        )
        val completion = tracker.complete(success = false)!!

        assertNull(completion.autoRetryUrl)
        assertEquals(url, completion.outcome.failure?.failedUrl)
        assertEquals(
            "Secure connection failed (bad certificate)",
            completion.outcome.failure?.errorLabel
        )
    }

    @Test
    fun restoreFailureWithoutErrorCodeIsNotAutoRetried() {
        // An error whose code was never recorded cannot be classified as
        // transient: it surfaces like any other genuine failure.
        val url = "https://example.test/restored"
        val tracker = GeckoNavigationTracker()
        tracker.markNextLoadAsRestore()
        tracker.begin(url, geckoSafeRetryUrl("GET", url))

        assertTrue(tracker.markLoadError(url, "Load error (category 1, code 424242)"))
        val completion = tracker.complete(success = false)!!

        assertNull(completion.autoRetryUrl)
        assertEquals(url, completion.outcome.failure?.failedUrl)
    }

    @Test
    fun staleRestoreFailureRequestsNoAutoRetry() {
        // The restore load failed after the user already navigated away:
        // its failure belongs to a dead navigation and must not trigger a
        // reload that would hijack the current page.
        val tracker = GeckoNavigationTracker()
        tracker.markNextLoadAsRestore()
        val restored = tracker.begin("https://example.test/restored", null)
        val newer = tracker.begin("https://example.test/newer", null)

        assertTrue(
            tracker.markLoadError(
                "https://example.test/restored",
                "Server address could not be found (DNS)",
errorCode = WebRequestError.ERROR_UNKNOWN_HOST
            )
        )
        val staleCompletion = tracker.complete(success = false)!!
        assertEquals(restored.generation, staleCompletion.generation)
        assertFalse(staleCompletion.isCurrent)
        assertNull(staleCompletion.autoRetryUrl)

        val newerCompletion = tracker.complete(success = true)!!
        assertEquals(newer.generation, newerCompletion.generation)
        assertTrue(newerCompletion.outcome.commitsContent)
    }

    @Test
    fun freshNavigationStartsANewAutoRetryChain() {
        val url = "https://example.test/restored"
        val label = "Server address could not be found (DNS)"
        val tracker = GeckoNavigationTracker()

        // First chain consumes one retry, then the user navigates elsewhere
        // and that load is interrupted without any error.
        tracker.markNextLoadAsRestore()
        tracker.begin(url, null)
        assertTrue(
            tracker.markLoadError(
                url, label,
                errorCode = WebRequestError.ERROR_UNKNOWN_HOST
            )
        )
        assertEquals(url, tracker.complete(success = false)!!.autoRetryUrl)

        tracker.begin("https://example.test/elsewhere", null)
        assertNull(tracker.complete(success = false)!!.outcome.failure)

        // A later, independent restore failure gets the full budget again:
        // two silent retries, then the card.
        tracker.markNextLoadAsRestore()
        tracker.begin(url, null)
        assertTrue(
            tracker.markLoadError(
                url, label,
                errorCode = WebRequestError.ERROR_UNKNOWN_HOST
            )
        )
        assertEquals(url, tracker.complete(success = false)!!.autoRetryUrl)

        tracker.markNextLoadAsAutoRetry()
        tracker.begin(url, null)
        assertTrue(
            tracker.markLoadError(
                url, label,
                errorCode = WebRequestError.ERROR_UNKNOWN_HOST
            )
        )
        assertEquals(url, tracker.complete(success = false)!!.autoRetryUrl)

        tracker.markNextLoadAsAutoRetry()
        tracker.begin(url, null)
        assertTrue(
            tracker.markLoadError(
                url, label,
                errorCode = WebRequestError.ERROR_UNKNOWN_HOST
            )
        )
        val surfaced = tracker.complete(success = false)!!
        assertNull(surfaced.autoRetryUrl)
        assertEquals(url, surfaced.outcome.failure?.failedUrl)
    }

    @Test
    fun transientLoadErrorClassification() {
        assertTrue(isTransientGeckoLoadError(WebRequestError.ERROR_UNKNOWN_HOST))
        assertTrue(isTransientGeckoLoadError(WebRequestError.ERROR_NET_TIMEOUT))
        assertTrue(isTransientGeckoLoadError(WebRequestError.ERROR_NET_INTERRUPT))
        assertTrue(isTransientGeckoLoadError(WebRequestError.ERROR_NET_RESET))
        assertTrue(isTransientGeckoLoadError(WebRequestError.ERROR_OFFLINE))
        assertTrue(isTransientGeckoLoadError(WebRequestError.ERROR_CONNECTION_REFUSED))
        assertTrue(isTransientGeckoLoadError(WebRequestError.ERROR_UNKNOWN_PROXY_HOST))
        assertTrue(isTransientGeckoLoadError(WebRequestError.ERROR_PROXY_CONNECTION_REFUSED))

        assertFalse(isTransientGeckoLoadError(WebRequestError.ERROR_SECURITY_BAD_CERT))
        assertFalse(isTransientGeckoLoadError(WebRequestError.ERROR_SECURITY_SSL))
        assertFalse(isTransientGeckoLoadError(WebRequestError.ERROR_MALFORMED_URI))
        assertFalse(isTransientGeckoLoadError(WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI))
        assertFalse(isTransientGeckoLoadError(null))
        assertFalse(isTransientGeckoLoadError(424242))
    }

    @Test
    fun knownGetRetryCandidateCanFollowItsRedirect() {
        val tracker = GeckoNavigationTracker()
        val originalUrl = "https://example.test/start"
        val redirectedUrl = "https://example.test/final"
        tracker.begin(originalUrl, geckoSafeRetryUrl("GET", originalUrl))

        tracker.updateCurrentUrl(redirectedUrl)
        tracker.updateCurrentRetryUrl(redirectedUrl)
        assertTrue(tracker.markLoadError(redirectedUrl))
        val completion = tracker.complete(success = false)!!

        assertEquals(redirectedUrl, completion.url)
        assertEquals(redirectedUrl, completion.outcome.failure?.retryUrl)
    }

    @Test
    fun unknownAndUnsafeRequestsNeverReceiveRetryUrl() {
        assertNull(geckoSafeRetryUrl("POST", "https://example.test/submit"))
        assertNull(geckoSafeRetryUrl("GET", "javascript:alert(1)"))
        assertNull(geckoSafeRetryUrl("GET", "https:///missing-host"))

        val tracker = GeckoNavigationTracker()
        tracker.begin(
            url = "https://example.test/submit",
            retryUrl = geckoSafeRetryUrl("POST", "https://example.test/submit")
        )
        assertTrue(tracker.markLoadError("https://example.test/submit"))
        val failedPost = tracker.complete(success = false)!!
        assertFalse(failedPost.outcome.commitsContent)
        assertTrue(failedPost.outcome.failure != null)
        assertNull(failedPost.outcome.failure?.retryUrl)

        val staleCandidate = geckoNavigationStopOutcome(
            success = false,
            failedUrl = "https://example.test/new-page",
            safeRetryUrl = "https://example.test/previous-page"
        )
        assertFalse(staleCandidate.commitsContent)
        assertTrue(staleCandidate.failure != null)
        assertNull(staleCandidate.failure?.retryUrl)

        val nonWebFailure = geckoNavigationStopOutcome(
            success = false,
            failedUrl = "file:///tmp/page.html",
            safeRetryUrl = null
        )
        assertFalse(nonWebFailure.commitsContent)
        assertNull(nonWebFailure.failure)
    }
}
