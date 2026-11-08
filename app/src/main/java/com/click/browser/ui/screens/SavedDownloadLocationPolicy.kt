package com.click.browser.ui.screens

import java.net.URI

internal sealed interface SavedDownloadLocation {
    data class ContentUri(val value: String) : SavedDownloadLocation
    data class FilePath(val absolutePath: String) : SavedDownloadLocation
}

/** Parses only the URI/path forms that Click stores for saved downloads. */
internal fun parseSavedDownloadLocation(rawPath: String): SavedDownloadLocation? {
    val value = rawPath.trim()
    if (value.isEmpty()) return null

    if (value.startsWith("content://", ignoreCase = true)) {
        return SavedDownloadLocation.ContentUri(value)
    }

    val filePath = when {
        value.startsWith("file:", ignoreCase = true) ->
            runCatching { URI(value).path }.getOrNull()
        value.startsWith("/") -> value
        else -> null
    }
    return filePath
        ?.takeIf { it.startsWith("/") }
        ?.let { SavedDownloadLocation.FilePath(it) }
}
