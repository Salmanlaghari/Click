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
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    currentSearchEngine: String,
    onSearchEngineChange: (String) -> Unit,
    currentHomepage: String,
    onHomepageChange: (String) -> Unit,
    onClearData: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            item {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onThemeChange("Light") },
                        colors = if (currentTheme == "Light") ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Text("Light")
                    }
                    Button(
                        onClick = { onThemeChange("Dark") },
                        colors = if (currentTheme == "Dark") ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Text("Dark")
                    }
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.LightGray,
                    thickness = 1.dp
                )
            }

            item {
                Text("Search Engine", style = MaterialTheme.typography.titleMedium)
                Column {
                    listOf("Google", "Bing", "DuckDuckGo").forEach { engine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSearchEngineChange(engine) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = currentSearchEngine == engine, onClick = { onSearchEngineChange(engine) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(engine)
                        }
                    }
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.LightGray,
                    thickness = 1.dp
                )
            }

            item {
                Text("Default Homepage", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = currentHomepage,
                    onValueChange = onHomepageChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL") }
                )
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.LightGray,
                    thickness = 1.dp
                )
            }

            item {
                Button(
                    onClick = onClearData,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear All Browser Data", color = MaterialTheme.colorScheme.onError)
                }
            }
        }
    }
}
