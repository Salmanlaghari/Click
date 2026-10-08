package com.click.browser.engine

/**
 * Tampermonkey-style userscript engine for HACK mode.
 *
 * HONESTY NOTE: this is NOT Chrome-extension (.crx) support — Android
 * WebView technically cannot run .crx extensions (that needs a full
 * Chromium fork like Mises). What this engine really does, and what the UI
 * calls it, is "Userscript Extensions (.user.js)": it parses the
 * ==UserScript== metadata block (@name, @match, @include, @run-at, @grant),
 * matches scripts against the loaded URL, and injects them via
 * evaluateJavascript. GM_setValue/GM_getValue/GM_deleteValue/GM_listValues/
 * GM_log/GM_addStyle are provided through UserscriptBridge.
 *
 * WebView limit: there is no true document-start injection point, so
 * scripts tagged @run-at document-start run at page finish — the earliest
 * reliable moment. The UI states this plainly.
 */
data class UserscriptMeta(
    val name: String,
    val namespace: String,
    val version: String,
    val description: String,
    val matches: List<String>,
    val includes: List<String>,
    val runAt: String,
    val grants: List<String>
)

data class UserscriptInfo(
    val id: String,
    val meta: UserscriptMeta,
    val enabled: Boolean
)

object UserscriptEngine {

    private val META_BLOCK = Regex("// ==UserScript==([\\s\\S]*?)// ==/UserScript==")
    private val META_LINE = Regex("""//\s*@(\S+)\s+(.*)""")

    /**
     * Parses a .user.js source. Returns (meta, code) or null when there is
     * no valid metadata block / no @name / no code body.
     */
    fun parse(source: String): Pair<UserscriptMeta, String>? {
        val block = META_BLOCK.find(source)?.groupValues?.get(1) ?: return null
        val tags = mutableMapOf<String, MutableList<String>>()
        META_LINE.findAll(block).forEach { m ->
            tags.getOrPut(m.groupValues[1]) { mutableListOf() }.add(m.groupValues[2].trim())
        }
        val name = tags["name"]?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val code = source.substringAfter("// ==/UserScript==").trim()
        if (code.isBlank()) return null
        val meta = UserscriptMeta(
            name = name,
            namespace = tags["namespace"]?.firstOrNull().orEmpty(),
            version = tags["version"]?.firstOrNull().orEmpty(),
            description = tags["description"]?.firstOrNull().orEmpty(),
            matches = tags["match"] ?: emptyList(),
            includes = tags["include"] ?: emptyList(),
            runAt = tags["run-at"]?.firstOrNull()?.lowercase() ?: "document-end",
            grants = tags["grant"] ?: listOf("none")
        )
        return meta to code
    }

    fun matchesUrl(meta: UserscriptMeta, url: String): Boolean {
        if (url.isBlank()) return false
        if (meta.matches.isEmpty() && meta.includes.isEmpty()) return false
        if (meta.matches.any { matchPattern(it, url) }) return true
        if (meta.includes.any { inc ->
            runCatching { Regex(inc).containsMatchIn(url) }.getOrDefault(false)
        }) return true
        return false
    }

    /**
     * Converts a Tampermonkey @match pattern (scheme://host/path with
     * * wildcards) into a Regex.
     */
    fun matchPatternToRegex(pattern: String): Regex {
        val schemeSplit = pattern.split("://", limit = 2)
        if (schemeSplit.size != 2) return Regex("^$")
        val schemePart = schemeSplit[0]
        val rest = schemeSplit[1]
        val hostPart = rest.substringBefore("/")
        val pathPart = "/" + rest.substringAfter("/", "")
        val schemeRegex = when (schemePart) {
            "*" -> "https?"
            "http" -> "http"
            "https" -> "https"
            else -> Regex.escape(schemePart)
        }
        val hostRegex = when {
            hostPart == "*" -> "[^/]+"
            hostPart.startsWith("*.") -> "([^/]+\\.)?" + Regex.escape(hostPart.removePrefix("*."))
            else -> Regex.escape(hostPart)
        }
        val pathRegex = buildString {
            for (ch in pathPart) {
                if (ch == '*') append(".*") else append(Regex.escape(ch.toString()))
            }
        }
        return Regex("^$schemeRegex://$hostRegex$pathRegex$")
    }

    private fun matchPattern(pattern: String, url: String): Boolean =
        runCatching { matchPatternToRegex(pattern).matches(url) }.getOrDefault(false)

    /**
     * Builds the full JS evaluated for one script on a matching page: a
     * small GM_* compatibility shim (backed by UserscriptBridge) followed
     * by the user's code.
     */
    fun buildInjection(scriptId: String, scriptName: String, code: String): String {
        val sid = org.json.JSONObject.quote(scriptId)
        val sname = org.json.JSONObject.quote(scriptName)
        val shim = """
        (function() {
            try {
                var __usid = $sid;
                window.GM_setValue = function(k, v) { try { UserscriptBridge.gmSetValue(__usid, String(k), String(v)); } catch (e) {} };
                window.GM_getValue = function(k, d) { try { return UserscriptBridge.gmGetValue(__usid, String(k), d == null ? "" : String(d)); } catch (e) { return d; } };
                window.GM_deleteValue = function(k) { try { UserscriptBridge.gmDeleteValue(__usid, String(k)); } catch (e) {} };
                window.GM_listValues = function() { try { return JSON.parse(UserscriptBridge.gmListValues(__usid)); } catch (e) { return []; } };
                window.GM_log = function(m) { try { UserscriptBridge.gmLog(__usid, String(m)); } catch (e) {} };
                window.GM_addStyle = function(css) { try { var s = document.createElement('style'); s.textContent = css; (document.head || document.documentElement).appendChild(s); } catch (e) {} };
                window.GM_info = { script: { name: $sname }, scriptHandler: "Click Userscripts" };
            } catch (e) {}
        })();
        """.trimIndent()
        return "$shim\n$code"
    }
}
