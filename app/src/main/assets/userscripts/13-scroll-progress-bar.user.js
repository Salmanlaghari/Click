// ==UserScript==
// @name         Scroll Progress Bar
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Shows a thin accent-colored progress bar at the very top of the page as you scroll.
// @match        *://*/*
// @grant        GM_addStyle
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;
    if (document.getElementById('click-scroll-progress')) return;

    var bar = document.createElement('div');
    bar.id = 'click-scroll-progress';
    document.documentElement.appendChild(bar);

    GM_addStyle([
      '#click-scroll-progress{position:fixed;top:0;left:0;height:3px;width:0%;z-index:2147483647;',
      ' background:linear-gradient(90deg,#3b82f6,#8b5cf6);border-radius:0 2px 2px 0;}'
    ].join(''));

    function update() {
      try {
        var doc = document.documentElement;
        var max = doc.scrollHeight - window.innerHeight;
        var pct = max > 0 ? (window.scrollY / max) * 100 : 0;
        bar.style.width = Math.max(0, Math.min(100, pct)) + '%';
      } catch (e) {}
    }
    window.addEventListener('scroll', update, { passive: true });
    window.addEventListener('resize', update);
    update();
  } catch (e) { /* never break the host page */ }
})();
