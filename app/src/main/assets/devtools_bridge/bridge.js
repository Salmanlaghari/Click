'use strict';

// Click DevTools Bridge (built-in, bundled with the app — Developer mode).
//
// The bridge extension is enabled natively only while Developer mode is
// active. Its content script runs at document_start on http/https pages:
//
//  1. Console capture: console.* calls, window errors and unhandled
//     promise rejections are recorded into a bounded ring buffer
//     (200 entries, each message capped) AND pushed live over the port.
//     The page's own console behaviour is unchanged (originals are
//     called through). Capture exists only so Click's DevTools panel can
//     show the messages after the user consents in the panel; the app
//     drops anything that arrives without that consent.
//  2. Command port: the app sends {id, code} where code is one of
//     Click's own first-party inspection snippets (the same bounded,
//     redacting scripts the WebView panel uses). The snippet runs in
//     this content-script sandbox against the page DOM and the string
//     result is posted back as {id, result} JSON-encoded exactly the
//     way WebView evaluateJavascript encodes it, so the app parses both
//     engines' results with the same code. {id, kind:"consoleHistory"}
//     answers with the buffered entries instead.
(function () {
  if (!/^https?:$/.test(window.location.protocol)) return;
  var NATIVE_APP = 'click.devtools';
  var MAX_ENTRIES = 200;
  var MAX_MESSAGE = 2000;
  var entries = [];
  var nextSeq = 1;
  var port = null;

  function stringifyArg(value) {
    if (typeof value === 'string') return value;
    if (value === null) return 'null';
    if (value === undefined) return 'undefined';
    try {
      var json = JSON.stringify(value);
      return json === undefined ? String(value) : json;
    } catch (e) {
      return String(value);
    }
  }

  function record(type, args) {
    var message = Array.prototype.map.call(args, stringifyArg).join(' ').slice(0, MAX_MESSAGE);
    var entry = { seq: nextSeq++, type: type, message: message, timestamp: Date.now() };
    entries.push(entry);
    if (entries.length > MAX_ENTRIES) entries.splice(0, entries.length - MAX_ENTRIES);
    if (port) {
      try { port.postMessage({ type: 'console', entry: entry }); } catch (e) { /* port gone */ }
    }
  }

  ['log', 'info', 'warn', 'error', 'debug'].forEach(function (level) {
    var original = console[level];
    if (typeof original !== 'function') return;
    console[level] = function () {
      record(level === 'warn' ? 'warning' : level, arguments);
      return original.apply(console, arguments);
    };
  });
  window.addEventListener('error', function (event) {
    record('error', [event.message || 'Script error']);
  });
  window.addEventListener('unhandledrejection', function (event) {
    var reason = event.reason;
    record('error', ['Unhandled promise rejection: ' + stringifyArg(reason && reason.message ? reason.message : reason)]);
  });

  function handleCommand(message) {
    if (!message || typeof message.id !== 'number') return;
    if (message.kind === 'consoleHistory') {
      try { port.postMessage({ id: message.id, entries: entries.slice() }); } catch (e) {}
      return;
    }
    if (typeof message.code !== 'string') return;
    var result;
    try {
      // By design: `message.code` can only arrive over this native port
      // from the app itself — it is either one of Click's own bounded
      // inspection snippets (DevToolsInspectionPolicy, the same scripts
      // the WebView path runs through evaluateJavascript) or a command
      // the user personally typed into the DevTools console. Page or
      // remote code has no way to post to this port.
      var value = (new Function('return (' + message.code + ')'))();
      result = JSON.stringify(value === undefined || value === null ? null : String(value));
    } catch (err) {
      try {
        port.postMessage({ id: message.id, error: String((err && err.message) || err).slice(0, 500) });
      } catch (e) {}
      return;
    }
    try { port.postMessage({ id: message.id, result: result }); } catch (e) {}
  }

  try {
    port = browser.runtime.connectNative(NATIVE_APP);
    port.onMessage.addListener(handleCommand);
    port.onDisconnect.addListener(function () { port = null; });
  } catch (e) {
    // No delegate registered for this session (not a browsable tab):
    // stay silent; console capture simply has nowhere to go.
    port = null;
  }
})();
