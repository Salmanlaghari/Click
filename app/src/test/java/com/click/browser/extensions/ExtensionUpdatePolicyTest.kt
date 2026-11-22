package com.click.browser.extensions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionUpdatePolicyTest {

    @Test
    fun detectsStrictlyNewerVersions() {
        assertTrue(ExtensionUpdatePolicy.isNewerVersion("1.2.1", "1.2.0"))
        assertTrue(ExtensionUpdatePolicy.isNewerVersion("1.2.10", "1.2.9"))
        assertTrue(ExtensionUpdatePolicy.isNewerVersion("2.0", "1.9.9"))
        assertTrue(ExtensionUpdatePolicy.isNewerVersion("1.3", "1.2.9"))
        assertTrue(ExtensionUpdatePolicy.isNewerVersion("1.0.1", "1.0"))
    }

    @Test
    fun equalOrOlderVersionsAreNotUpdates() {
        assertFalse(ExtensionUpdatePolicy.isNewerVersion("1.2.0", "1.2.0"))
        assertFalse(ExtensionUpdatePolicy.isNewerVersion("1.2", "1.2.0"))
        assertFalse(ExtensionUpdatePolicy.isNewerVersion("1.1.9", "1.2.0"))
        assertFalse(ExtensionUpdatePolicy.isNewerVersion("0.9", "1.0"))
    }

    @Test
    fun segmentSuffixesCompareByTheirLeadingDigits() {
        assertTrue(ExtensionUpdatePolicy.isNewerVersion("1.0.4beta1", "1.0.3"))
        assertFalse(ExtensionUpdatePolicy.isNewerVersion("1.0.3beta2", "1.0.3"))
    }

    @Test
    fun blankOrUnparsableVersionsNeverTriggerAnUpdate() {
        assertFalse(ExtensionUpdatePolicy.isNewerVersion("", "1.0"))
        assertFalse(ExtensionUpdatePolicy.isNewerVersion("2.0", ""))
        assertFalse(ExtensionUpdatePolicy.isNewerVersion("beta", "1.0"))
        assertFalse(ExtensionUpdatePolicy.isNewerVersion("2.0", "unknown"))
        assertFalse(ExtensionUpdatePolicy.isNewerVersion("1..2", "1.0"))
    }
}
