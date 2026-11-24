package com.click.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkExporterTest {

    @Test
    fun emitsNetscapeHeaderAndEntries() {
        val html = BookmarkExporter.toNetscapeHtml(
            listOf(Bookmark("Example", "https://example.com/"))
        )
        assertTrue(html.startsWith("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assertTrue(html.contains("<DT><A HREF=\"https://example.com/\">Example</A>"))
    }

    @Test
    fun groupsBookmarksUnderTheirFolderLabel() {
        val html = BookmarkExporter.toNetscapeHtml(
            listOf(
                Bookmark("Loose", "https://loose.example/"),
                Bookmark("Filed", "https://filed.example/", folder = "News")
            )
        )
        assertTrue(html.contains("<DT><H3>News</H3>"))
        // The filed entry appears after its folder heading.
        assertTrue(html.indexOf("<H3>News</H3>") < html.indexOf("https://filed.example/"))
    }

    @Test
    fun escapesHtmlInTitlesUrlsAndFolders() {
        val html = BookmarkExporter.toNetscapeHtml(
            listOf(Bookmark("A & \"B\" <C>", "https://x.example/?a=1&b=2", folder = "R&D"))
        )
        assertTrue(html.contains("A &amp; &quot;B&quot; &lt;C&gt;"))
        assertTrue(html.contains("https://x.example/?a=1&amp;b=2"))
        assertTrue(html.contains("<H3>R&amp;D</H3>"))
    }

    @Test
    fun emptyListStillProducesValidShell() {
        val html = BookmarkExporter.toNetscapeHtml(emptyList())
        assertTrue(html.contains("<DL><p>"))
        assertTrue(html.trimEnd().endsWith("</DL><p>"))
    }

    @Test
    fun escapeCoversAllFourEntities() {
        assertEquals("&amp;&lt;&gt;&quot;", BookmarkExporter.escape("&<>\""))
    }
}
