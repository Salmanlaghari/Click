package com.click.browser.engine

/** Pure rules for keeping Ask AI retry actions attached to their failed message rows. */
internal object AiRequestRetryPolicy {
    fun canRetry(
        messageRole: String?,
        hasSavedRequest: Boolean,
        requestInProgress: Boolean,
        requiresPageContextConsent: Boolean = false,
        pageContextConsentGranted: Boolean = true
    ): Boolean = messageRole == "error" &&
        hasSavedRequest &&
        !requestInProgress &&
        (!requiresPageContextConsent || pageContextConsentGranted)

    fun <T> afterErrorRemoved(
        requestsByMessageIndex: Map<Int, T>,
        removedErrorIndex: Int
    ): Map<Int, T> = buildMap(requestsByMessageIndex.size) {
        requestsByMessageIndex.forEach { (index, request) ->
            if (index != removedErrorIndex) {
                put(if (index > removedErrorIndex) index - 1 else index, request)
            }
        }
    }
}
