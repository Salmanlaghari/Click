// ==UserScript==
// @name         Video Speed Controller
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Floating control to speed up or slow down any HTML5 video on the page (0.25x to 3x).
// @match        *://*/*
// @grant        GM_addStyle
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;
    if (document.getElementById('click-vsc')) return;

    var SPEEDS = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3];
    var current = 1;

    function applyAll() {
      try {
        var vids = document.querySelectorAll('video');
        for (var i = 0; i < vids.length; i++) {
          try { vids[i].playbackRate = current; } catch (e) {}
        }
      } catch (e) {}
    }

    var panel = document.createElement('div');
    panel.id = 'click-vsc';
    panel.innerHTML =
      '<button id="click-vsc-minus" title="Slower">−</button>' +
      '<span id="click-vsc-label">1x</span>' +
      '<button id="click-vsc-plus" title="Faster">+</button>' +
      '<button id="click-vsc-hide" title="Hide">×</button>';
    document.documentElement.appendChild(panel);

    GM_addStyle([
      '#click-vsc{position:fixed;right:12px;bottom:76px;z-index:2147483647;display:flex;align-items:center;gap:4px;',
      ' background:rgba(20,20,20,.85);border:1px solid rgba(255,255,255,.2);border-radius:20px;padding:4px 6px;}',
      '#click-vsc button{background:transparent;border:none;color:#fff;font-size:15px;font-weight:bold;',
      ' width:26px;height:26px;border-radius:13px;cursor:pointer;line-height:1;}',
      '#click-vsc button:active{background:rgba(255,255,255,.2);}',
      '#click-vsc-label{color:#fff;font-size:12px;min-width:34px;text-align:center;font-family:sans-serif;}'
    ].join(''));

    function label() {
      var el = document.getElementById('click-vsc-label');
      if (el) el.textContent = (current + 'x').replace('.0x', 'x');
    }
    function step(dir) {
      var i = SPEEDS.indexOf(current);
      if (i < 0) i = SPEEDS.indexOf(1);
      i = Math.max(0, Math.min(SPEEDS.length - 1, i + dir));
      current = SPEEDS[i];
      applyAll();
      label();
    }
    document.getElementById('click-vsc-minus').addEventListener('click', function () { step(-1); });
    document.getElementById('click-vsc-plus').addEventListener('click', function () { step(1); });
    document.getElementById('click-vsc-hide').addEventListener('click', function () {
      var p = document.getElementById('click-vsc');
      if (p) p.style.display = 'none';
    });

    applyAll();
    try {
      new MutationObserver(function () { applyAll(); })
        .observe(document.documentElement, { childList: true, subtree: true });
    } catch (e) {}
  } catch (e) { /* never break the host page */ }
})();
