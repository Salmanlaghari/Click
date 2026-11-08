package com.click.browser.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateBrowsingDisclosurePolicyTest {

    @Test
    fun engineCopyMatchesRegularAndPrivateTabDefaults() {
        val copy = PrivateBrowsingDisclosurePolicy.engineDefaultsDescription()

        assertTrue(copy.contains("Regular tabs use GeckoView by default"))
        assertTrue(copy.contains("private tabs use Android System WebView"))
        assertFalse(copy.contains("Regular tabs use Android System WebView by default"))
    }

    @Test
    fun privateTabCopyDescribesExcludedRecordsAndPersistentWebViewData() {
        val copy = PrivateBrowsingDisclosurePolicy.privateTabStorageDescription

        assertTrue(copy.contains("not recorded in local history"))
        assertTrue(copy.contains("excluded from session restore"))
        assertTrue(copy.contains("Recent tabs"))
        assertTrue(copy.contains(PrivateBrowsingDisclosurePolicy.profileWebViewStorageScopeDescription))
        assertTrue(copy.contains("not a temporary private-only store"))
        assertTrue(copy.contains("Closing a private tab does not clear those cookies or site data"))
        assertTrue(copy.contains("may be available to other WebView tabs in the same profile"))
        assertTrue(copy.contains("Marking an existing tab private does not remove history or site data recorded before it was marked"))
        assertTrue(copy.contains("Clear data on exit setting requests cleanup when Click closes"))
        assertTrue(copy.contains("not a guaranteed wipe"))
        assertTrue(copy.contains("private tabs are not anonymous"))
        assertFalse(copy.contains("private data is erased"))
    }

    @Test
    fun siteDataScopeCopyIdentifiesCurrentProfileWebViewStorage() {
        assertTrue(PrivateBrowsingDisclosurePolicy.profileWebViewStorageScopeDescription.contains("profile"))
        assertTrue(PrivateBrowsingDisclosurePolicy.profileWebViewStorageScopeDescription.contains("Android System WebView"))
        assertTrue(PrivateBrowsingDisclosurePolicy.cookieManagerScopeDescription.contains("WebView"))
        assertTrue(PrivateBrowsingDisclosurePolicy.cookieManagerScopeDescription.contains("current profile"))
    }
}
