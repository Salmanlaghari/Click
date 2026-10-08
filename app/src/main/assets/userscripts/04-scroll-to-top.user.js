// ==UserScript==
// @name         Scroll To Top
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Shows a floating button after scrolling down; tap it to smoothly return to the top of the page.
// @match        *://*/*
// @grant        GM_addStyle
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;
    if (document.getElementById('click-stt')) return;

    var btn = document.createElement('button');
    btn.id = 'click-stt';
    btn.setAttribute('aria-label', 'Scroll to top');
    btn.textContent = '↑';
    document.documentElement.appendChild(btn);

    GM_addStyle([
      '#click-stt{position:fixed;right:14px;bottom:130px;z-index:2147483647;width:44px;height:44px;border-radius:22px;',
      ' border:1px solid rgba(255,255,255,.25);background:rgba(20,20,20,.85);color:#fff;font-size:20px;cursor:pointer;',
      ' display:none;align-items:center;justify-content:center;}',
      '#click-stt.click-stt-show{display:flex;}'
    ].join(''));

    function onScroll() {
      try {
        var y = window.scrollY || document.documentElement.scrollTop || 0;
        btn.classList.toggle('click-stt-show', y > 400);
      } catch (e) {}
    }
    window.addEventListener('scroll', onScroll, { passive: true });
    btn.addEventListener('click', function () {
      try { window.scrollTo({ top: 0, behavior: 'smooth' }); }
      catch (e) { window.scrollTo(0, 0); }
    });
    onScroll();
  } catch (e) { /* never break the host page */ }
})();
