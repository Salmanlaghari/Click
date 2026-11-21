package com.click.browser.engine

/**
 * Direct-download detection policy (Prince, 2026-11-21): "Jo website
 * downloading allowed karti hein, unke download automatically detected hon."
 *
 * Honest scope — what "automatic detection" means in Click:
 * - A response the server itself offers as a download
 *   (`Content-Disposition: attachment`, or a MIME type the engine cannot
 *   display) already reaches Click through GeckoView's external-response
 *   callback / WebView's download listener and is saved automatically.
 * - What was missing is the case this policy covers: a DIRECT FILE LINK the
 *   site serves for opening in the browser — above all media files
 *   (.mp3/.mp4/…), which GeckoView renders as an inline media page, so the
 *   user got playback with no way to save the file the site offered, and
 *   PDFs in Gecko tabs, which never got the offer sheet WebView tabs show.
 *
 * This policy only ever looks at the URL of a main-frame navigation or a
 * long-pressed link. It never inspects page content, never extracts media
 * from a page, and never touches streaming services: navigations to known
 * streaming hosts are always left alone (see [STREAMING_HOSTS]), DRM and
 * paywalled content are out of scope by construction — the only downloads
 * offered are files the server itself hands out at a plain http(s) URL the
 * user navigated to or long-pressed.
 */

/** What a main-frame navigation to [DirectDownloadPolicy] should do. */
enum class DirectNavigationAction {
    /** Load the URL normally. */
    ALLOW,

    /** Direct PDF link: show the existing view/download offer sheet. */
    OFFER_PDF,

    /** Direct media file link: show the open/download offer sheet. */
    OFFER_MEDIA
}

/** A pending direct-media offer for the tab that started the navigation. */
data class DirectMediaOffer(
    val url: String,
    val fileName: String,
    val tabId: String
)

object DirectDownloadPolicy {

    /**
     * Media formats GeckoView renders as an inline media document instead of
     * reporting an external response — a tap on these used to strand the
     * user on a player page with no download path. Formats Gecko cannot
     * render (e.g. .mkv, .avi) are deliberately absent: those already arrive
     * via the external-response download path.
     */
    private val DISPLAYABLE_MEDIA_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac",
        "mp4", "m4v", "webm", "mov", "3gp"
    )

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac"
    )

    /**
     * Extensions that make a long-pressed link "look like a direct file" —
     * the link section of the long-press menu offers Download link for
     * these. Wider than [DISPLAYABLE_MEDIA_EXTENSIONS] because the user is
     * explicitly asking for the file behind the link; non-displayable types
     * would download through the engine anyway once tapped.
     */
    private val DIRECT_FILE_EXTENSIONS = DISPLAYABLE_MEDIA_EXTENSIONS + setOf(
        "pdf",
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz",
        "apk", "xapk", "apks", "apkm",
        "mkv", "avi",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "epub",
        "dmg", "iso", "img", "torrent"
    )

    /**
     * Streaming-service hosts whose pages must never be treated as file
     * downloads. A top-level navigation to one of these is a platform page,
     * not a file the site offers the user — Click does not extract media
     * from streaming services, by standing policy.
     */
    private val STREAMING_HOSTS = listOf(
        "youtube.com", "youtu.be", "youtube-nocookie.com",
        "facebook.com", "fb.watch", "fbcdn.net",
        "instagram.com", "tiktok.com", "twimg.com",
        "snapchat.com", "dailymotion.com"
    )

    /** Lowercase file extension of the URL path (no dot), or null. */
    fun extensionOf(url: String): String? {
        val path = pathOf(url) ?: return null
        val segment = path.substringAfterLast('/')
        if (!segment.contains('.')) return null
        return segment.substringAfterLast('.').lowercase().ifBlank { null }
    }

    /** True for http(s) URLs whose path ends in `.pdf`. */
    fun isPdfFileUrl(url: String): Boolean =
        isHttpUrl(url) && extensionOf(url) == "pdf"

    /**
     * True for http(s) URLs pointing at a media file GeckoView would play
     * inline — unless the host is a streaming service, which is never
     * treated as a downloadable file.
     */
    fun isDisplayableMediaFileUrl(url: String): Boolean {
        if (!isHttpUrl(url)) return false
        if (isStreamingHost(url)) return false
        return extensionOf(url) in DISPLAYABLE_MEDIA_EXTENSIONS
    }

    /** True when a long-pressed link looks like a direct file download. */
    fun looksLikeDirectFileUrl(url: String): Boolean {
        if (!isHttpUrl(url)) return false
        if (isStreamingHost(url)) return false
        return extensionOf(url) in DIRECT_FILE_EXTENSIONS
    }

    /** True when the URL's file is an audio file (icon choice in the UI). */
    fun isAudioFileUrl(url: String): Boolean = extensionOf(url) in AUDIO_EXTENSIONS

    /** True when the URL belongs to a known streaming-service host. */
    fun isStreamingHost(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return STREAMING_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /**
     * Decides what a main-frame navigation should do.
     *
     * [bypassUrl] is the one URL the user already chose "Open" for from the
     * media offer sheet: it loads normally this once instead of offering
     * again (which would loop forever).
     */
    fun navigationAction(url: String, bypassUrl: String? = null): DirectNavigationAction {
        if (!isHttpUrl(url)) return DirectNavigationAction.ALLOW
        if (bypassUrl != null && url == bypassUrl) return DirectNavigationAction.ALLOW
        if (isPdfFileUrl(url)) return DirectNavigationAction.OFFER_PDF
        if (isDisplayableMediaFileUrl(url)) return DirectNavigationAction.OFFER_MEDIA
        return DirectNavigationAction.ALLOW
    }

    /**
     * Human file name for the offer sheet and the download record:
     * the URL-decoded last path segment, or a safe fallback.
     */
    fun fileNameOf(url: String): String {
        val segment = pathOf(url)?.substringAfterLast('/').orEmpty()
        val decoded = runCatching {
            java.net.URLDecoder.decode(segment, "UTF-8")
        }.getOrDefault(segment).trim()
        // A segment like ".mp3" has an extension but no actual name.
        if (decoded.isNotBlank() && decoded.substringBefore('.').isNotBlank()) return decoded
        val ext = extensionOf(url)
        return if (ext != null) "download.$ext" else "download"
    }

    private fun isHttpUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")

    private fun pathOf(url: String): String? = try {
        java.net.URI(url.trim()).path
    } catch (_: Exception) {
        null
    }

    private fun hostOf(url: String): String? = try {
        java.net.URI(url.trim()).host?.lowercase()
    } catch (_: Exception) {
        null
    }
}
