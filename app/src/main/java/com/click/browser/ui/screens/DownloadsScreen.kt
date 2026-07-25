package com.click.browser.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.click.browser.data.BrowserRepository
import com.click.browser.data.DownloadItem
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    repository: BrowserRepository,
    onClose: () -> Unit
) {
    val downloads by repository.downloadsFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No downloads yet.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(downloads) { item ->
                    val dateStr = DateFormat.getDateTimeInstance().format(Date(item.timestamp))
                    ListItem(
                        headlineContent = { Text(item.fileName) },
                        supportingContent = { Text("URL: ${item.url}\nSaved: ${item.path}\nDate: $dateStr") },
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
    }
}
