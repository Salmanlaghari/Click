package com.click.browser.engine

import android.app.Activity
import android.util.Log
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException

/**
 * Google Password Manager integration via the AndroidX Credential Manager.
 *
 * Passwords saved here live in the USER'S Google Password Manager (synced
 * with their Google account) — Click never sees a server copy. This is the
 * companion to Click's own on-device encrypted password store
 * ([PasswordManager]): Credential Manager = cloud-synced convenience,
 * PasswordManager = on-device only.
 *
 * Every call is wrapped: Play Services may be missing/outdated, and the
 * user can cancel — both are normal, surfaced as Result.failure with a
 * human-readable message.
 */
object CredentialVault {

    private const val TAG = "CredentialVault"

    data class VaultCredential(val username: String, val password: String)

    sealed interface VaultResult<out T> {
        data class Ok<T>(val value: T) : VaultResult<T>
        /** User cancelled — not an error. */
        data object Cancelled : VaultResult<Nothing>
        data class Error(val message: String) : VaultResult<Nothing>
    }

    /**
     * Saves a username/password to the user's Google Password Manager.
     * Must be called from an Activity context (system UI may appear).
     */
    suspend fun savePassword(
        activity: Activity,
        username: String,
        password: String
    ): VaultResult<Unit> {
        if (username.isBlank() || password.isBlank()) {
            return VaultResult.Error("Username and password can't be empty")
        }
        return try {
            val manager = CredentialManager.create(activity)
            manager.createCredential(activity, CreatePasswordRequest(username, password))
            VaultResult.Ok(Unit)
        } catch (e: CreateCredentialCancellationException) {
            VaultResult.Cancelled
        } catch (e: CreateCredentialException) {
            Log.w(TAG, "savePassword failed", e)
            VaultResult.Error(humanize(e))
        } catch (e: Exception) {
            Log.w(TAG, "savePassword failed", e)
            VaultResult.Error("Couldn't reach Google Password Manager (${e.javaClass.simpleName})")
        }
    }

    /**
     * Retrieves a saved password. Shows the system credential picker when
     * several match. Returns null-result as Cancelled when the user backs out.
     */
    suspend fun getPassword(activity: Activity): VaultResult<VaultCredential> {
        return try {
            val manager = CredentialManager.create(activity)
            val request = GetCredentialRequest(listOf(GetPasswordOption()))
            val response = manager.getCredential(activity, request)
            val credential = response.credential
            if (credential is PasswordCredential) {
                VaultResult.Ok(VaultCredential(credential.id, credential.password))
            } else {
                VaultResult.Error("No password credential returned")
            }
        } catch (e: GetCredentialCancellationException) {
            VaultResult.Cancelled
        } catch (e: NoCredentialException) {
            VaultResult.Error("No saved passwords found in Google Password Manager")
        } catch (e: GetCredentialException) {
            Log.w(TAG, "getPassword failed", e)
            VaultResult.Error(humanize(e))
        } catch (e: Exception) {
            Log.w(TAG, "getPassword failed", e)
            VaultResult.Error("Couldn't reach Google Password Manager (${e.javaClass.simpleName})")
        }
    }

    private fun humanize(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            "play services" in msg.lowercase() -> "Google Play Services needs an update"
            msg.isNotBlank() -> msg.take(120)
            else -> "Google Password Manager unavailable"
        }
    }
}
