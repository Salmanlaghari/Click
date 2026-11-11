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
