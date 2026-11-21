package com.click.browser.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.ModeTheme

/**
 * Privacy & Safety — Safe Browsing, tracking signals, browsing-data
 * controls and entries into the existing privacy/security screens.
 *
 * Enforcement notes (no fake controls):
 *  - Safe Browsing: Gecko content-blocking safe-browsing categories in
 *    engine tabs + the system Safe Browsing setting in WebView tabs.
 *  - Do Not Track: sends the DNT signal on WebView navigations (sites may
 *    ignore it — the description says so).
 *  - Clear data on exit: MainActivity clears the current profile's
 *    history, cookies and engine site data when the app is closed.
 *  - Clear browsing data: the same per-profile actions that lived in the
 *    old Settings "Safety & DNS" section, unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySafetySettingsScreen(
    theme: ModeTheme,
    activeMode: BrowserMode,
    safeBrowsingEnabled: Boolean,
    onToggleSafeBrowsing: (Boolean) -> Unit,
    dntEnabled: Boolean,
    onToggleDnt: (Boolean) -> Unit,
    stripTrackingParams: Boolean,
    onToggleStripTrackingParams: (Boolean) -> Unit,
    forgetfulBrowsing: Boolean,
    onToggleForgetfulBrowsing: (Boolean) -> Unit,
    forgetfulExceptions: Set<String>,
    onForgetfulExceptionsChange: (Set<String>) -> Unit,
    biometricLockEnabled: Boolean,
    onToggleBiometricLock: (Boolean) -> Unit,
    biometricStatusText: String,
    locationSummary: String,
    onOpenLocationSettings: () -> Unit,
    clearOnExitEnabled: Boolean,
    onToggleClearOnExit: (Boolean) -> Unit,
    onClearHistoryForMode: (BrowserMode) -> Unit,
    onClearCookies: () -> Unit,
    onClearCache: () -> Unit,
    onClearAllCurrentMode: () -> Unit,
    onOpenCookieManager: () -> Unit,
    onOpenSiteSettings: () -> Unit,
    onOpenPrivacySecurity: () -> Unit,
    onOpenAdvancedSecurity: () -> Unit,
    onOpenV9Shield: () -> Unit,
    onOpenPrivacyGuards: () -> Unit,
    onClose: () -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    var historyModeTarget by remember { mutableStateOf(activeMode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Safety", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background,
                    titleContentColor = theme.onSurface
                )
            )
        },
        containerColor = theme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                PrivacyCard(theme) {
                    Text("Safety", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    PrivacyToggleRow(
                        title = "Safe Browsing",
                        desc = "Warn before malicious, deceptive or harmful sites load — " +
                            "engine harmful-site lists in engine tabs, the system Safe Browsing " +
                            "service in WebView tabs",
                        checked = safeBrowsingEnabled,
                        onCheckedChange = onToggleSafeBrowsing,
                        theme = theme
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    PrivacyActionRow(
                        "V9 Shield",
                        "DNS protection only · not a VPN",
                        Icons.Default.Shield,
                        theme,
                        onOpenV9Shield
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    PrivacyActionRow(
                        "Advanced Security",
                        "Safety Check, site settings and app integrity",
                        Icons.Default.Shield,
                        theme,
                        onOpenAdvancedSecurity
                    )
                }
            }
            item {
                PrivacyCard(theme) {
                    Text("Privacy", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    PrivacyToggleRow(
                        title = "Do Not Track",
                        desc = "Send a Do Not Track signal with page loads in WebView tabs. " +
                            "Sites may ignore it.",
                        checked = dntEnabled,
                        onCheckedChange = onToggleDnt,
                        theme = theme
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    PrivacyToggleRow(
                        title = "Strip Tracking Links",
                        desc = "Auto-remove utm_*, gclid, fbclid & other trackers from URLs",
                        checked = stripTrackingParams,
                        onCheckedChange = onToggleStripTrackingParams,
                        theme = theme
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    PrivacyToggleRow(
                        title = "Forgetful Browsing",
                        desc = "Wipe a site's cookies & data when its last tab closes",
                        checked = forgetfulBrowsing,
                        onCheckedChange = onToggleForgetfulBrowsing,
                        theme = theme
                    )
                    if (forgetfulBrowsing) {
                        ForgetfulExceptionsRow(
                            exceptions = forgetfulExceptions,
                            onExceptionsChange = onForgetfulExceptionsChange,
                            theme = theme
                        )
                    }
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    PrivacyToggleRow(
                        title = "Lock Private Tabs",
                        desc = biometricStatusText,
                        checked = biometricLockEnabled,
                        onCheckedChange = onToggleBiometricLock,
                        theme = theme
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    // Location permission behavior is transparent and never fabricates coordinates.
                    PrivacyActionRow(
                        "Location",
                        "Websites will see: $locationSummary",
                        Icons.Default.LocationOn,
                        theme,
                        onOpenLocationSettings
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    PrivacyActionRow(
                        "Privacy Guards",
                        "Do Not Track & Global Privacy Control signals, secure DNS, WebRTC",
                        Icons.Default.VerifiedUser,
                        theme,
                        onOpenPrivacyGuards
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    PrivacyActionRow(
                        "Site settings",
                        "Review & reset per-site choices for all websites",
                        Icons.Default.Shield,
                        theme,
                        onOpenSiteSettings
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    PrivacyActionRow(
                        "Privacy & Security",
                        "Protection status, site controls, private browsing, accounts and passwords",
                        Icons.Default.VerifiedUser,
                        theme,
                        onOpenPrivacySecurity
                    )
                }
            }
            item {
                PrivacyCard(theme) {
                    Text("Browsing Data", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    PrivacyToggleRow(
                        title = "Clear data on exit",
                        desc = "When you close Click, clear this profile's history, cookies " +
                            "and site data automatically",
                        checked = clearOnExitEnabled,
                        onCheckedChange = onToggleClearOnExit,
                        theme = theme
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    Text(
                        "WebView cookies and cache are separate per profile. History is shared by Simple, Developer and Power; Advance has separate app data.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onClearCookies, modifier = Modifier.weight(1f)) { Text("Cookies") }
                        OutlinedButton(onClick = onClearCache, modifier = Modifier.weight(1f)) { Text("Cache") }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Per-mode history clearing (DataStore-backed, safe for any mode).
                    Text("Clear history for:", color = theme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(BrowserMode.SIMPLE, BrowserMode.ADVANCED).forEach { mode ->
                            val isShared = mode != BrowserMode.ADVANCED
                            FilterChip(
                                selected = if (isShared) historyModeTarget != BrowserMode.ADVANCED
                                    else historyModeTarget == BrowserMode.ADVANCED,
                                onClick = { historyModeTarget = if (isShared) BrowserMode.SIMPLE else BrowserMode.ADVANCED },
                                label = {
                                    Text(
                                        if (isShared) "Simple / Developer / Power" else "Advance",
                                        fontSize = 11.sp
                                    )
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onClearHistoryForMode(historyModeTarget) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (historyModeTarget == BrowserMode.ADVANCED) "Clear Advance history" else "Clear shared-profile history")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onClearAllCurrentMode,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear cookies, cache & history", color = MaterialTheme.colorScheme.onError)
                    }
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    PrivacyActionRow("Cookie Manager", "Inspect & delete cookies per site", Icons.Default.Cookie, theme, onOpenCookieManager)
                }
            }
        }
    }
}

@Composable
private fun PrivacyCard(theme: ModeTheme, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun PrivacyToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    theme: ModeTheme,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = theme.onSurface.copy(alpha = if (enabled) 1f else 0.62f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                desc,
                fontSize = 11.sp,
                color = theme.onSurface.copy(alpha = if (enabled) 0.6f else 0.55f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun PrivacyActionRow(
    title: String,
    desc: String,
    icon: ImageVector,
    theme: ModeTheme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(desc, fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
    }
}

/**
 * Forgetful Browsing per-site exceptions: sites whose cookies/data are kept
 * even when their last tab closes. Add/remove hosts; input is normalized
 * ("https://Example.com/" -> "example.com") and invalid hosts are rejected.
 */
