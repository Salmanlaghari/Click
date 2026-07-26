package com.click.browser.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
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
    onClearData: () -> Unit,
    onClose: () -> Unit
) {
    var expandedModeMenu by remember { mutableStateOf(false) }

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
                    listOf("Google", "Bing", "DuckDuckGo").forEach { engine ->
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
        }
    }
}
