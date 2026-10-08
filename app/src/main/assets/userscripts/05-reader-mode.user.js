// ==UserScript==
// @name         Reader Mode
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Adds a floating "Reader" button on article pages; tap it to hide navigation, ads and sidebars for clean reading. Tap again to restore.
// @match        *://*/*
// @grant        GM_addStyle
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;
    if (document.getElementById('click-reader-btn')) return;

    // Heuristic: only show on pages that look like articles.
    var textLen = 0;
    try { textLen = (document.body ? document.body.innerText.length : 0); } catch (e) {}
    var hasArticle = !!document.querySelector('article, [role="main"]');
    if (!hasArticle && textLen < 2500) return;

    var btn = document.createElement('button');
    btn.id = 'click-reader-btn';
    btn.textContent = '☰ Reader';
    document.documentElement.appendChild(btn);

    GM_addStyle([
      '#click-reader-btn{position:fixed;left:12px;bottom:130px;z-index:2147483647;padding:8px 14px;border-radius:20px;',
      ' border:1px solid rgba(255,255,255,.25);background:rgba(20,20,20,.85);color:#fff;font-size:13px;cursor:pointer;}',
      'html.click-reader-on header, html.click-reader-on nav, html.click-reader-on footer,',
      'html.click-reader-on aside, html.click-reader-on [role="banner"], html.click-reader-on [role="complementary"],',
      'html.click-reader-on [class*="sidebar"], html.click-reader-on [id*="sidebar"],',
      'html.click-reader-on [class*="ad-"], html.click-reader-on [id*="ad-"],',
      'html.click-reader-on [class*="popup"], html.click-reader-on [class*="newsletter"]{display:none !important;}',
      'html.click-reader-on main, html.click-reader-on article{max-width:720px !important;margin:0 auto !important;',
      ' font-size:19px !important;line-height:1.75 !important;padding:16px !important;}'
    ].join(''));

    btn.addEventListener('click', function () {
      try {
        var on = document.documentElement.classList.toggle('click-reader-on');
        btn.textContent = on ? '✕ Exit Reader' : '☰ Reader';
      } catch (e) {}
    });
  } catch (e) { /* never break the host page */ }
})();
