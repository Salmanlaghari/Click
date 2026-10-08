package com.click.browser.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.click.browser.engine.AiProviders
import com.click.browser.engine.BrowserMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSettingsScreen(
    currentThemeSetting: String,
    onThemeChange: (String) -> Unit,
    activeMode: BrowserMode,
    onModeChange: (BrowserMode) -> Unit,
    currentSearchEngineSetting: String,
    onSearchEngineChange: (String) -> Unit,
    adBlockerEnabled: Boolean,
    onToggleAdBlocker: (Boolean) -> Unit,
    forceNightMode: Boolean,
    onToggleNightMode: (Boolean) -> Unit,
    httpsOnlyMode: Boolean,
    onToggleHttpsOnly: (Boolean) -> Unit,
    jsEnabled: Boolean,
    onToggleJs: (Boolean) -> Unit,
    dataSaver: Boolean,
    onToggleDataSaver: (Boolean) -> Unit,
    aiApiKey: String,
    onAiApiKeyChange: (String) -> Unit,
    aiProvider: String,
    onAiProviderChange: (String) -> Unit,
    aiModel: String,
    onAiModelChange: (String) -> Unit,
    wallpaperUri: String?,
    onWallpaperChange: (String?) -> Unit,
    onClearData: () -> Unit,
    onClose: () -> Unit
) {
    var expandedModeMenu by remember { mutableStateOf(false) }
    var showAiKey by remember { mutableStateOf(false) }
    val context = LocalContext.current

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Click Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General Category
            item {
                Text("General Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Text("Theme Mode", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Day/Night applies to every browsing mode — each mode has its own light & dark premium theme.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onThemeChange("Light") },
                        colors = if (currentThemeSetting == "Light") ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Text("Light")
                    }
                    Button(
                        onClick = { onThemeChange("Dark") },
                        colors = if (currentThemeSetting == "Dark") ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Text("Dark")
                    }
                }
            }

            item {
                Text("Home Wallpaper", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (wallpaperUri == null) "No custom wallpaper — using the mode theme background."
                    else "Custom wallpaper set.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { wallpaperPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick image")
                    }
                    if (wallpaperUri != null) {
                        OutlinedButton(
                            onClick = { onWallpaperChange(null) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset")
                        }
                    }
                }
            }

            item {
                Text("Active Browser Mode", style = MaterialTheme.typography.titleSmall)
                Box {
                    Button(onClick = { expandedModeMenu = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("Current: ${activeMode.name}")
                    }
                    DropdownMenu(
                        expanded = expandedModeMenu,
                        onDismissRequest = { expandedModeMenu = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BrowserMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.name) },
                                onClick = {
                                    onModeChange(mode)
                                    expandedModeMenu = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Text("Default Search Engine", style = MaterialTheme.typography.titleSmall)
                Column {
                    val engines = when (activeMode) {
                        BrowserMode.SIMPLE -> listOf("Google", "Yahoo", "Bing")
                        BrowserMode.DEVELOPER -> listOf("Yandex", "DuckDuckGo", "Baidu")
                        BrowserMode.HACK -> listOf("Onion/Dark Web search", "Deep Search", "integrated AI search")
                    }

                    LaunchedEffect(activeMode) {
                        if (currentSearchEngineSetting !in engines) {
                            onSearchEngineChange(engines.first())
                        }
                    }

                    engines.forEach { engine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSearchEngineChange(engine) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = currentSearchEngineSetting == engine, onClick = { onSearchEngineChange(engine) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(engine)
                        }
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.fillMaxWidth(), color = Color.Gray, thickness = 1.dp)
            }

            // Privacy Category
            item {
                Text("Privacy & Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Ad Blocker", style = MaterialTheme.typography.titleSmall)
                        Text("Block intrusive ads & track scripts", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = adBlockerEnabled, onCheckedChange = onToggleAdBlocker)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Force Night Mode", style = MaterialTheme.typography.titleSmall)
                        Text("Inject night theme on any web pages", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = forceNightMode, onCheckedChange = onToggleNightMode)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("HTTPS-Only Mode", style = MaterialTheme.typography.titleSmall)
                        Text("Require secure TLS connections", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = httpsOnlyMode, onCheckedChange = onToggleHttpsOnly)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("JavaScript Support", style = MaterialTheme.typography.titleSmall)
                        Text("Enable core scripting execution", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = jsEnabled, onCheckedChange = onToggleJs)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Data Saver Mode", style = MaterialTheme.typography.titleSmall)
                        Text("Reduce web resource overheads", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = dataSaver, onCheckedChange = onToggleDataSaver)
                }
            }

            item {
                Button(
                    onClick = onClearData,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text("Clear Browsing History & Cache", color = MaterialTheme.colorScheme.onError)
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.fillMaxWidth(), color = Color.Gray, thickness = 1.dp)
            }

            // AI Assistant Category — real AI chat via your own API key.
            // The key is stored only in on-device DataStore; never bundled, never logged.
            item {
                Text("AI Assistant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Chat with a real AI using your own key. Keys starting with gsk_ auto-select Groq, sk-or- auto-selects OpenRouter.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                OutlinedTextField(
                    value = aiApiKey,
                    onValueChange = onAiApiKeyChange,
                    label = { Text("AI API Key") },
                    placeholder = { Text("Paste your key here") },
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
            }

            item {
                Text("Provider", style = MaterialTheme.typography.titleSmall)
                Column {
                    AiProviders.all().forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAiProviderChange(p.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = aiProvider == p.id, onClick = { onAiProviderChange(p.id) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(p.displayName)
                                Text(
                                    "Keys start with ${p.keyPrefixHint} • ${p.keySignupUrl}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            item {
                val providerInfo = AiProviders.byId(aiProvider)
                OutlinedTextField(
                    value = aiModel,
                    onValueChange = onAiModelChange,
                    label = { Text("Model (blank = ${providerInfo.defaultModel})") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}
