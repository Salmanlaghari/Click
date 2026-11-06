package com.click.browser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.TextScaleStore

/**
 * Bottom-sheet-style dialog for text scaling (accessibility).
 *
 * - Slider adjusts the CURRENT tab's text size live (50–300%).
 * - "Save for this site" persists a per-host override; otherwise the
 *   change applies to this page view only and the global default stays.
 * - "Use global" clears the per-host override for [host].
 */
@Composable
fun TextScaleSheet(
    theme: ModeTheme,
    host: String,
    currentScale: Int,
    globalScale: Int,
    hostHasOverride: Boolean,
    onScaleChange: (Int) -> Unit,
    onSaveForSite: (Boolean) -> Unit,
    onResetGlobal: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TextFields, contentDescription = null, tint = theme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Text Size", color = theme.onSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Text(
                    host.ifBlank { "this page" },
                    color = theme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "$currentScale%",
                    color = theme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { onScaleChange(currentScale - TextScaleStore.STEP) }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Smaller", tint = theme.onSurface)
                    }
                    Slider(
                        value = currentScale.toFloat(),
                        onValueChange = { onScaleChange(it.toInt()) },
                        valueRange = TextScaleStore.MIN.toFloat()..TextScaleStore.MAX.toFloat(),
                        steps = (TextScaleStore.MAX - TextScaleStore.MIN) / TextScaleStore.STEP - 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onScaleChange(currentScale + TextScaleStore.STEP) }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Larger", tint = theme.onSurface)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Remember for this site", color = theme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (hostHasOverride) "Using this site's saved size"
                            else "Using global default ($globalScale%)",
                            color = theme.onSurface.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                    Switch(checked = hostHasOverride, onCheckedChange = onSaveForSite)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onResetGlobal,
                        modifier = Modifier.weight(1f)
                    ) { Text("Reset ($globalScale%)") }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text("Done") }
                }
            }
        }
    }
}

/**
 * Full-screen overlay shown when private tabs are locked. Covers the whole
 * browser surface (including the tab switcher) so private content is never
 * visible without authentication. Incognito thumbnails are never captured
 * anyway (see TabThumbnailStore) — this is the second layer.
 */
@Composable
fun PrivateTabLockOverlay(
    theme: ModeTheme,
    useBiometricLabel: Boolean,
    onUnlock: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = theme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = theme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Private Tabs Locked",
                color = theme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (useBiometricLabel)
                    "Authenticate with your fingerprint or face to view private tabs"
                else
                    "Authenticate with your device PIN to view private tabs",
                color = theme.onSurface.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Unlock")
            }
        }
    }
}
