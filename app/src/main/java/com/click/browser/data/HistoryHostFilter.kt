package com.click.browser.data

import java.net.IDN
import java.net.URI
import java.util.Locale

/** Exact, fail-closed host matching for site-scoped history deletion. */
internal object HistoryHostFilter {
    /** Normalize a bare host (as supplied by the current site-details sheet). */
    fun normalizeHost(host: String?): String? {
        val value = host?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        if (value.startsWith("[") && value.endsWith("]")) {
            val literal = value.substring(1, value.length - 1)
            if (!literal.contains(':') || !literal.matches(Regex("[0-9A-Fa-f:.]+"))) return null
            return "[${literal.lowercase(Locale.ROOT)}]"
        }
        if (value.contains("://") || value.any {
                it == '/' || it == '?' || it == '#' || it == '@' || it == ':' || it == '\\'
            }
        ) return null

        val withoutRootDot = value.removeSuffix(".")
        if (withoutRootDot.isEmpty()) return null
        return runCatching {
            val ascii = IDN.toASCII(withoutRootDot, IDN.USE_STD3_ASCII_RULES)
                .lowercase(Locale.ROOT)
            if (ascii.length > 253 || ascii.split('.').any { it.isEmpty() }) null else ascii
        }.getOrNull()
    }

    /** Return the normalized hostname of an absolute URL, or null if it is not safely parseable. */
    fun hostForUrl(url: String?): String? {
        val value = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.isAbsolute) return null
        val host = uri.host ?: hostFromRawAuthority(uri.rawAuthority) ?: return null
        return normalizeHost(host)
    }

    /** Keep every entry except those whose URL has the exact normalized target host. */
    fun <T> retainOtherHosts(
        entries: List<T>,
        normalizedHost: String,
        urlOf: (T) -> String?
    ): List<T> {
        val target = normalizeHost(normalizedHost) ?: return entries
        return entries.filterNot { entry -> hostForUrl(urlOf(entry)) == target }
    }

    private fun hostFromRawAuthority(authority: String?): String? {
        val value = authority?.takeIf { it.isNotBlank() } ?: return null
        val hostAndPort = value.substringAfterLast('@')

        if (hostAndPort.startsWith("[")) {
            val closingBracket = hostAndPort.indexOf(']')
            if (closingBracket < 0) return null
            val suffix = hostAndPort.substring(closingBracket + 1)
            if (suffix.isNotEmpty()) {
                val port = suffix.removePrefix(":")
                if (!suffix.startsWith(":") || port.isEmpty() ||
                    !port.all(Char::isDigit) || port.toIntOrNull() !in 0..65535
                ) return null
            }
            return hostAndPort.substring(0, closingBracket + 1)
        }

        return when (hostAndPort.count { it == ':' }) {
            0 -> hostAndPort
            1 -> {
                val port = hostAndPort.substringAfterLast(':')
                if (port.isEmpty() || !port.all(Char::isDigit) || port.toIntOrNull() !in 0..65535) {
                    null
                } else {
                    hostAndPort.substringBeforeLast(':')
                }
            }
            else -> null
        }
    }
}
