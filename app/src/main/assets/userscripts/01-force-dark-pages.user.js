// ==UserScript==
// @name         Force Dark Pages
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Applies a tasteful CSS-based dark theme to websites that don't offer one. Images and videos keep their natural brightness.
// @match        *://*/*
// @grant        GM_addStyle
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;
    if (document.documentElement.hasAttribute('data-click-dark')) return;
    document.documentElement.setAttribute('data-click-dark', '1');

    GM_addStyle([
      'html[data-click-dark] { background:#121212 !important; }',
      'html[data-click-dark] body { background-color:#121212 !important; color:#e8e8e8 !important; }',
      'html[data-click-dark] body *:not(img):not(video):not(svg):not(canvas):not([data-click-dark-keep]) {',
      '  background-color:transparent !important;',
      '  border-color:rgba(255,255,255,.12) !important;',
      '  color:inherit;',
      '}',
      'html[data-click-dark] body { color-scheme: dark; }',
      'html[data-click-dark] a { color:#8ab4f8 !important; }',
      'html[data-click-dark] input, html[data-click-dark] textarea, html[data-click-dark] select {',
      '  background-color:#1e1e1e !important; color:#e8e8e8 !important; border-color:rgba(255,255,255,.2) !important;',
      '}',
      'html[data-click-dark] img, html[data-click-dark] video { filter:brightness(.92) contrast(1.02); }',
      'html[data-click-dark] ::placeholder { color:#9a9a9a !important; }'
    ].join('\n'));
  } catch (e) { /* never break the host page */ }
})();
