'use strict';

// Click WebRTC Guard (built-in, bundled with the app — not a userscript).
// Runs at document_start in GeckoView tabs while the user has "Block
// WebRTC" turned on in Privacy Guards. It replaces RTCPeerConnection with
// a stub whose constructor throws, so page scripts cannot create peer
// connections or start STUN/ICE candidate gathering (the WebRTC IP-leak
// path). It deliberately does NOT touch getUserMedia or enumerateDevices:
// camera/microphone access stays governed by Click's per-site permission
// prompts. Keep the stub text in sync with PrivacyGuards.WEBRTC_BLOCK_JS,
// which applies the same block in WebView (private) tabs.
(function () {
  try {
    // location.protocol includes the trailing colon ("https:"), so the
    // pattern must not end-anchor past it — the old anchored version
    // never matched any real page.
    if (!/^https?:/.test(window.location.protocol)) return;
    function BlockedRTCPeerConnection() {
      throw new DOMException(
        "WebRTC is disabled by Click's Block WebRTC setting.",
        'NotSupportedError'
      );
    }
    ['RTCPeerConnection', 'webkitRTCPeerConnection'].forEach(function (prop) {
      try {
        // Locked descriptor (writable/configurable false) is deliberate:
        // a writable stub could be replaced by any page script, silently
        // undoing the block. Turning the setting off disables this
        // extension entirely, so fresh page loads get the real
        // RTCPeerConnection back — the lock only binds pages where the
        // user asked for the block.
        Object.defineProperty(window, prop, {
          configurable: false,
          writable: false,
          value: BlockedRTCPeerConnection
        });
      } catch (e) { /* never break the host page */ }
    });
  } catch (e) { /* never break the host page */ }
})();
