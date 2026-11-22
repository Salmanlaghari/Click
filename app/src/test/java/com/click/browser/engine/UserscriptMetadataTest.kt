package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptMetadataTest {

    private val sample = """
        // ==UserScript==
        // @name        Dark Example
        // @namespace   https://example.com/scripts
        // @version     1.2.3
        // @description Makes example dark
        // @match       https://*.example.com/*
        // @match       https://other.org/app/*
        // @include     https://included.example.net/*
        // @exclude     https://*.example.com/logout*
        // @run-at      document-start
        // @grant       GM_addStyle
        // @grant       GM_setValue
        // @noframes
        // ==/UserScript==
        console.log('hi');
    """.trimIndent()

    @Test
    fun parsesFullBlock() {
        val meta = UserscriptMetadataParser.parse(sample)
        assertNotNull(meta)
        meta!!
        assertEquals("Dark Example", meta.name)
        assertEquals("https://example.com/scripts", meta.namespace)
        assertEquals("1.2.3", meta.version)
        assertEquals("Makes example dark", meta.description)
        assertEquals(
            listOf("https://*.example.com/*", "https://other.org/app/*"),
            meta.matchPatterns
        )
        assertEquals(listOf("https://included.example.net/*"), meta.includes)
        assertEquals(listOf("https://*.example.com/logout*"), meta.excludes)
        assertEquals(UserscriptRunAt.DOCUMENT_START, meta.runAt)
        assertEquals(setOf("GM_addStyle", "GM_setValue"), meta.grants)
        assertTrue(meta.noFrames)
        assertEquals("https://example.com/scripts|Dark Example", meta.identityKey)
    }

    @Test
    fun missingBlockOrNameReturnsNull() {
        assertNull(UserscriptMetadataParser.parse("console.log(1)"))
        assertNull(
            UserscriptMetadataParser.parse(
                "// ==UserScript==\n// @version 1\n// ==/UserScript==\n"
            )
        )
    }

    @Test
    fun defaultsRunAtToDocumentEnd() {
        val meta = UserscriptMetadataParser.parse(
            "// ==UserScript==\n// @name X\n// @match *://*/*\n// ==/UserScript==\n"
        )!!
        assertEquals(UserscriptRunAt.DOCUMENT_END, meta.runAt)
        // document-idle is honoured as document-end (runner fires at DOMContentLoaded).
        val idle = UserscriptMetadataParser.parse(
            "// ==UserScript==\n// @name X\n// @run-at document-idle\n// ==/UserScript==\n"
        )!!
        assertEquals(UserscriptRunAt.DOCUMENT_END, idle.runAt)
    }

    @Test
    fun grantNoneDetection() {
        val meta = UserscriptMetadataParser.parse(
            "// ==UserScript==\n// @name X\n// @grant none\n// ==/UserScript==\n"
        )!!
        assertTrue(meta.grantsNone)
    }

    private fun meta(
        matches: List<String> = emptyList(),
        includes: List<String> = emptyList(),
        excludes: List<String> = emptyList()
    ) = UserscriptMetadata(
        name = "T", namespace = "", version = "", description = "",
        matchPatterns = matches, includes = includes, excludes = excludes,
        runAt = UserscriptRunAt.DOCUMENT_END, grants = emptySet(), noFrames = false
    )

    @Test
    fun matchPatternHostWildcardCoversSubdomainsAndBareDomain() {
        val m = meta(matches = listOf("https://*.example.com/*"))
        assertTrue(UserscriptUrlMatcher.appliesTo(m, "https://example.com/page"))
        assertTrue(UserscriptUrlMatcher.appliesTo(m, "https://sub.example.com/page"))
        assertFalse(UserscriptUrlMatcher.appliesTo(m, "https://notexample.com/page"))
        assertFalse(UserscriptUrlMatcher.appliesTo(m, "http://sub.example.com/page"))
    }

    @Test
    fun matchPatternSchemeStarAndAllUrls() {
        val star = meta(matches = listOf("*://example.com/*"))
        assertTrue(UserscriptUrlMatcher.appliesTo(star, "http://example.com/"))
        assertTrue(UserscriptUrlMatcher.appliesTo(star, "https://example.com/"))
        val all = meta(matches = listOf("<all_urls>"))
        assertTrue(UserscriptUrlMatcher.appliesTo(all, "https://anything.example/x"))
        assertFalse(UserscriptUrlMatcher.appliesTo(all, "about:blank"))
    }

    @Test
    fun matchPatternPathGlob() {
        val m = meta(matches = listOf("https://other.org/app/*"))
        assertTrue(UserscriptUrlMatcher.appliesTo(m, "https://other.org/app/home?x=1"))
        assertFalse(UserscriptUrlMatcher.appliesTo(m, "https://other.org/blog/home"))
    }

    @Test
    fun includeGlobAndExcludeWins() {
        val m = meta(
            matches = listOf("https://*.example.com/*"),
            includes = listOf("https://included.example.net/*"),
            excludes = listOf("https://*.example.com/logout*")
        )
        assertTrue(UserscriptUrlMatcher.appliesTo(m, "https://included.example.net/a"))
        assertFalse(UserscriptUrlMatcher.appliesTo(m, "https://sub.example.com/logout?next=/"))
        assertTrue(UserscriptUrlMatcher.appliesTo(m, "https://sub.example.com/home"))
    }

    @Test
    fun noMatchNoIncludeNeverApplies() {
        val m = meta()
        assertFalse(UserscriptUrlMatcher.appliesTo(m, "https://example.com/"))
    }

    @Test
    fun globEscapesRegexChars() {
        assertTrue(UserscriptUrlMatcher.globMatches("https://a.b/*", "https://a.b/x"))
        assertFalse(UserscriptUrlMatcher.globMatches("https://a.b/*", "https://aXb/x"))
    }

    @Test
    fun jsonRoundTrip() {
        val original = InstalledUserscript.fromMetadata(
            UserscriptMetadataParser.parse(sample)!!,
            code = sample,
            sourceUrl = "https://example.com/dark.user.js",
            id = "fixed-id",
            installedAtMs = 42L
        ).copy(enabled = false, lastError = "boom")
        val decoded = UserscriptJson.decode(UserscriptJson.encode(listOf(original)))
        assertEquals(1, decoded.size)
        val d = decoded[0]
        assertEquals(original.id, d.id)
        assertEquals(original.name, d.name)
        assertEquals(original.namespace, d.namespace)
        assertEquals(original.version, d.version)
        assertEquals(original.matchPatterns, d.matchPatterns)
        assertEquals(original.includes, d.includes)
        assertEquals(original.excludes, d.excludes)
        assertEquals(original.runAt, d.runAt)
        assertEquals(original.grants, d.grants)
        assertEquals(original.code, d.code)
        assertEquals(original.sourceUrl, d.sourceUrl)
        assertFalse(d.enabled)
        assertEquals("boom", d.lastError)
        assertEquals(42L, d.installedAtMs)
    }

    @Test
    fun decodeToleratesGarbage() {
        assertTrue(UserscriptJson.decode(null).isEmpty())
        assertTrue(UserscriptJson.decode("not json").isEmpty())
        assertTrue(UserscriptJson.decode("[]").isEmpty())
    }
}
