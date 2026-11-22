package com.click.browser.engine

import java.net.URI

/** When a userscript wants to run, reduced to the two timings Click's
 *  runner can actually honour. `document-idle` and an absent `@run-at`
 *  both map to [DOCUMENT_END]: the runner fires them at DOMContentLoaded,
 *  which is the closest honest equivalent GeckoView content scripts get. */
internal enum class UserscriptRunAt { DOCUMENT_START, DOCUMENT_END }

/**
 * Parsed `==UserScript==` metadata block of one `.user.js` file.
 *
 * Only the directives Click's runner actually honours are modelled:
 * `@match`, `@include`, `@exclude`, `@run-at`, `@grant`, `@noframes`.
 * Anything else (`@require`, `@resource`, `@connect`, `@updateURL`, …)
 * is parsed past and ignored — the install screen says so plainly.
 */
internal data class UserscriptMetadata(
    val name: String,
    val namespace: String,
    val version: String,
    val description: String,
    val matchPatterns: List<String>,
    val includes: List<String>,
    val excludes: List<String>,
    val runAt: UserscriptRunAt,
    val grants: Set<String>,
    val noFrames: Boolean
) {
    /** Stable identity used to recognise a re-install/update of the same
     *  script (Tampermonkey keys scripts by namespace + name). */
    val identityKey: String get() = namespace + "|" + name

    /** True when `@grant none` is the only grant: the script asked for no
     *  GM APIs at all, so the runner passes none in. */
    val grantsNone: Boolean get() = grants == setOf("none")
}

internal object UserscriptMetadataParser {

    private val BLOCK_START = Regex("""^\s*//\s*==UserScript==\s*$""")
    private val BLOCK_END = Regex("""^\s*//\s*==/UserScript==\s*$""")
    private val DIRECTIVE = Regex("""^\s*//\s*@(\S+)\s*(.*?)\s*$""")

    /** Hard cap accepted by the installer; a `.user.js` beyond this is
     *  rejected with an honest error instead of being silently truncated. */
    const val MAX_SCRIPT_CHARS = 2_000_000

    /**
     * Parses [source]; returns null when there is no metadata block or the
     * block carries no `@name` (the one field a script cannot do without).
     */
    fun parse(source: String): UserscriptMetadata? {
        if (source.length > MAX_SCRIPT_CHARS) return null
        val lines = source.lineSequence().take(400).toList()
        val start = lines.indexOfFirst { BLOCK_START.matches(it) }
        if (start < 0) return null
        val end = (start + 1 until lines.size).firstOrNull { BLOCK_END.matches(lines[it]) }
            ?: return null

        var name: String? = null
        var localizedName: String? = null
        var namespace = ""
        var version = ""
        var description = ""
        val matches = mutableListOf<String>()
        val includes = mutableListOf<String>()
        val excludes = mutableListOf<String>()
        var runAt = UserscriptRunAt.DOCUMENT_END
        val grants = mutableSetOf<String>()
        var noFrames = false

        for (line in lines.subList(start + 1, end)) {
            val m = DIRECTIVE.matchEntire(line) ?: continue
            val key = m.groupValues[1].lowercase()
            val value = m.groupValues[2]
            when {
                key == "name" -> if (value.isNotBlank()) name = value
                key.startsWith("name:") -> if (localizedName == null && value.isNotBlank()) localizedName = value
                key == "namespace" -> namespace = value
                key == "version" -> version = value
                key == "description" -> if (description.isBlank()) description = value
                key == "match" -> if (value.isNotBlank()) matches.add(value)
                key == "include" -> if (value.isNotBlank()) includes.add(value)
                key == "exclude" -> if (value.isNotBlank()) excludes.add(value)
                key == "run-at" -> runAt = when (value.lowercase()) {
                    "document-start" -> UserscriptRunAt.DOCUMENT_START
                    else -> UserscriptRunAt.DOCUMENT_END
                }
                key == "grant" -> if (value.isNotBlank()) grants.add(value)
                key == "noframes" -> noFrames = true
            }
        }
        val resolvedName = (name ?: localizedName)?.takeIf { it.isNotBlank() } ?: return null
        return UserscriptMetadata(
            name = resolvedName,
            namespace = namespace,
            version = version,
            description = description,
            matchPatterns = matches,
            includes = includes,
            excludes = excludes,
            runAt = runAt,
            grants = grants,
            noFrames = noFrames
        )
    }
}

/**
 * URL matching for userscripts: `@match` patterns use the WebExtension
 * match-pattern grammar; `@include`/`@exclude` use Tampermonkey's
 * whole-URL globs (`*` matches any run of characters). A script applies
 * when at least one match pattern or include matches and no exclude does.
 * A script with neither `@match` nor `@include` never applies (same rule
 * as Tampermonkey — it would have nowhere defined to run).
 */
internal object UserscriptUrlMatcher {

    fun appliesTo(meta: UserscriptMetadata, url: String): Boolean {
        if (meta.matchPatterns.isEmpty() && meta.includes.isEmpty()) return false
        if (meta.excludes.any { globMatches(it, url) || matchPatternMatches(it, url) }) return false
        return meta.matchPatterns.any { matchPatternMatches(it, url) } ||
            meta.includes.any { globMatches(it, url) }
    }

    /** WebExtension match pattern: `<scheme>://<host><path>` or `<all_urls>`. */
    fun matchPatternMatches(pattern: String, url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (pattern == "<all_urls>") return scheme == "http" || scheme == "https"
        val sep = pattern.indexOf("://")
        if (sep < 0) return false
        val pScheme = pattern.substring(0, sep).lowercase()
        val rest = pattern.substring(sep + 3)
        val slash = rest.indexOf('/')
        if (slash < 0) return false // a match pattern always carries a path
        val pHost = rest.substring(0, slash).lowercase()
        val pPath = rest.substring(slash)

        val schemeOk = when (pScheme) {
            "*" -> scheme == "http" || scheme == "https"
            else -> scheme == pScheme
        }
        if (!schemeOk) return false

        val host = uri.host?.lowercase() ?: return false
        val hostOk = when {
            pHost == "*" -> true
            pHost.startsWith("*.") -> {
                val suffix = pHost.substring(2)
                host == suffix || host.endsWith(".$suffix")
            }
            else -> host == pHost
        }
        if (!hostOk) return false

        val path = buildString {
            append(uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/")
            uri.rawQuery?.let { append('?').append(it) }
        }
        return globMatches(pPath, path)
    }

    /** Glob where `*` is the only wildcard; everything else is literal. */
    fun globMatches(glob: String, value: String): Boolean {
        val regex = StringBuilder()
        for (ch in glob) {
            if (ch == '*') regex.append(".*")
            else regex.append(Regex.escape(ch.toString()))
        }
        return Regex("^$regex$", RegexOption.DOT_MATCHES_ALL).matches(value)
    }
}
