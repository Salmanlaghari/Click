package com.click.browser.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevToolsInspectionPolicyTest {
    @Test
    fun pageInspectionRequiresDeveloperWebViewOpenSheetLoadedPageAndMatchingTarget() {
        BrowserMode.values().forEach { mode ->
            BrowserTabEngine.values().forEach { engine ->
                val expected = mode == BrowserMode.DEVELOPER && engine == BrowserTabEngine.WEBVIEW
                assertEquals(
                    expected,
                    DevToolsInspectionPolicy.canInspectPage(
                        mode = mode,
                        engine = engine,
                        panelVisible = true,
                        pageLoaded = true,
                        targetMatches = true
                    )
                )
                assertFalse(
                    DevToolsInspectionPolicy.canInspectPage(
                        mode = mode,
                        engine = engine,
                        panelVisible = false,
                        pageLoaded = true,
                        targetMatches = true
                    )
                )
                assertFalse(
                    DevToolsInspectionPolicy.canInspectPage(
                        mode = mode,
                        engine = engine,
                        panelVisible = true,
                        pageLoaded = false,
                        targetMatches = true
                    )
                )
                assertFalse(
                    DevToolsInspectionPolicy.canInspectPage(
                        mode = mode,
                        engine = engine,
                        panelVisible = true,
                        pageLoaded = true,
                        targetMatches = false
                    )
                )
            }
        }
    }

    @Test
    fun consoleAndNetworkCaptureAreLimitedToTheirSelectedTabs() {
        BrowserMode.values().forEach { mode ->
            BrowserTabEngine.values().forEach { engine ->
                (0..4).forEach { selectedTab ->
                    assertEquals(
                        mode == BrowserMode.DEVELOPER && engine == BrowserTabEngine.WEBVIEW && selectedTab == 1,
                        DevToolsInspectionPolicy.shouldCaptureSelectedTabData(
                            mode = mode,
                            engine = engine,
                            panelVisible = true,
                            pageLoaded = true,
                            targetMatches = true,
                            selectedTab = selectedTab,
                            requiredTab = 1
                        )
                    )
                    assertEquals(
                        mode == BrowserMode.DEVELOPER && engine == BrowserTabEngine.WEBVIEW && selectedTab == 2,
                        DevToolsInspectionPolicy.shouldCaptureSelectedTabData(
                            mode = mode,
                            engine = engine,
                            panelVisible = true,
                            pageLoaded = true,
                            targetMatches = true,
                            selectedTab = selectedTab,
                            requiredTab = 2
                        )
                    )
                    assertFalse(
                        DevToolsInspectionPolicy.shouldCapturePageConsoleMessages(
                            mode = mode,
                            engine = engine,
                            panelVisible = true,
                            pageLoaded = true,
                            targetMatches = true,
                            selectedTab = selectedTab,
                            consentGranted = false
                        )
                    )
                    assertEquals(
                        mode == BrowserMode.DEVELOPER && engine == BrowserTabEngine.WEBVIEW && selectedTab == 1,
                        DevToolsInspectionPolicy.shouldCapturePageConsoleMessages(
                            mode = mode,
                            engine = engine,
                            panelVisible = true,
                            pageLoaded = true,
                            targetMatches = true,
                            selectedTab = selectedTab,
                            consentGranted = true
                        )
                    )
                }
            }
        }
    }

    @Test
    fun lateCallbacksAreRejectedAfterSessionOrTargetChanges() {
        assertTrue(
            DevToolsInspectionPolicy.acceptsSessionResult(
                capturedGeneration = 7L,
                currentGeneration = 7L,
                targetMatches = true,
                sessionActive = true
            )
        )
        assertFalse(
            DevToolsInspectionPolicy.acceptsSessionResult(
                capturedGeneration = 7L,
                currentGeneration = 8L,
                targetMatches = true,
                sessionActive = true
            )
        )
        assertFalse(
            DevToolsInspectionPolicy.acceptsSessionResult(
                capturedGeneration = 8L,
                currentGeneration = 8L,
                targetMatches = false,
                sessionActive = true
            )
        )
        assertFalse(
            DevToolsInspectionPolicy.acceptsSessionResult(
                capturedGeneration = 8L,
                currentGeneration = 8L,
                targetMatches = true,
                sessionActive = false
            )
        )
    }

    @Test
    fun domSnapshotIsBoundedAndDoesNotReadTextFormValuesCookiesOrStorage() {
        val script = DevToolsInspectionPolicy.domSnapshotScript

        assertTrue(script.contains("var maxNodes = ${DevToolsInspectionPolicy.MAX_DOM_NODES}"))
        assertTrue(script.contains("var maxDepth = ${DevToolsInspectionPolicy.MAX_DOM_DEPTH}"))
        assertTrue(script.contains("element.children"))
        assertFalse(script.contains("outerHTML"))
        assertFalse(script.contains("innerHTML"))
        assertFalse(script.contains("textContent"))
        assertFalse(script.contains("innerText"))
        assertFalse(script.contains("document.cookie"))
        assertFalse(script.contains("localStorage"))
        assertFalse(script.contains("sessionStorage"))
        assertFalse(script.contains(".value"))
    }

    @Test
    fun sourceEnumerationAvoidsUnboundedNodeListsAndCapsTraversalAndOutput() {
        val script = DevToolsInspectionPolicy.sourceReferencesScript

        assertTrue(script.contains("document.createTreeWalker"))
        assertTrue(script.contains("maxScan=${DevToolsInspectionPolicy.MAX_SOURCE_SCAN_NODES}"))
        assertTrue(script.contains("maxReferences=${DevToolsInspectionPolicy.MAX_SOURCE_REFERENCES}"))
        assertTrue(script.contains("maxUrlLength=${DevToolsInspectionPolicy.MAX_SOURCE_URL_LENGTH}"))
        assertFalse(script.contains("querySelectorAll"))
        assertFalse(script.contains("Array.from"))
    }

    @Test
    fun parserIgnoresUntrustedFormValuesCookiesAndUnknownAttributes() {
        val pageJson = """{"root":{"tag":"input","id":"user@example.com","className":"field","type":"password","path":[],"childCount":0,"children":[],"value":"form-secret","cookie":"cookie-secret","text":"private text"},"truncated":false}"""
        val snapshot = DevToolsInspectionPolicy.parseDomSnapshot(JSONObject.quote(pageJson))
        val root = snapshot.root

        assertNotNull(root)
        assertEquals("input", root?.tagName)
        assertEquals("password", root?.elementType)
        assertEquals("[redacted]", root?.id)
        assertFalse(root.toString().contains("form-secret"))
        assertFalse(root.toString().contains("cookie-secret"))
        assertFalse(root.toString().contains("private text"))
    }

    @Test
    fun oversizedDomResultIsRejectedBeforeTreeParsing() {
        val snapshot = DevToolsInspectionPolicy.parseDomSnapshot(
            "x".repeat(DevToolsInspectionPolicy.MAX_DOM_RESULT_LENGTH + 1)
        )

        assertNull(snapshot.root)
        assertTrue(snapshot.truncated)
    }

    @Test
    fun sourceReferencesAreCappedSanitizedAndOversizedPayloadsRejected() {
        val sourceArray = JSONArray()
        repeat(DevToolsInspectionPolicy.MAX_SOURCE_REFERENCES + 20) { index ->
            sourceArray.put("https://example.com/resource-$index.js?token=private#fragment")
        }
        val references = DevToolsInspectionPolicy.parseSourceReferences(JSONObject.quote(sourceArray.toString()))

        assertEquals(DevToolsInspectionPolicy.MAX_SOURCE_REFERENCES, references.size)
        assertTrue(references.all { !it.contains("token") && !it.contains("private") && !it.contains("fragment") })
        assertTrue(
            DevToolsInspectionPolicy.parseSourceReferences(
                "x".repeat(DevToolsInspectionPolicy.MAX_SOURCE_RESULT_LENGTH + 1)
            ).isEmpty()
        )
    }

    @Test
    fun selectedStyleScriptUsesOnlyNumericPathAndSafeStyleProperties() {
        val script = DevToolsInspectionPolicy.buildComputedStylesScript(listOf(0, 3, 1))

        assertNotNull(script)
        assertTrue(script.orEmpty().contains("var path = [0, 3, 1]"))
        DevToolsInspectionPolicy.computedStyleProperties.forEach { property ->
            assertTrue(script.orEmpty().contains(JSONObject.quote(property)))
        }
        assertFalse(script.orEmpty().contains("document.cookie"))
        assertFalse(script.orEmpty().contains(".value"))
        assertNull(DevToolsInspectionPolicy.buildComputedStylesScript(listOf(-1)))
    }

    @Test
    fun styleParserKeepsOnlyAllowlistedProperties() {
        val styleJson = """{"tag":"button","properties":{"display":"block","color":"rgb(1, 2, 3)","width":"20px","value":"private","cookie":"secret"}}"""
        val styles = DevToolsInspectionPolicy.parseComputedStyles(JSONObject.quote(styleJson))

        assertEquals("button", styles?.tagName)
        assertEquals(setOf("display", "color", "width"), styles?.properties?.keys)
        assertFalse(styles.toString().contains("private"))
        assertFalse(styles.toString().contains("secret"))
    }

    @Test
    fun consoleCommandIsQuotedCappedAndCookieLikeStringResultsAreRedacted() {
        val command = "document.title; window.__probe = \"quoted\""
        val script = DevToolsInspectionPolicy.buildConsoleEvaluationScript(command)

        assertNotNull(script)
        assertTrue(script.orEmpty().contains(JSONObject.quote(command)))
        assertTrue(script.orEmpty().contains("[redacted cookie-like value]"))
        assertTrue(script.orEmpty().contains("access[_-]?key"))
        assertTrue(script.orEmpty().contains("Math.min(value.length, ${DevToolsInspectionPolicy.MAX_CONSOLE_ITEMS})"))
        assertTrue(script.orEmpty().contains("depth >= ${DevToolsInspectionPolicy.MAX_CONSOLE_DEPTH}"))
        assertTrue(script.orEmpty().contains("remaining:${DevToolsInspectionPolicy.MAX_CONSOLE_NODES}"))
        assertTrue(script.orEmpty().contains("Object.getOwnPropertyDescriptor"))
        assertFalse(script.orEmpty().contains("JSON.stringify(result,"))
        assertFalse(script.orEmpty().contains("window.__probe = \"quoted\""))
        assertNull(DevToolsInspectionPolicy.buildConsoleEvaluationScript("   "))
        val longCommand = "x".repeat(2_000)
        val limitedScript = DevToolsInspectionPolicy.buildConsoleEvaluationScript(longCommand).orEmpty()
        assertTrue(limitedScript.contains(JSONObject.quote(longCommand.take(DevToolsInspectionPolicy.MAX_COMMAND_LENGTH))))
        assertFalse(limitedScript.contains(JSONObject.quote(longCommand)))
    }

    @Test
    fun consoleResultParserBoundsOutputAndLabelsErrors() {
        val longResult = "r".repeat(DevToolsInspectionPolicy.MAX_RESULT_LENGTH + 10)
        val encoded = JSONObject.quote("""{"kind":"result","value":"$longResult"}""")
        assertEquals(DevToolsInspectionPolicy.MAX_RESULT_LENGTH, DevToolsInspectionPolicy.parseConsoleResult(encoded).length)

        val error = JSONObject.quote("""{"kind":"error","value":"bad command"}""")
        assertEquals("Error: bad command", DevToolsInspectionPolicy.parseConsoleResult(error))
        assertEquals(
            "Result was too large to display.",
            DevToolsInspectionPolicy.parseConsoleResult("x".repeat(DevToolsInspectionPolicy.MAX_CONSOLE_RESULT_LENGTH + 1))
        )
    }

    @Test
    fun displayedRequestUrlDropsCredentialsQueryFragmentAndLongTokenSegments() {
        assertEquals(
            "https://example.com/private/page",
            DevToolsInspectionPolicy.sanitizeUrl(
                "https://user:pass@example.com/private/page?access_token=secret#fragment"
            )
        )
        assertEquals(
            "https://example.com/[redacted]/asset.js",
            DevToolsInspectionPolicy.sanitizeUrl("https://example.com/${"a".repeat(64)}/asset.js")
        )
        assertEquals(
            "https://example.com/route/[redacted]/profile",
            DevToolsInspectionPolicy.sanitizeUrl("https://example.com/route/aBcD0123456789eFgHiJk/profile")
        )
        assertEquals(
            "https://example.com/[redacted]/asset.js",
            DevToolsInspectionPolicy.sanitizeUrl("https://example.com/%61BcD0123456789eFgHiJk/asset.js")
        )
        assertEquals(
            "https://example.com/api/access_token/[redacted]/profile",
            DevToolsInspectionPolicy.sanitizeUrl("https://example.com/api/access_token/shortToken123/profile")
        )
        assertEquals(
            "https://example.com/api/[redacted]/profile",
            DevToolsInspectionPolicy.sanitizeUrl("https://example.com/api/token=inline-secret/profile")
        )
        assertEquals("URL hidden", DevToolsInspectionPolicy.sanitizeUrl("file:///private/data"))
        assertEquals(
            "URL hidden",
            DevToolsInspectionPolicy.sanitizeUrl("https://example.com/" + "x".repeat(DevToolsInspectionPolicy.MAX_RAW_URL_LENGTH))
        )
    }

    @Test
    fun onlyOrdinaryHttpAndHttpsWebsiteUrlsAreInspectable() {
        assertTrue(DevToolsInspectionPolicy.isInspectableWebPageUrl("https://example.com/path"))
        assertTrue(DevToolsInspectionPolicy.isInspectableWebPageUrl("HTTP://example.com/path"))
        assertFalse(DevToolsInspectionPolicy.isInspectableWebPageUrl("devtools://devtools/"))
        assertFalse(DevToolsInspectionPolicy.isInspectableWebPageUrl("click://settings"))
        assertFalse(DevToolsInspectionPolicy.isInspectableWebPageUrl("file:///data/private.html"))
        assertFalse(DevToolsInspectionPolicy.isInspectableWebPageUrl("https:///missing-host"))
        assertFalse(DevToolsInspectionPolicy.isInspectableWebPageUrl(null))
    }

    @Test
    fun sameDocumentUrlChangesRequireAFreshPageSession() {
        assertTrue(
            DevToolsInspectionPolicy.shouldResetPageSessionForUrlChange(
                "https://example.com/first",
                "https://example.com/next"
            )
        )
        assertTrue(
            DevToolsInspectionPolicy.shouldResetPageSessionForUrlChange(
                "https://example.com/page#first",
                "https://example.com/page#next"
            )
        )
        assertFalse(
            DevToolsInspectionPolicy.shouldResetPageSessionForUrlChange(
                "https://example.com/page",
                "https://example.com/page"
            )
        )
        assertFalse(
            DevToolsInspectionPolicy.shouldResetPageSessionForUrlChange(
                "https://example.com/page",
                ""
            )
        )
    }
}
