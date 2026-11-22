package com.click.browser.extensions

/** Version comparison behind the manager's user-initiated add-on update checks. */
internal object ExtensionUpdatePolicy {

    /**
     * True only when [candidate] is strictly newer than [installed]. Versions are
     * dot-separated segments; only the leading digits of each segment count, so a
     * suffix such as "3beta1" compares as 3. Blank or unparsable versions are
     * never treated as newer, so a malformed AMO record cannot trigger an update
     * or a downgrade.
     */
    fun isNewerVersion(candidate: String, installed: String): Boolean {
        val candidateParts = versionParts(candidate) ?: return false
        val installedParts = versionParts(installed) ?: return false
        val size = maxOf(candidateParts.size, installedParts.size)
        for (index in 0 until size) {
            val newer = candidateParts.getOrElse(index) { 0 }
            val current = installedParts.getOrElse(index) { 0 }
            if (newer != current) return newer > current
        }
        return false
    }

    private fun versionParts(version: String): List<Int>? {
        val segments = version.trim().split('.')
        if (segments.isEmpty() || segments.size > 6) return null
        return segments.map { segment ->
            segment.takeWhile(Char::isDigit).toIntOrNull() ?: return null
        }
    }
}
