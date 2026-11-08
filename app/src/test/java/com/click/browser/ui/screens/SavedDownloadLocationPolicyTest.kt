package com.click.browser.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedDownloadLocationPolicyTest {
    @Test
    fun preservesSafContentUris() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ADownload"

        assertEquals(SavedDownloadLocation.ContentUri(uri), parseSavedDownloadLocation(uri))
    }

    @Test
    fun decodesLocalFileUris() {
        assertEquals(
            SavedDownloadLocation.FilePath("/storage/emulated/0/Download/Click report.pdf"),
            parseSavedDownloadLocation("file:///storage/emulated/0/Download/Click%20report.pdf")
        )
    }

    @Test
    fun acceptsAbsolutePrivateFilePaths() {
        assertEquals(
            SavedDownloadLocation.FilePath("/data/user/0/com.teampkai.clickbrowser/files/Download/report.pdf"),
            parseSavedDownloadLocation("/data/user/0/com.teampkai.clickbrowser/files/Download/report.pdf")
        )
    }

    @Test
    fun rejectsEmptyRelativeAndNetworkLocations() {
        assertNull(parseSavedDownloadLocation(""))
        assertNull(parseSavedDownloadLocation("relative/report.pdf"))
        assertNull(parseSavedDownloadLocation("https://example.test/report.pdf"))
    }
}
