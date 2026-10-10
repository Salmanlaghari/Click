package com.click.browser.engine

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted password manager: offers "Save password?" when the user logs
 * into a site, stores credentials encrypted with an Android Keystore-backed
 * AES-GCM key, and auto-fills on return visits.
 *
 * Security: the AES key never leaves the Android Keystore (hardware-backed
 * where available). Only the encrypted blobs are stored in DataStore.
 */
data class SavedPassword(
    val host: String,
    val username: String,
    val password: String, // decrypted only in memory
    val savedAt: Long = System.currentTimeMillis()
)

object PasswordManager {

    private const val KEYSTORE_ALIAS = "ClickBrowserPasswords"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val PASSWORDS_KEY = stringPreferencesKey("saved_passwords_v1")
    private val ENABLED_KEY = booleanPreferencesKey("password_manager_enabled")

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // Store IV (12 bytes) + ciphertext, Base64-encoded.
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE, getOrCreateKey(),
            GCMParameterSpec(128, iv)
        )
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    suspend fun isEnabled(context: Context): Boolean {
        return context.profileDataStore.data.map { it[ENABLED_KEY] ?: true }.first()
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.profileDataStore.edit { it[ENABLED_KEY] = enabled }
    }

    suspend fun getAll(context: Context): List<SavedPassword> {
        return try {
            val json = context.profileDataStore.data.map { it[PASSWORDS_KEY].orEmpty() }.first()
            if (json.isBlank()) return emptyList()
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    SavedPassword(
                        host = o.getString("host"),
                        username = decrypt(o.getString("u")),
                        password = decrypt(o.getString("p")),
                        savedAt = o.optLong("t", 0L)
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getForHost(context: Context, host: String): SavedPassword? {
        return getAll(context).firstOrNull { it.host.equals(host, ignoreCase = true) }
    }

    suspend fun save(context: Context, host: String, username: String, password: String) {
        val existing = getAll(context).toMutableList()
        existing.removeAll { it.host.equals(host, ignoreCase = true) && it.username == username }
        existing.add(0, SavedPassword(host, username, password))
        // Cap at 200 entries.
        val trimmed = existing.take(200)
        val arr = JSONArray()
        for (s in trimmed) {
            arr.put(JSONObject().apply {
                put("host", s.host)
                put("u", encrypt(s.username))
                put("p", encrypt(s.password))
                put("t", s.savedAt)
            })
        }
        context.profileDataStore.edit { it[PASSWORDS_KEY] = arr.toString() }
    }

    suspend fun delete(context: Context, host: String, username: String) {
        val remaining = getAll(context).filterNot {
            it.host.equals(host, ignoreCase = true) && it.username == username
        }
        val arr = JSONArray()
        for (s in remaining) {
            arr.put(JSONObject().apply {
                put("host", s.host)
                put("u", encrypt(s.username))
                put("p", encrypt(s.password))
                put("t", s.savedAt)
            })
        }
        context.profileDataStore.edit { it[PASSWORDS_KEY] = arr.toString() }
    }

    suspend fun clearAll(context: Context) {
        context.profileDataStore.edit { it.remove(PASSWORDS_KEY) }
    }
}
