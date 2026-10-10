package com.click.browser.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.LocationGuard
import com.click.browser.engine.ModeTheme

/**
 * LocationGuard UI — Prince's request: "User apni location hide kar ke surf kar sake".
 *
 * - [LocationPromptDialog]: ASK-mode dialog shown when a website requests
 *   geolocation. Block / Spoof / remember-per-site.
 * - [LocationSettingsSheet]: full settings — global mode, spoof target
 *   (presets + custom lat/lng), per-site overrides list.
 *
 * Honest labels throughout: BLOCK = "Websites will see: Denied",
 * SPOOF = "Websites will see: <label>".
 */

/** ASK-mode dialog: a website wants the device location. */
@Composable
fun LocationPromptDialog(
    origin: String,
    host: String?,
    spoofLabel: String,
    onBlock: (rememberForSite: Boolean) -> Unit,
    onSpoof: (rememberForSite: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var rememberChoice by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
        title = { Text("Share location?") },
        text = {
            Column {
                Text(
                    (host ?: origin) + " wants to know your location.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Block = site sees Denied. Spoof = site sees $spoofLabel.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = { rememberChoice = it }
                    )
                    Text("Remember for this site", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onBlock(rememberChoice) }) { Text("Block") }
                TextButton(onClick = { onSpoof(rememberChoice) }) { Text("Spoof") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        }
    )
}

/** Full location settings bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSettingsSheet(
    theme: ModeTheme,
    mode: LocationGuard.LocationMode,
    onModeChange: (LocationGuard.LocationMode) -> Unit,
    spoofLat: Double,
    spoofLng: Double,
    spoofLabel: String,
    onSpoofPreset: (LocationGuard.SpoofPreset) -> Unit,
    onCustomSpoof: (lat: Double, lng: Double, label: String) -> Unit,
    siteModes: Map<String, String>,
    onClearSiteMode: (String) -> Unit,
    onClose: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCustomEditor by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = theme.surface
    ) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Location Privacy",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.onSurface
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.onSurface)
                    }
                }
                Text(
                    "Controls the browser geolocation API only. IP-based location still works (needs VPN to change). No GPS permission is used.",
                    fontSize = 12.sp,
                    color = theme.onSurface.copy(alpha = 0.6f)
                )
            }

            item {
                Text("Default for all sites", fontWeight = FontWeight.SemiBold, color = theme.onSurface)
                Spacer(Modifier.height(4.dp))
                LocationGuard.LocationMode.values().forEach { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeChange(m) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == m, onClick = { onModeChange(m) })
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (m) {
                                    LocationGuard.LocationMode.ASK -> "Ask every time"
                                    LocationGuard.LocationMode.BLOCK -> "Block location"
                                    LocationGuard.LocationMode.SPOOF -> "Spoof location"
                                },
                                fontWeight = FontWeight.Medium,
                                color = theme.onSurface
                            )
                            Text(
                                "Websites will see: " +
                                    LocationGuard.websitesWillSee(
                                        m,
                                        if (m == LocationGuard.LocationMode.SPOOF) spoofLabel else ""
                                    ),
                                fontSize = 12.sp,
                                color = theme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Icon(
                            when (m) {
                                LocationGuard.LocationMode.ASK -> Icons.Default.MyLocation
                                LocationGuard.LocationMode.BLOCK -> Icons.Default.LocationOff
                                LocationGuard.LocationMode.SPOOF -> Icons.Default.LocationOn
                            },
                            contentDescription = null,
                            tint = theme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (mode == LocationGuard.LocationMode.SPOOF) {
                item {
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    Text("Spoof location", fontWeight = FontWeight.SemiBold, color = theme.onSurface)
                    Text(
                        "Websites will see: $spoofLabel ($spoofLat, $spoofLng)",
                        fontSize = 12.sp,
                        color = theme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(LocationGuard.PRESETS.chunked(2)) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { preset ->
                            FilterChip(
                                selected = spoofLabel == preset.label,
                                onClick = { onSpoofPreset(preset) },
                                label = { Text(preset.label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { showCustomEditor = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Custom coordinates…")
                    }
                    if (showCustomEditor) {
                        CustomSpoofEditor(
                            theme = theme,
                            initialLat = spoofLat,
                            initialLng = spoofLng,
                            onSave = { lat, lng ->
                                onCustomSpoof(lat, lng, "Custom (%.4f, %.4f)".format(lat, lng))
                                showCustomEditor = false
                            },
                            onCancel = { showCustomEditor = false }
                        )
                    }
                }
            }

            if (siteModes.isNotEmpty()) {
                item {
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.1f))
                    Text("Per-site overrides", fontWeight = FontWeight.SemiBold, color = theme.onSurface)
                    Text(
                        "These sites ignore the global default.",
                        fontSize = 12.sp,
                        color = theme.onSurface.copy(alpha = 0.6f)
                    )
                }
                items(siteModes.entries.toList()) { (host, modeKey) ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = theme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(host, fontWeight = FontWeight.Medium, color = theme.onSurface, fontSize = 14.sp)
                                Text(
                                    "Websites will see: " + LocationGuard.websitesWillSee(
                                        LocationGuard.LocationMode.fromKey(modeKey), spoofLabel
                                    ),
                                    fontSize = 12.sp,
                                    color = theme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            TextButton(onClick = { onClearSiteMode(host) }) {
                                Text("Reset", color = theme.primary)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Custom lat/lng editor for the spoof target. */
@Composable
private fun CustomSpoofEditor(
    theme: ModeTheme,
    initialLat: Double,
    initialLng: Double,
    onSave: (Double, Double) -> Unit,
    onCancel: () -> Unit
) {
    var latText by remember { mutableStateOf(initialLat.toString()) }
    var lngText by remember { mutableStateOf(initialLng.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = latText,
                onValueChange = { latText = it; error = null },
                label = { Text("Latitude") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = lngText,
                onValueChange = { lngText = it; error = null },
                label = { Text("Longitude") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val lat = latText.toDoubleOrNull()
                val lng = lngText.toDoubleOrNull()
                when {
                    lat == null || lng == null -> error = "Enter valid numbers."
                    lat !in -90.0..90.0 -> error = "Latitude must be -90…90."
                    lng !in -180.0..180.0 -> error = "Longitude must be -180…180."
                    else -> onSave(lat, lng)
                }
            }) { Text("Save") }
        }
    }
}
