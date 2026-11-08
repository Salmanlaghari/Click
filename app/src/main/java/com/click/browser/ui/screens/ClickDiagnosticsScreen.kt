package com.click.browser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.ClickCatLogEntry
import com.click.browser.engine.ClickDiagnosticsLog
import com.click.browser.engine.ModeTheme
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickDiagnosticsScreen(
    theme: ModeTheme,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<ClickCatLogEntry>>(emptyList()) }
    var showClearConfirm by remember { mutableStateOf(false) }

    fun refresh() {
        entries = ClickDiagnosticsLog.read(context).asReversed()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                title = { Text("Click diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = ::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh diagnostics")
                    }
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear diagnostics")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background,
                    titleContentColor = theme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = theme.surface)) {
                Text(
                    "Local Click events only—not Android logcat. Renderer events store the tab ID, sanitized URL origin/path (credentials, query, and fragment removed), GeckoView/Android versions, available memory, event details, and exception class. No page contents, passwords, cookies, exception messages/stacks, or logs from other apps are stored; events stay on this device and are limited to the newest 120.",
                    modifier = Modifier.padding(14.dp),
                    color = theme.onSurface,
                    fontSize = 13.sp
                )
            }
            if (entries.isEmpty()) {
                Text("No diagnostic events yet.", color = theme.onSurface.copy(alpha = 0.7f))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(entries, key = { index, entry -> "${entry.timestampMillis}-${entry.code}-$index" }) { _, entry ->
                        Card(colors = CardDefaults.cardColors(containerColor = theme.surface)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(
                                    entry.code.label,
                                    color = theme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    buildString {
                                        append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(entry.timestampMillis)))
                                        val from = entry.fromMode?.displayName()
                                        val to = entry.toMode?.displayName()
                                        when {
                                            from != null && to != null -> {
                                            append(" · ")
                                            append(from)
                                            append(" → ")
                                            append(to)
                                            }
                                            to != null -> {
                                            append(" · ")
                                            append(to)
                                            }
                                            from != null -> {
                                                append(" · From ")
                                                append(from)
                                            }
                                        }
                                    },
                                    color = theme.onSurface.copy(alpha = 0.65f),
                                    fontSize = 12.sp
                                )
                                entry.rendererDetails?.let { details ->
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Tab ${details.tabId} · ${details.url ?: "URL unavailable"}",
                                        color = theme.onSurface.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "GeckoView ${details.geckoViewVersion} · Android ${details.androidVersion} · " +
                                            (details.availableMemoryMb?.let { "$it MB available" } ?: "memory unavailable"),
                                        color = theme.onSurface.copy(alpha = 0.65f),
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        buildString {
                                            append(details.eventDetails)
                                            details.exceptionType?.let { append(" · Exception: ").append(it) }
                                        },
                                        color = theme.onSurface.copy(alpha = 0.65f),
                                        fontSize = 11.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Click diagnostics?") },
            text = { Text("This removes only the local troubleshooting events. It does not clear browser history, passwords, or site data.") },
            confirmButton = {
                TextButton(onClick = {
                    ClickDiagnosticsLog.clear(context)
                    showClearConfirm = false
                    refresh()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun BrowserMode.displayName(): String = when (this) {
    BrowserMode.SIMPLE -> "Simple"
    BrowserMode.DEVELOPER -> "Developer"
    BrowserMode.HACK -> "Power"
    BrowserMode.ADVANCED -> "Advance"
}
