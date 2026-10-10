// ==UserScript==
// @name         WebRTC Leak Guard
// @namespace    https://click.browser/userscripts
// @version      1.0.0
// @description  Reduces page-JS WebRTC fingerprinting: stops page scripts from creating RTCPeerConnection (the path used for STUN-based IP discovery) and from enumerating cameras/microphones. Opt-in — may break legitimate video calls on pages where enabled. Not 100% leak-proof: it cannot stop anything below the JS layer, so a system-wide VPN remains the real mitigation.
// @match        *://*/*
// @grant        none
// @run-at       document-start
// ==/UserScript==

(function () {
  'use strict';
  try {
    if (!/^https?:$/.test(window.location.protocol)) return;

    // Block camera/mic device enumeration: enumerateDevices() can leak
    // hardware fingerprints (device labels) to any page script.
    function blockEnumeration() {
      try {
        var md = navigator.mediaDevices;
        if (md && md.enumerateDevices) {
          var empty = function () { return Promise.resolve([]); };
          try {
            Object.defineProperty(md, 'enumerateDevices', {
              configurable: true, writable: true, value: empty
            });
          } catch (e) { md.enumerateDevices = empty; }
        }
      } catch (e) {}
    }

    // Block getUserMedia: pages cannot request camera/microphone access
    // while the guard is on (this is also why legit video calls break).
    function blockGetUserMedia() {
      try {
        var denied = function () {
          return Promise.reject(new DOMException('Permission denied', 'NotAllowedError'));
        };
        var md = navigator.mediaDevices;
        if (md && md.getUserMedia) {
          try {
            Object.defineProperty(md, 'getUserMedia', {
              configurable: true, writable: true, value: denied
            });
          } catch (e) { md.getUserMedia = denied; }
        }
        if (navigator.getUserMedia) {
          try {
            Object.defineProperty(navigator, 'getUserMedia', {
              configurable: true, writable: true, value: denied
            });
          } catch (e) { navigator.getUserMedia = denied; }
        }
      } catch (e) {}
    }

    // Neuter RTCPeerConnection / webkitRTCPeerConnection: constructing one
    // throws, which stops page-JS-initiated STUN/ICE candidate gathering —
    // the common WebRTC IP-leak/fingerprinting path. This cannot stop
    // anything below the JS layer (WebView exposes no API to disable
    // WebRTC; STUN runs over UDP).
    function blockPeerConnection() {
      var names = ['RTCPeerConnection', 'webkitRTCPeerConnection'];
      for (var i = 0; i < names.length; i++) {
        (function (prop) {
          function BlockedPC() {
            throw new DOMException(
              "WebRTC is disabled by Click's WebRTC Leak Guard.",
              'NotSupportedError'
            );
          }
          try {
            Object.defineProperty(window, prop, {
              configurable: true, writable: true, value: BlockedPC
            });
          } catch (e) {}
        })(names[i]);
      }
    }

    blockEnumeration();
    blockGetUserMedia();
    blockPeerConnection();
  } catch (e) { /* never break the host page */ }
})();
