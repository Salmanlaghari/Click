package com.click.browser.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.BuildConfig
import com.click.browser.engine.AiProviders
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.ClickInternalPages
import com.click.browser.engine.ModePersonalization
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.NewsNotifications
import kotlinx.coroutines.launch

private const val PLAY_STORE_PACKAGE = "com.teampkai.clickbrowser"

/**
 * Unified Settings screen — the single home for every app setting.
 *
 * Sections: General / Privacy / Modes / About.
 * Replaces the old fragmented settings (PremiumSettingsScreen). Every
 * toggle and button here is wired to a real action — no dead controls.
 *
 * 4-mode isolation is respected throughout:
 * - history can be cleared per-mode (per-profile DataStore),
 * - cookies / cache clearing applies to the CURRENT mode only (WebView data
 *   directories are pinned per boot mode), and the labels say so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    theme: ModeTheme,
    // --- General ---
    currentThemeSetting: String,
    onThemeChange: (String) -> Unit,
    wallpaperUri: String?,
    onWallpaperChange: (String?) -> Unit,
    activeMode: BrowserMode,
    onModeChange: (BrowserMode) -> Unit,
    currentSearchEngineSetting: String,
    onSearchEngineChange: (String) -> Unit,
    // --- Address bar position ---
    addressBarPosition: String, // "top" | "bottom"
    onAddressBarPositionChange: (String) -> Unit,
    // --- Customizable menu ---
    onCustomizeMenu: () -> Unit,
    // --- Default browser ---
    isDefaultBrowser: Boolean,
    onSetDefaultBrowser: () -> Unit,
    // --- Privacy toggles ---
    adBlockerEnabled: Boolean,
    onToggleAdBlocker: (Boolean) -> Unit,
    forceNightMode: Boolean,
    onToggleNightMode: (Boolean) -> Unit,
    httpsMode: String,
    onHttpsModeChange: (String) -> Unit,
    httpsStrictExceptions: List<String>,
    onAddHttpsException: (String) -> Unit,
    onRemoveHttpsException: (String) -> Unit,
    jsEnabled: Boolean,
    onToggleJs: (Boolean) -> Unit,
    dataSaver: Boolean,
    onToggleDataSaver: (Boolean) -> Unit,
    backgroundAudioEnabled: Boolean,
    onToggleBackgroundAudio: (Boolean) -> Unit,
    // --- Biometric private-tab lock ---
    biometricLockEnabled: Boolean,
    onToggleBiometricLock: (Boolean) -> Unit,
    biometricStatusText: String,
    // --- Text scaling (accessibility) ---
    globalTextScale: Int,
    onGlobalTextScaleChange: (Int) -> Unit,
    // --- Brave-inspired privacy quick wins ---
    stripTrackingParams: Boolean,
    onToggleStripTrackingParams: (Boolean) -> Unit,
    forgetfulBrowsing: Boolean,
    onToggleForgetfulBrowsing: (Boolean) -> Unit,
    blockConsentBanners: Boolean,
    onToggleBlockConsentBanners: (Boolean) -> Unit,
    forgetfulExceptions: Set<String>,
    onForgetfulExceptionsChange: (Set<String>) -> Unit,
    // --- Clear browsing data ---
    onClearHistoryForMode: (BrowserMode) -> Unit,
    onClearCookies: () -> Unit,
    onClearCache: () -> Unit,
    onClearAllCurrentMode: () -> Unit,
    onOpenCookieManager: () -> Unit,
    // --- LocationGuard ---
    locationSummary: String,
    onOpenLocationSettings: () -> Unit,
    // --- Per-mode themes ---
    perModeDark: Map<BrowserMode, Boolean?>,
    onPerModeThemeChange: (BrowserMode, Boolean?) -> Unit,
    // --- UI animations ---
    animationsEnabled: Boolean,
    onToggleAnimations: (Boolean) -> Unit,
    // --- AI Assistant ---
    aiApiKey: String,
    onAiApiKeyChange: (String) -> Unit,
    builtInKeyActive: Boolean = false,
    aiProvider: String,
    onAiProviderChange: (String) -> Unit,
    aiModel: String,
    onAiModelChange: (String) -> Unit,
    // --- News notifications (background alerts, opt-in) ---
    newsNotificationsEnabled: Boolean,
    onToggleNewsNotifications: (Boolean) -> Unit,
    newsTopics: Set<String>,
    onNewsTopicsChange: (Set<String>) -> Unit,
    // --- About ---
    onOpenPrivacyPolicy: () -> Unit,
    onOpenHelpFeedback: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showAiKey by remember { mutableStateOf(false) }
    var historyModeTarget by remember { mutableStateOf(activeMode) }

    // Gallery picker for the custom home-screen wallpaper. OpenDocument gives
    // a persistable URI permission so the wallpaper survives app restarts.
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            onWallpaperChange(uri.toString())
        }
    }

    // ---- Row model: each row optionally opens a named section ----
    data class Row(val section: String?, val content: @Composable () -> Unit)
    val rows = remember(
        theme, currentThemeSetting, wallpaperUri, activeMode,
        currentSearchEngineSetting, addressBarPosition,
        adBlockerEnabled, forceNightMode,
        httpsMode, httpsStrictExceptions, jsEnabled, dataSaver, perModeDark, historyModeTarget,
        aiApiKey, aiProvider, aiModel, showAiKey, animationsEnabled, isDefaultBrowser,
        newsNotificationsEnabled, newsTopics
    ) {
        buildList {
            // ================= GENERAL =================
            add(Row("General") {
                SectionHeader("General", Icons.Default.Tune, theme)
            })
            add(Row(null) {
                CardRow(theme) {
                    Text("Appearance", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Day/Night default for every mode — override per mode in the Modes section.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = currentThemeSetting == "Light",
                            onClick = { onThemeChange("Light") },
                            label = { Text("☀️ Light") }
                        )
                        FilterChip(
                            selected = currentThemeSetting == "Dark",
                            onClick = { onThemeChange("Dark") },
                            label = { Text("🌙 Dark") }
                        )
                    }
                }
            })
            add(Row(null) {
                CardRow(theme) {
                    Text("Text Size (Global Default)", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Default text size for every website — override per site from the Text Size menu. Applies on next page load.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "$globalTextScale%",
                            color = theme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.width(64.dp)
                        )
                        Slider(
                            value = globalTextScale.toFloat(),
                            onValueChange = { onGlobalTextScaleChange(it.toInt()) },
                            valueRange = 50f..300f,
                            steps = 24,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            })
            add(Row(null) {
                CardRow(theme) {
                    Text("Home Wallpaper", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        if (wallpaperUri == null) "No custom wallpaper — using the mode theme background."
                        else "Custom wallpaper set.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { wallpaperPicker.launch(arrayOf("image/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pick image")
                        }
                        if (wallpaperUri != null) {
                            OutlinedButton(
                                onClick = { onWallpaperChange(null) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reset")
                            }
                        }
                    }
                }
            })
            add(Row(null) {
                CardRow(theme) {
                    Text("Default Search Engine", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Applies to ${activeMode.name.lowercase().replaceFirstChar { it.uppercase() }} mode.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    val engines = ModePersonalization.searchEngines(activeMode)
                    engines.forEach { engine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSearchEngineChange(engine) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentSearchEngineSetting == engine,
                                onClick = { onSearchEngineChange(engine) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(engine, color = theme.onSurface, fontSize = 14.sp)
                        }
                    }
                }
            })
            add(Row(null) {
                CardRow(theme) {
                    ToggleRow(
                        "UI Animations",
                        "Tab close & page transition effects",
                        animationsEnabled, onToggleAnimations, theme
                    )
                }
            })
            // ---- Address bar position (Brave/Chrome style) ----
            add(Row(null) {
                CardRow(theme) {
                    Text("Address Bar Position", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Move the URL bar to the top or bottom of the screen — works in all 4 modes.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = addressBarPosition == "top",
                            onClick = { onAddressBarPositionChange("top") },
                            label = { Text("⬆️ Top") }
                        )
                        FilterChip(
                            selected = addressBarPosition == "bottom",
                            onClick = { onAddressBarPositionChange("bottom") },
                            label = { Text("⬇️ Bottom") }
                        )
                    }
                }
            })
            // ---- Customize browser menu ----
            add(Row(null) {
                CardRow(theme) {
                    ActionRow(
                        "Customize Menu",
                        "Reorder & hide items in the browser menu",
                        Icons.Default.Tune,
                        theme,
                        onCustomizeMenu
                    )
                }
            })
            // ---- Default browser ----
            add(Row(null) {
                CardRow(theme) {
                    Text("Default Browser", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        if (isDefaultBrowser) "Click is your default browser ✓ — links from other apps open here."
                        else "Click is not your default browser — links from other apps open elsewhere.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    // No button when already default: a button here would be a
                    // dead control. The status text above is the truth.
                    if (!isDefaultBrowser) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onSetDefaultBrowser,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Set as default")
                        }
                    }
                }
            })
            // ---- AI Assistant (kept from the old settings) ----
            add(Row(null) {
                CardRow(theme) {
                    Text("AI Assistant", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Chat with a real AI using your own key, or leave it empty to use the built-in key.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = aiApiKey,
                        onValueChange = onAiApiKeyChange,
                        label = { Text("AI API Key") },
                        placeholder = { Text(if (builtInKeyActive) "optional — built-in key active" else "Paste your key here") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showAiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showAiKey = !showAiKey }) {
                                Icon(
                                    if (showAiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showAiKey) "Hide key" else "Show key"
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Provider", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    AiProviders.all().forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAiProviderChange(p.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = aiProvider == p.id, onClick = { onAiProviderChange(p.id) })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(p.displayName, color = theme.onSurface, fontSize = 14.sp)
                                Text(
                                    "Keys start with ${p.keyPrefixHint}",
                                    fontSize = 11.sp,
                                    color = theme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                    val providerInfo = AiProviders.byId(aiProvider)
                    OutlinedTextField(
                        value = aiModel,
                        onValueChange = onAiModelChange,
                        label = { Text("Model (blank = ${providerInfo.defaultModel})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            })

            // ---- News Notifications (background alerts, opt-in) ----
            add(Row(null) {
                CardRow(theme) {
                    Text("News Notifications", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "WhatsApp-style alerts for fresh stories in your topics — even with the browser closed. Tapping an alert opens the article in a new tab.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(4.dp))
                    ToggleRow(
                        "Breaking news alerts",
                        "Off by default — enable to opt in",
                        newsNotificationsEnabled,
                        onToggleNewsNotifications,
                        theme
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Topics", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    // Multi-select topic chips (same FilterChip pattern as the theme picker).
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NewsNotifications.TOPIC_CHOICES.forEach { (name, label) ->
                            FilterChip(
                                selected = newsTopics.contains(name),
                                onClick = {
                                    val updated = if (newsTopics.contains(name)) newsTopics - name else newsTopics + name
                                    onNewsTopicsChange(updated)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Checks every ~2 hours on Wi-Fi/mobile data. Battery-friendly: no exact alarms, no background service — checks are skipped when the battery is low.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                }
            })

            // ================= PRIVACY =================
            add(Row("Privacy") {
                SectionHeader("Privacy & Security", Icons.Default.Shield, theme)
            })
            add(Row(null) {
                CardRow(theme) {
                    ToggleRow("Ad Blocker", "Block intrusive ads & tracker scripts", adBlockerEnabled, onToggleAdBlocker, theme)
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ToggleRow("Force Night Mode", "Inject night theme on web pages", forceNightMode, onToggleNightMode, theme)
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    // ---- HTTPS mode: Off / Standard / Strict ----
                    Text(
                        "HTTPS Mode",
                        color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                    Text(
                        "Standard upgrades http to https. Strict BLOCKS plain-http pages " +
                            "(per-site exceptions below). Off allows http.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    ModeOptionRow("off", "Off", "Allow plain-http pages (least safe)",
                        selected = httpsMode == "off",
                        onSelect = { onHttpsModeChange("off") }, theme)
                    ModeOptionRow("standard", "Standard", "Upgrade http to https automatically",
                        selected = httpsMode == "standard",
                        onSelect = { onHttpsModeChange("standard") }, theme)
                    ModeOptionRow("strict", "Strict", "Block plain-http pages entirely",
                        selected = httpsMode == "strict",
                        onSelect = { onHttpsModeChange("strict") }, theme)
                    // ---- HTTPS-Strict per-site exceptions ----
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
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ToggleRow("JavaScript", "Enable core scripting execution", jsEnabled, onToggleJs, theme)
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ToggleRow("Data Saver", "Reduce web resource overhead", dataSaver, onToggleDataSaver, theme)
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ToggleRow("Lock Private Tabs", biometricStatusText, biometricLockEnabled, onToggleBiometricLock, theme)
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ToggleRow(
                        "Background audio",
                        "Let web-page audio keep playing when the app is in the background",
                        backgroundAudioEnabled, onToggleBackgroundAudio, theme
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ToggleRow(
                        "Strip Tracking Links",
                        "Auto-remove utm_*, gclid, fbclid & other trackers from URLs",
                        stripTrackingParams, onToggleStripTrackingParams, theme
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ToggleRow(
                        "Forgetful Browsing",
                        "Wipe a site's cookies & data when its last tab closes",
                        forgetfulBrowsing, onToggleForgetfulBrowsing, theme
                    )
                    if (forgetfulBrowsing) {
                        ForgetfulExceptionsRow(
                            exceptions = forgetfulExceptions,
                            onExceptionsChange = onForgetfulExceptionsChange,
                            theme = theme
                        )
                    }
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ToggleRow(
                        "Block Cookie Banners",
                        "Auto-hide cookie-consent / GDPR popups",
                        blockConsentBanners, onToggleBlockConsentBanners, theme
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    // LocationGuard: hide/spoof browser geolocation.
                    ActionRow(
                        "Location",
                        "Websites will see: $locationSummary",
                        Icons.Default.LocationOn,
                        theme,
                        onOpenLocationSettings
                    )
                }
            })
            add(Row(null) {
                CardRow(theme) {
                    Text("Clear Browsing Data", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Cookies & cache clear for the CURRENT mode (${activeMode.name.lowercase().replaceFirstChar { it.uppercase() }}) — each mode has its own isolated storage.",
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
                        BrowserMode.values().forEach { mode ->
                            FilterChip(
                                selected = historyModeTarget == mode,
                                onClick = { historyModeTarget = mode },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }
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
                        Text("Clear ${historyModeTarget.name.lowercase().replaceFirstChar { it.uppercase() }} history")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onClearAllCurrentMode,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear ALL data (current mode)", color = MaterialTheme.colorScheme.onError)
                    }
                }
            })
            add(Row(null) {
                CardRow(theme) {
                    ActionRow("Cookie Manager", "Inspect & delete cookies per site", Icons.Default.Cookie, theme, onOpenCookieManager)
                }
            })

            // ================= MODES =================
            add(Row("Modes") {
                SectionHeader("Modes", Icons.Default.Apps, theme)
            })
            add(Row(null) {
                CardRow(theme) {
                    Text(
                        "Each mode is fully isolated — own cookies, cache, history, bookmarks, passwords. Switching restarts the app into that engine.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    BrowserMode.values().forEach { mode ->
                        val isActive = mode == activeMode
                        val override = perModeDark[mode]
                        val effective = override ?: (currentThemeSetting == "Dark")
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) theme.primary.copy(alpha = 0.12f)
                                else theme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        mode.name.lowercase().replaceFirstChar { it.uppercase() } +
                                            if (isActive) " • active" else "",
                                        color = theme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    if (!isActive) {
                                        TextButton(onClick = { onModeChange(mode) }) {
                                            Text("Switch")
                                        }
                                    }
                                }
                                Text(
                                    "Theme: ${if (effective) "Dark" else "Light"}${if (override == null) " (global)" else ""}",
                                    fontSize = 11.sp,
                                    color = theme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = effective && override != null,
                                        onClick = { onPerModeThemeChange(mode, true) },
                                        label = { Text("🌙 Dark", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = !effective && override != null,
                                        onClick = { onPerModeThemeChange(mode, false) },
                                        label = { Text("☀️ Light", fontSize = 11.sp) }
                                    )
                                    if (override != null) {
                                        FilterChip(
                                            selected = false,
                                            onClick = { onPerModeThemeChange(mode, null) },
                                            label = { Text("↩ Global", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            })

            // ================= ABOUT =================
            add(Row("About") {
                SectionHeader("About", Icons.Default.Info, theme)
            })
            add(Row(null) {
                CardRow(theme) {
                    InfoLine("App version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", theme)
                    InfoLine("V9 engine", "Chromium WebView · 4 isolated profiles", theme)
                    InfoLine("Features", "${ClickInternalPages.FEATURE_COUNT} built-in", theme)
                    InfoLine("Developer", "TEAM PK AI", theme)
                }
            })
            add(Row(null) {
                CardRow(theme) {
                    ActionRow("Privacy Policy", "How Click handles your data", Icons.Default.Description, theme, onOpenPrivacyPolicy)
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ActionRow("Help & Feedback", "FAQs + email the developer", Icons.Default.Help, theme, onOpenHelpFeedback)
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ActionRow("Rate Click", "Rate us on Google Play", Icons.Default.Star, theme) {
                        try {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=$PLAY_STORE_PACKAGE")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=$PLAY_STORE_PACKAGE")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ActionRow("Share Click", "Tell a friend about Click Browser", Icons.Default.Share, theme) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Try Click Browser — 4 isolated modes, ad blocker & AI chat: " +
                                    "https://play.google.com/store/apps/details?id=$PLAY_STORE_PACKAGE"
                            )
                        }
                        context.startActivity(Intent.createChooser(send, "Share Click Browser"))
                    }
                }
            })
        }
    }

    val headerIndex = remember(rows) {
        buildMap {
            rows.forEachIndexed { i, row -> row.section?.let { put(it, i) } }
        }
    }
    val sections = listOf("General", "Privacy", "Modes", "About")
    var activeSection by remember { mutableStateOf("General") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Section quick-nav chips
            ScrollableTabRow(
                selectedTabIndex = sections.indexOf(activeSection),
                containerColor = theme.background,
                contentColor = theme.primary,
                edgePadding = 16.dp
            ) {
                sections.forEach { s ->
                    Tab(
                        selected = activeSection == s,
                        onClick = {
                            activeSection = s
                            headerIndex[s]?.let { idx ->
                                scope.launch { listState.animateScrollToItem(idx) }
                            }
                        },
                        text = { Text(s, fontSize = 13.sp) }
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(rows.size) { i -> rows[i].content() }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector, theme: ModeTheme) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = theme.primary)
    }
}

@Composable
private fun CardRow(theme: ModeTheme, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun ModeOptionRow(
    value: String,
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
private fun ToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    theme: ModeTheme
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(desc, fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(
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
        Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(desc, fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun InfoLine(label: String, value: String, theme: ModeTheme) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = theme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
        Text(value, color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
