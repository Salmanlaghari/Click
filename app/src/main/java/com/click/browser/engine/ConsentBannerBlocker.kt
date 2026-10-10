package com.click.browser.engine

import org.json.JSONArray

/**
 * Cookie-consent / GDPR banner blocking (Brave-style).
 *
 * Cosmetic approach — the same technique uBlock Origin's "annoyances" lists
 * use:
 * 1. A `<style>` rule hides every known banner selector
 *    (`display:none !important`).
 * 2. A MutationObserver re-applies the hiding for banners injected late
 *    (most CMPs render after page load).
 * 3. Best-effort auto-reject: inside an element that matches a banner
 *    selector, click the first button whose label looks like a rejection
 *    ("Reject all", "Decline", "Necessary only"...). This only ever touches
 *    elements inside already-hidden banner containers, so it can never
 *    click a real page button.
 *
 * Selectors come from [FilterListManager.currentConsentSelectors] (bundled
 * asset + weekly remote update). An empty selector set = no-op script.
 */
object ConsentBannerBlocker {

    /** Button labels treated as "reject" (lowercased, trimmed). English-first;
     * banner hiding (step 1+2) is language-independent and always applies. */
    private val REJECT_LABELS = setOf(
        "reject all", "reject", "decline", "decline all",
        "necessary only", "essential only", "essentials only",
        "save selection", "confirm selection", "confirm choices"
    )

    /**
     * Builds the injection script for [selectors]. Returns null when there
     * is nothing to block (caller skips injection).
     */
    fun buildScript(selectors: Set<String>): String? {
        if (selectors.isEmpty()) return null
        val json = JSONArray(selectors.toList()).toString()
        val rejectJson = JSONArray(REJECT_LABELS.toList()).toString()
        return """
        (function() {
            if (window.__clickConsentBlocked) return;
            window.__clickConsentBlocked = true;
            var SELECTORS = $json;
            var REJECT = $rejectJson;
            function cssText() {
                return SELECTORS.join(',') + '{display:none!important;visibility:hidden!important;pointer-events:none!important;}';
            }
            function applyCss() {
                try {
                    var id = '__click_consent_css';
                    var el = document.getElementById(id);
                    if (!el) {
                        el = document.createElement('style');
                        el.id = id;
                        (document.head || document.documentElement).appendChild(el);
                    }
                    el.textContent = cssText();
                } catch (e) {}
            }
            function tryReject(root) {
                try {
                    var btns = root.querySelectorAll('button, [role="button"], input[type="button"], input[type="submit"], a');
                    for (var i = 0; i < btns.length; i++) {
                        var t = (btns[i].innerText || btns[i].value || '').trim().toLowerCase();
                        if (REJECT.indexOf(t) !== -1) { btns[i].click(); return true; }
                    }
                } catch (e) {}
                return false;
            }
            function sweep() {
                try {
                    for (var i = 0; i < SELECTORS.length; i++) {
                        var nodes;
                        try { nodes = document.querySelectorAll(SELECTORS[i]); }
                        catch (e) { continue; }
                        for (var j = 0; j < nodes.length; j++) {
                            tryReject(nodes[j]);
                            nodes[j].style.setProperty('display', 'none', 'important');
                        }
                    }
                } catch (e) {}
            }
            applyCss();
            sweep();
            // Late-loading CMPs: re-sweep on DOM mutations (throttled).
            var pending = false;
            try {
                new MutationObserver(function() {
                    if (pending) return;
                    pending = true;
                    setTimeout(function() { pending = false; applyCss(); sweep(); }, 400);
                }).observe(document.documentElement, { childList: true, subtree: true });
            } catch (e) {}
            // One more sweep after full load for slow CMPs.
            setTimeout(function() { applyCss(); sweep(); }, 2500);
        })();
        """.trimIndent()
    }
}
