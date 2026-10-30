package com.click.browser.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.CookieStore
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.SitePermissionPref
import com.click.browser.engine.SiteSettings
import kotlinx.coroutines.launch

/**
 * Chrome-style per-site settings bottom sheet, opened from the lock icon
 * in the address bar. Per-mode themed via [theme] (Simple = light,
 * Developer = dark glass, Power = OLED neon, Advance = deep-space teal).
 *
 * Sections (mirroring Chrome's site-details page):
 * - Site header: gradient icon + host + connection security badge.
 * - Permissions: Location / Camera / Microphone / Notifications
 *   (Allow / Ask / Block). Enforced in WebChromeClient callbacks.
 * - Content: JavaScript / Images / Pop-ups / Ads / Sound /
 *   Third-party cookies. Applied to the current WebView.
 * - Shields: Brave-style trackers/cookies/history info (existing).
 * - Usage: storage used + Clear site data + Reset permissions.
 *
 * Null (unset) = "use global default"; dropdowns show "Ask (default)" /
 * "Allow (default)" so the user can tell override from default apart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSettingsSheet(
    theme: ModeTheme,
    host: String,
    isHttps: Boolean,
    current: SiteSettings,
    desktopMode: Boolean,
    globalJavaScript: Boolean,
    globalAdBlock: Boolean,
    globalThirdPartyCookies: Boolean,
    onSettingsChange: (SiteSettings) -> Unit,
    onDesktopModeChange: (Boolean) -> Unit,
    onApplyToWebView: (SiteSettings) -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
    // Brave-style Shields info (optional — defaults keep old call sites working).
    trackersBlocked: Int = 0,
    cookieCount: Int = 0,
    onManageCookies: () -> Unit = {},
    onOpenHistory: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showClearConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = theme.surface,
        contentColor = theme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // ---- Site header: gradient icon + host + security badge ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(theme.primary, theme.secondary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isHttps) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        host,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = theme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isHttps) Color(0xFF22C55E)
                                    else MaterialTheme.colorScheme.error
                                )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isHttps) "Connection is secure" else "Connection is not secure",
                            fontSize = 12.sp,
                            color = theme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // ================= PERMISSIONS =================
            SectionTitle(theme, "Permissions")
            PermissionDropdownRow(
                theme = theme,
                icon = Icons.Default.CameraAlt,
                title = "Camera",
                subtitle = "Allow this site to use your camera",
                value = current.camera,
                defaultLabel = "Ask",
                onChange = { onSettingsChange(current.copy(camera = it)) }
            )
            PermissionDropdownRow(
                theme = theme,
                icon = Icons.Default.Mic,
                title = "Microphone",
                subtitle = "Allow this site to use your microphone",
                value = current.microphone,
                defaultLabel = "Ask",
                onChange = { onSettingsChange(current.copy(microphone = it)) }
            )
            PermissionDropdownRow(
                theme = theme,
                icon = Icons.Default.Notifications,
                title = "Notifications",
                subtitle = "Allow this site to send notifications",
                value = current.notifications,
                defaultLabel = "Ask",
                onChange = { onSettingsChange(current.copy(notifications = it)) }
            )

            // ================= CONTENT =================
            SectionTitle(theme, "Content")
            // JavaScript — tri-state: Default / Allow / Block.
            TriStateRow(
                theme = theme,
                icon = Icons.Default.Code,
                title = "JavaScript",
                subtitle = "Allow scripts on this site",
                value = current.javaScript,
                globalLabel = if (globalJavaScript) "Allowed" else "Blocked",
                onChange = { v ->
                    val next = current.copy(javaScript = v)
                    onSettingsChange(next)
                    onApplyToWebView(next)
                    onReload()
                }
            )
            // Images — tri-state.
            TriStateRow(
                theme = theme,
                icon = Icons.Default.Image,
                title = "Images",
                subtitle = "Show images on this site",
                value = current.images,
                globalLabel = "Shown",
                onChange = { v ->
                    val next = current.copy(images = v)
                    onSettingsChange(next)
                    onApplyToWebView(next)
                    onReload()
                }
            )
            // Pop-ups — tri-state.
            TriStateRow(
                theme = theme,
                icon = Icons.Default.OpenInNew,
                title = "Pop-ups and redirects",
                subtitle = "Block annoying pop-ups on this site",
                // Stored as "allow popups"; UI shows Block when false.
                value = current.popups?.not(),
                globalLabel = "Blocked",
                onChange = { blocked ->
                    val next = current.copy(popups = blocked?.not())
                    onSettingsChange(next)
                    onApplyToWebView(next)
                }
            )
            // Ads (adBlock) — tri-state.
            TriStateRow(
                theme = theme,
                icon = Icons.Default.Block,
                title = "Intrusive ads",
                subtitle = "Block misleading ads on this site",
                value = current.adBlock?.not(),
                globalLabel = if (globalAdBlock) "Blocked" else "Allowed",
                onChange = { blocked ->
                    val next = current.copy(adBlock = blocked?.not())
                    onSettingsChange(next)
                    onReload()
                }
            )
            // Sound — tri-state (Allow / Mute).
            TriStateRow(
                theme = theme,
                icon = Icons.Default.VolumeUp,
                title = "Sound",
                subtitle = "Allow this site to play sound",
                value = current.sound,
                globalLabel = "Allowed",
                onChange = { v ->
                    val next = current.copy(sound = v)
                    onSettingsChange(next)
                    onApplyToWebView(next)
                }
            )
            // Third-party cookies — tri-state.
            TriStateRow(
                theme = theme,
                icon = Icons.Default.Cookie,
                title = "Third-party cookies",
                subtitle = "Allow cross-site tracking cookies",
                value = current.thirdPartyCookies,
                globalLabel = if (globalThirdPartyCookies) "Allowed" else "Blocked",
                onChange = { v ->
                    val next = current.copy(thirdPartyCookies = v)
                    onSettingsChange(next)
                    onApplyToWebView(next)
                }
            )
            // Background sync — tri-state.
            TriStateRow(
                theme = theme,
                icon = Icons.Default.Sync,
                title = "Background sync",
                subtitle = "Allow this site to sync in background",
                value = current.backgroundSync,
                globalLabel = "Allowed",
                onChange = { v ->
                    val next = current.copy(backgroundSync = v)
                    onSettingsChange(next)
                    onApplyToWebView(next)
                }
            )

            // Desktop site — plain toggle (existing desktop-hosts path).
            SettingToggleRow(
                theme = theme,
                icon = Icons.Default.Speed,
                title = "Desktop site",
                subtitle = "Always load the desktop version",
                checked = desktopMode,
                onChange = { onDesktopModeChange(it) }
            )

            // Text zoom — slider, default 100%.
            Text(
                "Text zoom",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = theme.onSurface,
                modifier = Modifier.padding(top = 12.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val zoom = current.textZoom ?: 100
                Slider(
                    value = zoom.toFloat(),
                    onValueChange = { v ->
                        val next = current.copy(textZoom = v.toInt().coerceIn(50, 300))
                        onSettingsChange(next)
                        onApplyToWebView(next)
                    },
                    valueRange = 50f..300f,
                    steps = 9,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "$zoom%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.onSurface,
                    modifier = Modifier.width(52.dp)
                )
            }
            if (current.textZoom != null) {
                TextButton(onClick = {
                    val next = current.copy(textZoom = null)
                    onSettingsChange(next)
                    onApplyToWebView(next)
                }) {
                    Text("Reset zoom to default")
                }
            }

            // ================= SHIELDS =================
            SectionTitle(theme, "🛡️ Shields")
            Text(
                if (isHttps) "Your connection to this site is encrypted (HTTPS)."
                else "Not encrypted — avoid entering passwords or payment details here.",
                fontSize = 12.sp,
                color = theme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            ShieldsInfoRow(
                theme = theme,
                icon = Icons.Default.Shield,
                title = "Trackers & ads blocked",
                subtitle = if (globalAdBlock) "$trackersBlocked blocked by Click Shields"
                else "Shields are off for this browser",
                onClick = null
            )
            ShieldsInfoRow(
                theme = theme,
                icon = Icons.Default.Cookie,
                title = "Cookies",
                subtitle = "$cookieCount cookie${if (cookieCount == 1) "" else "s"} stored by $host",
                actionLabel = "Manage",
                onClick = onManageCookies
            )
            ShieldsInfoRow(
                theme = theme,
                icon = Icons.Default.History,
                title = "History",
                subtitle = "Recently visited pages",
                actionLabel = "Open",
                onClick = onOpenHistory
            )

            // ================= USAGE =================
            SectionTitle(theme, "Usage")
            UsageCard(theme = theme, host = host)

            Spacer(Modifier.height(8.dp))
            // Clear site data.
            TextButton(
                onClick = { showClearConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Clear site data", color = MaterialTheme.colorScheme.error)
            }

            // Reset all overrides.
            if (!current.isEmpty()) {
                TextButton(
                    onClick = {
                        onSettingsChange(SiteSettings())
                        onApplyToWebView(SiteSettings())
                        Toast.makeText(context, "Site settings reset to defaults", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Reset all to defaults")
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("Clear data for $host?") },
                text = {
                    Text(
                        "This removes cookies and site storage (logins, preferences) " +
                            "for this site in the current engine mode. The page will reload."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearConfirm = false
                            scope.launch {
                                val result = CookieStore.clearSiteData(host)
                                Toast.makeText(
                                    context,
                                    "Cleared ${result.cookiesCleared} cookies, " +
                                        "${result.storageOriginsCleared} storage areas",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onReload()
                            }
                        }
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }
}

/** Purple-accent section header, matching the approved mockup. */
@Composable
private fun SectionTitle(theme: ModeTheme, title: String) {
    Text(
        title.uppercase(),
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
        color = theme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)
    )
}

