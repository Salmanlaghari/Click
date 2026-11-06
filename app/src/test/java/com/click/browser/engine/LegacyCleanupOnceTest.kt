package com.click.browser.engine

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyCleanupOnceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun successfulCleanupRunsOnlyOnce() = runBlocking {
        val marker = File(temporaryFolder.root, "cleanup.complete")
        var calls = 0

        repeat(2) {
            runCleanupOnce(marker) {
                calls += 1
                true
            }
        }

        assertEquals(1, calls)
        assertTrue(marker.isFile)
    }

    @Test
    fun incompleteCleanupIsRetriedBeforeMarkerIsWritten() = runBlocking {
        val marker = File(temporaryFolder.root, "cleanup.complete")
        var calls = 0

        runCleanupOnce(marker) {
            calls += 1
            false
        }
        assertFalse(marker.exists())

        repeat(2) {
            runCleanupOnce(marker) {
                calls += 1
                true
            }
        }

        assertEquals(2, calls)
        assertTrue(marker.isFile)
    }

    @Test
    fun thrownCleanupFailureDoesNotWriteCompletionMarker() = runBlocking {
        val marker = File(temporaryFolder.root, "cleanup.complete")
        val failure = runCatching {
            runCleanupOnce(marker) { throw IllegalStateException("cleanup failed") }
        }.exceptionOrNull()

        assertNotNull(failure)
        assertFalse(marker.exists())
    }

    @Test
    fun markerWriteFailureDoesNotSuppressCleanupRetry() = runBlocking {
        val parentFile = temporaryFolder.newFile("not-a-directory")
        val marker = File(parentFile, "cleanup.complete")
        var calls = 0

        repeat(2) {
            runCleanupOnce(marker) {
                calls += 1
                true
            }
        }

        assertEquals(2, calls)
        assertFalse(marker.exists())
    }
}
