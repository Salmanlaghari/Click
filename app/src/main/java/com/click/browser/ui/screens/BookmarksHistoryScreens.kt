package com.click.browser.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
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
import com.click.browser.data.BookmarkEditResult
import com.click.browser.data.BookmarkEditValidationError
import com.click.browser.data.BookmarkImporter
import com.click.browser.data.BookmarkOrganizationPolicy
import com.click.browser.data.BookmarkOrganizationResult
import com.click.browser.data.validateBookmarkEdit
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
    var bookmarkBeingEdited by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkBeingOrganized by remember { mutableStateOf<Bookmark?>(null) }
    var selectedFolderFilter by remember { mutableStateOf<String?>(null) }
    val folders = remember(bookmarks) {
        bookmarks.mapNotNull { it.folder?.takeIf { folder -> folder.isNotBlank() } }
            .distinct()
            .sortedBy { it.lowercase() }
    }
    val visibleBookmarks = if (selectedFolderFilter == null) {
        bookmarks
    } else {
        bookmarks.filter { it.folder == selectedFolderFilter }
    }
    LaunchedEffect(folders, selectedFolderFilter) {
        if (selectedFolderFilter != null && selectedFolderFilter !in folders) {
            selectedFolderFilter = null
        }
    }

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
                if (folders.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedFolderFilter == null,
                                onClick = { selectedFolderFilter = null },
                                label = { Text("All") }
                            )
                            folders.forEach { folder ->
                                FilterChip(
                                    selected = selectedFolderFilter == folder,
                                    onClick = { selectedFolderFilter = folder },
                                    label = { Text(folder) }
                                )
                            }
                        }
                    }
                }
                if (visibleBookmarks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No bookmarks in this folder.")
                        }
                    }
                }
                items(visibleBookmarks) { bookmark ->
                    ListItem(
                        headlineContent = { Text(bookmark.title) },
                        supportingContent = {
                            Text(
                                bookmark.folder?.let { "Folder: $it · ${bookmark.url}" } ?: bookmark.url,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { bookmarkBeingOrganized = bookmark }) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = "Organize bookmark ${bookmark.title}"
                                    )
                                }
                                IconButton(onClick = { bookmarkBeingEdited = bookmark }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit bookmark ${bookmark.title}")
                                }
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        repository.deleteBookmark(bookmark.url)
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete bookmark ${bookmark.title}")
                                }
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

    bookmarkBeingEdited?.let { bookmark ->
        BookmarkEditorDialog(
            bookmark = bookmark,
            onDismiss = { bookmarkBeingEdited = null },
            onSave = { replacement -> repository.updateBookmark(bookmark.url, replacement) },
            onSaved = {
                bookmarkBeingEdited = null
                coroutineScope.launch { snackbarHostState.showSnackbar("Bookmark updated.") }
            }
        )
    }

    bookmarkBeingOrganized?.let { bookmark ->
        BookmarkFolderDialog(
            bookmark = bookmark,
            folders = folders,
            onDismiss = { bookmarkBeingOrganized = null },
            onSave = { folder -> repository.moveBookmarkToFolder(bookmark.url, folder) },
            onSaved = {
                bookmarkBeingOrganized = null
                coroutineScope.launch { snackbarHostState.showSnackbar("Bookmark organization updated.") }
            }
        )
    }
}

@Composable
private fun BookmarkEditorDialog(
    bookmark: Bookmark,
    onDismiss: () -> Unit,
    onSave: suspend (Bookmark) -> BookmarkEditResult,
    onSaved: () -> Unit
) {
    var title by remember(bookmark.url) { mutableStateOf(bookmark.title) }
    var url by remember(bookmark.url) { mutableStateOf(bookmark.url) }
    var validationMessage by remember(bookmark.url) { mutableStateOf<String?>(null) }
    var isSaving by remember(bookmark.url) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit bookmark") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        validationMessage = null
                    },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        validationMessage = null
                    },
                    label = { Text("Website URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                validationMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val validation = validateBookmarkEdit(title, url)
                    if (validation != null) {
                        validationMessage = when (validation) {
                            BookmarkEditValidationError.EMPTY_TITLE -> "Enter a title for this bookmark."
                            BookmarkEditValidationError.INVALID_URL -> "Enter a complete HTTP or HTTPS URL."
                        }
                    } else {
                        isSaving = true
                        scope.launch {
                            val result = try {
                                onSave(Bookmark(title.trim(), url.trim()))
                            } catch (_: Exception) {
                                validationMessage = "Could not save this bookmark. Try again."
                                isSaving = false
                                return@launch
                            }
                            when (result) {
                                BookmarkEditResult.UPDATED -> onSaved()
                                BookmarkEditResult.DUPLICATE_URL -> validationMessage = "A bookmark with this URL already exists."
                                BookmarkEditResult.NOT_FOUND -> validationMessage = "This bookmark was removed. Close the editor and try again."
                                BookmarkEditResult.INVALID_INPUT -> validationMessage = "Enter a title and a complete HTTP or HTTPS URL."
                            }
                            isSaving = false
                        }
                    }
                }
            ) {
                Text(if (isSaving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}

@Composable
private fun BookmarkFolderDialog(
    bookmark: Bookmark,
    folders: List<String>,
    onDismiss: () -> Unit,
    onSave: suspend (String?) -> BookmarkOrganizationResult,
    onSaved: () -> Unit
) {
    var folderName by remember(bookmark.url) { mutableStateOf(bookmark.folder.orEmpty()) }
    var validationMessage by remember(bookmark.url) { mutableStateOf<String?>(null) }
    var isSaving by remember(bookmark.url) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Organize bookmark") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Choose a folder or enter a name to create one. Leave blank for Unfiled.")
                OutlinedTextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                        validationMessage = null
                    },
                    label = { Text("Folder name") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                if (folders.isNotEmpty()) {
                    Text("Existing folders", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        folders.forEach { folder ->
                            FilterChip(
                                selected = folderName.trim() == folder,
                                onClick = {
                                    folderName = folder
                                    validationMessage = null
                                },
                                label = { Text(folder) },
                                enabled = !isSaving
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        folderName = ""
                        validationMessage = null
                    },
                    enabled = !isSaving
                ) {
                    Text("Move to Unfiled")
                }
                validationMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val normalizedFolder = if (folderName.isBlank()) {
                        null
                    } else {
                        BookmarkOrganizationPolicy.normalizeFolderName(folderName)
                    }
                    if (folderName.isNotBlank() && normalizedFolder == null) {
                        validationMessage = "Folder names must be 1–40 characters and cannot contain slashes."
                    } else {
                        isSaving = true
                        scope.launch {
                            val result = try {
                                onSave(normalizedFolder)
                            } catch (_: Exception) {
                                validationMessage = "Could not organize this bookmark. Try again."
                                isSaving = false
                                return@launch
                            }
                            when (result) {
                                BookmarkOrganizationResult.UPDATED -> onSaved()
                                BookmarkOrganizationResult.NOT_FOUND -> validationMessage = "This bookmark was removed. Close the dialog and try again."
                                BookmarkOrganizationResult.INVALID_FOLDER -> validationMessage = "Folder names must be 1–40 characters and cannot contain slashes."
                            }
                            isSaving = false
                        }
                    }
                }
            ) {
                Text(if (isSaving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
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
