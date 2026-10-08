// ==UserScript==
// @name         JSON Formatter
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Detects pages that are raw JSON and re-renders them pretty-printed with simple syntax highlighting.
// @match        *://*/*
// @grant        GM_addStyle
// @run-at       document-end
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;
    if (document.getElementById('click-json-view')) return;

    var raw = '';
    try { raw = (document.body ? document.body.innerText : '').trim(); } catch (e) {}
    if (!raw || raw.length > 2000000) return;
    var first = raw.charAt(0);
    if (first !== '{' && first !== '[') return;

    var data;
    try { data = JSON.parse(raw); } catch (e) { return; }

    function esc(s) {
      return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
    function highlight(json) {
      return esc(json).replace(
        /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*")(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g,
        function (m, str, _e, colon, word) {
          var cls = 'click-json-num';
          if (str) cls = colon ? 'click-json-key' : 'click-json-str';
          else if (word) cls = 'click-json-bool';
          return '<span class="' + cls + '">' + m + '</span>';
        }
      );
    }

    var pretty;
    try { pretty = JSON.stringify(data, null, 2); } catch (e) { return; }

    document.open();
    document.write(
      '<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">' +
      '<title>JSON — Click Browser</title></head>' +
      '<body><pre id="click-json-view">' + highlight(pretty) + '</pre></body></html>'
    );
    document.close();

    GM_addStyle([
      'body{background:#0d1117;margin:0;}',
      '#click-json-view{color:#c9d1d9;font:13px/1.6 monospace;padding:16px;margin:0;white-space:pre-wrap;word-break:break-word;}',
      '.click-json-key{color:#7ee787;}',
      '.click-json-str{color:#a5d6ff;}',
      '.click-json-num{color:#f2cc60;}',
      '.click-json-bool{color:#ff9e64;}'
    ].join(''));
  } catch (e) { /* never break the host page */ }
})();
