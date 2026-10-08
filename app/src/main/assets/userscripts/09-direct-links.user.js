// ==UserScript==
// @name         Direct Links
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Unwraps redirect-wrapper links (e.g. /url?q=..., /redirect?url=...) so taps go straight to the real destination.
// @match        *://*/*
// @grant        none
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;

    // Known wrapper parameter names, in priority order.
    var PARAMS = ['q', 'url', 'u', 'target', 'dest', 'destination', 'r', 'redirect'];

    function unwrap(href) {
      try {
        if (!href || href.indexOf('http') !== 0) return null;
        var u = new URL(href, window.location.href);
        for (var i = 0; i < PARAMS.length; i++) {
          var v = u.searchParams.get(PARAMS[i]);
          if (v && /^https?:\/\//i.test(v)) return v;
        }
        return null;
      } catch (e) { return null; }
    }

    function fix(root) {
      try {
        var links = (root || document).querySelectorAll('a[href]');
        for (var i = 0; i < links.length; i++) {
          try {
            var a = links[i];
            if (a.dataset.clickDirectFixed) continue;
            var direct = unwrap(a.getAttribute('href'));
            if (direct) {
              a.setAttribute('href', direct);
              a.dataset.clickDirectFixed = '1';
            }
          } catch (e) {}
        }
      } catch (e) {}
    }

    fix(document);
    try {
      new MutationObserver(function (muts) {
        muts.forEach(function (m) {
          for (var i = 0; i < m.addedNodes.length; i++) {
            var n = m.addedNodes[i];
            if (n && n.nodeType === 1) {
              if (n.tagName === 'A') fix(n.parentNode || document);
              else fix(n);
            }
          }
        });
      }).observe(document.documentElement, { childList: true, subtree: true });
    } catch (e) {}
  } catch (e) { /* never break the host page */ }
})();
