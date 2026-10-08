// ==UserScript==
// @name         Cookie Banner Helper
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Hides common cookie-consent banners with a conservative selector list. It never clicks "accept" — it only hides the banner element.
// @match        *://*/*
// @grant        GM_addStyle
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;

    // Conservative: only well-known cookie/consent banner hooks.
    var selectors = [
      '#cookie-banner', '#cookieBanner', '#cookie-consent', '#cookieConsent',
      '#cookie-notice', '#cookieNotice', '#cookies-banner',
      '.cookie-banner', '.cookie-consent', '.cookie-notice', '.cookies-banner',
      '#gdpr-banner', '#gdpr-consent', '.gdpr-banner',
      '#onetrust-banner-sdk', '#onetrust-consent-sdk',
      '.cc-window', '#cc-window',
      '[aria-label="cookie consent"]', '[data-cookie-banner]'
    ];
    GM_addStyle(selectors.join(',') + '{display:none !important;}');

    // Also catch banners injected late, by hiding (not removing) matching nodes.
    function sweep(root) {
      try {
        var els = (root || document).querySelectorAll(selectors.join(','));
        for (var i = 0; i < els.length; i++) {
          try { els[i].style.setProperty('display', 'none', 'important'); } catch (e) {}
        }
      } catch (e) {}
    }
    sweep(document);
    try {
      var obs = new MutationObserver(function (muts) {
        muts.forEach(function (m) {
          for (var i = 0; i < m.addedNodes.length; i++) {
            var n = m.addedNodes[i];
            if (n && n.nodeType === 1) sweep(n);
          }
        });
      });
      obs.observe(document.documentElement, { childList: true, subtree: true });
      // Stop watching after 20s — banners appear early or not at all.
      setTimeout(function () { try { obs.disconnect(); } catch (e) {} }, 20000);
    } catch (e) {}
  } catch (e) { /* never break the host page */ }
})();
