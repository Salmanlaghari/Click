package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadSaveMessagePolicyTest {
    @Test
    fun reportsTheSelectedLocationOnNormalSuccess() {
        assertEquals(
            "Saved report.pdf",
            downloadSaveStatusMessage("report.pdf", DownloadSaveOutcome.SAVED_TO_SELECTED_LOCATION)
        )
    }

    @Test
    fun explicitlyNamesPublicDownloadsWhenCustomFolderFallbackSucceeds() {
        assertEquals(
            "Couldn't save to your custom folder; saved to Downloads instead: report.pdf",
            downloadSaveStatusMessage("report.pdf", DownloadSaveOutcome.FALLBACK_TO_PUBLIC_DOWNLOADS)
        )
    }

    @Test
    fun reportsFailureWhenNoDestinationWasWritten() {
        assertEquals(
            "Could not save the authenticated download. Check the selected folder.",
            downloadSaveStatusMessage("report.pdf", DownloadSaveOutcome.FAILED)
        )
    }
}
