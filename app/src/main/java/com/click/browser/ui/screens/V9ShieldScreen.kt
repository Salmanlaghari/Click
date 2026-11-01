package com.click.browser.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.click.browser.engine.AppSettings
import com.click.browser.engine.V9DohResolver
import com.click.browser.engine.V9Engine
import com.click.browser.engine.V9VpnController
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.dataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * V9 Shield — DNS-only protection and DNS provider settings, plus an overview
 * of the browser-data profiles. It does not tunnel general web traffic.
 *
 * Includes the responsible-use notice: V9 Shield is a privacy tool, not a
 * tool for illegal activity. Click Browser applies no political censorship —
 * protection covers only malware, phishing and (where enabled) adult content.
 */
@Composable
fun V9ShieldScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val vpnOn by V9VpnController.isRunning.collectAsState(initial = false)
    val queries by V9VpnController.queries.collectAsState(initial = 0L)
    val blocked by V9VpnController.blocked.collectAsState(initial = 0L)

    val dohProvider by context.dataStore.data
        .map { it[AppSettings.DOH_PROVIDER] ?: V9DohResolver.PROVIDER_CLOUDFLARE }
        .collectAsState(initial = V9DohResolver.PROVIDER_CLOUDFLARE)
    val customUrl by context.dataStore.data
        .map { it[AppSettings.DOH_CUSTOM_URL].orEmpty() }
        .collectAsState(initial = "")
    val strictDns by context.dataStore.data
        .map { AppSettings.strictDnsEnabled(it) }
        .collectAsState(initial = false)
    var customDraft by remember(customUrl) { mutableStateOf(customUrl) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            V9VpnController.start(context)
            scope.launch {
                context.dataStore.edit { it[AppSettings.V9_VPN_ENABLED] = true }
            }
        }
    }

    fun setVpn(on: Boolean) {
        if (on) {
            val intent = V9VpnController.prepareIntent(context)
            if (intent == null) {
                V9VpnController.start(context)
                scope.launch {
                    context.dataStore.edit { it[AppSettings.V9_VPN_ENABLED] = true }
                }
            } else {
                vpnPermissionLauncher.launch(intent)
            }
        } else {
            V9VpnController.stop(context)
            scope.launch {
                context.dataStore.edit { it[AppSettings.V9_VPN_ENABLED] = false }
            }
        }
    }

    fun setProvider(p: String) {
        scope.launch {
            context.dataStore.edit { it[AppSettings.DOH_PROVIDER] = p }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF6F2E9))
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("V9 Shield", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1F2A24))
                    Text(
                        "DNS protection · Tracker blocking",
                        fontSize = 12.sp, color = Color(0xFF657168)
                    )
                }
                TextButton(onClick = onClose) { Text("Close", color = Color(0xFF2F6B4F)) }
            }
            Spacer(Modifier.height(16.dp))

            // DNS-only protection toggle card.
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFB)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("DNS protection", fontWeight = FontWeight.Bold, color = Color(0xFF1F2A24), fontSize = 15.sp)
                        Text(
                            if (!vpnOn) "OFF" else if (strictDns) {
                                "ON — Strict DNS; DoH failures are blocked"
                            } else {
                                "ON — plain-DNS fallback may be unencrypted"
                            },
                            fontSize = 12.sp,
                            color = if (vpnOn) Color(0xFF2F6B4F) else Color(0xFF657168)
                        )
                    }
                    Switch(
                        checked = vpnOn,
                        onCheckedChange = ::setVpn,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF2F6B4F))
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Stats row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("DNS queries", queries.toString(), Modifier.weight(1f))
                StatCard("Threats blocked", blocked.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))

            Text("DNS behavior", fontWeight = FontWeight.Bold, color = Color(0xFF1F2A24), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFB)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Strict DNS", fontWeight = FontWeight.Bold, color = Color(0xFF1F2A24), fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (strictDns) {
                                "ON — DoH failures return an error; no plain-DNS retry. Lookups may fail."
                            } else {
                                "OFF — DoH failures fall back to plain DNS; those lookups may be unencrypted."
                            },
                            fontSize = 11.5.sp, color = Color(0xFF657168), lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = strictDns,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                context.dataStore.edit { it[AppSettings.STRICT_DNS_ENABLED] = enabled }
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF2F6B4F))
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Text("DNS provider", fontWeight = FontWeight.Bold, color = Color(0xFF1F2A24), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            DnsOption("Cloudflare (1.1.1.1)", dohProvider == V9DohResolver.PROVIDER_CLOUDFLARE) {
                setProvider(V9DohResolver.PROVIDER_CLOUDFLARE)
            }
            DnsOption("Google (8.8.8.8)", dohProvider == V9DohResolver.PROVIDER_GOOGLE) {
                setProvider(V9DohResolver.PROVIDER_GOOGLE)
            }
            DnsOption("Custom DoH URL", dohProvider == V9DohResolver.PROVIDER_CUSTOM) {
                setProvider(V9DohResolver.PROVIDER_CUSTOM)
            }
            if (dohProvider == V9DohResolver.PROVIDER_CUSTOM) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customDraft,
                    onValueChange = { customDraft = it },
                    label = { Text("https://…/dns-query") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2F6B4F),
                        unfocusedBorderColor = Color(0xFFD6D2C7),
                        focusedTextColor = Color(0xFF1F2A24),
                        unfocusedTextColor = Color(0xFF1F2A24),
                    )
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val v = customDraft.trim()
                        scope.launch {
                            context.dataStore.edit { it[AppSettings.DOH_CUSTOM_URL] = v }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F6B4F))
                ) { Text("Save") }
            }
            Text(
                "Provider changes apply to new requests. DNS protection must be ON for device-wide DNS handling.",
                fontSize = 11.sp, color = Color(0xFF657168),
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(20.dp))
            Text("Browser profiles", fontWeight = FontWeight.Bold, color = Color(0xFF1F2A24), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            BrowserMode.values().forEach { mode ->
                val p = V9Engine.profileFor(mode)
                val isBoot = mode == V9Engine.bootMode
                val profileName = when (mode) {
                    BrowserMode.SIMPLE -> "Simple"
                    BrowserMode.DEVELOPER -> "Developer"
                    BrowserMode.HACK -> "Power"
                    BrowserMode.ADVANCED -> "Advance"
                }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBoot) Color(0xFFE8F0E8) else Color(0xFFFFFEFB)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "$profileName profile",
                                fontWeight = FontWeight.Bold, color = Color(0xFF1F2A24), fontSize = 13.sp
                            )
                            Text(p.deviceLabel, fontSize = 11.sp, color = Color(0xFF657168))
                        }
                        if (isBoot) {
                            Text(
                                "ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF2F6B4F),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2F6B4F).copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            // Responsible-use notice (anti-misuse safeguard).
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3D8)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Responsible use",
                        fontWeight = FontWeight.Bold, color = Color(0xFF946200), fontSize = 13.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "V9 Shield is a privacy tool: it encrypts your DNS and blocks " +
                            "trackers, malware and phishing. It is not intended for " +
                            "illegal activity, and it does not hide wrongdoing from " +
                            "the law. Click Browser applies no political censorship — " +
                            "protection covers only malware, phishing and adult " +
                            "content where you enable it.",
                        fontSize = 11.5.sp, color = Color(0xFF574B33), lineHeight = 16.sp
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFB)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1F2A24), fontSize = 18.sp)
            Text(label, fontSize = 11.sp, color = Color(0xFF657168))
        }
    }
}

@Composable
private fun DnsOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF2F6B4F))
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color(0xFF1F2A24), fontSize = 13.sp)
    }
}
