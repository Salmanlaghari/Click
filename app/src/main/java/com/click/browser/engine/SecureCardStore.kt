package com.click.browser.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun listCards(context: Context): List<PaymentCard> {
        val json = try {
            prefs(context).getString(CARDS_KEY, null)
        } catch (e: Exception) {
            Log.w(TAG, "Encrypted prefs unreadable", e)
            return emptyList()
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
            emptyList()
        }
    }

    /** Returns null when validation fails (message explains why). */
    fun validate(label: String, cardholder: String, number: String, expiry: String): String? {
        if (label.isBlank()) return "Give the card a label (e.g. Personal)"
        if (cardholder.isBlank()) return "Cardholder name is required"
        val digits = number.filter { it.isDigit() }
        if (digits.length !in 13..19) return "Card number looks wrong (13–19 digits)"
        if (!luhnOk(digits)) return "Card number failed the checksum — check the digits"
        if (!Regex("""^(0[1-9]|1[0-2])/\d{2}$""").matches(expiry.trim())) {
            return "Expiry must be MM/YY"
        }
        return null
    }

    fun addCard(context: Context, card: PaymentCard) {
        persist(context, listCards(context) + card)
    }

    fun updateCard(context: Context, card: PaymentCard) {
        persist(context, listCards(context).map { if (it.id == card.id) card else it })
    }

    fun deleteCard(context: Context, id: String) {
        persist(context, listCards(context).filterNot { it.id == id })
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
            prefs(context).edit().putString(CARDS_KEY, arr.toString()).apply()
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