@Composable
private fun ForgetfulExceptionsRow(
    exceptions: Set<String>,
    onExceptionsChange: (Set<String>) -> Unit,
    theme: ModeTheme
) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Shield, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Forgetful exceptions", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                if (exceptions.isEmpty()) "No exceptions — every site is forgotten"
                else "${exceptions.size} site(s) always remembered",
                fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
    }
    if (showDialog) {
        var input by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Remember these sites", color = theme.onSurface) },
            text = {
                Column {
                    Text(
                        "Excepted sites keep their cookies & data when tabs close.",
                        fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it; error = null },
                            label = { Text("example.com") },
                            singleLine = true,
                            isError = error != null,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val host = com.click.browser.engine.ForgetfulBrowsing.normalizeException(input)
                            if (host == null) {
                                error = "Not a valid host"
                            } else {
                                onExceptionsChange(exceptions + host)
                                input = ""
                            }
                        }) { Text("Add") }
                    }
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (exceptions.isEmpty()) {
                        Text("No exceptions yet.", fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f))
                    } else {
                        exceptions.sorted().forEach { host ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(host, modifier = Modifier.weight(1f), fontSize = 14.sp, color = theme.onSurface)
                                IconButton(onClick = { onExceptionsChange(exceptions - host) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove $host", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Done") }
            }
        )
    }
}
