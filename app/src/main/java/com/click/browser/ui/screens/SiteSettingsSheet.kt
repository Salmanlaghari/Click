package com.click.browser.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.CookieStore
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.SiteLocationPref
import com.click.browser.engine.SiteSettings
import kotlinx.coroutines.launch

/**
 * Per-site settings bottom sheet, opened from the lock icon in the address bar.
 *
 * Every control takes real effect — no dead toggles:
 * - JavaScript: applied to the current WebView + page reloaded.
 * - Ad-block: stored per host; the request interceptor honors it immediately.
 * - Third-party cookies: applied to the current WebView immediately.
 * - Desktop site: uses the existing desktop-hosts path + page reloaded.
 * - Location: enforced on the next geolocation prompt (allow/block silently).
 * - Text zoom: applied to the current WebView immediately.
 * - Clear site data: wipes cookies + DOM storage for this host, then reloads.
 *
 * Null (unset) = "use global default"; the UI shows a "Default" state for
 * unset toggles so the user can tell override from default apart.
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
    onDismiss: () -> Unit
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
            // Header: host + connection security.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isHttps) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (isHttps) androidx.compose.ui.graphics.Color(0xFF22C55E)
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        host,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = theme.onSurface
                    )
                    Text(
                        if (isHttps) "Connection is secure" else "Connection is not secure",
                        fontSize = 12.sp,
                        color = theme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // JavaScript — tri-state: Default / On / Off.
            TriStateRow(
                theme = theme,
                title = "JavaScript",
                subtitle = "Allow scripts on this site",
                value = current.javaScript,
                globalLabel = if (globalJavaScript) "On" else "Off",
                onChange = { v ->
                    val next = current.copy(javaScript = v)
                    onSettingsChange(next)
                    onApplyToWebView(next)
                    onReload()
                }
            )

            // Ad-block — tri-state.
            TriStateRow(
                theme = theme,
                title = "Ad-block",
                subtitle = "Block ads and trackers on this site",
                value = current.adBlock,
                globalLabel = if (globalAdBlock) "On" else "Off",
                onChange = { v ->
                    val next = current.copy(adBlock = v)
                    onSettingsChange(next)
                    onReload()
                }
            )

            // Third-party cookies — tri-state.
            TriStateRow(
                theme = theme,
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

            // Desktop site — plain toggle (existing desktop-hosts path).
            SettingToggleRow(
                theme = theme,
                title = "Desktop site",
                subtitle = "Always load the desktop version",
                checked = desktopMode,
                onChange = { onDesktopModeChange(it) }
            )

            // Location — 3-way segmented.
            Text(
                "Location access",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = theme.onSurface,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                "How this site may use your location",
                fontSize = 12.sp,
                color = theme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf(
                    null to "Ask",
                    SiteLocationPref.ALLOW to "Allow",
                    SiteLocationPref.BLOCK to "Block"
                )
                options.forEach { (pref, label) ->
                    val selected = current.location == pref
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val next = current.copy(location = pref)
                                onSettingsChange(next)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            color = if (selected) theme.primary
                            else theme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

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

            Spacer(Modifier.height(12.dp))

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

/**
 * Tri-state row: Default (follows global) / On / Off.
 * `value = null` renders as "Default (global: X)".
 */
@Composable
private fun TriStateRow(
    theme: ModeTheme,
    title: String,
    subtitle: String,
    value: Boolean?,
    globalLabel: String,
    onChange: (Boolean?) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = theme.onSurface)
                Text(subtitle, fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f))
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
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = theme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
