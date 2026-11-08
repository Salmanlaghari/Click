package com.click.browser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkImporterTest {

    @Test
    fun nestedFolderPathsAreStoredSeparatelyFromBookmarkTitles() {
        val result = parse(
            """<!DOCTYPE NETSCAPE-Bookmark-file-1>
                <DL><p>
                  <DT><H3>Work</H3><DL><p>
                    <DT><A HREF="https://parent.example/">Parent title</A>
                    <DT><H3>Reading</H3><DL><p>
                      <DT><A HREF="https://nested.example/article">Nested title</A>
                    </DL>
                  </DL>
                  <DT><A HREF="https://root.example/">Root title</A>
                </DL>
            """
        )

        assertEquals(
            listOf(
                Bookmark("Parent title", "https://parent.example/", "Work"),
                Bookmark(
                    "Nested title",
                    "https://nested.example/article",
                    "Work${BookmarkImporter.FOLDER_PATH_SEPARATOR}Reading"
                ),
                Bookmark("Root title", "https://root.example/")
            ),
            result.bookmarks
        )
        assertEquals(0, result.skippedDuplicates)
        assertEquals(0, result.skippedNonHttp)
    }

    @Test
    fun emptyAndMalformedFoldersDoNotCorruptTheParentPath() {
        val result = parse(
            """<!DOCTYPE NETSCAPE-Bookmark-file-1>
                <DL><p>
                  <DT><H3>Parent</H3><DL><p>
                    <DT><H3></H3><DL><p></DL>
                    <DT><H3>Missing child list</H3>
                    <DT><A HREF="https://inside-parent.example/">Parent item</A>
                  </DL>
                  <DT><H3>Detached heading</H3>
                  <DT><A HREF="https://root.example/">Root item</A>
                </DL>
                </DL>
            """
        )

        assertEquals(
            listOf(
                Bookmark("Parent item", "https://inside-parent.example/", "Parent"),
                Bookmark("Root item", "https://root.example/")
            ),
            result.bookmarks
        )
    }

    @Test
    fun keepsHttpLinksAndTitlesWhileSkippingNonHttpAndDuplicateUrls() {
        val result = parse(
            """<!DOCTYPE NETSCAPE-Bookmark-file-1>
                <DL><p>
                  <DT><A HREF="https://example.test/search?q=one&amp;lang=en">Search &amp; Explore</A>
                  <DT><H3>Other folder</H3><DL><p>
                    <DT><A HREF="javascript:alert(1)">Unsafe link</A>
                    <DT><A HREF="https://example.test/search?q=one&amp;lang=en">Duplicate link</A>
                  </DL>
                </DL>
            """
        )

        assertEquals(
            listOf(Bookmark("Search & Explore", "https://example.test/search?q=one&lang=en")),
            result.bookmarks
        )
        assertEquals(1, result.skippedNonHttp)
        assertEquals(1, result.skippedDuplicates)
    }

    @Test
    fun allowsHttpAndFallsBackToUrlWhenTitleIsBlank() {
        val result = parse(
            """<!DOCTYPE NETSCAPE-Bookmark-file-1>
                <DL><p>
                  <DT><A HREF="HTTP://example.test/path">   </A>
                </DL>
            """
        )

        assertEquals(
            listOf(Bookmark("HTTP://example.test/path", "HTTP://example.test/path")),
            result.bookmarks
        )
        assertEquals(0, result.skippedNonHttp)
    }

    @Test
    fun overDepthOrOverlongFolderPathsBecomeUnfiledWithoutChangingTitles() {
        val tooDeep = List(BookmarkImporter.MAX_IMPORTED_FOLDER_DEPTH + 1) { "A" }
        val deepResult = parse(nestedFolderHtml(tooDeep))
        assertEquals(Bookmark("Deep title", "https://deep.example/"), deepResult.bookmarks.single())

        val overlongResult = parse(nestedFolderHtml(listOf("A".repeat(25), "B".repeat(25))))
        assertEquals(Bookmark("Long title", "https://long.example/"), overlongResult.bookmarks.single())
    }

    private fun parse(html: String): BookmarkImporter.ParseResult =
        BookmarkImporter.parse(html) { value ->
            value
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
        }

    private fun nestedFolderHtml(folderNames: List<String>): String = buildString {
        append("<!DOCTYPE NETSCAPE-Bookmark-file-1><DL><p>")
        folderNames.forEach { name -> append("<DT><H3>$name</H3><DL><p>") }
        val isDepthCase = folderNames.size > BookmarkImporter.MAX_IMPORTED_FOLDER_DEPTH
        val title = if (isDepthCase) "Deep title" else "Long title"
        val url = if (isDepthCase) "https://deep.example/" else "https://long.example/"
        append("<DT><A HREF=\"$url\">$title</A>")
        repeat(folderNames.size + 1) { append("</DL>") }
    }
}
