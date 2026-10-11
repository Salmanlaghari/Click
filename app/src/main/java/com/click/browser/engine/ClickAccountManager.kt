package com.click.browser.engine

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Click account via Google Sign-In.
 *
 * The user signs in with THEIR OWN Google account on THEIR OWN device —
 * standard GoogleSignInClient flow, no credentials ever touch our servers
 * (there are none). The signed-in identity is cached in DataStore so the
 * UI can show it without re-querying Play Services.
 */
data class ClickAccount(
    val name: String?,
    val email: String?,
    val photoUrl: String?
)

object ClickAccountManager {

    private const val TAG = "ClickAccount"

    private fun client(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    /** Intent to launch with StartActivityForResult from the UI. */
    fun signInIntent(context: Context): Intent = client(context).signInIntent

    /**
     * Parses the sign-in result. Returns the account on success, null when the
     * user cancelled. Throws on real errors (caller shows the message).
     */
    suspend fun handleSignInResult(context: Context, data: Intent?): ClickAccount? {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val gsAccount: GoogleSignInAccount = try {
            task.getResult(ApiException::class.java)
        } catch (e: ApiException) {
            // 12501 = user cancelled the picker — not an error worth surfacing.
            if (e.statusCode == 12501) return null
            Log.w(TAG, "Sign-in failed: ${e.statusCode}", e)
            throw e
        }
        val account = ClickAccount(
            name = gsAccount.displayName,
            email = gsAccount.email,
            photoUrl = gsAccount.photoUrl?.toString()
        )
        saveAccount(context, account)
        return account
    }

    /** The account from the last successful sign-in (may be null). */
    suspend fun cachedAccount(context: Context): ClickAccount? {
        val json = context.dataStore.data.map { it[AppSettings.CLICK_ACCOUNT_JSON] }.first()
        if (json.isNullOrBlank()) return null
        return try {
            val o = JSONObject(json)
            ClickAccount(
                name = o.optString("name").ifBlank { null },
                email = o.optString("email").ifBlank { null },
                photoUrl = o.optString("photo").ifBlank { null }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Cached account JSON corrupt", e)
            null
        }
    }

    /** Also consults Play Services for a still-valid session. */
    fun playServicesAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    suspend fun signOut(context: Context) {
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine<Unit> { cont ->
                client(context).signOut()
                    .addOnCompleteListener { cont.resume(Unit) }
            }
        }
        context.dataStore.edit { it.remove(AppSettings.CLICK_ACCOUNT_JSON) }
    }

    private suspend fun saveAccount(context: Context, account: ClickAccount) {
        val json = JSONObject()
            .put("name", account.name ?: "")
            .put("email", account.email ?: "")
            .put("photo", account.photoUrl ?: "")
            .toString()
        context.dataStore.edit { it[AppSettings.CLICK_ACCOUNT_JSON] = json }
    }
}
