package com.click.browser.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.click.browser.data.Bookmark
import com.click.browser.data.BrowserRepository
import com.click.browser.data.HistoryItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    repository: BrowserRepository,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit
) {
    val bookmarks by repository.bookmarksFlow.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No bookmarks saved yet.")
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
    onNavigate: (String) -> Unit,
    onClose: () -> Unit
) {
    val history by repository.historyFlow.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                repository.clearHistory()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear History")
                        }
                    }
                }
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
                Text("No history recorded.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(history) { item ->
                    ListItem(
                        headlineContent = { Text(item.title.ifEmpty { "No Title" }) },
                        supportingContent = { Text(item.url) },
                        modifier = Modifier.clickable {
                            onNavigate(item.url)
                            onClose()
                        }
                    )
                }
            }
        }
    }
}
