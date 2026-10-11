package com.click.browser.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.DownloadSettings
import com.click.browser.engine.ModeTheme
import kotlinx.coroutines.launch

/**
 * Download location: phone's public Downloads folder by default (visible in
 * the file manager), app-private storage, or a custom folder picked by the
 * user (Storage Access Framework).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(
    theme: ModeTheme,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var location by remember { mutableStateOf(DownloadSettings.LOCATION_PHONE) }
    var displayPath by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            location = DownloadSettings.location(context)
            displayPath = try { DownloadSettings.displayPath(context) } catch (_: Exception) { "" }
            loaded = true
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
            scope.launch {
                DownloadSettings.setCustomUri(context, uri)
                DownloadSettings.setLocation(context, DownloadSettings.LOCATION_CUSTOM)
                refresh()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", fontWeight = FontWeight.Bold, color = theme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.topBarBg)
            )
        },
        containerColor = theme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DlCard(theme) {
                Text("Download location", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = theme.onSurface)
                Text(
                    "Where files go when you download them.",
                    fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                DlOption(
                    title = "Phone storage",
                    desc = "Public Downloads folder — visible in your file manager (default)",
                    icon = Icons.Default.Smartphone,
                    selected = location == DownloadSettings.LOCATION_PHONE,
                    enabled = loaded,
                    theme = theme,
                    onSelect = {
                        scope.launch {
                            DownloadSettings.setLocation(context, DownloadSettings.LOCATION_PHONE)
                            refresh()
                        }
                    }
                )
                DlOption(
                    title = "App private storage",
                    desc = "Only Click can see these files; deleted if the app is uninstalled",
                    icon = Icons.Default.Lock,
                    selected = location == DownloadSettings.LOCATION_APP,
                    enabled = loaded,
                    theme = theme,
                    onSelect = {
                        scope.launch {
                            DownloadSettings.setLocation(context, DownloadSettings.LOCATION_APP)
                            refresh()
                        }
                    }
                )
                DlOption(
                    title = "Custom folder",
                    desc = "Pick any folder on your phone",
                    icon = Icons.Default.FolderOpen,
                    selected = location == DownloadSettings.LOCATION_CUSTOM,
                    enabled = loaded,
                    theme = theme,
                    onSelect = { folderPicker.launch(null) }
                )
                if (location == DownloadSettings.LOCATION_CUSTOM) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Choose a different folder", fontSize = 13.sp) }
                }
            }
            DlCard(theme) {
                Text("Current folder", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = theme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (loaded) displayPath else "Loading…",
                    fontSize = 13.sp, color = theme.onSurface.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DlCard(theme: ModeTheme, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun DlOption(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean,
    theme: ModeTheme,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onSelect() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = theme.onSurface)
            Text(desc, fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f))
        }
    }
}
