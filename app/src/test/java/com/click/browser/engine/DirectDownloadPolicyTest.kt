package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectDownloadPolicyTest {

    // ---- navigationAction: media files ----

    @Test
    fun offersDownloadForDirectMp3Link() {
        assertEquals(
            DirectNavigationAction.OFFER_MEDIA,
            DirectDownloadPolicy.navigationAction("https://example.com/songs/track.mp3")
        )
    }

    @Test
    fun offersDownloadForDirectVideoAndAudioFormats() {
        val urls = listOf(
            "https://cdn.example.com/movie.mp4",
            "https://cdn.example.com/clip.webm",
            "https://cdn.example.com/voice.m4a",
            "https://cdn.example.com/song.flac",
            "https://cdn.example.com/song.wav",
            "https://cdn.example.com/song.ogg",
            "https://cdn.example.com/song.opus",
            "https://cdn.example.com/video.mov",
            "https://cdn.example.com/video.3gp"
        )
        for (url in urls) {
            assertEquals(url, DirectNavigationAction.OFFER_MEDIA, DirectDownloadPolicy.navigationAction(url))
        }
    }

    @Test
    fun mediaDetectionIgnoresQueryStringAndCase() {
        assertEquals(
            DirectNavigationAction.OFFER_MEDIA,
            DirectDownloadPolicy.navigationAction("https://example.com/Track.MP3?token=abc&dl=1")
        )
    }

    @Test
    fun bypassedMediaUrlLoadsNormally() {
        val url = "https://example.com/song.mp3"
        assertEquals(
            DirectNavigationAction.ALLOW,
            DirectDownloadPolicy.navigationAction(url, bypassUrl = url)
        )
    }

    @Test
    fun bypassForADifferentUrlDoesNotAllow() {
        assertEquals(
            DirectNavigationAction.OFFER_MEDIA,
            DirectDownloadPolicy.navigationAction(
                "https://example.com/song.mp3",
                bypassUrl = "https://example.com/other.mp3"
            )
        )
    }

    // ---- navigationAction: PDF parity ----

    @Test
    fun offersPdfSheetForDirectPdfLink() {
        assertEquals(
            DirectNavigationAction.OFFER_PDF,
            DirectDownloadPolicy.navigationAction("https://example.com/docs/report.pdf")
        )
        assertTrue(DirectDownloadPolicy.isPdfFileUrl("https://example.com/a.PDF?x=1"))
    }

    // ---- navigationAction: everything else loads ----

    @Test
    fun normalPagesAreAllowed() {
        val urls = listOf(
            "https://example.com/",
            "https://example.com/article.html",
            "https://example.com/watch?v=abc",
            "https://example.com/download?id=123",
            "https://example.com/files/readme.txt"
        )
        for (url in urls) {
            assertEquals(url, DirectNavigationAction.ALLOW, DirectDownloadPolicy.navigationAction(url))
        }
    }

    @Test
    fun nonHttpUrlsAreAllowed() {
        assertEquals(
            DirectNavigationAction.ALLOW,
            DirectDownloadPolicy.navigationAction("about:blank")
        )
        assertEquals(
            DirectNavigationAction.ALLOW,
            DirectDownloadPolicy.navigationAction("data:text/html,hi")
        )
    }

    @Test
    fun nonDisplayableArchivesAreLeftToTheExternalResponsePath() {
        // .zip/.mkv already download via GeckoView's external response;
        // the navigation offer must not double-handle them.
        assertEquals(
            DirectNavigationAction.ALLOW,
            DirectDownloadPolicy.navigationAction("https://example.com/file.zip")
        )
        assertEquals(
            DirectNavigationAction.ALLOW,
            DirectDownloadPolicy.navigationAction("https://example.com/movie.mkv")
        )
    }

    // ---- streaming services are never treated as files ----

    @Test
    fun streamingHostsNeverGetAMediaOffer() {
        val urls = listOf(
            "https://www.youtube.com/watch?v=abc",
            "https://music.youtube.com/song.mp3",
            "https://m.facebook.com/video.mp4",
            "https://www.instagram.com/reel.mp4",
            "https://www.tiktok.com/video.mp4",
            "https://video.twimg.com/ext_tw_video/1.mp4"
        )
        for (url in urls) {
            assertEquals(url, DirectNavigationAction.ALLOW, DirectDownloadPolicy.navigationAction(url))
            assertFalse(url, DirectDownloadPolicy.looksLikeDirectFileUrl(url))
        }
    }

    // ---- long-press "Download link" detection ----

    @Test
    fun longPressDownloadCoversFilesMediaAndDocuments() {
        val urls = listOf(
            "https://example.com/archive.zip",
            "https://example.com/archive.7z",
            "https://example.com/app.apk",
            "https://example.com/doc.pdf",
            "https://example.com/song.mp3",
            "https://example.com/movie.mkv",
            "https://example.com/book.epub",
            "https://example.com/sheet.xlsx"
        )
        for (url in urls) {
            assertTrue(url, DirectDownloadPolicy.looksLikeDirectFileUrl(url))
        }
    }

    @Test
    fun longPressDownloadIgnoresPagesAndNonHttp() {
        assertFalse(DirectDownloadPolicy.looksLikeDirectFileUrl("https://example.com/page"))
        assertFalse(DirectDownloadPolicy.looksLikeDirectFileUrl("https://example.com/page.html"))
        assertFalse(DirectDownloadPolicy.looksLikeDirectFileUrl("ftp://example.com/file.zip"))
        assertFalse(DirectDownloadPolicy.looksLikeDirectFileUrl("blob:https://example.com/file.zip"))
    }

    // ---- helpers ----

    @Test
    fun extensionComesFromThePathNotTheQuery() {
        assertEquals("mp3", DirectDownloadPolicy.extensionOf("https://example.com/a/b.mp3?fmt=zip"))
        assertEquals(null, DirectDownloadPolicy.extensionOf("https://example.com/a/b?fmt=mp3"))
        assertEquals(null, DirectDownloadPolicy.extensionOf("https://example.com/a/b"))
    }

    @Test
    fun fileNameIsDecodedFromTheLastPathSegment() {
        assertEquals(
            "My Song (2026).mp3",
            DirectDownloadPolicy.fileNameOf("https://example.com/files/My%20Song%20(2026).mp3")
        )
        assertEquals("track.mp4", DirectDownloadPolicy.fileNameOf("https://example.com/track.mp4?dl=1"))
    }

    @Test
    fun fileNameFallsBackWhenThePathHasNoFileName() {
        assertEquals("download.mp3", DirectDownloadPolicy.fileNameOf("https://example.com/.mp3"))
        assertEquals("download", DirectDownloadPolicy.fileNameOf("https://example.com/"))
    }

    @Test
    fun audioDetectionDrivesTheSheetIcon() {
        assertTrue(DirectDownloadPolicy.isAudioFileUrl("https://example.com/song.mp3"))
        assertFalse(DirectDownloadPolicy.isAudioFileUrl("https://example.com/movie.mp4"))
    }
}
