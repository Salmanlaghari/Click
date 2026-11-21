package com.click.browser.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult

/**
 * GeckoView login storage for Click's own password vault.
 *
 * The save-login UI and the encrypted write are owned by Click's per-tab
 * PromptDelegate, so [onLoginSave] deliberately does not write a second
 * copy — confirmed saves are already persisted before the prompt resolves.
 *
 * Fetch (autofill) is served from [PasswordManager]:
 * - [onLoginFetch] with a domain returns ONLY records whose stored host is
 *   exactly that domain (see [savedPasswordsForExactHost]). The earlier
 *   all-empty implementation existed so an origin-only record could never
 *   leak into a different port or site; exact-host matching plus keeping
 *   each record's stored origin on the returned entry (Gecko re-checks the
 *   origin before filling) preserves that guarantee while letting Gecko
 *   fill forms in regular GeckoView tabs.
 * - The no-domain [onLoginFetch] stays empty on purpose: without a domain
 *   there is nothing safe to match against.
 *
 * Threading: GeckoView invokes StorageDelegate methods on the UI thread, so
 * this delegate never blocks. It answers synchronously from
 * [PasswordManager]'s in-memory cache when the vault is already loaded, and
 * otherwise completes the returned [GeckoResult] asynchronously once the
 * IO read lands.
 */
internal class ClickAutocompleteStorageDelegate(context: Context) : Autocomplete.StorageDelegate {

    private val appContext = context.applicationContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onLoginFetch(): GeckoResult<Array<Autocomplete.LoginEntry>> =
        GeckoResult.fromValue(emptyArray())

    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>> {
        PasswordManager.cachedLoginsForHost(domain)?.let { cached ->
            return GeckoResult.fromValue(cached.toLoginEntries())
        }
        val result = GeckoResult<Array<Autocomplete.LoginEntry>>()
        ioScope.launch {
            val entries = try {
                if (PasswordManager.isEnabled(appContext)) {
                    PasswordManager.getAllForHost(appContext, domain)
                } else {
                    emptyList()
                }
            } catch (error: Exception) {
                // The requested domain is deliberately not logged: it names
                // a site the user has a saved login for.
                Log.w(TAG, "Login fetch failed", error)
                emptyList()
            }
            result.complete(entries.toLoginEntries())
        }
        return result
    }

    override fun onLoginSave(login: Autocomplete.LoginEntry) = Unit

    private fun List<SavedPassword>.toLoginEntries(): Array<Autocomplete.LoginEntry> =
        map { saved ->
            Autocomplete.LoginEntry.Builder()
                .origin(saved.origin)
                .username(saved.username)
                .password(saved.password)
                .build()
        }.toTypedArray()

    private companion object {
        const val TAG = "ClickAutocomplete"
    }
}