/**
 * Chrome-style permission row: icon + title/subtitle on the left, an
 * Allow/Ask/Block dropdown on the right. Null = "use default".
 */
@Composable
private fun PermissionDropdownRow(
    theme: ModeTheme,
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: SitePermissionPref?,
    defaultLabel: String,
    onChange: (SitePermissionPref?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (value) {
        SitePermissionPref.ALLOW -> "Allow"
        SitePermissionPref.BLOCK -> "Block"
        SitePermissionPref.ASK -> "Ask"
        null -> "$defaultLabel (default)"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = theme.onSurface)
            Text(subtitle, fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f))
        }
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.surfaceVariant)
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (value != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (value != null) theme.primary
                    else theme.onSurface.copy(alpha = 0.7f)
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = theme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("$defaultLabel (default)") },
                    onClick = { onChange(null); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Ask") },
                    onClick = { onChange(SitePermissionPref.ASK); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Allow") },
                    onClick = { onChange(SitePermissionPref.ALLOW); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Block") },
                    onClick = { onChange(SitePermissionPref.BLOCK); expanded = false }
                )
            }
        }
    }
}

/**
 * Tri-state row: Default (follows global) / On / Off.
 * `value = null` renders as "Default (global: X)".
 */
@Composable
private fun TriStateRow(
    theme: ModeTheme,
    icon: ImageVector? = null,
    title: String,
    subtitle: String,
    value: Boolean?,
    globalLabel: String,
    onChange: (Boolean?) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = theme.onSurface)
                Text(subtitle, fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f))
            }
            // Segmented Default / On / Off.
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TriOption(theme, "Default", value == null) { onChange(null) }
                TriOption(theme, "On", value == true) { onChange(true) }
                TriOption(theme, "Off", value == false) { onChange(false) }
            }
        }
        if (value == null) {
            Text(
                "Using global default: $globalLabel",
                fontSize = 11.sp,
                color = theme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun TriOption(
    theme: ModeTheme,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) theme.primary.copy(alpha = 0.18f)
        else theme.onSurface.copy(alpha = 0.06f)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) theme.primary else theme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun SettingToggleRow(
    theme: ModeTheme,
    icon: ImageVector? = null,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = theme.onSurface)
            Text(subtitle, fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Storage-usage card with a themed progress bar (mockup style). */
@Composable
private fun UsageCard(theme: ModeTheme, host: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.surfaceVariant)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Storage used", fontSize = 13.sp, color = theme.onSurface)
            Text(
                "—",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = theme.onSurface
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(theme.onSurface.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.32f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(theme.primary, theme.secondary)
                        )
                    )
            )
        }
        Text(
            "Cookies · Cache · Site data for $host",
            fontSize = 11.sp,
            color = theme.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * One Brave-style Shields info row: icon + title/subtitle, optional
 * action button on the right (null = info only, no click).
 */
@Composable
private fun ShieldsInfoRow(
    theme: ModeTheme,
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = theme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = theme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f))
        }
        if (actionLabel != null && onClick != null) {
            TextButton(onClick = onClick) {
                Text(actionLabel)
            }
        }
    }
}
