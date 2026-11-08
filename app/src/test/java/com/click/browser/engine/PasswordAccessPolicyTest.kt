package com.click.browser.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordAccessPolicyTest {
    @Test
    fun vaultRequiresAvailableDeviceAuthenticationAndSuccessfulUnlock() {
        assertTrue(PasswordAccessPolicy.canOpenVault(deviceAuthenticationAvailable = true, deviceAuthenticated = true))
        assertFalse(PasswordAccessPolicy.canOpenVault(deviceAuthenticationAvailable = false, deviceAuthenticated = true))
        assertFalse(PasswordAccessPolicy.canOpenVault(deviceAuthenticationAvailable = true, deviceAuthenticated = false))
    }

    @Test
    fun autofillPromptRequiresEnabledManagerSavedLoginSecureActiveNormalTabAndDeviceLock() {
        assertTrue(PasswordAccessPolicy.shouldRequestAutofillAuthentication(validContext()))
        assertFalse(PasswordAccessPolicy.shouldRequestAutofillAuthentication(validContext().copy(passwordManagerEnabled = false)))
        assertFalse(PasswordAccessPolicy.shouldRequestAutofillAuthentication(validContext().copy(hasSavedCredential = false)))
        assertFalse(PasswordAccessPolicy.shouldRequestAutofillAuthentication(validContext().copy(isPrivateTab = true)))
        assertFalse(PasswordAccessPolicy.shouldRequestAutofillAuthentication(validContext().copy(isActiveTab = false)))
        assertFalse(PasswordAccessPolicy.shouldRequestAutofillAuthentication(validContext().copy(isSecurePage = false)))
        assertFalse(PasswordAccessPolicy.shouldRequestAutofillAuthentication(validContext().copy(isSameOrigin = false)))
        assertFalse(PasswordAccessPolicy.shouldRequestAutofillAuthentication(validContext().copy(deviceAuthenticationAvailable = false)))
    }

    @Test
    fun pageNeverReceivesSavedCredentialsUntilAuthenticationSucceeds() {
        val unauthenticated = validContext().copy(deviceAuthenticated = false)
        assertTrue(PasswordAccessPolicy.shouldRequestAutofillAuthentication(unauthenticated))
        assertFalse(PasswordAccessPolicy.mayAutofill(unauthenticated))
        assertTrue(PasswordAccessPolicy.mayAutofill(unauthenticated.copy(deviceAuthenticated = true)))
        assertFalse(PasswordAccessPolicy.mayAutofill(unauthenticated.copy(deviceAuthenticated = true, isSameOrigin = false)))
    }

    private fun validContext() = PasswordAccessPolicy.AutofillContext(
        passwordManagerEnabled = true,
        hasSavedCredential = true,
        isPrivateTab = false,
        isActiveTab = true,
        isSecurePage = true,
        isSameOrigin = true,
        deviceAuthenticationAvailable = true,
        deviceAuthenticated = false
    )
}
