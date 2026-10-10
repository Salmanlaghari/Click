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
 * V9 Shield — built-in VPN (encrypted DNS + DNS threat blocking), DNS
 * provider settings, and the 3-engine identity overview.
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
                .background(Color(0xFF0A0A0F))
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
                    Text("V9 Shield", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(
                        "Encrypted DNS · Tracker blocking",
                        fontSize = 12.sp, color = Color(0xFF8E8EA3)
                    )
                }
                TextButton(onClick = onClose) { Text("Close", color = Color(0xFF7C6CFF)) }
            }
            Spacer(Modifier.height(16.dp))

            // VPN toggle card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14141F)),
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
                        Text("Shield VPN", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                        Text(
                            if (vpnOn) "ON — DNS is encrypted" else "OFF",
                            fontSize = 12.sp,
                            color = if (vpnOn) Color(0xFF34D399) else Color(0xFF8E8EA3)
                        )
                    }
                    Switch(
                        checked = vpnOn,
                        onCheckedChange = ::setVpn,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF7C6CFF))
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

            Text("DNS provider", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
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
                        focusedBorderColor = Color(0xFF7C6CFF),
                        unfocusedBorderColor = Color(0xFF2A2A3E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C6CFF))
                ) { Text("Save") }
            }
            Text(
                "Changing DNS applies to new connections. The Shield VPN must be ON for device-wide encrypted DNS.",
                fontSize = 11.sp, color = Color(0xFF8E8EA3),
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(20.dp))
            Text("V9 Engines — 1 Browser, 4 Engines", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            BrowserMode.values().forEach { mode ->
                val p = V9Engine.profileFor(mode)
                val isBoot = mode == V9Engine.bootMode
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBoot) Color(0xFF1B2340) else Color(0xFF14141F)
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
                                mode.name.lowercase().replaceFirstChar { it.uppercase() } + " Engine",
                                fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp
                            )
                            Text(p.deviceLabel, fontSize = 11.sp, color = Color(0xFF8E8EA3))
                        }
                        if (isBoot) {
                            Text(
                                "ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF34D399),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF34D399).copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            // Responsible-use notice (anti-misuse safeguard).
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1408)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Responsible use",
                        fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24), fontSize = 13.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "V9 Shield is a privacy tool: it encrypts your DNS and blocks " +
                            "trackers, malware and phishing. It is not intended for " +
                            "illegal activity, and it does not hide wrongdoing from " +
                            "the law. Click Browser applies no political censorship — " +
                            "protection covers only malware, phishing and adult " +
                            "content where you enable it.",
                        fontSize = 11.5.sp, color = Color(0xFFD6C9A8), lineHeight = 16.sp
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141F)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 18.sp)
            Text(label, fontSize = 11.sp, color = Color(0xFF8E8EA3))
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
            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7C6CFF))
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}
