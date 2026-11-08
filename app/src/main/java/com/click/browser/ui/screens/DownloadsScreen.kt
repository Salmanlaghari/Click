package com.click.browser.ui.screens

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.widget.Toast
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
import com.click.browser.engine.DownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private data class ManagedDownload(
    val id: Long,
    val label: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val state: ManagedDownloadState,
    val sourceUrl: String
)

/**
 * Downloads screen: live DownloadManager state for Click-started downloads plus
 * saved Click download records. Successful and failed system downloads remain
 * visible after they leave the active state. The system manager continues to
 * own transfer notifications and file access.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    repository: BrowserRepository,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val downloads by repository.downloadsFlow.collectAsState(initial = emptyList())
    var managedDownloads by remember { mutableStateOf<List<ManagedDownload>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (isActive) {
            managedDownloads = queryManagedDownloads(context)
            delay(1000)
        }
    }

    val active = managedDownloads.filter {
        it.state == ManagedDownloadState.PENDING ||
            it.state == ManagedDownloadState.RUNNING ||
            it.state == ManagedDownloadState.PAUSED
    }
    val completed = managedDownloads.filter { it.state == ManagedDownloadState.COMPLETED }
    val failed = managedDownloads.filter { it.state == ManagedDownloadState.FAILED }

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
                        "In progress",
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
                            } catch (_: Exception) {
                                Toast.makeText(context, "Could not cancel this download.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            }

            if (completed.isNotEmpty()) {
                item {
                    Text(
                        "Completed",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(completed, key = { it.id }) { item ->
                    ManagedDownloadRow(
                        item = item,
                        onOpen = { openManagedDownload(context, item.id, item.label) },
                        onShare = { shareManagedDownload(context, item.id, item.label) }
                    )
                }
            }

            if (failed.isNotEmpty()) {
                item {
                    Text(
                        "Failed",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(failed, key = { it.id }) { item ->
                    FailedDownloadRow(
                        item = item,
                        onRetry = {
                            scope.launch {
                                DownloadHelper.enqueue(context, item.sourceUrl, item.label)
                            }
                        }
                    )
                }
            }

            if (downloads.isNotEmpty()) {
                item {
                    Text(
                        "Saved files",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(downloads) { item -> SavedDownloadRow(item) }
            }

            if (managedDownloads.isEmpty() && downloads.isEmpty()) {
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
            }
        }
    }
}

@Composable
private fun ActiveDownloadRow(item: ManagedDownload, onCancel: () -> Unit) {
    val progress = if (item.totalBytes > 0) {
        (item.downloadedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else null
    val statusText = when (item.state) {
        ManagedDownloadState.PAUSED -> "Paused"
        ManagedDownloadState.PENDING -> "Waiting…"
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

@Composable
private fun ManagedDownloadRow(
    item: ManagedDownload,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(item.label, style = MaterialTheme.typography.bodyMedium)
        if (item.totalBytes > 0) {
            Text(
                formatBytes(item.totalBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row {
            TextButton(onClick = onOpen) { Text("Open") }
            TextButton(onClick = onShare) { Text("Share") }
        }
    }
}

@Composable
private fun FailedDownloadRow(item: ManagedDownload, onRetry: () -> Unit) {
    val retryable = item.sourceUrl.startsWith("https://", ignoreCase = true) ||
        item.sourceUrl.startsWith("http://", ignoreCase = true)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(item.label, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (retryable) "Download failed" else "Download failed · retry is unavailable for this item",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        if (retryable) {
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun SavedDownloadRow(item: DownloadItem) {
    val dateStr = DateFormat.getDateTimeInstance().format(Date(item.timestamp))
    ListItem(
        headlineContent = { Text(item.fileName) },
        supportingContent = { Text("URL: ${item.url}\nSaved: ${item.path}\nDate: $dateStr") },
        modifier = Modifier.padding(4.dp)
    )
}

private fun openManagedDownload(context: Context, id: Long, label: String) {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
    val uri = try { dm?.getUriForDownloadedFile(id) } catch (_: Exception) { null }
    if (uri == null) {
        Toast.makeText(context, "This downloaded file is no longer available.", Toast.LENGTH_SHORT).show()
        return
    }
    val mimeType = try { dm?.getMimeTypeForDownloadedFile(id) } catch (_: Exception) { null }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType?.takeIf { it.isNotBlank() } ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No app can open this file.", Toast.LENGTH_SHORT).show()
    }
}

private fun shareManagedDownload(context: Context, id: Long, label: String) {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
    val uri = try { dm?.getUriForDownloadedFile(id) } catch (_: Exception) { null }
    if (uri == null) {
        Toast.makeText(context, "This downloaded file is no longer available.", Toast.LENGTH_SHORT).show()
        return
    }
    val mimeType = try { dm?.getMimeTypeForDownloadedFile(id) } catch (_: Exception) { null }
        ?.takeIf { it.isNotBlank() } ?: "*/*"
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, label, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(sendIntent, "Share $label"))
    } catch (_: Exception) {
        Toast.makeText(context, "Could not share this file.", Toast.LENGTH_SHORT).show()
    }
}

/** Queries all Click-owned DownloadManager lifecycle states; unrelated apps are excluded. */
private suspend fun queryManagedDownloads(context: Context): List<ManagedDownload> =
    withContext(Dispatchers.IO) {
        val result = mutableListOf<ManagedDownload>()
        var cursor: Cursor? = null
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val statuses = DownloadManager.STATUS_RUNNING or
                DownloadManager.STATUS_PAUSED or
                DownloadManager.STATUS_PENDING or
                DownloadManager.STATUS_SUCCESSFUL or
                DownloadManager.STATUS_FAILED
            cursor = dm.query(DownloadManager.Query().setFilterByStatus(statuses))
            val idCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val titleCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            val descCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION)
            val downloadedCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val statusCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val uriCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
            while (cursor.moveToNext()) {
                val status = cursor.getInt(statusCol)
                if (!isClickManagedDownload(cursor.getString(titleCol), status)) continue
                result.add(
                    ManagedDownload(
                        id = cursor.getLong(idCol),
                        label = cursor.getString(descCol).orEmpty().ifBlank { "Click download" }.take(120),
                        downloadedBytes = cursor.getLong(downloadedCol),
                        totalBytes = cursor.getLong(totalCol),
                        state = managedDownloadState(status) ?: continue,
                        sourceUrl = cursor.getString(uriCol).orEmpty()
                    )
                )
            }
        } catch (_: Exception) {
            // DownloadManager unavailable or query failed — do not crash the screen.
        } finally {
            try { cursor?.close() } catch (_: Exception) { }
        }
        result
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
