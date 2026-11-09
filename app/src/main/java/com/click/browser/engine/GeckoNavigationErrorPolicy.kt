package com.click.browser.engine

/** A recoverable main-frame navigation failure shown above GeckoView content. */
internal data class GeckoNavigationFailure(
    val failedUrl: String,
    val retryUrl: String?,
    val wasCancelled: Boolean = false
)

/** Result of stopping a Gecko navigation; only a successful page stop commits content. */
internal data class GeckoNavigationStopOutcome(
    val commitsContent: Boolean,
    val failure: GeckoNavigationFailure?
)

internal data class GeckoNavigationStart(
    val generation: Long,
    val url: String
)

/** Snapshot attached to one page-stop callback; stale generations are ignored by the UI. */
internal data class GeckoNavigationCompletion(
    val generation: Long,
    val url: String,
    val outcome: GeckoNavigationStopOutcome,
    val wasCancelled: Boolean,
    val isCurrent: Boolean
)

private data class PendingGeckoNavigation(
    val generation: Long,
    var url: String,
    var retryUrl: String?,
    var loadErrorObserved: Boolean = false
)

private object GeckoNavigationGenerationIds {
    private val next = java.util.concurrent.atomic.AtomicLong(0)
    fun next(): Long = next.incrementAndGet()
}

/**
 * Gecko's page-stop callback has no URL or load ID. Keep a FIFO of page starts so
 * overlapping callbacks are attributed to the oldest unmatched main-frame load.
 * Per-load error, cancellation, and retry state never comes from mutable URL state.
 */
internal class GeckoNavigationTracker {
    private val lock = Any()
    private val pending = java.util.ArrayDeque<PendingGeckoNavigation>()
    private var latestGeneration = 0L
    private var active: PendingGeckoNavigation? = null
    private var cancelledGeneration: Long? = null

    fun begin(url: String, retryUrl: String?): GeckoNavigationStart = synchronized(lock) {
        val generation = GeckoNavigationGenerationIds.next()
        latestGeneration = generation
        val attempt = PendingGeckoNavigation(
            generation = generation,
            url = url,
            retryUrl = retryUrl?.takeIf { it == url }
        )
        pending.addLast(attempt)
        active = attempt
        GeckoNavigationStart(attempt.generation, attempt.url)
    }

    fun updateCurrentUrl(url: String) = synchronized(lock) {
        active?.url = url
    }

    fun updateCurrentRetryUrl(url: String?) = synchronized(lock) {
        active?.retryUrl = url?.takeIf(::isSafeGeckoRetryUrl)
    }

    fun currentRetryUrl(): String? = synchronized(lock) { active?.retryUrl }

    /** Gecko may report a null URI; attribute it only when one pending attempt is unambiguous. */
    fun markLoadError(uri: String?): Boolean = synchronized(lock) {
        val attempt = if (uri == null) {
            active?.takeIf { pending.size == 1 }
        } else {
            pending.toList().lastOrNull { it.url == uri } ?: active?.takeIf { it.url == uri }
        } ?: return@synchronized false
        if (uri != null) attempt.url = uri
        attempt.loadErrorObserved = true
        true
    }

    fun cancelActive(): Long? = synchronized(lock) {
        active?.generation.also { cancelledGeneration = it }
    }

    fun clearCancellation(generation: Long?) = synchronized(lock) {
        if (generation != null && cancelledGeneration == generation) cancelledGeneration = null
    }

    /** Raw Gecko progress has no ID; only expose it when exactly one page start is pending. */
    fun progressGeneration(): Long? = synchronized(lock) {
        pending.singleOrNull()?.generation
    }

    fun complete(success: Boolean): GeckoNavigationCompletion? = synchronized(lock) {
        val attempt = pending.pollFirst() ?: return@synchronized null
        if (active?.generation == attempt.generation) active = pending.peekLast()

        val commitsContent = success && !attempt.loadErrorObserved
        val wasCancelled = !commitsContent && cancelledGeneration == attempt.generation
        if (cancelledGeneration == attempt.generation) cancelledGeneration = null
        val retryUrl = if (commitsContent) null else attempt.retryUrl?.takeIf { it == attempt.url }
        val outcome = geckoNavigationStopOutcome(
            success = commitsContent,
            failedUrl = attempt.url,
            safeRetryUrl = retryUrl,
            wasCancelled = wasCancelled
        )
        GeckoNavigationCompletion(
            generation = attempt.generation,
            url = attempt.url,
            outcome = outcome,
            wasCancelled = wasCancelled,
            isCurrent = attempt.generation == latestGeneration
        )
    }
}

/**
 * Retry is available only for a Click-issued GET to an HTTP(S) origin. GeckoView's
 * load-error callback does not expose the request method, so page-originated or
 * otherwise unknown requests must not be replayed.
 */
internal fun geckoSafeRetryUrl(method: String, url: String?): String? {
    if (!method.equals("GET", ignoreCase = true)) return null
    return url?.takeIf(::isSafeGeckoRetryUrl)
}

internal fun geckoNavigationStopOutcome(
    success: Boolean,
    failedUrl: String?,
    safeRetryUrl: String?,
    wasCancelled: Boolean = false
): GeckoNavigationStopOutcome {
    if (success) return GeckoNavigationStopOutcome(commitsContent = true, failure = null)

    val validFailureUrl = failedUrl?.takeIf(::isSafeGeckoRetryUrl)
        ?: return GeckoNavigationStopOutcome(commitsContent = false, failure = null)
    val retryUrl = safeRetryUrl?.takeIf { it == validFailureUrl && isSafeGeckoRetryUrl(it) }
    return GeckoNavigationStopOutcome(
        commitsContent = false,
        failure = GeckoNavigationFailure(
            failedUrl = validFailureUrl,
            retryUrl = retryUrl,
            wasCancelled = wasCancelled
        )
    )
}

private fun isSafeGeckoRetryUrl(url: String): Boolean {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return false
    val scheme = url.substring(0, schemeEnd)
    if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
        return false
    }
    val authority = url.substring(schemeEnd + 3)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    return authority.isNotBlank() && '@' !in authority && authority.none { it.isWhitespace() || it.isISOControl() }
}
