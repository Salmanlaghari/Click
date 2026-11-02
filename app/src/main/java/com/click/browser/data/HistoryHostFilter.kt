package com.click.browser.data

import java.net.IDN
import java.net.URI
import java.util.Locale

/** Exact, fail-closed host matching for site-scoped history deletion. */
internal object HistoryHostFilter {
    /** Normalize a bare DNS host or bracketed/unbracketed IPv6 literal. */
    fun normalizeHost(host: String?): String? {
        val value = host?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.contains("://")) return null

        if (value.startsWith("[") || value.endsWith("]")) {
            if (!value.startsWith("[") || !value.endsWith("]")) return null
            return normalizeIpv6Literal(value.substring(1, value.length - 1))
        }
        if (value.contains(':')) return normalizeIpv6Literal(value)
        if (value.any { it == '/' || it == '?' || it == '#' || it == '@' || it == '\\' }) return null

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

    private fun normalizeIpv6Literal(literal: String): String? {
        if (literal.isEmpty() || !literal.contains(':') ||
            !literal.matches(Regex("[0-9A-Fa-f:.]+"))
        ) return null
        val groups = parseIpv6Groups(literal) ?: return null
        return "[${formatIpv6(groups)}]"
    }

    /** Parse numeric IPv6 text directly; this path never resolves a hostname. */
    private fun parseIpv6Groups(literal: String): IntArray? {
        if (literal.contains(":::")) return null
        val compressionAt = literal.indexOf("::")
        val compressed = compressionAt >= 0
        if (compressed && literal.indexOf("::", compressionAt + 2) >= 0) return null
        if (!compressed && (literal.startsWith(":") || literal.endsWith(":"))) return null

        val leftText = if (compressed) literal.substring(0, compressionAt) else literal
        val rightText = if (compressed) literal.substring(compressionAt + 2) else ""
        val left = parseIpv6Part(leftText, allowIpv4Tail = !compressed) ?: return null
        val right = if (compressed) {
            parseIpv6Part(rightText, allowIpv4Tail = true) ?: return null
        } else {
            emptyList()
        }
        val writtenGroups = left.size + right.size

        if (!compressed) {
            if (writtenGroups != 8) return null
            return (left + right).toIntArray()
        }

        val zeroGroups = 8 - writtenGroups
        if (zeroGroups < 1) return null
        return IntArray(8).apply {
            left.forEachIndexed { index, group -> this[index] = group }
            right.forEachIndexed { index, group -> this[8 - right.size + index] = group }
        }
    }

    private fun parseIpv6Part(part: String, allowIpv4Tail: Boolean): List<Int>? {
        if (part.isEmpty()) return emptyList()
        val tokens = part.split(':')
        val groups = mutableListOf<Int>()
        for ((index, token) in tokens.withIndex()) {
            if (token.isEmpty()) return null
            if (token.contains('.')) {
                if (!allowIpv4Tail || index != tokens.lastIndex) return null
                val octets = parseIpv4Tail(token) ?: return null
                groups += (octets[0] shl 8) or octets[1]
                groups += (octets[2] shl 8) or octets[3]
            } else {
                if (token.length !in 1..4 || token.any {
                        it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F'
                    }
                ) return null
                groups += token.toInt(16)
            }
        }
        return groups
    }

    private fun parseIpv4Tail(value: String): IntArray? {
        val parts = value.split('.')
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for (index in parts.indices) {
            val part = parts[index]
            if (part.isEmpty() || part.length > 3 ||
                (part.length > 1 && part.startsWith('0')) || part.any { it !in '0'..'9' }
            ) return null
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            octets[index] = octet
        }
        return octets
    }

    /** RFC-style lowercase IPv6 formatting with the longest zero run compressed. */
    private fun formatIpv6(groups: IntArray): String {
        var bestStart = -1
        var bestLength = 0
        var index = 0
        while (index < groups.size) {
            if (groups[index] != 0) {
                index++
                continue
            }
            var end = index
            while (end < groups.size && groups[end] == 0) end++
            val runLength = end - index
            if (runLength >= 2 && runLength > bestLength) {
                bestStart = index
                bestLength = runLength
            }
            index = end
        }

        if (bestStart < 0) return groups.joinToString(":") { it.toString(16) }
        val left = (0 until bestStart).joinToString(":") { groups[it].toString(16) }
        val right = (bestStart + bestLength until groups.size)
            .joinToString(":") { groups[it].toString(16) }
        return when {
            left.isEmpty() && right.isEmpty() -> "::"
            left.isEmpty() -> "::$right"
            right.isEmpty() -> "$left::"
            else -> "$left::$right"
        }
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
