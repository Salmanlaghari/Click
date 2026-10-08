package com.click.browser.engine

import android.util.Base64

/**
 * Runtime side of the build-time key obfuscation (see app/build.gradle.kts:
 * the GROQ_API_KEY env var is XOR-ed with [PAD] and base64-encoded into
 * BuildConfig.GROQ_API_KEY_OBF).
 *
 * Honest note: this is OBFUSCATION, not encryption. It defeats casual
 * `strings` extraction of the APK, but a determined reverser who reads this
 * file can recover the key. The real fix for key secrecy is a backend proxy
 * so the key never ships in the APK at all.
 */
object KeyObfuscator {

    // MUST match the pad used in app/build.gradle.kts.
    private const val PAD = "ClickBrowserObfPad2026"

    /** Returns the de-obfuscated key, or "" if the field is empty/invalid. */
    fun decode(obfuscated: String): String {
        if (obfuscated.isBlank()) return ""
        return try {
            val bytes = Base64.decode(obfuscated, Base64.DEFAULT)
            val pad = PAD.toByteArray(Charsets.UTF_8)
            val plain = ByteArray(bytes.size) { i ->
                (bytes[i] xor pad[i % pad.size]).toByte()
            }
            String(plain, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
