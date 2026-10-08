package com.click.browser.engine

package com.click.browser.engine

import com.click.browser.BuildConfig

/**
 * Runtime side of the build-time key obfuscation (see app/build.gradle.kts:
 * the GROQ_API_KEY env var is XOR-ed with the pad and hex-encoded into
 * BuildConfig.GROQ_API_KEY_OBF; the pad itself comes from
 * BuildConfig.GROQ_OBF_PAD — single source of truth, set at build time).
 *
 * Honest note: this is OBFUSCATION, not encryption. It defeats casual
 * `strings` extraction of the APK, but a determined reverser who reads this
 * file can recover the key. The real fix for key secrecy is a backend proxy
 * so the key never ships in the APK at all.
 */
object KeyObfuscator {

    /** Returns the de-obfuscated key, or "" if the field is empty/invalid. */
    fun decode(obfuscated: String): String {
        if (obfuscated.isBlank() || obfuscated.length % 2 != 0) return ""
        return try {
            val bytes = ByteArray(obfuscated.length / 2) { i ->
                obfuscated.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            val pad = BuildConfig.GROQ_OBF_PAD.toByteArray(Charsets.UTF_8)
            if (pad.isEmpty()) return ""
            val plain = ByteArray(bytes.size) { i ->
                (bytes[i].toInt() xor pad[i % pad.size].toInt()).toByte()
            }
            String(plain, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
