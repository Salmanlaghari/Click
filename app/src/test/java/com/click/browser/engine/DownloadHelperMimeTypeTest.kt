package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadHelperMimeTypeTest {
    @Test
    fun normalizesServerMimeTypeAndDropsParameters() {
        assertEquals(
            "application/pdf",
            DownloadHelper.normalizeMimeType(" application/pdf ; charset=UTF-8 ", "file.bin")
        )
    }

    @Test
    fun guessesMimeFromFilenameWhenServerTypeIsMissingOrBlank() {
        assertEquals("image/png", DownloadHelper.normalizeMimeType(null, "photo.png"))
        assertEquals("application/pdf", DownloadHelper.normalizeMimeType("  ", "document.pdf"))
    }

    @Test
    fun unknownExtensionUsesBinaryFallback() {
        assertEquals("application/octet-stream", DownloadHelper.normalizeMimeType(null, "archive.unknown"))
    }
}
