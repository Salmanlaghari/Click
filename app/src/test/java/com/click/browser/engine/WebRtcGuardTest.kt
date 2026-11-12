package com.click.browser.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebRtcGuardTest {
    @Test
    fun guardFollowsTheSettingExactly() {
        assertEquals(
            WebRtcGuardSyncAction.ENABLE,
            webRtcGuardSyncAction(blocked = true, currentlyEnabled = false)
        )
        assertEquals(
            WebRtcGuardSyncAction.DISABLE,
            webRtcGuardSyncAction(blocked = false, currentlyEnabled = true)
        )
        assertEquals(
            WebRtcGuardSyncAction.NONE,
            webRtcGuardSyncAction(blocked = true, currentlyEnabled = true)
        )
        assertEquals(
            WebRtcGuardSyncAction.NONE,
            webRtcGuardSyncAction(blocked = false, currentlyEnabled = false)
        )
    }

    @Test
    fun noToggleIsIssuedFromStaleStateWhileAToggleIsInFlight() {
        // While an enable/disable GeckoResult is pending, metaData.enabled
        // is stale; every combination must defer (NONE) and let the
        // completion handler reconcile against the latest intent instead.
        for (blocked in listOf(true, false)) {
            for (enabled in listOf(true, false)) {
                assertEquals(
                    WebRtcGuardSyncAction.NONE,
                    webRtcGuardSyncAction(blocked, enabled, toggleInFlight = true)
                )
                assertEquals(
                    webRtcGuardSyncAction(blocked, enabled),
                    webRtcGuardSyncAction(blocked, enabled, toggleInFlight = false)
                )
            }
        }
    }

    @Test
    fun schemePatternMatchesRealPageProtocolsOnly() {
        // The guard scripts test window.location.protocol, which includes
        // the trailing colon. The fixed pattern /^https?:/ (no end anchor)
        // must match real pages and reject everything else; the previous
        // /^https?:$/ matched nothing at all.
        val pattern = Regex("^https?:")
        assertTrue(pattern.containsMatchIn("http:"))
        assertTrue(pattern.containsMatchIn("https:"))
        assertFalse(pattern.containsMatchIn("about:"))
        assertFalse(pattern.containsMatchIn("file:"))
        assertFalse(pattern.containsMatchIn("chrome:"))
        assertFalse(pattern.containsMatchIn("data:"))
        assertFalse(Regex("^https?:$").containsMatchIn("https:"))
        for (script in bothBlockScripts()) {
            assertTrue(script.contains("/^https?:/"))
            assertFalse(script.contains("/^https?:$/"))
        }
    }

    @Test
    fun stubDescriptorIsLockedSoPagesCannotUndoTheBlock() {
        // writable/configurable MUST stay false in both copies: a writable
        // stub can be replaced by any page script, silently re-enabling
        // RTCPeerConnection behind the user's back.
        for (script in bothBlockScripts()) {
            assertTrue(script.contains("writable: false"))
            assertTrue(script.contains("configurable: false"))
            assertFalse(script.contains("writable: true"))
            assertFalse(script.contains("configurable: true"))
        }
    }

    /** The WebView script (Kotlin) and the GeckoView extension script (asset). */
    private fun bothBlockScripts(): List<String> = listOf(
        PrivacyGuards.WEBRTC_BLOCK_JS,
        assetFile("webrtc_guard/guard.js").readText()
    )

    @Test
    fun blockScriptStubsPeerConnectionOnly() {
        val script = PrivacyGuards.WEBRTC_BLOCK_JS
        assertTrue(script.contains("RTCPeerConnection"))
        assertTrue(script.contains("webkitRTCPeerConnection"))
        assertTrue(script.contains("WebRTC is disabled by Click's Block WebRTC setting."))
        // Scope lock: camera/microphone APIs stay under Click's per-site
        // permission prompts; the block must not neuter them.
        assertFalse(script.contains("getUserMedia"))
        assertFalse(script.contains("enumerateDevices"))
    }

    @Test
    fun bundledGuardExtensionMatchesTheKotlinContract() {
        val manifestFile = assetFile("webrtc_guard/manifest.json")
        val guardFile = assetFile("webrtc_guard/guard.js")
        val manifest = JSONObject(manifestFile.readText())

        assertEquals(
            WebRtcGuard.EXTENSION_ID,
            manifest.getJSONObject("browser_specific_settings").getJSONObject("gecko").getString("id")
        )
        assertEquals(
            "resource://android/assets/webrtc_guard/",
            WebRtcGuard.EXTENSION_LOCATION
        )
        val contentScript = manifest.getJSONArray("content_scripts").getJSONObject(0)
        assertEquals("document_start", contentScript.getString("run_at"))
        assertTrue(contentScript.getBoolean("all_frames"))
        assertEquals("guard.js", contentScript.getJSONArray("js").getString(0))

        // The extension script and the WebView script must stay in sync: both
        // stub the same constructors with the same message, neither touches
        // media-device APIs. Scope is asserted on executable code only —
        // comment lines may legitimately name the APIs they explain avoiding.
        val guardScript = guardFile.readText()
        assertTrue(guardScript.contains("RTCPeerConnection"))
        assertTrue(guardScript.contains("webkitRTCPeerConnection"))
        assertTrue(guardScript.contains("WebRTC is disabled by Click's Block WebRTC setting."))
        val guardCode = guardScript.lineSequence()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")
        assertFalse(guardCode.contains("getUserMedia"))
        assertFalse(guardCode.contains("enumerateDevices"))
    }

    private fun assetFile(relativePath: String): File = listOf(
        "src/main/assets/$relativePath",
        "app/src/main/assets/$relativePath"
    ).map(::File).firstOrNull { it.isFile }
        ?: error("Bundled asset $relativePath not found from the test working directory")
}
