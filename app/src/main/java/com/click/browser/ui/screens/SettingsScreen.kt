package com.click.browser.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import com.click.browser.engine.BrowserSetting
import com.click.browser.engine.BrowserSettingCapabilityPolicy
import com.click.browser.engine.BrowserTabEngine
import com.click.browser.engine.LightThemeVariant
import com.click.browser.engine.ModePersonalization
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.NewsNotifications
import com.click.browser.engine.ProtectionPolicy
import com.click.browser.engine.SafetyCheck
import com.click.browser.engine.ThemeController

private const val PLAY_STORE_PACKAGE = "com.teampkai.clickbrowser"

/**
 * Unified Settings screen — the single home for every app setting.
 *
 * Section-wise structure (Nov 2026): the home page lists General /
 * Protection / Privacy & Safety / Appearance / Modes / Accessibility /
 * About with icons. General, Appearance, Modes and About render as pages
 * inside this screen; Protection and Privacy & Safety are their own
 * screens (ProtectionSettingsScreen / PrivacySafetySettingsScreen) opened
 * through [onOpenProtection] / [onOpenPrivacySafety], and Accessibility
 * keeps its existing dedicated screen. Every toggle and button here is
 * wired to a real action — no dead controls.
 *
 * WebView cookies/cache are separate per profile. Simple, Developer and Power
 * share app-level history/bookmarks/passwords; Advance has a separate app-data store.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    theme: ModeTheme,
    lightThemeVariant: LightThemeVariant,
    onLightThemeVariantChange: (LightThemeVariant) -> Unit,
    autoRotateThemes: Boolean,
    onToggleAutoRotateThemes: (Boolean) -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    // --- General ---
    wallpaperUri: String?,
    onWallpaperChange: (String?) -> Unit,
    activeMode: BrowserMode,
    onModeChange: (BrowserMode) -> Unit,
    currentSearchEngineSetting: String,
    onSearchEngineChange: (String) -> Unit,
    // --- Browser suite (tabs, accessibility, language, downloads) ---
    onOpenTabGroups: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    // --- Protection + Privacy & Safety (dedicated screens) ---
    protectionLevel: String,
    onOpenProtection: () -> Unit,
    onOpenPrivacySafety: () -> Unit,
    // --- Reports ---
    onOpenReports: () -> Unit,
    onOpenCatLog: () -> Unit,
    // --- Address bar position ---
    addressBarPosition: String, // "top" | "bottom"
    onAddressBarPositionChange: (String) -> Unit,
    // --- Customizable menu ---
    onCustomizeMenu: () -> Unit,
    // --- Default browser ---
    isDefaultBrowser: Boolean,
    onSetDefaultBrowser: () -> Unit,
    // --- Browsing toggles kept on the General page ---
    dataSaver: Boolean,
    onToggleDataSaver: (Boolean) -> Unit,
    currentTabEngine: BrowserTabEngine,
    backgroundAudioEnabled: Boolean,
    onToggleBackgroundAudio: (Boolean) -> Unit,
    // --- Text scaling (accessibility) ---
    globalTextScale: Int,
    onGlobalTextScaleChange: (Int) -> Unit,
    // --- UI animations ---
    animationsEnabled: Boolean,
    onToggleAnimations: (Boolean) -> Unit,
    // --- Sharp View (full-density rendering) ---
    sharpViewEnabled: Boolean,
    onToggleSharpView: (Boolean) -> Unit,
    // --- Advance Mode intro animation ---
    advanceIntroEnabled: Boolean,
    onToggleAdvanceIntro: (Boolean) -> Unit,
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
    onOpenLicenses: () -> Unit,
    onOpenHelpFeedback: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val dataSaverAvailability = BrowserSettingCapabilityPolicy.availability(
        BrowserSetting.DATA_SAVER, currentTabEngine
    )
    val textSizeAvailability = BrowserSettingCapabilityPolicy.availability(
        BrowserSetting.TEXT_SIZE, currentTabEngine
    )
    val sharpViewAvailability = BrowserSettingCapabilityPolicy.availability(
        BrowserSetting.SHARP_VIEW, currentTabEngine
    )

    var showAiKey by remember { mutableStateOf(false) }
    // Internal page: "home" lists the sections; the other values render the
    // matching rows below. Protection / Privacy & Safety / Accessibility
    // open their own screens instead.
    var page by remember { mutableStateOf("home") }
    val goBack: () -> Unit = { if (page != "home") page = "home" else onClose() }
    androidx.activity.compose.BackHandler(onBack = goBack)

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

    // ---- Row model: every row is tagged with the page it belongs to ----
    data class Row(val section: String, val content: @Composable () -> Unit)
    val rows = remember(
        theme, lightThemeVariant, autoRotateThemes, themeMode, wallpaperUri, activeMode,
        currentSearchEngineSetting, addressBarPosition,
        dataSaver, currentTabEngine, backgroundAudioEnabled,
        aiApiKey, aiProvider, aiModel, showAiKey, animationsEnabled, isDefaultBrowser, advanceIntroEnabled,
        sharpViewEnabled,
        newsNotificationsEnabled, newsTopics
    ) {
        buildList {
            // ================= GENERAL =================
            add(Row("General") {
                SectionHeader("General", Icons.Default.Tune, theme)
            })
            add(Row("General") {
                CardRow(theme) {
                    Text("Default Search Engine", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Applies to the ${activeMode.display().title} profile.",
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
            add(Row("General") {
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
            add(Row("General") {
                CardRow(theme) {
                    ToggleRow(
                        title = "Data Saver",
                        desc = dataSaverAvailability.description,
                        checked = dataSaver,
                        onCheckedChange = onToggleDataSaver,
                        theme = theme,
                        enabled = dataSaverAvailability.enabled
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ToggleRow(
                        "Background audio",
                        "Let web-page audio keep playing when the app is in the background",
                        backgroundAudioEnabled, onToggleBackgroundAudio, theme
                    )
                }
            })
            add(Row("General") {
                CardRow(theme) {
                    ActionRow("Tab Groups", "Named, color-coded tab collections", Icons.Default.FolderCopy, theme, onOpenTabGroups)
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ActionRow("Language", "App language + on-device page translate", Icons.Default.Translate, theme, onOpenLanguage)
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ActionRow("Downloads", "Download location — phone storage by default", Icons.Default.Download, theme, onOpenDownloadSettings)
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ActionRow(
                        "Customize Menu",
                        "Reorder & hide items in the browser menu",
                        Icons.Default.Tune,
                        theme,
                        onCustomizeMenu
                    )
                }
            })
            // ---- AI Assistant (kept from the old settings) ----
            add(Row("General") {
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
            add(Row("General") {
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

            // ================= APPEARANCE =================
            add(Row("Appearance") {
                SectionHeader("Appearance", Icons.Default.Palette, theme)
            })
            add(Row("Appearance") {
                CardRow(theme) {
                    Text("Appearance", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Theme applies to the app in every profile. Each mode keeps its own colours in light and dark.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Theme", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThemeController.MODE_LIGHT,
                            ThemeController.MODE_DARK,
                            ThemeController.MODE_AUTO
                        ).forEach { mode ->
                            FilterChip(
                                selected = ThemeController.normalize(themeMode) == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(ThemeController.displayName(mode), fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Palette (light theme)", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LightThemeVariant.values().toList().chunked(2).forEach { variants ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                variants.forEach { variant ->
                                    FilterChip(
                                        selected = lightThemeVariant == variant,
                                        onClick = { onLightThemeVariantChange(variant) },
                                        label = { Text(variant.label, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ToggleRow(
                        "Rotate theme on launch",
                        "Advance to the next light palette each time Click launches",
                        autoRotateThemes,
                        onToggleAutoRotateThemes,
                        theme
                    )
                }
            })
            add(Row("Appearance") {
                CardRow(theme) {
                    Text("Text Size (Global Default)", color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        if (textSizeAvailability.enabled) {
                            "Default text size for every website — override per site from the Text Size menu. Applies on next page load."
                        } else {
                            textSizeAvailability.description
                        },
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
                            enabled = textSizeAvailability.enabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            })
            add(Row("Appearance") {
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
            add(Row("Appearance") {
                CardRow(theme) {
                    ToggleRow(
                        "UI Animations",
                        "Tab close & page transition effects",
                        animationsEnabled, onToggleAnimations, theme
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ToggleRow(
                        "Advance intro animation",
                        "5-second intro when entering Advance mode (tap skips)",
                        advanceIntroEnabled, onToggleAdvanceIntro, theme
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ToggleRow(
                        title = "Sharp View",
                        desc = if (sharpViewAvailability.enabled) {
                            "Full-density page rendering for crisp text & images (applies on next page load)"
                        } else {
                            sharpViewAvailability.description
                        },
                        checked = sharpViewEnabled,
                        onCheckedChange = onToggleSharpView,
                        theme = theme,
                        enabled = sharpViewAvailability.enabled
                    )
                }
            })
            // ---- Address bar position ----
            add(Row("Appearance") {
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

            // ================= MODES =================
            add(Row("Modes") {
                SectionHeader("Modes", Icons.Default.Apps, theme)
            })
            add(Row("Modes") {
                CardRow(theme) {
                    Text(
                        "Each profile has separate WebView cookies/cache. Simple, Developer and Power share app-level history, bookmarks and passwords; Advance also isolates app data. Switching restarts into that profile.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    BrowserMode.values().forEach { mode ->
                        val isActive = mode == activeMode
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
                                        mode.display().title +
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
                                    "Theme: ${lightThemeVariant.label}",
                                    fontSize = 11.sp,
                                    color = theme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            })

            // ================= ABOUT =================
            add(Row("About") {
                SectionHeader("About", Icons.Default.Info, theme)
            })
            add(Row("About") {
                CardRow(theme) {
                    InfoLine("App version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", theme)
                    InfoLine("Browser profiles", "Android System WebView · 4 data profiles", theme)
                    InfoLine("Feature details", "See click://version", theme)
                    InfoLine("Developer", "TEAM PK AI", theme)
                }
            })
            add(Row("About") {
                CardRow(theme) {
                    ActionRow(
                        "Check for updates",
                        "Official Google Play update check — also checked automatically once a day",
                        Icons.Default.Refresh,
                        theme
                    ) {
                        val activity = context as? android.app.Activity
                        if (activity == null) {
                            SafetyCheck.openPlayListing(context)
                        } else {
                            com.click.browser.engine.AppUpdateChecker.manualCheck(activity) { message ->
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ActionRow(
                        "Open-source licenses",
                        "Click's MIT license and third-party project notices",
                        Icons.Default.Description,
                        theme,
                        onOpenLicenses
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ActionRow("Privacy Policy", "How Click handles your data", Icons.Default.Description, theme, onOpenPrivacyPolicy)
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ActionRow("Help & Feedback", "FAQs + email the developer", Icons.Default.Help, theme, onOpenHelpFeedback)
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ActionRow("Reports", "Crash & OOM reports, feedback, bug reports", Icons.Default.BugReport, theme, onOpenReports)
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    ActionRow("CatLog — local diagnostics", "Profile-switch and renderer events from Click only", Icons.Default.BugReport, theme, onOpenCatLog)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (page == "home") "Settings" else page, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
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
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (page == "home") {
                item {
                    CardRow(theme) {
                        ActionRow(
                            "General",
                            "Search engine, default browser, AI assistant, downloads",
                            Icons.Default.Tune,
                            theme
                        ) { page = "General" }
                        HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                        ActionRow(
                            "Protection",
                            "Trackers & ads blocking — currently ${ProtectionPolicy.levelLabel(protectionLevel)}",
                            Icons.Default.Shield,
                            theme,
                            onOpenProtection
                        )
                        HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                        ActionRow(
                            "Privacy & Safety",
                            "Safe Browsing, clear browsing data, Do Not Track, site settings",
                            Icons.Default.VerifiedUser,
                            theme,
                            onOpenPrivacySafety
                        )
                        HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                        ActionRow(
                            "Appearance",
                            "Theme, text size, wallpaper, address bar",
                            Icons.Default.Palette,
                            theme
                        ) { page = "Appearance" }
                        HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                        ActionRow(
                            "Modes",
                            "Switch between your browser profiles",
                            Icons.Default.Apps,
                            theme
                        ) { page = "Modes" }
                        HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                        ActionRow(
                            "Accessibility",
                            "Force zoom, reader mode, reduced motion",
                            Icons.Default.Accessibility,
                            theme,
                            onOpenAccessibility
                        )
                        HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                        ActionRow(
                            "About",
                            "Version, licenses, privacy policy, help",
                            Icons.Default.Info,
                            theme
                        ) { page = "About" }
                    }
                }
            } else {
                val visibleRows = rows.filter { it.section == page }
                items(visibleRows.size) { i -> visibleRows[i].content() }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector, theme: ModeTheme) {
    val iconContainer by animateColorAsState(
        targetValue = theme.primary.copy(alpha = 0.10f),
        animationSpec = tween(180),
        label = "settingsSectionIcon"
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = theme.primary)
    }
}

@Composable
private fun CardRow(theme: ModeTheme, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.animateContentSize().padding(16.dp), content = content)
    }
}

@Composable
private fun ToggleRow(
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
