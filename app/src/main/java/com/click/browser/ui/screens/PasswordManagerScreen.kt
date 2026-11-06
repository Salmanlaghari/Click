package com.click.browser.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.PasswordManager
import com.click.browser.engine.SavedPassword
import kotlinx.coroutines.launch

/**
 * Saved passwords management: lists all saved logins (decrypted in memory
 * only), with per-entry delete and a "delete all" option. Passwords are
 * shown masked by default; tap the eye to reveal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordManagerScreen(
    theme: ModeTheme,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passwords by remember { mutableStateOf<List<SavedPassword>>(emptyList()) }
    var revealed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        passwords = PasswordManager.getAll(context)
    }

    fun refresh() {
        scope.launch { passwords = PasswordManager.getAll(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Passwords", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (passwords.isNotEmpty()) {
                        TextButton(onClick = { showDeleteAll = true }) {
                            Text("Delete all", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background,
                    titleContentColor = theme.onSurface
                )
            )
        },
        containerColor = theme.background
    ) { padding ->
        if (passwords.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔐", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No saved passwords yet.",
                        color = theme.onSurface.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                    Text(
                        "Log into a site and Click will offer to save it.",
                        color = theme.onSurface.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "🔒 Encrypted with your device's secure hardware key.",
                        color = theme.onSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(passwords, key = { it.host + it.username }) { pw ->
                    val key = pw.host + pw.username
                    val isRevealed = key in revealed
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    pw.host,
                                    color = theme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    pw.username.ifBlank { "(no username)" },
                                    color = theme.onSurface.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
                                Text(
                                    if (isRevealed) pw.password else "••••••••",
                                    color = theme.onSurface.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            }
                            IconButton(onClick = {
                                revealed = if (isRevealed) revealed - key else revealed + key
                            }) {
                                Icon(
                                    if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isRevealed) "Hide" else "Show",
                                    tint = theme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    PasswordManager.delete(context, pw.host, pw.username)
                                    refresh()
                                }
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteAll) {
            AlertDialog(
                onDismissRequest = { showDeleteAll = false },
                title = { Text("Delete all passwords?") },
                text = { Text("This removes all ${passwords.size} saved logins. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            PasswordManager.clearAll(context)
                            refresh()
                        }
                        showDeleteAll = false
                    }) {
                        Text("Delete all", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAll = false }) { Text("Cancel") }
                }
            )
        }
    }
}
