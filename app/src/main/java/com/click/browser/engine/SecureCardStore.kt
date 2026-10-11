package com.click.browser.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Payment methods stored in EncryptedSharedPreferences (AES-256-GCM, key in
 * the Android Keystore). Cards NEVER leave the device and are never logged:
 * only the last 4 digits are ever shown in the UI.
 *
 * Honest scope: this is a convenience wallet for faster checkout form-fill.
 * It is NOT a payment processor — no money moves through Click.
 */
data class PaymentCard(
    val id: String = UUID.randomUUID().toString(),
    val label: String,       // "Personal", "Work" …
    val cardholder: String,
    val number: String,      // full PAN — only ever decrypted in memory
    val expiry: String,      // "MM/YY"
    val addedAt: Long = System.currentTimeMillis()
) {
    val last4: String get() = number.filter { it.isDigit() }.takeLast(4)
    val masked: String get() = "•••• •••• •••• ${last4.ifBlank { "••••" }}"
}

object SecureCardStore {

    private const val TAG = "SecureCardStore"
    private const val PREFS_FILE = "click_payment_methods"
    private const val CARDS_KEY = "cards_json_v1"

    /**
     * Cached EncryptedSharedPreferences. MasterKey.Builder.build() hits the
     * Android Keystore on every call — creating it per-access causes UI lag
     * and Keystore contention, so we build it once per process and reuse it.
     * The application context is used so the cached instance never leaks an
     * Activity.
     */
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: createPrefs(context.applicationContext).also { cachedPrefs = it }
        }
    }

    private fun createPrefs(appContext: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Returns the saved cards. Empty list = genuinely no cards saved.
     * @throws IllegalStateException when the encrypted store is unreadable
     * or the saved JSON is corrupt — callers must surface this distinctly
     * from "no cards yet" so data loss is never silent.
     */
    fun listCards(context: Context): List<PaymentCard> {
        val json = try {
            prefs(context).getString(CARDS_KEY, null)
        } catch (e: Exception) {
            Log.w(TAG, "Encrypted prefs unreadable", e)
            throw IllegalStateException("Saved cards couldn't be decrypted", e)
        } ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                PaymentCard(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    label = o.optString("label"),
                    cardholder = o.optString("cardholder"),
                    number = o.optString("number"),
                    expiry = o.optString("expiry"),
                    addedAt = o.optLong("addedAt", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Card JSON corrupt", e)
            throw IllegalStateException("Saved cards are corrupt and couldn't be read", e)
        }
    }

    /** Returns null when validation fails (message explains why). */
    fun validate(label: String, cardholder: String, number: String, expiry: String): String? {
        if (label.isBlank()) return "Give the card a label (e.g. Personal)"
        if (cardholder.isBlank()) return "Cardholder name is required"
        val digits = number.filter { it.isDigit() }
        if (digits.length !in 13..19) return "Card number looks wrong (13–19 digits)"
        if (!luhnOk(digits)) return "Card number failed the checksum — check the digits"
        val norm = normalizeExpiry(expiry)
        if (!Regex("""^(0[1-9]|1[0-2])/\d{2}$""").matches(norm)) {
            return "Expiry must be MM/YY"
        }
        return null
    }

    /**
     * Accepts flexible expiry input ("1/25", "0125", "12/2025", "MM/YY") and
     * normalizes to "MM/YY". Returns the trimmed input unchanged when it
     * can't be interpreted, so validate() still rejects it with a message.
     */
    fun normalizeExpiry(input: String): String {
        val trimmed = input.trim()
        if (Regex("""^(0[1-9]|1[0-2])/\d{2}$""").matches(trimmed)) return trimmed
        val digits = trimmed.filter { it.isDigit() }
        return when (digits.length) {
            // MMYY -> MM/YY
            4 -> "${digits.substring(0, 2)}/${digits.substring(2, 4)}"
            // MMYYYY -> MM/YY
            6 -> "${digits.substring(0, 2)}/${digits.substring(4, 6)}"
            // MYY -> 0M/YY
            3 -> "0${digits[0]}/${digits.substring(1, 3)}"
            else -> trimmed
        }
    }

    /**
     * All writes run on Dispatchers.IO: encrypted-prefs writes involve
     * Keystore crypto and must never block the UI thread.
     */
    suspend fun addCard(context: Context, card: PaymentCard) {
        withContext(Dispatchers.IO) {
            persist(context, listCards(context) + card)
        }
    }

    suspend fun updateCard(context: Context, card: PaymentCard) {
        withContext(Dispatchers.IO) {
            persist(context, listCards(context).map { if (it.id == card.id) card else it })
        }
    }

    suspend fun deleteCard(context: Context, id: String) {
        withContext(Dispatchers.IO) {
            persist(context, listCards(context).filterNot { it.id == id })
        }
    }

    private fun persist(context: Context, cards: List<PaymentCard>) {
        val arr = JSONArray()
        cards.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("label", c.label)
                    .put("cardholder", c.cardholder)
                    .put("number", c.number)
                    .put("expiry", c.expiry)
                    .put("addedAt", c.addedAt)
            )
        }
        try {
            // commit() (synchronous) not apply(): for payment data we must not
            // risk losing a write if the process dies before the async flush.
            // Already on Dispatchers.IO, so the blocking write is safe.
            val ok = prefs(context).edit().putString(CARDS_KEY, arr.toString()).commit()
            if (!ok) Log.w(TAG, "persist: commit() returned false")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist cards", e)
        }
    }

    /** Luhn checksum — catches typos, not fraud. */
    private fun luhnOk(digits: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }
}
