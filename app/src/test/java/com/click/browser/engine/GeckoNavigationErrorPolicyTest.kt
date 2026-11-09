package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoNavigationErrorPolicyTest {
    @Test
    fun failedFirstNavigationShowsRecoveryWithoutMarkingContentCommitted() {
        val url = "https://example.test/search?q=click"
        val tracker = GeckoNavigationTracker()
        tracker.begin(url, geckoSafeRetryUrl("GET", url))

        val completion = tracker.complete(success = false)!!

        assertFalse(completion.outcome.commitsContent)
        assertEquals(url, completion.outcome.failure?.failedUrl)
        assertEquals(url, completion.outcome.failure?.retryUrl)
        assertFalse(completion.wasCancelled)
        assertTrue(completion.isCurrent)
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
    fun nullableLoadErrorUriStillPreventsFalseSuccessfulCommit() {
        val url = "https://example.test/failed"
        val tracker = GeckoNavigationTracker()
        tracker.begin(url, geckoSafeRetryUrl("GET", url))

        assertTrue(tracker.markLoadError(uri = null))
        val completion = tracker.complete(success = true)!!

        assertFalse(completion.outcome.commitsContent)
        assertEquals(url, completion.outcome.failure?.failedUrl)
        assertEquals(url, completion.outcome.failure?.retryUrl)
    }

    @Test
    fun staleStopIsAttributedToItsOwnStartAndCannotBeCurrent() {
        val tracker = GeckoNavigationTracker()
        val olderUrl = "https://example.test/older"
        val newerUrl = "https://example.test/newer"
        val older = tracker.begin(olderUrl, geckoSafeRetryUrl("GET", olderUrl))
        val newer = tracker.begin(newerUrl, geckoSafeRetryUrl("GET", newerUrl))

        assertNull(tracker.progressGeneration())
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
    fun knownGetRetryCandidateCanFollowItsRedirect() {
        val tracker = GeckoNavigationTracker()
        val originalUrl = "https://example.test/start"
        val redirectedUrl = "https://example.test/final"
        tracker.begin(originalUrl, geckoSafeRetryUrl("GET", originalUrl))

        tracker.updateCurrentUrl(redirectedUrl)
        tracker.updateCurrentRetryUrl(redirectedUrl)
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
