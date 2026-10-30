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
/**
 * Privacy controls with clear behavior: optional DNT/GPC request signals,
 * DNS-over-HTTPS for the app's AI requests, and a WebRTC visibility test.
 * This screen does not change page identity or run user-provided scripts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyGuardsScreen(
    dntEnabled: Boolean,
    onToggleDnt: (Boolean) -> Unit,
    gpcEnabled: Boolean,
    onToggleGpc: (Boolean) -> Unit,
    secureDns: Boolean,
    onToggleSecureDns: (Boolean) -> Unit,
    webrtcRunning: Boolean,
    webrtcIps: List<String>?,
    webrtcTested: Boolean,
    onRunWebrtcTest: () -> Unit,
    onClose: () -> Unit
) {
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
            // ---- Do-Not-Track + Global Privacy Control (opt-in) ----
            item {
                GuardToggleRow(
                    title = "Do-Not-Track (DNT)",
                    subtitle = "Sends DNT: 1 with page requests. Opt-in and off by " +
                        "default. Honest note: this is a voluntary signal — most " +
                        "sites ignore it; it does not force privacy.",
                    checked = dntEnabled,
                    onCheckedChange = onToggleDnt
                )
            }
            item {
                GuardToggleRow(
                    title = "Global Privacy Control (GPC)",
                    subtitle = "Sends Sec-GPC: 1, the legal opt-out signal " +
                        "(e.g. CCPA 'do not sell my data'). Opt-in and off by " +
                        "default. Like DNT, sites choose whether to honor it.",
                    checked = gpcEnabled,
                    onCheckedChange = onToggleGpc
                )
            }

            // ---- Secure DNS ----
            item {
                GuardToggleRow(
                    title = "Secure DNS (app traffic)",
                    subtitle = "Resolves AI chat requests " +
                        "over DNS-over-HTTPS via Cloudflare. WebView page loads still use " +
                        "your system's DNS — Android WebView has no DoH setting.",
                    checked = secureDns,
                    onCheckedChange = onToggleSecureDns
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
