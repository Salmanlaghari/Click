package com.click.browser.data

import android.text.Html

/**
 * Parses the Netscape Bookmark File Format — the standard HTML format Chrome,
 * Edge, Firefox and other browsers use for bookmark exports (e.g. the
 * `bookmarks_<date>.html` file from Chrome's "Export bookmarks").
 *
 * Pure client-side string parsing: no network calls, no account access,
 * nothing leaves the device.
 *
 * The current [Bookmark] model stores one flat folder label, not a folder tree.
 * Imported nesting is therefore kept as a readable path in that label, using
 * [FOLDER_PATH_SEPARATOR], without changing bookmark titles. Paths deeper than
 * [MAX_IMPORTED_FOLDER_DEPTH], longer than the existing 40-character folder
 * limit, or invalid under the existing folder-label rules are left unfiled
 * rather than truncated or misrepresented.
 */
object BookmarkImporter {

    /** Files larger than this are rejected before parsing (10 MiB). */
    const val MAX_FILE_BYTES = 10 * 1024 * 1024

    /** Maximum number of named nested folders retained in an imported path. */
    const val MAX_IMPORTED_FOLDER_DEPTH = 8

    /** Readable separator used when representing nested folders in a flat label. */
    const val FOLDER_PATH_SEPARATOR = " › "

    data class ParseResult(
        /** Parsed bookmarks, de-duplicated within the file itself. */
        val bookmarks: List<Bookmark>,
        /** Entries skipped because the URL is not http/https (javascript:, chrome:, place:, ...). */
        val skippedNonHttp: Int,
        /** Duplicate URLs found within the file itself (already-skipped). */
        val skippedDuplicates: Int
    )

    // One token pass in document order. Each <DL> gets a stack frame, including
    // empty/malformed folders, so closing it cannot accidentally pop its parent.
    private val TokenPattern = Regex(
        """<DT\b[^>]*>\s*<A\b([^>]*)>(.*?)</A>|<DT\b[^>]*>\s*<H3\b[^>]*>(.*?)</H3>|</?DL\b[^>]*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val HrefPattern = Regex(
        """(?:^|\s)HREF\s*=\s*(?:"([^"]*)"|'([^']*)')""",
        RegexOption.IGNORE_CASE
    )

    private data class FolderFrame(val name: String?)

    /**
     * Parses raw bookmark-export HTML.
     *
     * @throws IllegalArgumentException when the content is not a bookmarks export
     * or is otherwise unreadable.
     */
    fun parse(html: String): ParseResult = parse(html, ::fromHtml)

    /** Injection point keeps parser regression tests independent of Android's HTML framework. */
    internal fun parse(html: String, decodeHtml: (String) -> String): ParseResult {
        if (!html.contains("NETSCAPE-Bookmark-file-1", ignoreCase = true) &&
            !TokenPattern.containsMatchIn(html)
        ) {
            throw IllegalArgumentException("Not a bookmarks export file")
        }

        val bookmarks = mutableListOf<Bookmark>()
        val seenUrls = mutableSetOf<String>()
        var skippedNonHttp = 0
        var skippedDuplicates = 0
        val folderStack = ArrayDeque<FolderFrame>()
        var pendingFolder: String? = null

        for (match in TokenPattern.findAll(html)) {
            val anchorAttributes = match.groups[1]?.value
            val anchorText = match.groups[2]?.value
            val folderName = match.groups[3]?.value
            val token = match.value

            when {
                // Bookmark entry
                anchorAttributes != null -> {
                    // A heading with no following <DL> is malformed; do not apply
                    // it to this bookmark or to later siblings.
                    pendingFolder = null
                    val hrefMatch = HrefPattern.find(anchorAttributes) ?: continue
                    val href = hrefMatch.groups[1]?.value ?: hrefMatch.groups[2]?.value.orEmpty()
                    val url = decodeHtml(href).trim()
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
                        ?.let { decodeHtml(it).trim() }
                        ?.takeIf { it.isNotEmpty() }
                    val baseTitle = decodedTitle ?: url
                    bookmarks.add(
                        Bookmark(
                            title = baseTitle,
                            url = url,
                            folder = folderPath(folderStack)
                        )
                    )
                }
                // A folder heading belongs to the next <DL>. Empty names are kept
                // as pending null frames so their matching close remains balanced.
                folderName != null -> {
                    pendingFolder = decodeHtml(folderName).trim().takeIf { it.isNotEmpty() }
                }
                token.startsWith("</", ignoreCase = true) -> {
                    if (folderStack.isNotEmpty()) folderStack.removeLast()
                    pendingFolder = null
                }
                else -> {
                    folderStack.addLast(FolderFrame(pendingFolder))
                    pendingFolder = null
                }
            }
        }

        return ParseResult(
            bookmarks = bookmarks,
            skippedNonHttp = skippedNonHttp,
            skippedDuplicates = skippedDuplicates
        )
    }

    private fun folderPath(folderStack: Iterable<FolderFrame>): String? {
        val names = folderStack.mapNotNull { it.name }
        if (names.isEmpty() || names.size > MAX_IMPORTED_FOLDER_DEPTH) return null
        val path = names.joinToString(FOLDER_PATH_SEPARATOR)
        return path.takeIf { BookmarkOrganizationPolicy.normalizeFolderName(it) != null }
    }

    private fun fromHtml(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
}
