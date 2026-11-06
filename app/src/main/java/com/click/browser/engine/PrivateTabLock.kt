package com.click.browser.engine

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Private/incognito tab lock: gates private tabs behind the device
 * biometric (fingerprint/face) with the device PIN/pattern as fallback.
 *
 * Rules:
 * - Uses AndroidX BiometricPrompt only — no custom crypto, no stored
 *   secrets. Authentication is a pure gate; nothing is encrypted with it.
 * - [canLock] is true when EITHER a strong biometric OR the device
 *   credential (PIN/pattern/password) is enrolled, so devices without a
 *   fingerprint sensor still get a working lock via their lock screen.
 * - [hasStrongBiometric] distinguishes the two for honest UI copy
 *   ("fingerprint" vs "device lock").
 */
object PrivateTabLock {

    /** Authenticators accepted by the lock prompt. */
    val AUTHENTICATORS: Int = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    /**
     * True when the device can actually perform the lock prompt
     * (strong biometric enrolled, or device PIN/pattern/password set).
     */
    fun canLock(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** True when a real biometric (fingerprint/face) is enrolled. */
    fun hasStrongBiometric(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** Human-readable reason when [canLock] is false, for the Settings UI. */
    fun unavailableReason(context: Context): String {
        return when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "This device has no biometric hardware and no lock screen set"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Biometric hardware is temporarily unavailable"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "No fingerprint/face or PIN enrolled — add one in Android Settings"
            else -> "Biometric authentication is not available on this device"
        }
    }
}
