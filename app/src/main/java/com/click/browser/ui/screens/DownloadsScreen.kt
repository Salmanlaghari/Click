package com.click.browser.ui.screens

import android.app.DownloadManager
import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.click.browser.data.BrowserRepository
import com.click.browser.data.CustomDownloadItem
import com.click.browser.data.customDownloadProgress
import com.click.browser.data.CustomDownloadState
import com.click.browser.data.DownloadItem
import com.click.browser.engine.DownloadHelper
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.CustomDownloadNotifications
import com.click.browser.engine.CustomDownloadScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Downloads screen: live DownloadManager state for Click-started downloads plus
 * saved Click download records. Successful and failed system downloads remain
 * visible after they leave the active state. Files copied to a custom SAF folder
 * are retained as saved-file records with their provider URI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    repository: BrowserRepository,
    mode: BrowserMode,
    resumeSignal: Long,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val downloadsFlow = remember(mode) { repository.downloadsFlowFor(mode) }
    val customDownloadsFlow = remember(mode) { repository.customDownloadsFlowFor(mode) }
    val downloads by downloadsFlow.collectAsState(initial = emptyList())
    val customDownloads by customDownloadsFlow.collectAsState(initial = emptyList())
    LaunchedEffect(mode) {
        CustomDownloadScheduler.reconcile(context, mode)
    }
    var notificationsEnabled by remember {
        mutableStateOf(CustomDownloadNotifications.canPostNotifications(context))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationsEnabled = CustomDownloadNotifications.canPostNotifications(context)
    }
    LaunchedEffect(resumeSignal) {
        notificationsEnabled = CustomDownloadNotifications.canPostNotifications(context)
    }
    var queryState by remember {
        mutableStateOf<DownloadCenterQueryState>(DownloadCenterQueryState.Loading)
    }
    var retryToken by remember { mutableStateOf(0) }
    var isRetrying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(retryToken) {
        while (isActive) {
            val result = queryManagedDownloads(context)
            if (result.isSuccess) {
                queryState = DownloadCenterQueryState.Loaded(result.getOrThrow())
                isRetrying = false
            } else {
                queryState = queryState.recordFailure()
                isRetrying = false
                return@LaunchedEffect
            }
            delay(2_000)
        }
    }

    val managedDownloads = queryState.downloadsForDisplay()
    val active = managedDownloads.filter {
        it.state == ManagedDownloadState.PENDING ||
            it.state == ManagedDownloadState.RUNNING ||
            it.state == ManagedDownloadState.PAUSED
    }
    val completed = managedDownloads.filter { it.state == ManagedDownloadState.COMPLETED }
    val failed = managedDownloads.filter { it.state == ManagedDownloadState.FAILED }
    val activeCustom = customDownloads.filter { it.canCancel }
    val activeCustomIds = activeCustom.mapTo(mutableSetOf()) { it.id }
    val savedDownloads = downloads.filter { it.transferId == null || it.transferId !in activeCustomIds }
    val failedCustom = customDownloads.filter {
        it.state == CustomDownloadState.FAILED || it.state == CustomDownloadState.CANCELED
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
            if (!notificationsEnabled && customDownloads.isNotEmpty()) {
                item(key = "custom-download-notification-permission") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "Download notifications are off",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Transfers remain visible here, but Android may not show progress or completion notifications.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = {
                                if (!CustomDownloadNotifications.hasRuntimePermission(context)) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val settingsIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        putExtra(Settings.EXTRA_CHANNEL_ID, CustomDownloadNotifications.CHANNEL_ID)
                                    }
                                    runCatching { context.startActivity(settingsIntent) }
                                }
                            }) {
                                Text("Enable notifications")
                            }
                        }
                    }
                }
            }

            when (val state = queryState) {
                DownloadCenterQueryState.Loading -> {
                    if (downloads.isEmpty()) {
                        item(key = "downloads-query-loading") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Checking downloads…")
                            }
                        }
                    }
                }
                is DownloadCenterQueryState.Failed -> {
                    item(key = "downloads-query-error") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                if (state.lastSuccessfulDownloads == null) {
                                    "Couldn't load downloads."
                                } else {
                                    "Couldn't refresh downloads."
                                },
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Android's download list is unavailable. Saved Click records are unaffected.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    isRetrying = true
                                    retryToken += 1
                                },
                                enabled = !isRetrying
                            ) {
                                Text(if (isRetrying) "Retrying…" else "Retry")
                            }
                        }
                    }
                }
                is DownloadCenterQueryState.Loaded -> Unit
            }

            if (active.isNotEmpty() || activeCustom.isNotEmpty()) {
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
                items(activeCustom, key = { "custom-${it.id}" }) { item ->
                    CustomTransferRow(
                        item = item,
                        onCancel = { scope.launch { CustomDownloadScheduler.cancel(context, item) } },
                        onRetry = {}
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
                        onOpen = { openManagedDownload(context, item.id) },
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
                                val saved = DownloadHelper.enqueue(
                                    context,
                                    item.sourceUrl,
                                    item.label,
                                    mode = mode
                                )
                                if (saved != null) {
                                    repository.addDownloadItemFor(
                                        mode,
                                        DownloadItem(
                                            saved.fileName,
                                            item.sourceUrl,
                                            saved.location,
                                            System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            }

            if (failedCustom.isNotEmpty()) {
                items(failedCustom, key = { "custom-failed-${it.id}" }) { item ->
                    CustomTransferRow(
                        item = item,
                        onCancel = {},
                        onRetry = {
                            scope.launch {
                                if (!DownloadHelper.retryCustomFolder(context, item)) {
                                    Toast.makeText(context, "Could not restart this download.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            if (savedDownloads.isNotEmpty()) {
                item {
                    Text(
                        "Saved files",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(savedDownloads) { item -> SavedDownloadRow(context, item) }
            }

            if (queryState.shouldShowEmptyState(
                    savedFileCount = savedDownloads.size + activeCustom.size + failedCustom.size
                )
            ) {
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
private fun CustomTransferRow(
    item: CustomDownloadItem,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val progress = customDownloadProgress(item.bytesDownloaded, item.totalBytes)
    val status = when (item.state) {
        CustomDownloadState.QUEUED -> "Waiting for network…"
        CustomDownloadState.RUNNING -> "Downloading…"
        CustomDownloadState.FAILED -> item.errorMessage ?: "Download failed."
        CustomDownloadState.CANCELED -> "Canceled."
        CustomDownloadState.COMPLETED -> "Saved."
    }
    val sizeText = if (item.totalBytes > 0L) {
        "${formatBytes(item.bytesDownloaded)} of ${formatBytes(item.totalBytes)}"
    } else {
        "${formatBytes(item.bytesDownloaded)} downloaded"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            item.fileName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (item.canCancel) {
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                "$status · $sizeText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.state == CustomDownloadState.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Row {
            if (item.canCancel) TextButton(onClick = onCancel) { Text("Cancel") }
            if (item.canRetry) TextButton(onClick = onRetry) { Text("Retry") }
        }
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
private fun SavedDownloadRow(context: Context, item: DownloadItem) {
    val dateStr = DateFormat.getDateTimeInstance().format(Date(item.timestamp))
    val location = parseSavedDownloadLocation(item.path)
    val available = when (location) {
        is SavedDownloadLocation.ContentUri -> true
        is SavedDownloadLocation.FilePath -> File(location.absolutePath).isFile
        null -> false
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(item.fileName, style = MaterialTheme.typography.bodyMedium)
        if (item.url.isNotBlank()) {
            Text(
                "URL: ${item.url}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "Saved: ${item.path}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text("Date: $dateStr", style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(onClick = { openSavedDownload(context, item) }, enabled = available) {
                Text("Open")
            }
            TextButton(onClick = { shareSavedDownload(context, item) }, enabled = available) {
                Text("Share")
            }
        }
        if (!available) {
            Text(
                "This saved file is no longer available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun savedDownloadUri(context: Context, item: DownloadItem): Uri? {
    return when (val location = parseSavedDownloadLocation(item.path)) {
        is SavedDownloadLocation.ContentUri -> Uri.parse(location.value)
        is SavedDownloadLocation.FilePath -> {
            val file = File(location.absolutePath)
            if (!file.isFile) null else runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull()
        }
        null -> null
    }
}

private fun savedDownloadMimeType(context: Context, item: DownloadItem, uri: Uri): String =
    runCatching { context.contentResolver.getType(uri) }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(item.fileName.substringAfterLast('.', "").lowercase())
        ?: "*/*"

private fun openSavedDownload(context: Context, item: DownloadItem) {
    val uri = savedDownloadUri(context, item)
    if (uri == null) {
        Toast.makeText(context, "This saved file is no longer available.", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, savedDownloadMimeType(context, item, uri))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No app can open this file.", Toast.LENGTH_SHORT).show()
    }
}

private fun shareSavedDownload(context: Context, item: DownloadItem) {
    val uri = savedDownloadUri(context, item)
    if (uri == null) {
        Toast.makeText(context, "This saved file is no longer available.", Toast.LENGTH_SHORT).show()
        return
    }
    val mimeType = savedDownloadMimeType(context, item, uri)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, item.fileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(sendIntent, "Share ${item.fileName}"))
    } catch (_: Exception) {
        Toast.makeText(context, "Could not share this file.", Toast.LENGTH_SHORT).show()
    }
}

private fun openManagedDownload(context: Context, id: Long) {
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
private suspend fun queryManagedDownloads(context: Context): Result<List<ManagedDownload>> =
    withContext(Dispatchers.IO) {
        val active = mutableListOf<ManagedDownload>()
        val recentHistory = mutableListOf<ManagedDownload>()
        var cursor: Cursor? = null
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: throw IllegalStateException("DownloadManager unavailable")
            val statuses = DownloadManager.STATUS_RUNNING or
                DownloadManager.STATUS_PAUSED or
                DownloadManager.STATUS_PENDING or
                DownloadManager.STATUS_SUCCESSFUL or
                DownloadManager.STATUS_FAILED
            cursor = dm.query(DownloadManager.Query().setFilterByStatus(statuses))
            val queryCursor = cursor ?: throw IllegalStateException("DownloadManager returned no results")
            val idCol = queryCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val titleCol = queryCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            val descCol = queryCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION)
            val downloadedCol = queryCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = queryCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val statusCol = queryCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val uriCol = queryCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
            while (queryCursor.moveToNext()) {
                val status = queryCursor.getInt(statusCol)
                if (!isClickManagedDownload(queryCursor.getString(titleCol), status)) continue
                val state = managedDownloadState(status) ?: continue
                val item = ManagedDownload(
                    id = queryCursor.getLong(idCol),
                    label = queryCursor.getString(descCol).orEmpty().ifBlank { "Click download" }.take(120),
                    downloadedBytes = queryCursor.getLong(downloadedCol),
                    totalBytes = queryCursor.getLong(totalCol),
                    state = state,
                    sourceUrl = queryCursor.getString(uriCol).orEmpty()
                )
                when (state) {
                    ManagedDownloadState.PENDING,
                    ManagedDownloadState.RUNNING,
                    ManagedDownloadState.PAUSED -> active.add(item)
                    ManagedDownloadState.COMPLETED,
                    ManagedDownloadState.FAILED -> retainRecentManagedDownload(recentHistory, item)
                }
            }
            Result.success(active.sortedByDescending { it.id } + recentHistory)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { cursor?.close() } catch (_: Exception) { }
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
