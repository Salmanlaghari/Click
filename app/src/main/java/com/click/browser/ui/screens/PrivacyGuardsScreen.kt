package com.click.browser.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.AppSettings

/**
 * Privacy Guards screen. Every control here does exactly what its label
 * claims — there are no toast-only fakes:
 *
 * - Spoof HTTP Headers: adds YOUR header list to page navigations
 *   (loadUrl extraHeaders) and re-fetches page resources with those headers
 *   via shouldInterceptRequest. Hop-by-hop headers can't be overridden.
 * - Fingerprint Protection: injects per-session randomized canvas + audio
 *   noise in every browsing mode (session salt changes each app launch).
 * - Secure DNS: DNS-over-HTTPS (Cloudflare) for the APP's own requests
 *   (AI chat, header re-fetch). WebView page loads still use system DNS —
 *   Android exposes no WebView DoH setting; the UI says so.
 * - WebRTC Leak Test: runs a REAL STUN-based ICE candidate gathering in the
 *   current tab and lists the IPs a site could see. It is a test, not a
 *   blocker — WebView has no API to disable WebRTC (STUN is UDP), so a
 *   system VPN remains the real mitigation; the UI says so.
 * - WebRTC Leak Guard: OPT-IN bundled userscript that stops page scripts
 *   from creating RTCPeerConnection and enumerating cameras/mics. Off by
 *   default because it breaks legitimate video calls. Reduces page-JS
 *   fingerprinting only — never presented as 100% leak-proof.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyGuardsScreen(
    headerSpoofEnabled: Boolean,
    onToggleHeaderSpoof: (Boolean) -> Unit,
    customHeaders: List<AppSettings.CustomHeader>,
    onAddHeader: (String, String) -> Unit,
    onRemoveHeader: (Int) -> Unit,
    fingerprintProtection: Boolean,
    onToggleFingerprint: (Boolean) -> Unit,
    secureDns: Boolean,
    onToggleSecureDns: (Boolean) -> Unit,
    webrtcRunning: Boolean,
    webrtcIps: List<String>?,
    webrtcTested: Boolean,
    onRunWebrtcTest: () -> Unit,
    webrtcGuardEnabled: Boolean,
    webrtcGuardApplies: Boolean,
    onToggleWebrtcGuard: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    var newHeaderName by remember { mutableStateOf("") }
    var newHeaderValue by remember { mutableStateOf("") }
    var headersExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Guards", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            // ---- Spoof HTTP Headers ----
            item {
                GuardToggleRow(
                    title = "Spoof HTTP Headers",
                    subtitle = "Adds your header list to page navigations and re-fetches page " +
                        "resources with those headers. Hop-by-hop headers (Host, " +
                        "Content-Length, Connection) cannot be overridden.",
                    checked = headerSpoofEnabled,
                    onCheckedChange = onToggleHeaderSpoof
                )
            }
            item {
                OutlinedButton(
                    onClick = { headersExpanded = !headersExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (headersExpanded) "Hide header list (${customHeaders.size})" else "Edit header list (${customHeaders.size})")
                }
            }
            if (headersExpanded) {
                itemsIndexed(customHeaders) { index, h ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(h.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(h.value, fontSize = 12.sp, color = Color.Gray)
                        }
                        IconButton(onClick = { onRemoveHeader(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red)
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newHeaderName,
                            onValueChange = { newHeaderName = it },
                            label = { Text("Header name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = newHeaderValue,
                            onValueChange = { newHeaderValue = it },
                            label = { Text("Value") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (newHeaderName.isNotBlank()) {
                                onAddHeader(newHeaderName.trim(), newHeaderValue)
                                newHeaderName = ""
                                newHeaderValue = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add header")
                    }
                }
            }

            // ---- Fingerprint Protection ----
            item {
                GuardToggleRow(
                    title = "Fingerprint Protection",
                    subtitle = "Poisons canvas + AudioContext fingerprint reads with noise " +
                        "that is re-randomized every app launch. Runs in all browsing modes.",
                    checked = fingerprintProtection,
                    onCheckedChange = onToggleFingerprint
                )
            }

            // ---- Secure DNS ----
            item {
                GuardToggleRow(
                    title = "Secure DNS (app traffic)",
                    subtitle = "Resolves the app's own requests (AI chat, header re-fetch) " +
                        "over DNS-over-HTTPS via Cloudflare. WebView page loads still use " +
                        "your system's DNS — Android WebView has no DoH setting.",
                    checked = secureDns,
                    onCheckedChange = onToggleSecureDns
                )
            }

            // ---- WebRTC Leak Guard (opt-in bundled userscript) ----
            // Userscripts only inject in Developer/Hack modes; in Simple
            // mode the toggle is shown disabled so it never implies
            // protection that isn't happening.
            item {
                GuardToggleRow(
                    title = "WebRTC Leak Guard (opt-in)",
                    subtitle = if (webrtcGuardApplies)
                        "Stops page scripts from creating RTCPeerConnection " +
                            "and from enumerating cameras/microphones. May break " +
                            "legitimate video calls on pages where enabled. Reduces " +
                            "page-JS fingerprinting only — not 100% leak-proof; a " +
                            "system-wide VPN remains the real mitigation. Verify " +
                            "with the leak test below."
                    else
                        "Userscript extensions only run in Developer and Hack " +
                            "modes — switch modes to use this guard. It stays " +
                            "installed but inactive in Simple mode.",
                    checked = webrtcGuardEnabled && webrtcGuardApplies,
                    enabled = webrtcGuardApplies,
                    onCheckedChange = onToggleWebrtcGuard
                )
            }

            // ---- WebRTC Leak Test ----
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("WebRTC Leak Test", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Runs a real STUN check in the current tab and lists the IP " +
                                "addresses a website could discover via WebRTC. This is a " +
                                "test, not a blocker — WebView has no API to disable " +
                                "WebRTC, so a system-wide VPN remains the real fix.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRunWebrtcTest,
                            enabled = !webrtcRunning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (webrtcRunning) "Testing…" else "Run WebRTC leak test")
                        }
                        if (webrtcTested && !webrtcRunning) {
                            Spacer(modifier = Modifier.height(12.dp))
                            if (webrtcIps.isNullOrEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF22C55E)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "No IPs leaked — WebRTC exposed nothing on this page.",
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "These IPs were visible to the page via WebRTC:",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                webrtcIps.forEach { ip ->
                                    Text(
                                        "• $ip",
                                        fontSize = 13.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
