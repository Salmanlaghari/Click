package com.click.browser.engine

import android.webkit.JavascriptInterface

/**
 * JS bridge: the injected [PASSWORD_DETECT_JS] watches for password-form
 * submissions and reports the host + username back to native code, which
 * shows the "Save password?" dialog.
 */
class PasswordBridge(
    private val onPasswordDetected: (host: String, username: String, password: String) -> Unit
) {
    @JavascriptInterface
    fun onLoginDetected(host: String, username: String, password: String) {
        try {
            if (host.isNotBlank() && password.isNotBlank()) {
                onPasswordDetected(host, username, password)
            }
        } catch (_: Exception) { /* ignore */ }
    }
}

object PasswordDetector {
    /**
     * Injected on every page finish. Hooks form submits containing a password
     * field; reports (host, username, password) once per page. The password
     * value is passed to native only to offer saving — never logged.
     */
    const val PASSWORD_DETECT_JS = """
        (function() {
            if (window.__clickPwHooked) return;
            window.__clickPwHooked = true;
            function findUsername(form) {
                var u = form.querySelector('input[type="text"][name*="user" i], input[type="email"], input[name*="email" i], input[type="text"][autocomplete="username"]');
                if (!u) u = form.querySelector('input[type="text"], input[type="email"]');
                return u ? (u.value || '') : '';
            }
            function hookForm(form) {
                var pw = form.querySelector('input[type="password"]');
                if (!pw || form.__clickPwHooked) return;
                form.__clickPwHooked = true;
                form.addEventListener('submit', function() {
                    try {
                        var user = findUsername(form);
                        var pass = pw.value || '';
                        if (pass && window.PasswordBridge) {
                            window.PasswordBridge.onLoginDetected(window.location.hostname, user, pass);
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
                            if (n.querySelectorAll) n.querySelectorAll('form').forEach(hookForm);
                        });
                    });
                }).observe(document.documentElement, { childList: true, subtree: true });
            } catch (e) {}
            // Auto-fill hook: native can call window.__clickFillLogin(u, p).
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
    """
}
