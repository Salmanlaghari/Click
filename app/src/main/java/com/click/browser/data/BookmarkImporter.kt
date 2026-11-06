package com.click.browser.data

import android.text.Html

/**
 * Parses the Netscape Bookmark File Format — the standard HTML format Chrome,
 * Edge, Firefox and other browsers use for bookmark exports (e.g. the
 * `bookmarks_<date>.html` file from Chrome's "Export bookmarks").
 *
 * Pure client-side string parsing: no network calls, no account access,
 * nothing leaves the device.
 */
object BookmarkImporter {

    /** Files larger than this are rejected before parsing (10 MiB). */
    const val MAX_FILE_BYTES = 10 * 1024 * 1024

    data class ParseResult(
        /** Parsed bookmarks, de-duplicated within the file itself. */
        val bookmarks: List<Bookmark>,
        /** Entries skipped because the URL is not http/https (javascript:, chrome:, place:, ...). */
        val skippedNonHttp: Int,
        /** Duplicate URLs found within the file itself (already-skipped). */
        val skippedDuplicates: Int
    )

    // One token pass in document order: bookmark anchors, folder headings and
    // <DL> tags. Folders are tracked on a stack so their hierarchy can be
    // flattened into the bookmark title ("Folder / Subfolder / Title").
    private val TokenPattern = Regex(
        """<DT>\s*<A\s+HREF="([^"]+)"[^>]*>(.*?)</A>|<DT>\s*<H3[^>]*>(.*?)</H3>|</?DL>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * Parses raw bookmark-export HTML.
     *
     * @throws IllegalArgumentException when the content is not a bookmarks export
     * or is otherwise unreadable.
     */
    fun parse(html: String): ParseResult {
        if (!html.contains("NETSCAPE-Bookmark-file-1", ignoreCase = true) &&
            !TokenPattern.containsMatchIn(html)
        ) {
            throw IllegalArgumentException("Not a bookmarks export file")
        }

        val bookmarks = mutableListOf<Bookmark>()
        val seenUrls = mutableSetOf<String>()
        var skippedNonHttp = 0
        var skippedDuplicates = 0
        val folderStack = ArrayDeque<String>()

        for (match in TokenPattern.findAll(html)) {
            val href = match.groups[1]?.value
            val anchorText = match.groups[2]?.value
            val folderName = match.groups[3]?.value
            val token = match.value

            when {
                // Bookmark entry
                href != null -> {
                    val url = href.trim()
                    if (!url.startsWith("http://", ignoreCase = true) &&
                        !url.startsWith("https://", ignoreCase = true)
                    ) {
                        skippedNonHttp++
                        continue
                    }
                    if (!seenUrls.add(url)) {
                        skippedDuplicates++
                        continue
                    }
                    val decodedTitle = anchorText
                        ?.let { fromHtml(it).trim() }
                        ?.takeIf { it.isNotEmpty() }
                    val baseTitle = decodedTitle ?: url
                    val title = if (folderStack.isNotEmpty()) {
                        (folderStack.toList() + baseTitle).joinToString(" / ")
                    } else {
                        baseTitle
                    }
                    bookmarks.add(Bookmark(title = title, url = url))
                }
                // Folder heading — push, popped when its <DL> closes
                folderName != null -> {
                    val name = fromHtml(folderName).trim()
                    if (name.isNotEmpty()) folderStack.addLast(name)
                }
                // </DL> closes a folder level (the root <DL> closes with an empty stack)
                token.equals("</DL>", ignoreCase = true) -> {
                    if (folderStack.isNotEmpty()) folderStack.removeLast()
                }
                // <DL> open tag — nothing to do
                else -> Unit
            }
        }

        return ParseResult(
            bookmarks = bookmarks,
            skippedNonHttp = skippedNonHttp,
            skippedDuplicates = skippedDuplicates
        )
    }

    private fun fromHtml(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
}
