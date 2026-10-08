// ==UserScript==
// @name         Enable Copy & Right-Click
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Re-enables text selection, copying and the right-click menu on sites that block them.
// @match        *://*/*
// @grant        GM_addStyle
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;

    // Capture-phase shield: swallow page handlers that cancel these events.
    ['contextmenu', 'copy', 'cut', 'selectstart', 'dragstart', 'mousedown'].forEach(function (type) {
      document.addEventListener(type, function (e) {
        try { e.stopImmediatePropagation(); } catch (err) {}
      }, true);
      window.addEventListener(type, function (e) {
        try { e.stopImmediatePropagation(); } catch (err) {}
      }, true);
    });

    // Neutralize inline on* handlers set by the page.
    ['oncontextmenu', 'oncopy', 'oncut', 'onselectstart', 'ondragstart', 'onmousedown'].forEach(function (prop) {
      try { document[prop] = null; window[prop] = null; } catch (e) {}
      try {
        Object.defineProperty(document, prop, { configurable: true, get: function () { return null; }, set: function () {} });
      } catch (e) {}
    });

    // Force selectable text everywhere.
    GM_addStyle('*{-webkit-user-select:text !important;user-select:text !important;}');
  } catch (e) { /* never break the host page */ }
})();
