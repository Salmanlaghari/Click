// ==UserScript==
// @name         Disable Video Autoplay
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Pauses videos that try to autoplay on page load, so pages stay quiet until you press play.
// @match        *://*/*
// @grant        none
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;

    function silence(root) {
      try {
        var vids = (root || document).querySelectorAll('video');
        for (var i = 0; i < vids.length; i++) {
          try {
            var v = vids[i];
            if (v.autoplay || v.getAttribute('autoplay') !== null) {
              v.autoplay = false;
              v.removeAttribute('autoplay');
            }
            // Pause only videos the user hasn't interacted with.
            if (!v.paused && !v.dataset.clickUserPlayed) v.pause();
          } catch (e) {}
        }
      } catch (e) {}
    }

    document.addEventListener('play', function (e) {
      try {
        var t = e.target;
        // Mark videos the user started deliberately so we never pause them.
        if (t && t.tagName === 'VIDEO') t.dataset.clickUserPlayed = '1';
      } catch (err) {}
    }, true);

    silence(document);
    try {
      new MutationObserver(function (muts) {
        muts.forEach(function (m) {
          for (var i = 0; i < m.addedNodes.length; i++) {
            var n = m.addedNodes[i];
            if (n && n.nodeType === 1) silence(n);
          }
        });
      }).observe(document.documentElement, { childList: true, subtree: true });
    } catch (e) {}
  } catch (e) { /* never break the host page */ }
})();
