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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.AlertDialog
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
import com.click.browser.engine.ConnectionSecurityInfo
import com.click.browser.engine.ConnectionSecurityPolicy
import com.click.browser.engine.ConnectionSecurityStatus
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.SiteSettings
import com.click.browser.engine.WebsitePermissionCapability
import com.click.browser.engine.WebsitePermissionStatus
import com.click.browser.engine.websitePermissionStatus
import kotlinx.coroutines.launch

/**
 * Chrome-style per-site settings bottom sheet, opened from the lock icon
 * in the address bar. Per-mode themed via [theme] (Simple = light,
 * Developer = dark glass, Power = OLED neon, Advance = deep-space teal).
 *
 * Sections (mirroring Chrome's site-details page):
 * - Site header: host + the browser's observed connection-security state.
 * - Permissions: a clear explanation of website permissions that Click does
 *   not grant in this build (camera, microphone and location); notifications
 *   are unsupported for websites.
 * - Content: JavaScript / Images / Pop-ups / Ads / Third-party cookies.
 *   Applied to the current WebView.
 * - Shields: protection status plus cookies and history information.
 * - Usage: storage used + Clear site data + Reset supported site settings.
 *
 * Null (unset) = "use global default" for the supported content settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSettingsSheet(
    theme: ModeTheme,
    host: String,
    connectionSecurity: ConnectionSecurityInfo,
    current: SiteSettings,
    desktopMode: Boolean,
    globalJavaScript: Boolean,
    globalAdBlock: Boolean,
    adBlockStatusLabel: String,
    globalThirdPartyCookies: Boolean,
    onSettingsChange: (SiteSettings) -> Unit,
    onDesktopModeChange: (Boolean) -> Unit,
    onApplyToWebView: (SiteSettings) -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
    siteAdBlockOverrideVisible: Boolean = true,
    siteAdBlockOverrideEnabled: Boolean = true,
    siteAdBlockUnavailableExplanation: String? = null,
    // Site data displayed in the Shields section.
    cookieCount: Int = 0,
    onManageCookies: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    historyScopeDescription: String = "current profile history",
    otherHistoryScopeDescription: String = "other profile history",
    onClearHistoryForHost: suspend (String) -> Int = { 0 }
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val securityStatus = connectionSecurity.status
    val securityColor = when (securityStatus) {
        ConnectionSecurityStatus.SECURE_HTTPS -> Color(0xFF22C55E)
        ConnectionSecurityStatus.SECURE_WITH_MIXED_CONTENT,
        ConnectionSecurityStatus.UNENCRYPTED_HTTP -> Color(0xFFF59E0B)
        ConnectionSecurityStatus.CERTIFICATE_WARNING -> MaterialTheme.colorScheme.error
        ConnectionSecurityStatus.CHECKING,
        ConnectionSecurityStatus.UNKNOWN -> theme.onSurface.copy(alpha = 0.5f)
    }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember(host, historyScopeDescription) { mutableStateOf(false) }

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
            // ---- Site header: host + engine-reported connection status ----
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
                        when (securityStatus) {
                            ConnectionSecurityStatus.SECURE_HTTPS,
                            ConnectionSecurityStatus.SECURE_WITH_MIXED_CONTENT -> Icons.Default.Lock
                            ConnectionSecurityStatus.UNENCRYPTED_HTTP,
                            ConnectionSecurityStatus.CERTIFICATE_WARNING -> Icons.Default.LockOpen
                            ConnectionSecurityStatus.CHECKING,
                            ConnectionSecurityStatus.UNKNOWN -> Icons.Default.Shield
                        },
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
                                .background(securityColor)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            ConnectionSecurityPolicy.title(securityStatus),
                            fontSize = 12.sp,
                            color = theme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // ================= WEBSITE PERMISSIONS =================
            SectionTitle(theme, "Website permissions")
            Text(
                "These statuses apply to all websites. This build has no per-site allow controls for device permissions.",
                fontSize = 12.sp,
                color = theme.onSurface.copy(alpha = 0.68f),
                lineHeight = 18.sp
            )
            WebsitePermissionStatusRow(
                theme = theme,
                capability = WebsitePermissionCapability.CAMERA,
                title = "Camera",
                explanation = "Click denies website camera requests."
            )
            WebsitePermissionStatusRow(
                theme = theme,
                capability = WebsitePermissionCapability.MICROPHONE,
                title = "Microphone",
                explanation = "Website access is denied. Android microphone access is reserved for Click's optional AI voice input."
            )
            WebsitePermissionStatusRow(
                theme = theme,
                capability = WebsitePermissionCapability.LOCATION,
                title = "Location",
                explanation = "Click denies website location requests and does not request Android location access."
            )
            WebsitePermissionStatusRow(
                theme = theme,
                capability = WebsitePermissionCapability.NOTIFICATIONS,
                title = "Notifications",
                explanation = "Website notifications are not supported; app notifications are separate."
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
            if (siteAdBlockOverrideVisible) {
                SiteAdBlockOverrideRow(
                    theme = theme,
                    value = current.adBlock?.not(),
                    globalAdBlock = globalAdBlock,
                    enabled = siteAdBlockOverrideEnabled,
                    unavailableExplanation = siteAdBlockUnavailableExplanation,
                    onChange = { blocked ->
                        val next = current.copy(adBlock = blocked?.not())
                        onSettingsChange(next)
                        onReload()
                    }
                )
            }
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
            // Per-site wide-layout override; the WebView identity is unchanged.
            SettingToggleRow(
                theme = theme,
                icon = Icons.Default.Speed,
                title = "Wide layout",
                subtitle = "Use a wide viewport where supported; some sites may still show mobile content.",
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
                ConnectionSecurityPolicy.description(securityStatus),
                fontSize = 12.sp,
                color = theme.onSurface.copy(alpha = 0.6f)
            )
            connectionSecurity.detail?.let { detail ->
                Text(
                    detail,
                    fontSize = 11.sp,
                    color = theme.onSurface.copy(alpha = 0.58f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            connectionSecurity.certificateSubject?.let { subject ->
                Text(
                    "Certificate subject: $subject",
                    fontSize = 10.sp,
                    color = theme.onSurface.copy(alpha = 0.55f),
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            connectionSecurity.certificateIssuer?.let { issuer ->
                Text(
                    "Issued by: $issuer",
                    fontSize = 10.sp,
                    color = theme.onSurface.copy(alpha = 0.55f),
                    maxLines = 2
                )
            }
            Spacer(Modifier.height(8.dp))
            ShieldsInfoRow(
                theme = theme,
                icon = Icons.Default.Shield,
                title = "Ad & tracker protection",
                subtitle = adBlockStatusLabel,
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
            TextButton(
                onClick = { showClearHistoryConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Clear site history", color = MaterialTheme.colorScheme.error)
            }

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
                            "for this site in ${PrivateBrowsingDisclosurePolicy.profileWebViewStorageScopeDescription}. The page will reload."
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
        if (showClearHistoryConfirm) {
            AlertDialog(
                onDismissRequest = { showClearHistoryConfirm = false },
                title = { Text("Clear history for $host?") },
                text = {
                    Text(
                        "This removes recorded visits to $host from $historyScopeDescription only. " +
                            "Other website hosts and $otherHistoryScopeDescription are unchanged. " +
                            "Future visits may be recorded again."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearHistoryConfirm = false
                            scope.launch {
                                try {
                                    val removed = onClearHistoryForHost(host)
                                    val message = if (removed == 0) {
                                        "No saved history entries for $host"
                                    } else {
                                        "Cleared $removed history entr${if (removed == 1) "y" else "ies"} for $host"
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Unable to clear this site's history",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    ) {
                        Text("Clear history", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryConfirm = false }) { Text("Cancel") }
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

@Composable
private fun WebsitePermissionStatusRow(
    theme: ModeTheme,
    capability: WebsitePermissionCapability,
    title: String,
    explanation: String
) {
    val statusLabel = when (websitePermissionStatus(capability)) {
        WebsitePermissionStatus.BLOCKED_IN_CLICK -> "Blocked by Click"
        WebsitePermissionStatus.UNSUPPORTED -> "Not supported"
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = theme.onSurface)
            Text(statusLabel, fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.7f))
        }
        Text(explanation, fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.62f))
    }
}

@Composable
internal fun SiteAdBlockOverrideRow(
    theme: ModeTheme,
    value: Boolean?,
    globalAdBlock: Boolean,
    enabled: Boolean,
    unavailableExplanation: String?,
    onChange: (Boolean?) -> Unit
) {
    TriStateRow(
        theme = theme,
        icon = Icons.Default.Block,
        title = "Intrusive ads",
        subtitle = "Block misleading ads on this site",
        value = value,
        globalLabel = if (globalAdBlock) "Blocked" else "Allowed",
        onChange = onChange,
        enabled = enabled,
        disabledExplanation = unavailableExplanation
    )
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
    onChange: (Boolean?) -> Unit,
    enabled: Boolean = true,
    disabledExplanation: String? = null
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
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (enabled) theme.onSurface else theme.onSurface.copy(alpha = 0.68f)
                )
                Text(
                    if (enabled) subtitle else disabledExplanation ?: subtitle,
                    fontSize = 11.sp,
                    color = theme.onSurface.copy(alpha = if (enabled) 0.6f else 0.72f)
                )
            }
            if (enabled) {
                // Segmented Default / On / Off.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TriOption(theme, "Default", value == null) { onChange(null) }
                    TriOption(theme, "On", value == true) { onChange(true) }
                    TriOption(theme, "Off", value == false) { onChange(false) }
                }
            } else {
                Text(
                    "Unavailable",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.onSurface.copy(alpha = 0.58f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        if (enabled && value == null) {
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
