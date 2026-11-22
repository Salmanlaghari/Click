package com.click.browser.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateBrowsingDisclosurePolicyTest {

    @Test
    fun engineCopyMatchesRegularAndPrivateTabDefaults() {
        val copy = PrivateBrowsingDisclosurePolicy.engineDefaultsDescription()

        assertTrue(copy.contains("Regular and private tabs use GeckoView by default"))
        assertTrue(copy.contains("private mode"))
        assertFalse(copy.contains("Android System WebView"))
    }

    @Test
    fun privateTabCopyDescribesExcludedRecordsAndDiscardedPrivateStorage() {
        val copy = PrivateBrowsingDisclosurePolicy.privateTabStorageDescription

        assertTrue(copy.contains("not recorded in local history"))
        assertTrue(copy.contains("excluded from session restore"))
        assertTrue(copy.contains("Recent tabs"))
        assertTrue(copy.contains("GeckoView private mode"))
        assertTrue(copy.contains("separate from regular tabs"))
        assertTrue(copy.contains("discarded when the private session ends"))
        assertTrue(copy.contains("Marking an existing tab private does not remove history or site data recorded before it was marked"))
        assertTrue(copy.contains("private tabs are not anonymous"))
        // The old WebView truth must not survive: ending the private session
        // DOES discard its storage now.
        assertFalse(copy.contains("does not clear those cookies"))
        assertFalse(copy.contains("not a temporary private-only store"))
    }

    @Test
    fun siteDataScopeCopyIdentifiesCurrentProfileWebViewStorage() {
        assertTrue(PrivateBrowsingDisclosurePolicy.profileWebViewStorageScopeDescription.contains("profile"))
        assertTrue(PrivateBrowsingDisclosurePolicy.profileWebViewStorageScopeDescription.contains("Android System WebView"))
        assertTrue(PrivateBrowsingDisclosurePolicy.cookieManagerScopeDescription.contains("WebView"))
        assertTrue(PrivateBrowsingDisclosurePolicy.cookieManagerScopeDescription.contains("current profile"))
    }
}
