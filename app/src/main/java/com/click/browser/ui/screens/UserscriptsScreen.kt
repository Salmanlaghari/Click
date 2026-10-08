package com.click.browser.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.UserscriptInfo

/**
 * Userscript Extensions management UI (HACK mode only).
 *
 * Honest framing, stated on-screen: these are Tampermonkey-style
 * `.user.js` userscripts — NOT Chrome Web Store / .crx extensions, which
 * Android WebView technically cannot run. @run-at document-end/idle is
 * fully supported; document-start runs at page finish (WebView's earliest
 * reliable injection point).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserscriptsScreen(
    scripts: List<UserscriptInfo>,
    notice: String?,
    onToggleScript: (String, Boolean) -> Unit,
    onDeleteScript: (String) -> Unit,
    onInstallSource: (String) -> Unit,
    onInstallUrl: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (_: Exception) { null }
            if (!text.isNullOrBlank()) onInstallSource(text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Userscript Extensions", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Tampermonkey-style .user.js scripts",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "These are userscripts — not Chrome Web Store extensions. " +
                                "Android WebView cannot run .crx files. Scripts inject on " +
                                "matching pages in HACK mode. GM_setValue / GM_getValue / " +
                                "GM_addStyle / GM_log are supported.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "@run-at: document-end and document-idle fully supported. " +
                                "document-start runs at page finish (WebView's earliest " +
                                "reliable injection point).",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            if (notice != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
                    ) {
                        Text(
                            notice,
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                Text("Install new script", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("text/*", "application/javascript")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("From file")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Script URL (.user.js)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        if (urlInput.isNotBlank()) {
                            onInstallUrl(urlInput.trim())
                            urlInput = ""
                        }
                    }) {
                        Icon(Icons.Default.Link, contentDescription = "Install from URL")
                    }
                }
            }

            item {
                Text(
                    "Installed (${scripts.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            if (scripts.isEmpty()) {
                item {
                    Text(
                        "No scripts installed yet.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }

            items(scripts, key = { it.id }) { script ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(script.meta.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (script.meta.version.isNotBlank()) {
                                    Text(
                                        "v${script.meta.version}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                if (script.meta.description.isNotBlank()) {
                                    Text(
                                        script.meta.description,
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Switch(
                                checked = script.enabled,
                                onCheckedChange = { onToggleScript(script.id, it) }
                            )
                            IconButton(onClick = { onDeleteScript(script.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                        val patterns = script.meta.matches + script.meta.includes
                        if (patterns.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Runs on: ${patterns.take(3).joinToString(", ")}" +
                                    if (patterns.size > 3) " (+${patterns.size - 3} more)" else "",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Text(
                            "run-at: ${script.meta.runAt}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}
