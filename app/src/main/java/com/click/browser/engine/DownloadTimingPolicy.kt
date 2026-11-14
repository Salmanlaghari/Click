package com.click.browser.engine

import kotlin.math.ceil

/**
 * Honest download timing (Prince, 14 Nov 2026): elapsed times and ETAs are
 * computed ONLY from real transfer data — bytes moved over a measured
 * interval, or a transfer's own start timestamp. When the total size is
 * unknown (streamed responses often report none) no ETA is produced at
 * all; callers show elapsed time instead of an invented estimate.
 */

/** Measured average speed in bytes/second; null when nothing measurable moved. */
internal fun downloadSpeedBps(bytesMoved: Long, elapsedMillis: Long): Double? =
    if (bytesMoved > 0L && elapsedMillis > 0L) {
        bytesMoved * 1000.0 / elapsedMillis
    } else {
        null
    }

/** Seconds remaining at [speedBps]; null when the total or the speed is unknown. */
internal fun downloadEtaSeconds(
    downloadedBytes: Long,
    totalBytes: Long,
    speedBps: Double?
): Long? {
    if (totalBytes <= 0L || speedBps == null || speedBps <= 0.0) return null
    val remainingBytes = totalBytes - downloadedBytes
    if (remainingBytes <= 0L) return 0L
    return ceil(remainingBytes / speedBps).toLong()
}

/** Whole seconds between a transfer's start and now; never negative. */
internal fun downloadElapsedSeconds(startedAtMillis: Long, nowMillis: Long): Long =
    ((nowMillis - startedAtMillis) / 1000L).coerceAtLeast(0L)

/** Compact duration for download UI: "45 sec", "2 min", "1 hr 5 min". */
internal fun formatDownloadDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0L -> if (minutes > 0L) "$hours hr $minutes min" else "$hours hr"
        minutes > 0L -> "$minutes min"
        else -> "$secs sec"
    }
}

/** 1024-based byte formatting, matching the Downloads screen's style. */
internal fun formatDownloadBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    // Locale.US pinned: the default locale can render "1,5 KB" or
    // non-ASCII digits on some devices (Kilo review, PR #218).
    return if (unit == 0) "$bytes B" else "%.1f %s".format(java.util.Locale.US, value, units[unit])
}
