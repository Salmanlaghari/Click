// ==UserScript==
// @name         Cosmetic Ad Hider
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Hides common advertisement containers and sponsored slots via conservative CSS selectors. Does not skip video ads.
// @match        *://*/*
// @grant        GM_addStyle
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;

    var selectors = [
      '[id^="ad-"]', '[id$="-ad"]', '[id^="ads-"]',
      '[class^="ad-"]', '[class$="-ad"]', '[class*=" ad-"]',
      '.ad-container', '.ads-container', '.advertisement', '.advert',
      '[data-ad]', '[data-ad-slot]', '[data-testid*="ad"]',
      'ins.adsbygoogle', '.sponsored-slot', '[aria-label="Advertisement"]'
    ];
    GM_addStyle(selectors.join(',') + '{display:none !important;}');
  } catch (e) { /* never break the host page */ }
})();
