package com.click.browser.engine

internal enum class DownloadSaveOutcome {
    SAVED_TO_SELECTED_LOCATION,
    FALLBACK_TO_PUBLIC_DOWNLOADS,
    FAILED
}

internal fun downloadSaveStatusMessage(fileName: String, outcome: DownloadSaveOutcome): String =
    when (outcome) {
        DownloadSaveOutcome.SAVED_TO_SELECTED_LOCATION -> "Saved $fileName"
        DownloadSaveOutcome.FALLBACK_TO_PUBLIC_DOWNLOADS ->
            "Couldn't save to your custom folder; saved to Downloads instead: $fileName"
        DownloadSaveOutcome.FAILED ->
            "Could not save the authenticated download. Check the selected folder."
    }
