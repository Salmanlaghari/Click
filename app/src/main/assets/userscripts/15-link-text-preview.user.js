// ==UserScript==
// @name         Link Text Preview
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Long-press (600ms) any link to preview its full URL in a small dialog before tapping through.
// @match        *://*/*
// @grant        GM_addStyle
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;

    var LONG_PRESS_MS = 600;
    var timer = null;
    var target = null;

    GM_addStyle([
      '#click-link-preview{position:fixed;left:50%;bottom:90px;transform:translateX(-50%);z-index:2147483647;',
      ' max-width:88vw;background:rgba(15,15,15,.95);border:1px solid rgba(255,255,255,.25);border-radius:12px;',
      ' padding:12px 16px;color:#fff;font-size:12px;font-family:sans-serif;word-break:break-all;}',
      '#click-link-preview .click-lp-title{color:#8ab4f8;font-weight:bold;margin-bottom:4px;font-size:11px;}',
      '#click-link-preview .click-lp-close{margin-top:8px;padding:4px 14px;border-radius:12px;border:1px solid rgba(255,255,255,.3);',
      ' background:transparent;color:#fff;font-size:12px;}'
    ].join(''));

    function show(link) {
      try {
        hide();
        var href = link.href || link.getAttribute('href') || '';
        var text = (link.innerText || '').trim().slice(0, 80);
        var box = document.createElement('div');
        box.id = 'click-link-preview';
        var title = document.createElement('div');
        title.className = 'click-lp-title';
        title.textContent = 'Link preview';
        var urlEl = document.createElement('div');
        urlEl.textContent = href;
        box.appendChild(title);
        if (text) {
          var t = document.createElement('div');
          t.textContent = '"' + text + '"';
          t.style.opacity = '.7';
          t.style.marginBottom = '4px';
          box.appendChild(t);
        }
        box.appendChild(urlEl);
        var close = document.createElement('button');
        close.className = 'click-lp-close';
        close.textContent = 'Close';
        close.addEventListener('click', hide);
        box.appendChild(close);
        document.documentElement.appendChild(box);
        setTimeout(hide, 8000);
      } catch (e) {}
    }
    function hide() {
      try {
        var b = document.getElementById('click-link-preview');
        if (b && b.parentNode) b.parentNode.removeChild(b);
      } catch (e) {}
    }

    function findLink(node) {
      try {
        while (node && node !== document) {
          if (node.tagName === 'A' && node.href) return node;
          node = node.parentNode;
        }
      } catch (e) {}
      return null;
    }

    document.addEventListener('touchstart', function (e) {
      try {
        hide();
        var link = e.touches && e.touches[0] ? findLink(e.touches[0].target) : null;
        if (!link) return;
        target = link;
        timer = setTimeout(function () {
          show(target);
          timer = null;
          target = null;
        }, LONG_PRESS_MS);
      } catch (err) {}
    }, { passive: true });
    document.addEventListener('touchend', cancel, { passive: true });
    document.addEventListener('touchmove', cancel, { passive: true });
    document.addEventListener('touchcancel', cancel, { passive: true });
    // Desktop fallback: Alt+click previews instead of navigating.
    document.addEventListener('click', function (e) {
      try {
        if (!e.altKey) return;
        var link = findLink(e.target);
        if (!link) return;
        e.preventDefault();
        e.stopPropagation();
        show(link);
      } catch (err) {}
    }, true);

    function cancel() {
      try {
        if (timer) { clearTimeout(timer); timer = null; }
        target = null;
      } catch (e) {}
    }
  } catch (e) { /* never break the host page */ }
})();
