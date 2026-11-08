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
 * - Secure DNS: Android WebView page loads use the system DNS resolver —
 *   there is no WebView API to change that. This DoH helper is for the app's
 *   own network requests only; it does not change website traffic.
 * - WebRTC: WebView exposes NO API to disable WebRTC, and STUN runs over
 *   UDP so shouldInterceptRequest cannot block it. The strongest real thing
 *   the app can do is run a genuine STUN-based leak TEST in the current tab
 *   and show the user exactly which IPs a site could see (see
 *   WEBRTC_LEAK_TEST_JS + WebRtcTestBridge). A system-wide VPN remains the
 *   real mitigation; the UI says so plainly.
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
     * Real WebRTC leak TEST. Gathers ICE candidates via a STUN server and
     * reports the discovered IPs back through the WebRtcTestBridge.
     * This is a TEST, not a blocker — WebView offers no API to disable
     * WebRTC, and STUN uses UDP which shouldInterceptRequest cannot see.
     */
    fun webRtcLeakTestScript(runId: String): String {
        require(runId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid WebRTC test run ID" }
        return WEBRTC_LEAK_TEST_JS.replace("__CLICK_WEBRTC_RUN_ID__", runId)
    }

    const val WEBRTC_LEAK_TEST_JS = """
        (function() {
            var runId = '__CLICK_WEBRTC_RUN_ID__';
            var ips = [];
            var done = false;
            function report(status) {
                try {
                    WebRtcTestBridge.onResult(runId, JSON.stringify({ status: status, ips: ips }));
                } catch (e) {}
            }
            function collect(candidateStr) {
                var m = /([0-9]{1,3}(\.[0-9]{1,3}){3})/.exec(candidateStr || '');
                if (m && ips.indexOf(m[1]) === -1) ips.push(m[1]);
            }
            function finish(status) {
                if (done) return;
                done = true;
                try { pc.close(); } catch (e) {}
                report(status);
            }
            var pc;
            try {
                var RTC = window.RTCPeerConnection || window.webkitRTCPeerConnection;
                if (!RTC) { report('unsupported'); return; }
                pc = new RTC({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] });
                pc.createDataChannel('click-leak-test');
                pc.onicecandidate = function(e) {
                    if (e && e.candidate && e.candidate.candidate) {
                        collect(e.candidate.candidate);
                    } else if (e && e.candidate === null) {
                        finish('completed');
                    }
                };
                pc.onicegatheringstatechange = function() {
                    if (pc.iceGatheringState === 'complete') finish('completed');
                };
                pc.createOffer()
                    .then(function(o) { return pc.setLocalDescription(o); })
                    .catch(function() { finish('error'); });
                setTimeout(function() { finish('timeout'); }, 6000);
            } catch (e) {
                if (pc) finish('error');
                else report('error');
            }
        })();
    """
}
