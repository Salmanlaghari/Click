package com.click.browser.data

/**
 * Exports bookmarks in the Netscape Bookmark File Format — the standard
 * HTML format Chrome, Edge and Firefox import, and the exact format
 * [BookmarkImporter] reads back, so a Click export round-trips into Click
 * as well as into the other browsers.
 *
 * Pure string generation (no Android classes) so it is unit-testable.
 * Folder labels are emitted as single folder names: Click's model stores
 * one flat label (imported nesting already lives in it as a readable
 * " › "-joined path), and the importer reads the label back unchanged.
 */
object BookmarkExporter {

    /** Builds the complete export document for [bookmarks]. */
    fun toNetscapeHtml(bookmarks: List<Bookmark>): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<!-- This is an automatically generated file.\n")
        sb.append("     It will be read and overwritten.\n")
        sb.append("     Do Not Edit! -->\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>Bookmarks</TITLE>\n")
        sb.append("<H1>Bookmarks</H1>\n")
        sb.append("<DL><p>\n")

        val (folderless, foldered) = bookmarks.partition { it.folder.isNullOrBlank() }
        for (bookmark in folderless) appendEntry(sb, bookmark, indent = "    ")
        val byFolder = foldered.groupBy { it.folder!!.trim() }
        for ((folder, entries) in byFolder.entries.sortedBy { it.key.lowercase() }) {
            sb.append("    <DT><H3>").append(escape(folder.trim())).append("</H3>\n")
            sb.append("    <DL><p>\n")
            for (bookmark in entries) appendEntry(sb, bookmark, indent = "        ")
            sb.append("    </DL><p>\n")
        }

        sb.append("</DL><p>\n")
        return sb.toString()
    }

    private fun appendEntry(sb: StringBuilder, bookmark: Bookmark, indent: String) {
        sb.append(indent)
            .append("<DT><A HREF=\"")
            .append(escape(bookmark.url))
            .append("\">")
            .append(escape(bookmark.title))
            .append("</A>\n")
    }

    /** Minimal HTML escaping for attribute values and text content. */
    internal fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
