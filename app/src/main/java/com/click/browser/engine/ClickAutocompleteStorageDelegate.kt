package com.click.browser.engine

import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult

/**
 * The save-login UI and encrypted write are owned by Click's per-tab PromptDelegate.
 * Returning empty fetch results prevents GeckoView from autofilling an origin-only
 * record into a different port or site while the browser's supported fetch key is
 * domain-based. Confirmed saves are already persisted before the prompt is accepted,
 * so this callback deliberately does not write a second copy.
 */
internal object ClickAutocompleteStorageDelegate : Autocomplete.StorageDelegate {
    override fun onLoginFetch(): GeckoResult<Array<Autocomplete.LoginEntry>> =
        GeckoResult.fromValue(emptyArray())

    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>> =
        GeckoResult.fromValue(emptyArray())

    override fun onLoginSave(login: Autocomplete.LoginEntry) = Unit
}
