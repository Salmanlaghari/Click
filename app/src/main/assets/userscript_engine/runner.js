'use strict';

// Click Userscript Engine — runner (built-in, bundled with the app).
//
// Runs at document_start on http/https pages in GeckoView tabs, but only
// while the engine extension is enabled natively (Click enables it only
// when at least one userscript is installed and switched on). Per page
// load it asks the app — via native messaging — which installed scripts
// apply to this URL, then executes them in this content-script sandbox
// (the Greasemonkey 4 model) with the classic GM API subset below.
//
// Supported: GM_addStyle, GM_setValue / GM_getValue / GM_deleteValue /
// GM_listValues (native-backed), GM_xmlhttpRequest (native relay,
// http/https only), GM_info, GM_log, unsafeWindow, plus async GM.*
// aliases (GM.getValue / GM.setValue / GM.deleteValue / GM.listValues).
// Not supported: GM_openInTab, GM_registerMenuCommand, GM_notification,
// GM_setClipboard, @require / @resource. Scripts run in the top frame
// only and never in private tabs (the app answers private sessions with
// an empty list).
(function () {
  if (!/^https?:$/.test(window.location.protocol)) return;
  var NATIVE_APP = 'click.userscripts';

  function nativeMessage(payload) {
    return browser.runtime.sendNativeMessage(NATIVE_APP, payload);
  }

  var domReady = document.readyState !== 'loading';
  if (!domReady) {
    document.addEventListener('DOMContentLoaded', function () { domReady = true; }, { once: true });
  }
  function whenDomReady(fn) {
    if (domReady) fn();
    else document.addEventListener('DOMContentLoaded', fn, { once: true });
  }

  function reportError(scriptId, err) {
    try {
      nativeMessage({
        type: 'error',
        scriptId: scriptId,
        message: String((err && err.message) || err || 'unknown error').slice(0, 300)
      }).catch(function () {});
    } catch (e) { /* reporting must never break the page */ }
  }

  function runScript(script) {
    var values = script.values || {};

    function GM_addStyle(css) {
      var style = document.createElement('style');
      style.textContent = String(css);
      (document.head || document.documentElement).appendChild(style);
      return style;
    }
    function GM_setValue(key, value) {
      values[key] = value;
      nativeMessage({ type: 'setValue', scriptId: script.id, key: String(key), value: value })
        .catch(function () {});
    }
    function GM_getValue(key, defaultValue) {
      return Object.prototype.hasOwnProperty.call(values, key) ? values[key] : defaultValue;
    }
    function GM_deleteValue(key) {
      delete values[key];
      nativeMessage({ type: 'deleteValue', scriptId: script.id, key: String(key) })
        .catch(function () {});
    }
    function GM_listValues() {
      return Object.keys(values);
    }
    function GM_xmlhttpRequest(details) {
      if (!details || !details.url) {
        if (details && details.onerror) details.onerror({ error: 'GM_xmlhttpRequest needs a url' });
        return;
      }
      nativeMessage({
        type: 'xhr',
        scriptId: script.id,
        details: {
          method: details.method || 'GET',
          url: details.url,
          headers: details.headers || {},
          data: details.data != null ? String(details.data) : null
        }
      }).then(function (resp) {
        if (!resp || resp.error) {
          if (details.onerror) details.onerror({ error: (resp && resp.error) || 'request failed' });
          return;
        }
        var response = {
          readyState: 4,
          status: resp.status,
          statusText: resp.statusText || '',
          responseText: resp.responseText || '',
          responseHeaders: resp.responseHeaders || '',
          finalUrl: resp.finalUrl || details.url,
          context: details.context
        };
        if (details.onreadystatechange) details.onreadystatechange(response);
        if (details.onload) details.onload(response);
      }).catch(function (err) {
        if (details.onerror) details.onerror({ error: String((err && err.message) || err) });
      });
    }
    var GM_info = {
      script: {
        name: script.name,
        namespace: script.namespace,
        version: script.version,
        description: script.description
      },
      scriptHandler: 'Click Userscripts',
      version: '1.0'
    };
    function GM_log(message) {
      try { console.log('[userscript:' + script.name + ']', message); } catch (e) {}
    }
    var GM = {
      info: GM_info,
      getValue: function (key, def) { return Promise.resolve(GM_getValue(key, def)); },
      setValue: function (key, value) { GM_setValue(key, value); return Promise.resolve(); },
      deleteValue: function (key) { GM_deleteValue(key); return Promise.resolve(); },
      listValues: function () { return Promise.resolve(GM_listValues()); },
      xmlHttpRequest: function (details) { GM_xmlhttpRequest(details); }
    };

    var grantNone = !!script.grantsNone;
    try {
      var fn = new Function(
        'GM_addStyle', 'GM_setValue', 'GM_getValue', 'GM_deleteValue',
        'GM_listValues', 'GM_xmlhttpRequest', 'GM_info', 'GM_log',
        'unsafeWindow', 'GM',
        '"use strict";\n' + script.code
      );
      fn(
        grantNone ? undefined : GM_addStyle,
        grantNone ? undefined : GM_setValue,
        grantNone ? undefined : GM_getValue,
        grantNone ? undefined : GM_deleteValue,
        grantNone ? undefined : GM_listValues,
        grantNone ? undefined : GM_xmlhttpRequest,
        grantNone ? undefined : GM_info,
        grantNone ? undefined : GM_log,
        window.wrappedJSObject,
        grantNone ? undefined : GM
      );
    } catch (err) {
      reportError(script.id, err);
    }
  }

  nativeMessage({ type: 'scripts', url: window.location.href })
    .then(function (resp) {
      var scripts = (resp && resp.scripts) || [];
      scripts.forEach(function (script) {
        if (script.runAt === 'start') runScript(script);
        else whenDomReady(function () { runScript(script); });
      });
    })
    .catch(function () {
      // No delegate (private tab, add-on surfaces) or engine disabled:
      // stay completely silent.
    });
})();
