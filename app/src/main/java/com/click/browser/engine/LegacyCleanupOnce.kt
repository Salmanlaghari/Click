package com.click.browser.engine

import java.io.File

/** Writes a completion marker only after cleanup succeeds; failures are retried on the next launch. */
internal suspend fun runCleanupOnce(
    markerFile: File,
    cleanup: suspend () -> Boolean
) {
    if (markerFile.isFile) return
    if (!cleanup()) return

    runCatching {
        markerFile.parentFile?.mkdirs()
        markerFile.createNewFile()
    }
}
