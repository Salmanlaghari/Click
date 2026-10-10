package com.click.browser.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.data.Bookmark
import com.click.browser.data.BookmarkImporter
import com.click.browser.data.BrowserRepository
import com.click.browser.data.HistoryItem
import com.click.browser.engine.ModeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    repository: BrowserRepository,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit
) {
    val bookmarks by repository.bookmarksFlow.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var isImporting by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImporting = true
        coroutineScope.launch(Dispatchers.IO) {
            val message = try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > BookmarkImporter.MAX_FILE_BYTES) {
                            throw IllegalArgumentException("File too large to import")
                        }
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                } ?: throw IllegalArgumentException("Could not open the selected file")

                val parsed = BookmarkImporter.parse(bytes.toString(Charsets.UTF_8))
                if (parsed.bookmarks.isEmpty()) {
                    "No bookmarks found in this file."
                } else {
                    val result = repository.importBookmarks(parsed.bookmarks)
                    val skipped = result.skippedExisting + parsed.skippedDuplicates
                    buildString {
                        append("Imported ${result.added} bookmark")
                        if (result.added != 1) append("s")
                        if (skipped > 0) append(", skipped $skipped duplicate(s)")
                    }
                }
            } catch (e: Exception) {
                "Import failed: ${e.message ?: "invalid file"}"
            }
            withContext(Dispatchers.Main) {
                isImporting = false
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { importLauncher.launch("text/html") },
                        enabled = !isImporting
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "Import bookmarks from Chrome")
                    }
                }
            )
        }
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No bookmarks saved yet.")
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { importLauncher.launch("text/html") },
                    enabled = !isImporting
                ) {
                    Text(if (isImporting) "Importing..." else "Import bookmarks from Chrome")
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(bookmarks) { bookmark ->
                    ListItem(
                        headlineContent = { Text(bookmark.title) },
                        supportingContent = { Text(bookmark.url) },
                        trailingContent = {
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    repository.deleteBookmark(bookmark.url)
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        },
                        modifier = Modifier.clickable {
                            onNavigate(bookmark.url)
                            onClose()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repository: BrowserRepository,
    theme: ModeTheme,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit
) {
    val history by repository.historyFlow.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                title = { Text("History", color = theme.onSurface, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.primary)
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                repository.clearHistory()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear History", tint = theme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.topBarBg)
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No history recorded.", color = theme.onBackground.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(history) { item ->
                    ListItem(
                        headlineContent = {
                            Text(
                                item.title.ifEmpty { "No Title" },
                                color = theme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supportingContent = {
                            Text(
                                item.url,
                                color = theme.onSurface.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = theme.background),
                        modifier = Modifier.clickable {
                            onNavigate(item.url)
                            onClose()
                        }
                    )
                    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.08f))
                }
            }
        }
    }
}
