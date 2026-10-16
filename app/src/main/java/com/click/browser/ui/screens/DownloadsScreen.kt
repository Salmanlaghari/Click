package com.click.browser.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.click.browser.data.BrowserRepository
import com.click.browser.data.DownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** One in-progress system download started by Click (title-filtered). */
private data class ActiveDownload(
    val id: Long,
    val label: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val status: Int
)

/**
 * Downloads screen: live progress for in-progress downloads + the
 * completed-download history.
 *
 * Active downloads come from Android DownloadManager, filtered by the
 * title Click uses for every enqueue ("Click Browser Download"), so
 * other apps' downloads never appear here. Polled once a second while
 * the screen is open; the poll stops when the screen closes.
 * Cancel removes the system download (DownloadManager.remove).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    repository: BrowserRepository,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val downloads by repository.downloadsFlow.collectAsState(initial = emptyList())
    var active by remember { mutableStateOf<List<ActiveDownload>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            active = queryActiveDownloads(context)
            delay(1000)
        }
    }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (active.isNotEmpty()) {
                item {
                    Text(
                        "Downloading",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(active, key = { it.id }) { item ->
                    ActiveDownloadRow(
                        item = item,
                        onCancel = {
                            try {
                                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                dm.remove(item.id)
                            } catch (_: Exception) { }
                        }
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            }
            if (downloads.isEmpty() && active.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No downloads yet.")
                    }
                }
            } else if (downloads.isNotEmpty()) {
                item {
                    Text(
                        "Completed",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
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

@Composable
private fun ActiveDownloadRow(item: ActiveDownload, onCancel: () -> Unit) {
    val progress = if (item.totalBytes > 0) {
        (item.downloadedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else null
    val statusText = when (item.status) {
        DownloadManager.STATUS_PAUSED -> "Paused"
        DownloadManager.STATUS_PENDING -> "Waiting…"
        else -> "Downloading…"
    }
    val sizeText = if (item.totalBytes > 0) {
        "${formatBytes(item.downloadedBytes)} of ${formatBytes(item.totalBytes)}"
    } else {
        formatBytes(item.downloadedBytes)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "$statusText · $sizeText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B" else "%.1f %s".format(value, units[unit])
}

/**
 * Queries DownloadManager for Click's in-progress downloads only.
 * Runs on IO; returns an empty list on any failure (never crashes the UI).
 */
private suspend fun queryActiveDownloads(context: Context): List<ActiveDownload> =
    withContext(Dispatchers.IO) {
        val result = mutableListOf<ActiveDownload>()
        var cursor: Cursor? = null
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            cursor = dm.query(
                DownloadManager.Query().setFilterByStatus(
                    DownloadManager.STATUS_RUNNING or
                        DownloadManager.STATUS_PAUSED or
                        DownloadManager.STATUS_PENDING
                )
            )
            val idCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val titleCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            val descCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION)
            val downloadedCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val statusCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            while (cursor.moveToNext()) {
                // Only Click's own downloads (both enqueue paths use this title).
                if (cursor.getString(titleCol) != "Click Browser Download") continue
                val desc = cursor.getString(descCol).orEmpty()
                result.add(
                    ActiveDownload(
                        id = cursor.getLong(idCol),
                        label = desc.ifBlank { "Click download" }.take(80),
                        downloadedBytes = cursor.getLong(downloadedCol),
                        totalBytes = cursor.getLong(totalCol),
                        status = cursor.getInt(statusCol)
                    )
                )
            }
        } catch (_: Exception) {
            // DownloadManager unavailable or query failed — show no actives.
        } finally {
            try { cursor?.close() } catch (_: Exception) { }
        }
        result
    }
