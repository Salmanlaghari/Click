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
 * Security: the AES key never leaves the Android Keystore and is hardware-backed
 * only where the device supports it. Credential values are AES-GCM encrypted;
 * site origin/host metadata remains local in DataStore for matching. UI display
 * and autofill must pass the device-authentication policy first.
 */
data class SavedPassword(
    val host: String,
    val username: String,
    val password: String, // decrypted only in memory
    val savedAt: Long = System.currentTimeMillis(),
    val origin: String = "https://$host"
)

object PasswordManager {

    private const val KEYSTORE_ALIAS = "ClickBrowserPasswords"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val PASSWORDS_KEY = stringPreferencesKey("saved_passwords_v1")
    private val ENABLED_KEY = booleanPreferencesKey("password_manager_enabled")

    // In-memory copy of the decrypted vault and of the enabled flag, kept
    // fresh by every read/write below. The GeckoView autocomplete storage
    // delegate is invoked on the UI thread, so it answers fetches from this
    // cache instead of blocking on a disk read (see
    // ClickAutocompleteStorageDelegate).
    @Volatile
    private var cache: List<SavedPassword>? = null

    @Volatile
    private var cachedEnabled: Boolean? = null

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
        val enabled = context.profileDataStore.data.map { it[ENABLED_KEY] ?: true }.first()
        cachedEnabled = enabled
        return enabled
    }

    suspend fun setEnabled(context: Context, enabled: Boolean): Boolean {
        return try {
            context.profileDataStore.edit { it[ENABLED_KEY] = enabled }
            cachedEnabled = enabled
            true
        } catch (e: Exception) {
            android.util.Log.w("PasswordManager", "setEnabled failed", e)
            false
        }
    }

    suspend fun getAll(context: Context): List<SavedPassword> {
        val loaded = loadAll(context)
        if (loaded != null) cache = loaded
        return loaded ?: cache ?: emptyList()
    }

    /** All saved logins whose host is exactly [host] (case-insensitive). */
    suspend fun getAllForHost(context: Context, host: String): List<SavedPassword> =
        savedPasswordsForExactHost(getAll(context), host)

    /**
     * Cache-only variant of [getAllForHost] for callers that must not block
     * (the GeckoView storage delegate runs on the UI thread): null while
     * the vault has not been loaded this process, otherwise the exact-host
     * matches — empty when the password manager is known to be disabled.
     */
    fun cachedLoginsForHost(host: String): List<SavedPassword>? {
        val all = cache ?: return null
        if (cachedEnabled == false) return emptyList()
        return savedPasswordsForExactHost(all, host)
    }

    /** Loads the vault and the enabled flag into the in-memory cache. */
    suspend fun warmCache(context: Context) {
        isEnabled(context)
        getAll(context)
    }

    /** Disk read behind [getAll]; null means the read itself failed. */
    private suspend fun loadAll(context: Context): List<SavedPassword>? {
        return try {
            val json = context.profileDataStore.data.map { it[PASSWORDS_KEY].orEmpty() }.first()
            if (json.isBlank()) return emptyList()
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    val legacyHost = o.getString("host")
                    val origin = PasswordOriginPolicy.secureOrigin(
                        o.optString("origin").ifBlank { "https://$legacyHost" },
                        isMainFrame = true
                    ) ?: return@mapNotNull null
                    val host = PasswordOriginPolicy.hostFromSecureOrigin(origin) ?: return@mapNotNull null
                    SavedPassword(
                        host = host,
                        username = decrypt(o.getString("u")),
                        password = decrypt(o.getString("p")),
                        savedAt = o.optLong("t", 0L),
                        origin = origin
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { null }
    }

    suspend fun getForHost(context: Context, host: String): SavedPassword? {
        return getAll(context).firstOrNull { it.host.equals(host, ignoreCase = true) }
    }

    suspend fun getForOrigin(context: Context, origin: String): SavedPassword? {
        val normalized = PasswordOriginPolicy.secureOrigin(origin, isMainFrame = true) ?: return null
        return getAll(context).firstOrNull { it.origin == normalized }
    }

    /** Checks stored origin metadata without decrypting usernames or passwords. */
    suspend fun hasForOrigin(context: Context, origin: String): Boolean {
        val normalized = PasswordOriginPolicy.secureOrigin(origin, isMainFrame = true) ?: return false
        return try {
            val json = context.profileDataStore.data.map { it[PASSWORDS_KEY].orEmpty() }.first()
            if (json.isBlank()) return false
            val entries = JSONArray(json)
            (0 until entries.length()).any { index ->
                val entry = entries.optJSONObject(index) ?: return@any false
                val legacyHost = entry.optString("host")
                val storedOrigin = entry.optString("origin").ifBlank { "https://$legacyHost" }
                PasswordOriginPolicy.secureOrigin(storedOrigin, isMainFrame = true) == normalized
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun save(context: Context, host: String, username: String, password: String) {
        saveForOrigin(context, "https://$host", username, password)
    }

    suspend fun saveForOrigin(context: Context, origin: String, username: String, password: String) {
        val normalizedOrigin = PasswordOriginPolicy.secureOrigin(origin, isMainFrame = true)
            ?: throw IllegalArgumentException("Only a valid HTTPS origin can store a login")
        val host = PasswordOriginPolicy.hostFromSecureOrigin(normalizedOrigin)
            ?: throw IllegalArgumentException("The HTTPS origin has no valid host")
        val existing = getAll(context).toMutableList()
        existing.removeAll { it.origin == normalizedOrigin && it.username == username }
        existing.add(0, SavedPassword(host, username, password, origin = normalizedOrigin))
        // Cap at 200 entries.
        val trimmed = existing.take(200)
        val arr = JSONArray()
        for (s in trimmed) {
            arr.put(JSONObject().apply {
                put("host", s.host)
                put("origin", s.origin)
                put("u", encrypt(s.username))
                put("p", encrypt(s.password))
                put("t", s.savedAt)
            })
        }
        context.profileDataStore.edit { it[PASSWORDS_KEY] = arr.toString() }
        cache = trimmed
    }

    suspend fun delete(context: Context, host: String, username: String) {
        val remaining = getAll(context).filterNot {
            it.host.equals(host, ignoreCase = true) && it.username == username
        }
        writeAll(context, remaining)
    }

    suspend fun deleteForOrigin(context: Context, origin: String, username: String) {
        val normalized = PasswordOriginPolicy.secureOrigin(origin, isMainFrame = true) ?: return
        val remaining = getAll(context).filterNot {
            it.origin == normalized && it.username == username
        }
        writeAll(context, remaining)
    }

    private suspend fun writeAll(context: Context, entries: List<SavedPassword>) {
        val arr = JSONArray()
        for (s in entries) {
            arr.put(JSONObject().apply {
                put("host", s.host)
                put("origin", s.origin)
                put("u", encrypt(s.username))
                put("p", encrypt(s.password))
                put("t", s.savedAt)
            })
        }
        context.profileDataStore.edit { it[PASSWORDS_KEY] = arr.toString() }
        cache = entries
    }

    suspend fun clearAll(context: Context) {
        context.profileDataStore.edit { it.remove(PASSWORDS_KEY) }
        cache = emptyList()
    }
}

/**
 * Exact-host filter for GeckoView autofill: a record matches only when its
 * stored host is exactly [host] (case-insensitive). Subdomains, parent
 * domains and look-alike hosts never match, so a login saved for one site
 * cannot leak into another. Matched records keep their stored origin, so
 * Gecko's own origin check still separates non-default ports.
 */
internal fun savedPasswordsForExactHost(
    logins: List<SavedPassword>,
    host: String
): List<SavedPassword> {
    val wanted = host.trim().lowercase()
    if (wanted.isEmpty()) return emptyList()
    return logins.filter { it.host.lowercase() == wanted }
}
