package com.click.browser.ui.screens

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
import androidx.datastore.preferences.core.edit
import com.click.browser.engine.AppSettings
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Accessibility settings: force zoom, reader mode, reduced motion.
 * (Global text scaling lives in Settings → Text Size.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityScreen(
    theme: ModeTheme,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var forceZoom by remember { mutableStateOf(false) }
    var readerMode by remember { mutableStateOf(false) }
    var reducedMotion by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val prefs = context.dataStore.data.first()
            forceZoom = prefs[AppSettings.FORCE_ZOOM] ?: false
            readerMode = prefs[AppSettings.READER_MODE] ?: false
            reducedMotion = prefs[AppSettings.REDUCED_MOTION] ?: false
        } catch (_: Exception) { }
        loaded = true
    }

    fun save(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        scope.launch {
            try { context.dataStore.edit { it[key] = value } } catch (_: Exception) { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accessibility", fontWeight = FontWeight.Bold, color = theme.onSurface) },
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
            A11yCard(theme) {
                A11yToggle(
                    title = "Force zoom",
                    desc = "Allow pinch-zoom on every site, even pages that try to block it. Applies on next page load.",
                    icon = Icons.Default.ZoomIn,
                    checked = forceZoom,
                    enabled = loaded,
                    theme = theme,
                    onChange = { forceZoom = it; save(AppSettings.FORCE_ZOOM, it) }
                )
                HorizontalDivider(color = theme.onSurface.copy(alpha = 0.08f))
                A11yToggle(
                    title = "Reader mode",
                    desc = "Show a clean reading view for articles (distraction-free text). Applies on next page load.",
                    icon = Icons.Default.Article,
                    checked = readerMode,
                    enabled = loaded,
                    theme = theme,
                    onChange = { readerMode = it; save(AppSettings.READER_MODE, it) }
                )
                HorizontalDivider(color = theme.onSurface.copy(alpha = 0.08f))
                A11yToggle(
                    title = "Reduced motion",
                    desc = "Minimize animations and transitions across the app. Applies on next page load.",
                    icon = Icons.Default.MotionPhotosOff,
                    checked = reducedMotion,
                    enabled = loaded,
                    theme = theme,
                    onChange = { reducedMotion = it; save(AppSettings.REDUCED_MOTION, it) }
                )
            }
            A11yCard(theme) {
                Text(
                    "Text size",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = theme.onSurface
                )
                Text(
                    "Global and per-site text scaling lives in Settings → Text Size.",
                    fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun A11yCard(theme: ModeTheme, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun A11yToggle(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    enabled: Boolean,
    theme: ModeTheme,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(desc, fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
