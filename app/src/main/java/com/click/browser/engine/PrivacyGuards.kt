package com.click.browser.engine

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/**
 * Real privacy-guard building blocks.
 *
 * HONESTY NOTES (also documented in the PR description):
 * - Secure DNS: Android WebView page loads ALWAYS use the system DNS
 *   resolver — there is no WebView API to change that. The DoH client here
 *   is used for the APP's own network requests (AI chat, header re-fetch).
 * - WebRTC: WebView exposes NO API to disable WebRTC, and STUN runs over
 *   UDP so shouldInterceptRequest cannot block it. The strongest real thing
 *   the app can do is run a genuine STUN-based leak TEST in the current tab
 *   and show the user exactly which IPs a site could see (see
 *   WEBRTC_LEAK_TEST_JS + WebRtcTestBridge). A system-wide VPN remains the
 *   real mitigation; the UI says so plainly.
 * - Header spoofing: applied to navigations via loadUrl(url, extraHeaders)
 *   and to subresources by re-fetching them with OkHttp + the custom headers
 *   in shouldInterceptRequest. Hop-by-hop headers (Host, Content-Length,
 *   Connection…) cannot be overridden — OkHttp/WebView will refuse or
 *   replace them; the UI warns about this.
 */
object PrivacyGuards {

    /**
     * DNS-over-HTTPS (Cloudflare) resolver for the app's own OkHttp traffic.
     * Falls back to system DNS if DoH cannot be constructed.
     */
    fun buildSecureDns(): Dns {
        val bootstrapClient = OkHttpClient.Builder().build()
        return DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                listOf(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1")
                )
            )
            .build()
    }

    fun safeSecureDns(): Dns = try {
        buildSecureDns()
    } catch (_: Exception) {
        Dns.SYSTEM
    }

    /**
     * Builds the fingerprint-protection script with per-session randomness.
     * [sessionSalt] is generated once per app launch: the seeded PRNG makes
     * canvas/audio noise stable within a session but different on every
     * launch, so fingerprint hashes cannot be correlated across sessions.
     * Unlike the HACK-mode anti-detection suite (which also spoofs UA,
     * screen, WebGL…), this script ONLY poisons the fingerprinting surfaces
     * (canvas reads + AudioContext analyser data) and is safe to run in any
     * browsing mode.
     */
    fun buildFingerprintScript(sessionSalt: String): String {
        val seed = sessionSalt.hashCode()
        return """
        (function() {
            if (window.fpProtectInjected) return;
            window.fpProtectInjected = true;
            // mulberry32 seeded PRNG — session-unique noise pattern
            var _s = ($seed >>> 0) || 1;
            function fpRnd() {
                _s |= 0; _s = _s + 0x6D2B79F5 | 0;
                var t = Math.imul(_s ^ _s >>> 15, 1 | _s);
                t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
                return ((t ^ t >>> 14) >>> 0) / 4294967296;
            }
            // 1. Canvas fingerprint noise: poison pixel reads with
            // imperceptible session-seeded noise so canvas hashes are
            // unstable and differ on every app launch.
            function addNoise(imageData) {
                var d = imageData.data;
                for (var i = 0; i < d.length; i += 4) {
                    var n = (fpRnd() - 0.5) * 2;
                    d[i]     = Math.max(0, Math.min(255, d[i] + n));
                    d[i + 1] = Math.max(0, Math.min(255, d[i + 1] + n));
                    d[i + 2] = Math.max(0, Math.min(255, d[i + 2] + n));
                }
                return imageData;
            }
            try {
                var origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                CanvasRenderingContext2D.prototype.getImageData = function() {
                    return addNoise(origGetImageData.apply(this, arguments));
                };
                var origToDataURL = HTMLCanvasElement.prototype.toDataURL;
                HTMLCanvasElement.prototype.toDataURL = function() {
                    try {
                        var ctx = this.getContext('2d');
                        if (ctx) {
                            var img = origGetImageData.call(ctx, 0, 0, this.width, this.height);
                            ctx.putImageData(addNoise(img), 0, 0);
                        }
                    } catch (e) {}
                    return origToDataURL.apply(this, arguments);
                };
            } catch (e) {}
            // 2. AudioContext fingerprint noise: jitter analyser frequency
            // data with session-seeded noise so audio-stack hashes differ
            // on every read and every launch.
            function hookAudioContext(AC) {
                if (!AC || AC.prototype.__fpSpoofed) return;
                var origCreateAnalyser = AC.prototype.createAnalyser;
                AC.prototype.createAnalyser = function() {
                    var analyser = origCreateAnalyser.apply(this, arguments);
                    var origGetFloat = analyser.getFloatFrequencyData;
                    analyser.getFloatFrequencyData = function(array) {
                        origGetFloat.apply(this, arguments);
                        for (var i = 0; i < array.length; i++) {
                            array[i] += (fpRnd() - 0.5) * 0.5;
                        }
                    };
                    return analyser;
                };
                AC.prototype.__fpSpoofed = true;
            }
            try {
                hookAudioContext(window.AudioContext);
                hookAudioContext(window.webkitAudioContext);
            } catch (e) {}
        })();
        """.trimIndent()
    }

    /**
     * Real WebRTC leak TEST. Gathers ICE candidates via a STUN server and
     * reports the discovered IPs back through the WebRtcTestBridge.
     * This is a TEST, not a blocker — WebView offers no API to disable
     * WebRTC, and STUN uses UDP which shouldInterceptRequest cannot see.
     */
    const val WEBRTC_LEAK_TEST_JS = """
        (function() {
            function report(ips) {
                try { WebRtcTestBridge.onIpsDetected(JSON.stringify(ips)); } catch (e) {}
            }
            var ips = [];
            function collect(candidateStr) {
                var m = /([0-9]{1,3}(\.[0-9]{1,3}){3})/.exec(candidateStr || '');
                if (m && ips.indexOf(m[1]) === -1) ips.push(m[1]);
            }
            try {
                var RTC = window.RTCPeerConnection || window.webkitRTCPeerConnection;
                if (!RTC) { report([]); return; }
                var pc = new RTC({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] });
                pc.createDataChannel('click-leak-test');
                var done = false;
                function finish() {
                    if (done) return;
                    done = true;
                    try { pc.close(); } catch (e) {}
                    report(ips);
                }
                pc.onicecandidate = function(e) {
                    if (e && e.candidate && e.candidate.candidate) {
                        collect(e.candidate.candidate);
                    } else {
                        finish();
                    }
                };
                pc.createOffer().then(function(o) { return pc.setLocalDescription(o); }).catch(finish);
                setTimeout(finish, 6000);
            } catch (e) { report([]); }
        })();
    """
}
