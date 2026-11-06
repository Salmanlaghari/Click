package com.click.browser.engine

import androidx.webkit.WebMessageCompat
import java.net.URI
import java.util.Locale
import org.json.JSONObject

/**
 * Receives password-save candidates only through WebView's origin-aware
 * WebMessageListener. The page never supplies the credential origin used for
 * storage; that origin is derived from the browser-provided source origin.
 */
class PasswordBridge(
    private val onPasswordDetected: (
        sourceOrigin: String,
        credentialOrigin: String,
        username: String,
        password: String
    ) -> Unit
) {
    fun onMessage(sourceOrigin: String, isMainFrame: Boolean, message: WebMessageCompat) {
        val credentialOrigin = PasswordOriginPolicy.secureOrigin(sourceOrigin, isMainFrame) ?: return
        if (message.type != WebMessageCompat.TYPE_STRING) return
        val data = message.data ?: return
        if (data.length > MAX_MESSAGE_LENGTH) return

        try {
            val payload = JSONObject(data)
            val usernameValue = payload.opt("username")
            val passwordValue = payload.opt("password")
            if (usernameValue != null && usernameValue !is String) return
            if (passwordValue !is String || passwordValue.isBlank() || passwordValue.length > MAX_PASSWORD_LENGTH) return
            val username = (usernameValue as? String).orEmpty()
            if (username.length > MAX_USERNAME_LENGTH) return
            onPasswordDetected(sourceOrigin, credentialOrigin, username, passwordValue)
        } catch (_: Exception) {
            // A page may send arbitrary messages; malformed input is ignored.
        }
    }

    private companion object {
        const val MAX_MESSAGE_LENGTH = 16_384
        const val MAX_USERNAME_LENGTH = 1_024
        const val MAX_PASSWORD_LENGTH = 4_096
    }
}

/** Strict HTTPS origin checks shared by the bridge, save UI, and autofill. */
internal object PasswordOriginPolicy {
    private data class SecureOrigin(val host: String, val port: Int?) {
        fun canonicalValue(): String {
            val printableHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
            return "https://$printableHost${port?.let { ":$it" }.orEmpty()}"
        }
    }

    /** Returns a canonical HTTPS host only for a top-level frame origin. */
    fun secureHost(sourceOrigin: String, isMainFrame: Boolean): String? {
        if (!isMainFrame) return null
        return parse(sourceOrigin, originOnly = true)?.host
    }

    /** Returns a host from a current top-level HTTPS page URL, including its path. */
    fun securePageHost(pageUrl: String): String? = parse(pageUrl, originOnly = false)?.host

    /** Canonical HTTPS origin key; non-default ports remain part of the key. */
    fun secureOrigin(sourceOrigin: String, isMainFrame: Boolean): String? {
        if (!isMainFrame) return null
        return parse(sourceOrigin, originOnly = true)?.canonicalValue()
    }

    /** Canonical origin for a current HTTPS page URL, including its path. */
    fun securePageOrigin(pageUrl: String): String? =
        parse(pageUrl, originOnly = false)?.canonicalValue()

    /** Returns the canonical host portion of a previously validated HTTPS origin. */
    fun hostFromSecureOrigin(origin: String): String? =
        parse(origin, originOnly = true)?.host

    /** True only when a secure source origin still matches the current page URL. */
    fun isSameSecureOrigin(sourceOrigin: String, pageUrl: String): Boolean {
        val source = parse(sourceOrigin, originOnly = true) ?: return false
        val page = parse(pageUrl, originOnly = false) ?: return false
        return source == page
    }

    private fun parse(value: String, originOnly: Boolean): SecureOrigin? {
        if (value.isBlank()) return null
        val uri = try {
            URI(value)
        } catch (_: Exception) {
            return null
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.rawUserInfo != null || uri.rawAuthority.isNullOrBlank()) return null
        if (originOnly && (uri.rawPath.orEmpty().isNotEmpty() && uri.rawPath != "/" || uri.rawQuery != null || uri.rawFragment != null)) return null

        val host = uri.host?.lowercase(Locale.ROOT)?.removeSuffix(".")?.takeIf { it.isNotBlank() } ?: return null
        val parsedPort = uri.port
        if (parsedPort !in -1..65_535) return null
        val normalizedPort = parsedPort.takeIf { it != -1 && it != 443 }
        return SecureOrigin(host, normalizedPort)
    }
}

/**
 * Injected after HTTPS pages finish loading. A candidate is sent only for a
 * trusted, top-level form submit. Native code independently validates the
 * WebView message origin and asks the user before persisting anything.
 */
object PasswordDetector {
    val PASSWORD_DETECT_JS = """
        (function() {
            if (window.__clickPwHooked) return;
            window.__clickPwHooked = true;
            var reported = false;
            function findUsername(form) {
                var u = form.querySelector('input[type="text"][name*="user" i], input[type="email"], input[name*="email" i], input[type="text"][autocomplete="username"]');
                if (!u) u = form.querySelector('input[type="text"], input[type="email"]');
                return u ? (u.value || '') : '';
            }
            function hookForm(form) {
                var pw = form.querySelector('input[type="password"]');
                if (!pw || form.__clickPwHooked) return;
                form.__clickPwHooked = true;
                form.addEventListener('submit', function(event) {
                    try {
                        if (!event.isTrusted || window.top !== window || reported) return;
                        var user = findUsername(form);
                        var pass = pw.value || '';
                        if (pass && window.PasswordBridge && typeof window.PasswordBridge.postMessage === 'function') {
                            reported = true;
                            window.PasswordBridge.postMessage(JSON.stringify({username:user, password:pass}));
                        }
                    } catch (e) {}
                });
            }
            document.querySelectorAll('form').forEach(hookForm);
            // Watch for dynamically added forms (SPA logins).
            try {
                new MutationObserver(function(muts) {
                    muts.forEach(function(m) {
                        m.addedNodes.forEach(function(n) {
                            if (n.matches && n.matches('form')) hookForm(n);
                            if (n.querySelectorAll) n.querySelectorAll('form').forEach(hookForm);
                        });
                    });
                }).observe(document.documentElement, { childList: true, subtree: true });
            } catch (e) {}
            // Auto-fill hook: native code calls this only for a matching HTTPS origin.
            window.__clickFillLogin = function(user, pass) {
                try {
                    document.querySelectorAll('form').forEach(function(form) {
                        var pw = form.querySelector('input[type="password"]');
                        if (!pw) return;
                        var u = form.querySelector('input[type="text"], input[type="email"]');
                        if (u && user) { u.value = user; u.dispatchEvent(new Event('input', {bubbles:true})); }
                        if (pass) { pw.value = pass; pw.dispatchEvent(new Event('input', {bubbles:true})); }
                    });
                    return true;
                } catch (e) { return false; }
            };
        })();
    """.trimIndent()
}
