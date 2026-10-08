// ==UserScript==
// @name         Image Zoom
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Tap any image on a page to view it in a fullscreen overlay. Tap the overlay to close.
// @match        *://*/*
// @grant        GM_addStyle
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;

    GM_addStyle([
      '#click-img-overlay{position:fixed;inset:0;z-index:2147483647;background:rgba(0,0,0,.92);',
      ' display:flex;align-items:center;justify-content:center;cursor:zoom-out;}',
      '#click-img-overlay img{max-width:96vw;max-height:94vh;object-fit:contain;border-radius:6px;}'
    ].join(''));

    function openOverlay(src) {
      try {
        closeOverlay();
        var ov = document.createElement('div');
        ov.id = 'click-img-overlay';
        var img = document.createElement('img');
        img.src = src;
        img.alt = '';
        ov.appendChild(img);
        ov.addEventListener('click', closeOverlay);
        document.documentElement.appendChild(ov);
      } catch (e) {}
    }
    function closeOverlay() {
      try {
        var ov = document.getElementById('click-img-overlay');
        if (ov && ov.parentNode) ov.parentNode.removeChild(ov);
      } catch (e) {}
    }

    document.addEventListener('click', function (e) {
      try {
        var t = e.target;
        if (!t || t.tagName !== 'IMG') return;
        if (document.getElementById('click-img-overlay')) return;
        var src = t.currentSrc || t.src;
        if (!src) return;
        // Ignore tiny icons and images already inside links (let the link work).
        if ((t.naturalWidth || 0) < 60 && (t.naturalHeight || 0) < 60) return;
        e.preventDefault();
        e.stopPropagation();
        openOverlay(src);
      } catch (err) {}
    }, true);
    document.addEventListener('keydown', function (e) {
      try { if (e.key === 'Escape') closeOverlay(); } catch (err) {}
    });
  } catch (e) { /* never break the host page */ }
})();
