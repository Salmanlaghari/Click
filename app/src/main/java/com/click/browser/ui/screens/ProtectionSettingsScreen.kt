package com.click.browser.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.AdBlocker
import com.click.browser.engine.BrowserSetting
import com.click.browser.engine.BrowserSettingCapabilityPolicy
import com.click.browser.engine.BrowserTabEngine
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.ProtectionPolicy

/**
 * Protection — Click's tracker/ad protection controls. Every control here
 * is wired to a real enforcement point:
 *  - Protection level + fingerprinting: the Gecko engine's live
 *    content-blocking settings (anti-tracking categories + ETP level) via
 *    ProtectionRuntime, and Click's own filter lists in WebView tabs
 *    (Off stops interception, Strong uses the aggressive list).
 *  - Block third-party cookies: Gecko cookie behavior + the WebView
 *    per-WebView cookie flag (per-site overrides still win in WebView).
 *  - HTTPS mode: Click's navigation-level upgrade/block enforcement.
 *  - JavaScript: the existing global WebView setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionSettingsScreen(
    theme: ModeTheme,
    protectionLevel: String,
    onProtectionLevelChange: (String) -> Unit,
    fingerprintingProtection: Boolean,
    onToggleFingerprintingProtection: (Boolean) -> Unit,
    blockThirdPartyCookies: Boolean,
    onToggleBlockThirdPartyCookies: (Boolean) -> Unit,
    httpsMode: String,
    onHttpsModeChange: (String) -> Unit,
    httpsStrictExceptions: List<String>,
    onAddHttpsException: (String) -> Unit,
    onRemoveHttpsException: (String) -> Unit,
    jsEnabled: Boolean,
    onToggleJs: (Boolean) -> Unit,
    currentTabEngine: BrowserTabEngine,
    onClose: () -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    val blockedThisSession by AdBlocker.blockedCountFlow.collectAsState()
    val javaScriptAvailability = BrowserSettingCapabilityPolicy.availability(
        BrowserSetting.JAVASCRIPT, currentTabEngine
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Protection", fontWeight = FontWeight.Bold) },
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            item {
                ProtectionCard(theme) {
                    Text("Trackers & Ads", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "How strongly Click blocks trackers and ads. Engine tabs use " +
                            "tracking-protection categories; WebView tabs use Click's filter lists.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    ProtectionOptionRow(
                        title = "Standard",
                        desc = "Block known ad, analytics and social trackers",
                        selected = protectionLevel == ProtectionPolicy.LEVEL_STANDARD,
                        onSelect = { onProtectionLevelChange(ProtectionPolicy.LEVEL_STANDARD) },
                        theme = theme
                    )
                    ProtectionOptionRow(
                        title = "Strong",
                        desc = "Also block content trackers, cryptominers and more — some sites may break",
                        selected = protectionLevel == ProtectionPolicy.LEVEL_STRONG,
                        onSelect = { onProtectionLevelChange(ProtectionPolicy.LEVEL_STRONG) },
                        theme = theme
                    )
                    ProtectionOptionRow(
                        title = "Off",
                        desc = "No tracker or ad blocking, except sites you set to always block",
                        selected = protectionLevel == ProtectionPolicy.LEVEL_OFF,
                        onSelect = { onProtectionLevelChange(ProtectionPolicy.LEVEL_OFF) },
                        theme = theme
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Blocked this session: $blockedThisSession",
                        fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            item {
                ProtectionCard(theme) {
                    ProtectionToggleRow(
                        title = "Fingerprinting protection",
                        desc = "Block known fingerprinting scripts in engine tabs, on top of the level above",
                        checked = fingerprintingProtection,
                        onCheckedChange = onToggleFingerprintingProtection,
                        theme = theme
                    )
                }
            }
            item {
                ProtectionCard(theme) {
                    Text("Cookies", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    ProtectionToggleRow(
                        title = "Block third-party cookies",
                        desc = "Only cookies from the site you are visiting are allowed. " +
                            "Per-site choices in Site settings still win in WebView tabs.",
                        checked = blockThirdPartyCookies,
                        onCheckedChange = onToggleBlockThirdPartyCookies,
                        theme = theme
                    )
                }
            }
            item {
                ProtectionCard(theme) {
                    Text("HTTPS Upgrades", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Standard upgrades http to https. Strict BLOCKS plain-http pages " +
                            "(per-site exceptions below). Off allows http.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    ProtectionOptionRow(
                        title = "Off",
                        desc = "Allow plain-http pages (least safe)",
                        selected = httpsMode == "off",
                        onSelect = { onHttpsModeChange("off") },
                        theme = theme
                    )
                    ProtectionOptionRow(
                        title = "Standard",
                        desc = "Upgrade http to https automatically",
                        selected = httpsMode == "standard",
                        onSelect = { onHttpsModeChange("standard") },
                        theme = theme
                    )
                    ProtectionOptionRow(
                        title = "Strict",
                        desc = "Block plain-http pages entirely",
                        selected = httpsMode == "strict",
                        onSelect = { onHttpsModeChange("strict") },
                        theme = theme
                    )
                    if (httpsMode == "strict") {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Strict exceptions",
                            color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                        )
                        Text(
                            "These sites may load over plain http even in Strict mode.",
                            fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(4.dp))
                        var newException by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newException,
                                onValueChange = { newException = it },
                                placeholder = { Text("example.com", fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                            Button(onClick = {
                                onAddHttpsException(newException)
                                newException = ""
                            }) { Text("Add") }
                        }
                        Spacer(Modifier.height(4.dp))
                        if (httpsStrictExceptions.isEmpty()) {
                            Text(
                                "No exceptions yet.",
                                fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.5f)
                            )
                        } else {
                            httpsStrictExceptions.forEach { host ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        host, fontSize = 13.sp,
                                        color = theme.onSurface, modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { onRemoveHttpsException(host) }) {
                                        Text("Remove", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                ProtectionCard(theme) {
                    Text("Scripts", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    ProtectionToggleRow(
                        title = "Block scripts (JavaScript off)",
                        desc = javaScriptAvailability.description,
                        checked = !jsEnabled,
                        onCheckedChange = { blocked -> onToggleJs(!blocked) },
                        theme = theme,
                        enabled = javaScriptAvailability.enabled
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtectionCard(theme: ModeTheme, content: @Composable ColumnScope.() -> Unit) {
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
private fun ProtectionOptionRow(
    title: String,
    desc: String,
    selected: Boolean,
    onSelect: () -> Unit,
    theme: ModeTheme
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = theme.onSurface, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun ProtectionToggleRow(
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
