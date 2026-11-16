package com.click.browser.engine

import org.mozilla.geckoview.WebRequestError

/** A recoverable main-frame navigation failure shown above GeckoView content. */
internal data class GeckoNavigationFailure(
    val failedUrl: String,
    val retryUrl: String?,
    val wasCancelled: Boolean = false,
    val errorLabel: String? = null
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
    var loadErrorObserved: Boolean = false,
    var loadErrorLabel: String? = null
)

private object GeckoNavigationGenerationIds {
    private val next = java.util.concurrent.atomic.AtomicLong(0)
    fun next(): Long = next.incrementAndGet()
}

/** A first-contentful-paint may reveal the page, but only for the active loading generation. */
internal fun shouldCommitGeckoContentOnFirstPaint(
    activeGeneration: Long,
    paintedGeneration: Long,
    isLoading: Boolean
): Boolean = isLoading && activeGeneration == paintedGeneration

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

    /**
     * Attribute a Gecko load error to a pending attempt ONLY on an exact URL
     * match. Gecko's error callback also fires for subresources, subframes,
     * and previous navigations that are already dying; guessing "the single
     * in-flight load" in those cases poisons a healthy navigation — its later
     * page-stop was then misreported as a failure. The tracker follows
     * redirects via [updateCurrentUrl], so a genuine main-frame error still
     * matches its attempt's current URL exactly.
     */
    fun markLoadError(uri: String?, errorLabel: String? = null): Boolean = synchronized(lock) {
        if (uri == null) return@synchronized false
        val attempt = pending.toList().lastOrNull { it.url == uri }
            ?: return@synchronized false
        attempt.loadErrorObserved = true
        attempt.loadErrorLabel = errorLabel
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

    /** Paint callbacks are session-level; use the latest load only for visible-content state. */
    fun contentPaintGeneration(): Long? = synchronized(lock) {
        active?.generation
    }

    fun complete(success: Boolean): GeckoNavigationCompletion? = synchronized(lock) {
        val attempt = pending.pollFirst() ?: return@synchronized null
        if (active?.generation == attempt.generation) active = pending.peekLast()

        val wasCancelled = !success && cancelledGeneration == attempt.generation
        if (cancelledGeneration == attempt.generation) cancelledGeneration = null

        // A page-stop with success=true means Gecko actually delivered the
        // page: it ALWAYS commits content and NEVER produces a failure, even
        // when an error was attributed to this attempt earlier. Error events
        // can be stray or late (a previous page's dying subresource, a
        // subframe failure), and letting one veto a successful stop turned
        // loaded pages into the "Page couldn't load" card over blank content.
        if (success) {
            return@synchronized GeckoNavigationCompletion(
                generation = attempt.generation,
                url = attempt.url,
                outcome = GeckoNavigationStopOutcome(commitsContent = true, failure = null),
                wasCancelled = false,
                isCurrent = attempt.generation == latestGeneration
            )
        }

        val retryUrl = attempt.retryUrl?.takeIf { it == attempt.url }
        // Gecko reports success=false for EVERY stopped load, not just failed
        // ones: a load superseded by a newer navigation (link tapped mid-load,
        // a redirect hop rewritten or denied in onLoadRequest, a popup
        // hand-off) stops with success=false and NO load error. Only a load
        // Gecko actually errored (onLoadError, exactly attributed) — or one
        // the user deliberately stopped — is a page failure worth the
        // recovery card. An interrupted stop commits nothing and surfaces
        // nothing; the newer navigation (or the still-displayed page) owns
        // the UI from here.
        val outcome = if (!attempt.loadErrorObserved && !wasCancelled) {
            GeckoNavigationStopOutcome(commitsContent = false, failure = null)
        } else {
            geckoNavigationStopOutcome(
                success = false,
                failedUrl = attempt.url,
                safeRetryUrl = retryUrl,
                wasCancelled = wasCancelled,
                errorLabel = attempt.loadErrorLabel
            )
        }
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
    wasCancelled: Boolean = false,
    errorLabel: String? = null
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
            wasCancelled = wasCancelled,
            errorLabel = errorLabel
        )
    )
}

/**
 * Short human-readable reason for a Gecko load error, shown on the recovery
 * card so a failure stays diagnosable instead of collapsing into a generic
 * message. Matches [WebRequestError]'s code against its named constants
 * (verified against the GeckoView build this app ships); anything unmapped
 * keeps the raw category/code numbers.
 */
internal fun geckoLoadErrorLabel(category: Int, error: Int): String {
    val known = when (error) {
        WebRequestError.ERROR_UNKNOWN_HOST -> "Server address could not be found (DNS)"
        WebRequestError.ERROR_NET_TIMEOUT -> "The connection timed out"
        WebRequestError.ERROR_NET_INTERRUPT -> "The connection was interrupted"
        WebRequestError.ERROR_NET_RESET -> "The connection was reset"
        WebRequestError.ERROR_CONNECTION_REFUSED -> "The server refused the connection"
        WebRequestError.ERROR_OFFLINE -> "The device is offline"
        WebRequestError.ERROR_REDIRECT_LOOP -> "Too many redirects"
        WebRequestError.ERROR_PORT_BLOCKED -> "The port is blocked"
        WebRequestError.ERROR_UNKNOWN_SOCKET_TYPE -> "Unknown connection type"
        WebRequestError.ERROR_SECURITY_SSL -> "Secure connection failed (SSL)"
        WebRequestError.ERROR_SECURITY_BAD_CERT -> "Secure connection failed (bad certificate)"
        WebRequestError.ERROR_BAD_HSTS_CERT -> "Secure connection failed (HSTS certificate)"
        WebRequestError.ERROR_HTTPS_ONLY -> "The site only allows secure connections"
        WebRequestError.ERROR_MALFORMED_URI -> "The address is not valid"
        WebRequestError.ERROR_UNKNOWN_PROTOCOL -> "Unsupported protocol"
        WebRequestError.ERROR_FILE_NOT_FOUND -> "File not found"
        WebRequestError.ERROR_FILE_ACCESS_DENIED -> "File access denied"
        WebRequestError.ERROR_DATA_URI_TOO_LONG -> "The data address is too long"
        WebRequestError.ERROR_PROXY_CONNECTION_REFUSED -> "The proxy refused the connection"
        WebRequestError.ERROR_UNKNOWN_PROXY_HOST -> "The proxy address could not be found"
        WebRequestError.ERROR_UNSAFE_CONTENT_TYPE -> "Unsafe content type"
        WebRequestError.ERROR_CORRUPTED_CONTENT -> "The page content was corrupted"
        WebRequestError.ERROR_CONTENT_CRASHED -> "The page crashed while loading"
        WebRequestError.ERROR_INVALID_CONTENT_ENCODING -> "Invalid content encoding"
        WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI -> "Blocked: reported malware site"
        WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI -> "Blocked: reported unwanted-software site"
        WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI -> "Blocked: reported harmful site"
        WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI -> "Blocked: reported phishing site"
        WebRequestError.ERROR_HARMFULADDON_URI -> "Blocked: harmful add-on site"
        WebRequestError.ERROR_UNKNOWN -> "Unknown error"
        else -> null
    }
    return known ?: "Load error (category $category, code $error)"
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
