package com.click.browser.ui.screens

import android.webkit.WebStorage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.ModeTheme

/** One WebStorage origin row: the origin string plus its stored usage in bytes. */
private data class StoredOrigin(val origin: String, val usageBytes: Long)

/** Human-readable byte size for the Data stored list (B / KB / MB / GB). */
internal fun formatStoredBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}

/**
 * Chrome-style "Data stored" screen (Site settings → Data stored): every
 * origin that has saved website storage in the WebView profile, largest
 * first, with per-origin delete and a delete-all action. Backed directly by
 * [WebStorage] — the usage figures are the Origin objects' own usage values
 * returned by [WebStorage.getOrigins].
 *
 * Honest scope: this is WebView-tab storage (local storage, IndexedDB and
 * related site data). Cookies are managed under Clear browsing data / the
 * Cookie Manager, and GeckoView tabs keep their own storage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataStoredScreen(
    theme: ModeTheme,
    onClose: () -> Unit
) {
    var origins by remember { mutableStateOf<List<StoredOrigin>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var originToDelete by remember { mutableStateOf<StoredOrigin?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        WebStorage.getInstance().getOrigins { map ->
            val list = map?.values.orEmpty().mapNotNull { value ->
                val origin = value as? WebStorage.Origin ?: return@mapNotNull null
                StoredOrigin(origin.origin, origin.usage)
            }.sortedByDescending { it.usageBytes }
            origins = list
            loaded = true
        }
    }

    val totalBytes = remember(origins) { origins.sumOf { it.usageBytes } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data stored", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Website storage for WebView tabs (local storage, IndexedDB). " +
                    "Cookies are cleared under Clear browsing data.",
                color = theme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (loaded) {
                Text(
                    if (origins.isEmpty()) "No website data stored."
                    else "Total: ${formatStoredBytes(totalBytes)} across ${origins.size} site${if (origins.size == 1) "" else "s"}",
                    color = theme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            when {
                !loaded -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = theme.primary)
                    }
                }
                origins.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Sites you visit can save data here; nothing is stored yet.",
                            color = theme.onSurface.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(origins, key = { it.origin }) { entry ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = theme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            entry.origin,
                                            color = theme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            formatStoredBytes(entry.usageBytes),
                                            color = theme.onSurface.copy(alpha = 0.6f),
                                            fontSize = 12.sp
                                        )
                                    }
                                    IconButton(onClick = { originToDelete = entry }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete data for ${entry.origin}",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = { showDeleteAll = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text("Delete all data", color = MaterialTheme.colorScheme.onError)
                    }
                }
            }
        }

        originToDelete?.let { entry ->
            AlertDialog(
                onDismissRequest = { originToDelete = null },
                title = { Text("Delete data for ${entry.origin}?") },
                text = {
                    Text(
                        "This removes the ${formatStoredBytes(entry.usageBytes)} this site has " +
                            "saved on the device. The site may sign you out or reset its preferences."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        originToDelete = null
                        WebStorage.getInstance().deleteOrigin(entry.origin)
                        refreshKey++
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { originToDelete = null }) { Text("Cancel") }
                }
            )
        }

        if (showDeleteAll) {
            AlertDialog(
                onDismissRequest = { showDeleteAll = false },
                title = { Text("Delete all stored site data?") },
                text = {
                    Text(
                        "This removes website storage for all ${origins.size} sites " +
                            "(${formatStoredBytes(totalBytes)}). Sites may sign you out or reset " +
                            "their preferences. Cookies are not included — clear those under " +
                            "Clear browsing data."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteAll = false
                        WebStorage.getInstance().deleteAllData()
                        refreshKey++
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
