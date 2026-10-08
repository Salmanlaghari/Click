// ==UserScript==
// @name         Code Copy Buttons
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Adds a small "Copy" button to every code block (<pre>) so snippets are one tap away.
// @match        *://*/*
// @grant        GM_addStyle
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;

    GM_addStyle([
      '.click-copy-btn{position:absolute;top:6px;right:6px;z-index:10;padding:4px 10px;border-radius:12px;',
      ' border:1px solid rgba(127,127,127,.5);background:rgba(30,30,30,.85);color:#fff;font-size:11px;cursor:pointer;}',
      'pre{position:relative;}'
    ].join(''));

    function copyText(text, btn) {
      function done(ok) {
        try { btn.textContent = ok ? 'Copied ✓' : 'Failed'; } catch (e) {}
        setTimeout(function () { try { btn.textContent = 'Copy'; } catch (e) {} }, 1500);
      }
      try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(function () { done(true); }, function () { done(false); });
        } else {
          var ta = document.createElement('textarea');
          ta.value = text;
          ta.style.position = 'fixed';
          ta.style.opacity = '0';
          document.body.appendChild(ta);
          ta.select();
          var ok = false;
          try { ok = document.execCommand('copy'); } catch (e) {}
          ta.parentNode.removeChild(ta);
          done(ok);
        }
      } catch (e) { done(false); }
    }

    function decorate(root) {
      try {
        var pres = (root || document).querySelectorAll('pre');
        for (var i = 0; i < pres.length; i++) {
          try {
            var pre = pres[i];
            if (pre.querySelector(':scope > .click-copy-btn')) continue;
            if (!pre.innerText || pre.innerText.trim().length < 2) continue;
            var btn = document.createElement('button');
            btn.className = 'click-copy-btn';
            btn.textContent = 'Copy';
            (function (p, b) {
              b.addEventListener('click', function (ev) {
                ev.stopPropagation();
                copyText(p.innerText, b);
              });
            })(pre, btn);
            pre.appendChild(btn);
          } catch (e) {}
        }
      } catch (e) {}
    }

    decorate(document);
    try {
      new MutationObserver(function () { decorate(document); })
        .observe(document.documentElement, { childList: true, subtree: true });
    } catch (e) {}
  } catch (e) { /* never break the host page */ }
})();
