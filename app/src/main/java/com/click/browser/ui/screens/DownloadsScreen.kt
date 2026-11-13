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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.click.browser.data.BrowserRepository
import com.click.browser.data.CustomDownloadItem
import com.click.browser.data.customDownloadProgress
import com.click.browser.data.CustomDownloadState
import com.click.browser.data.DownloadItem
import com.click.browser.engine.ActiveResponseDownload
import com.click.browser.engine.ActiveResponseDownloads
import com.click.browser.engine.DownloadHelper
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.CustomDownloadNotifications
import com.click.browser.engine.CustomDownloadScheduler
import com.click.browser.engine.forHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
    onClose: () -> Unit,
    openWithRecordId: String? = null,
    onOpenWithConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val downloadsFlow = remember(mode) { repository.downloadsFlowFor(mode) }
    val customDownloadsFlow = remember(mode) { repository.customDownloadsFlowFor(mode) }
    val downloads by downloadsFlow.collectAsState(initial = emptyList())
    val customDownloads by customDownloadsFlow.collectAsState(initial = emptyList())
    // Click's own download engine records (phone/app destinations).
    val clickDownloadStore = remember { com.click.browser.data.ClickDownloadStore(context) }
    val clickDownloadsFlow = remember(mode) { clickDownloadStore.flowFor(mode) }
    val clickDownloads by clickDownloadsFlow.collectAsState(initial = emptyList())
    // Response-body downloads are streamed by Click itself (no DownloadManager
    // entry exists for them); this registry is the only live view of them.
    val activeResponseDownloads by ActiveResponseDownloads.active.collectAsState()
    // Premium open-with sheet target (engine record or saved file).
    var openWithTarget by remember { mutableStateOf<OpenWithTarget?>(null) }
    // APK whose install is waiting on the "install unknown apps" allowance.
    var apkPermissionTarget by remember { mutableStateOf<OpenWithTarget?>(null) }
    /**
     * Opens a finished file. APKs get Click's own install flow instead of
     * the generic Open-With sheet: straight to the system installer when
     * installs from Click are allowed, otherwise a dialog that sends the
     * user to the exact system setting (see ApkInstaller).
     */
    /**
     * Per-site "Automatic downloads" gate, shared by every restart path on
     * this screen (retry, resume, custom-folder retry) so none of them can
     * bypass a site the user blocked in Site settings (Kilo, PR #215).
     * Returns the blocked host, or null when the download may proceed.
     * Fails open on any read error, matching the browser funnel.
     */
    suspend fun blockedDownloadHost(url: String): String? {
        val host = try {
            android.net.Uri.parse(url).host?.lowercase().orEmpty()
        } catch (_: Exception) {
            ""
        }
        if (host.isEmpty()) return null
        val blocked = try {
            repository.siteSettingsFlow.first().forHost(host)?.autoDownloads == false
        } catch (_: Exception) {
            false
        }
        return if (blocked) host else null
    }

    fun toastDownloadBlocked(host: String) {
        Toast.makeText(
            context,
            "Downloads are blocked for $host in Site settings",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun handleOpenTarget(target: OpenWithTarget?) {
        if (target == null) return
        if (com.click.browser.engine.ApkInstaller.isApk(target.mimeType, target.fileName)) {
            if (com.click.browser.engine.ApkInstaller.canInstall(context)) {
                // If the installer intent itself fails (no handler, stale
                // URI), fall back to the Open-With sheet instead of failing
                // silently (Kilo, PR #215).
                if (!com.click.browser.engine.ApkInstaller.launchInstaller(context, target.uri)) {
                    openWithTarget = target
                }
            } else {
                apkPermissionTarget = target
            }
        } else {
            openWithTarget = target
        }
    }
    LaunchedEffect(mode) {
        CustomDownloadScheduler.reconcile(context, mode)
        com.click.browser.engine.ClickDownloadEngine.reconcile(context, mode)
    }
    LaunchedEffect(openWithRecordId, clickDownloads) {
        val recordId = openWithRecordId ?: return@LaunchedEffect
        val record = clickDownloads.firstOrNull { it.id == recordId } ?: return@LaunchedEffect
        if (record.state == com.click.browser.engine.ClickDownloadState.COMPLETED) {
            handleOpenTarget(openWithTargetForRecord(context, record))
            onOpenWithConsumed()
        }
    }
    var notificationsEnabled by remember {
        mutableStateOf(CustomDownloadNotifications.canPostNotifications(context))
    }
    // Same check for Click's own download engine — previously the warning
    // below only looked at the custom-folder checker, so a user whose
    // downloads all ran through the Click engine (the default path) never
    // saw the "notifications are off" warning at all.
    var clickNotificationsEnabled by remember { mutableStateOf(true) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationsEnabled = CustomDownloadNotifications.canPostNotifications(context)
    }
    LaunchedEffect(resumeSignal) {
        notificationsEnabled = CustomDownloadNotifications.canPostNotifications(context)
        clickNotificationsEnabled =
            com.click.browser.engine.ClickDownloadNotifications.canPost(context)
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
    val activeClick = clickDownloads.filter {
        it.isActive || it.state == com.click.browser.engine.ClickDownloadState.PAUSED
    }
    val completedClick = clickDownloads.filter {
        it.state == com.click.browser.engine.ClickDownloadState.COMPLETED
    }
    val failedClick = clickDownloads.filter {
        it.state == com.click.browser.engine.ClickDownloadState.FAILED ||
            it.state == com.click.browser.engine.ClickDownloadState.CANCELED
    }
    val activeClickIds = activeClick.mapTo(mutableSetOf()) { it.id }
    val savedDownloads = downloads.filter {
        (it.transferId == null || (it.transferId !in activeCustomIds && it.transferId !in activeClickIds)) &&
            !isSavedRecordDuplicatedByManaged(it.fileName, it.url, managedDownloads)
    }
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
            if ((!notificationsEnabled || !clickNotificationsEnabled) &&
                (customDownloads.isNotEmpty() || clickDownloads.isNotEmpty() || downloads.isNotEmpty())
            ) {
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

            if (active.isNotEmpty() || activeCustom.isNotEmpty() || activeResponseDownloads.isNotEmpty() || activeClick.isNotEmpty()) {
                item {
                    Text(
                        "In progress",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(activeClick, key = { "click-${it.id}" }) { record ->
                    ClickDownloadRow(
                        record = record,
                        onPause = {
                            scope.launch {
                                com.click.browser.engine.ClickDownloadEngine.pause(context, mode, record.id)
                            }
                        },
                        onResume = {
                            scope.launch {
                                val blockedHost = blockedDownloadHost(record.url)
                                if (blockedHost != null) {
                                    toastDownloadBlocked(blockedHost)
                                } else {
                                    com.click.browser.engine.ClickDownloadEngine.resume(context, mode, record.id)
                                }
                            }
                        },
                        onCancel = {
                            scope.launch {
                                com.click.browser.engine.ClickDownloadEngine.cancel(context, mode, record.id)
                            }
                        }
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
                items(activeResponseDownloads, key = { "response-${it.id}" }) { item ->
                    ActiveResponseDownloadRow(item = item)
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            }

            if (completed.isNotEmpty() || completedClick.isNotEmpty()) {
                item {
                    Text(
                        "Completed",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(completedClick, key = { "click-done-${it.id}" }) { record ->
                    ClickDownloadRow(
                        record = record,
                        onOpen = { handleOpenTarget(openWithTargetForRecord(context, record)) },
                        onDismiss = {
                            scope.launch {
                                com.click.browser.engine.ClickDownloadEngine.dismiss(context, mode, record.id)
                            }
                        }
                    )
                }
                items(completed, key = { it.id }) { item ->
                    ManagedDownloadRow(
                        item = item,
                        onOpen = {
                            // Route through the same handler as every other
                            // row, so a managed APK also gets the
                            // install-permission flow instead of a bare
                            // ACTION_VIEW (Kilo, PR #215).
                            val target = managedOpenTarget(context, item.id, item.label)
                            if (target == null) {
                                Toast.makeText(
                                    context,
                                    "This downloaded file is no longer available.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                handleOpenTarget(target)
                            }
                        },
                        onShare = { shareManagedDownload(context, item.id, item.label) }
                    )
                }
            }

            if (failed.isNotEmpty() || failedClick.isNotEmpty()) {
                item {
                    Text(
                        "Failed",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(failedClick, key = { "click-failed-${it.id}" }) { record ->
                    ClickDownloadRow(
                        record = record,
                        onResume = {
                            scope.launch {
                                val blockedHost = blockedDownloadHost(record.url)
                                if (blockedHost != null) {
                                    toastDownloadBlocked(blockedHost)
                                } else {
                                    com.click.browser.engine.ClickDownloadEngine.resume(context, mode, record.id)
                                }
                            }
                        },
                        onDismiss = {
                            scope.launch {
                                com.click.browser.engine.ClickDownloadEngine.dismiss(context, mode, record.id)
                            }
                        }
                    )
                }
                items(failed, key = { it.id }) { item ->
                    FailedDownloadRow(
                        item = item,
                        onRetry = {
                            scope.launch {
                                // Per-site gate (shared helper): a retry
                                // cannot bypass a blocked site.
                                val blockedHost = blockedDownloadHost(item.sourceUrl)
                                if (blockedHost != null) {
                                    toastDownloadBlocked(blockedHost)
                                    return@launch
                                }
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
                                val blockedHost = blockedDownloadHost(item.url)
                                if (blockedHost != null) {
                                    toastDownloadBlocked(blockedHost)
                                } else if (!DownloadHelper.retryCustomFolder(context, item)) {
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
                items(savedDownloads) { item ->
                    SavedDownloadRow(
                        context = context,
                        item = item,
                        onOpen = { handleOpenTarget(openWithTargetForSaved(context, item)) }
                    )
                }
            }

            if (queryState.shouldShowEmptyState(
                    savedFileCount = savedDownloads.size + activeCustom.size + failedCustom.size +
                        activeResponseDownloads.size + activeClick.size + completedClick.size + failedClick.size
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

    apkPermissionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { apkPermissionTarget = null },
            title = { Text("Allow installs from Click") },
            text = {
                Text(
                    "To install ${target.fileName}, Android needs you to allow Click to " +
                        "install apps. Open Settings, turn on \"Allow from this source\", " +
                        "then come back and tap the file again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    com.click.browser.engine.ApkInstaller.openUnknownAppSourcesSettings(context)
                    apkPermissionTarget = null
                }) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { apkPermissionTarget = null }) {
                    Text("Not now")
                }
            }
        )
    }

    openWithTarget?.let { target ->
        OpenWithSheet(target = target, onDismiss = { openWithTarget = null })
    }
}

/** Live row for a response-body download Click is streaming to storage itself. */
@Composable
private fun ActiveResponseDownloadRow(item: ActiveResponseDownload) {
    val progress = if (item.totalBytes > 0) {
        (item.downloadedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else null
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = androidx.compose.animation.core.tween(400),
        label = "response-download-progress"
    )
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDownloadIcon(running = true)
            Spacer(Modifier.width(8.dp))
            Text(item.fileName, style = MaterialTheme.typography.bodyMedium)
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Downloading… · $sizeText · Saving to ${item.destinationLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
private fun SavedDownloadRow(context: Context, item: DownloadItem, onOpen: () -> Unit) {
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
            TextButton(onClick = onOpen, enabled = available) {
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

/** Builds an open target for a system DownloadManager row, or null when
 *  the file is gone. The caller routes it through the shared open handler
 *  (APK install flow included) and toasts on null. */
private fun managedOpenTarget(context: Context, id: Long, label: String): OpenWithTarget? {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return null
    val uri = try { dm.getUriForDownloadedFile(id) } catch (_: Exception) { null } ?: return null
    val mimeType = try { dm.getMimeTypeForDownloadedFile(id) } catch (_: Exception) { null }
        ?.takeIf { it.isNotBlank() } ?: "*/*"
    return OpenWithTarget(uri, mimeType, label)
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

/** Animated download glyph: pulses while a transfer is actually running. */
@Composable
private fun PulsingDownloadIcon(running: Boolean) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "download-pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(650),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "download-pulse-alpha"
    )
    Icon(
        Icons.Default.Download,
        contentDescription = null,
        modifier = Modifier
            .size(18.dp)
            .then(if (running) Modifier.graphicsLayer { this.alpha = alpha } else Modifier),
        tint = MaterialTheme.colorScheme.primary
    )
}

/** Row for one of Click's own engine downloads, in any lifecycle state. */
@Composable
private fun ClickDownloadRow(
    record: com.click.browser.engine.ClickDownloadRecord,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val state = record.state
    val progress = com.click.browser.data.customDownloadProgress(record.bytesDownloaded, record.totalBytes)
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = androidx.compose.animation.core.tween(400),
        label = "click-download-progress"
    )
    val sizeText = if (record.totalBytes > 0L) {
        "${formatBytes(record.bytesDownloaded)} of ${formatBytes(record.totalBytes)}"
    } else if (record.bytesDownloaded > 0L) {
        formatBytes(record.bytesDownloaded)
    } else {
        ""
    }
    val statusText = when (state) {
        com.click.browser.engine.ClickDownloadState.QUEUED -> "Waiting to start…"
        com.click.browser.engine.ClickDownloadState.RUNNING -> "Downloading…"
        com.click.browser.engine.ClickDownloadState.PAUSED ->
            record.errorMessage ?: "Paused"
        com.click.browser.engine.ClickDownloadState.FAILED ->
            record.errorMessage ?: "Download failed."
        com.click.browser.engine.ClickDownloadState.CANCELED -> "Canceled."
        com.click.browser.engine.ClickDownloadState.COMPLETED -> "Saved."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state == com.click.browser.engine.ClickDownloadState.RUNNING ||
                state == com.click.browser.engine.ClickDownloadState.QUEUED
            ) {
                PulsingDownloadIcon(running = state == com.click.browser.engine.ClickDownloadState.RUNNING)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                record.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (record.isActive || state == com.click.browser.engine.ClickDownloadState.PAUSED) {
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        Text(
            listOf(statusText, sizeText).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = if (state == com.click.browser.engine.ClickDownloadState.FAILED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Row {
            if (state == com.click.browser.engine.ClickDownloadState.RUNNING ||
                state == com.click.browser.engine.ClickDownloadState.QUEUED
            ) {
                if (onPause != null) TextButton(onClick = onPause) { Text("Pause") }
                if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancel") }
            }
            if (state == com.click.browser.engine.ClickDownloadState.PAUSED && onResume != null) {
                TextButton(onClick = onResume) { Text("Resume") }
                if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancel") }
            }
            if (state == com.click.browser.engine.ClickDownloadState.FAILED && onResume != null) {
                TextButton(onClick = onResume) { Text("Resume") }
            }
            if (state == com.click.browser.engine.ClickDownloadState.COMPLETED && onOpen != null) {
                TextButton(onClick = onOpen) { Text("Open") }
            }
            if (onDismiss != null &&
                (state == com.click.browser.engine.ClickDownloadState.COMPLETED ||
                    state == com.click.browser.engine.ClickDownloadState.FAILED ||
                    state == com.click.browser.engine.ClickDownloadState.CANCELED)
            ) {
                TextButton(onClick = onDismiss) { Text("Remove") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Premium open-with sheet: lists the device apps that can open a file
// (PackageManager query, real icons + names) instead of jumping straight
// into a single handler. Falls back to the system chooser when no handler
// is found.
// ---------------------------------------------------------------------------

private data class OpenWithTarget(
    val uri: Uri,
    val mimeType: String,
    val fileName: String
)

private fun openWithTargetForRecord(
    context: Context,
    record: com.click.browser.engine.ClickDownloadRecord
): OpenWithTarget? {
    val location = record.finalLocation?.takeIf { it.isNotBlank() } ?: return null
    val uri = if (location.startsWith("content://")) {
        Uri.parse(location)
    } else {
        val file = File(location)
        if (!file.isFile) return null
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return null
    }
    val mimeType = record.mimeType?.takeIf { it.isNotBlank() }
        ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
        ?: MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(record.fileName.substringAfterLast('.', "").lowercase())
        ?: "*/*"
    return OpenWithTarget(uri, mimeType, record.fileName)
}

private fun openWithTargetForSaved(context: Context, item: DownloadItem): OpenWithTarget? {
    val uri = savedDownloadUri(context, item) ?: return null
    return OpenWithTarget(uri, savedDownloadMimeType(context, item, uri), item.fileName)
}

private data class OpenHandler(
    val label: String,
    val iconBitmap: androidx.compose.ui.graphics.ImageBitmap,
    val component: android.content.ComponentName
)

private fun queryOpenHandlers(context: Context, target: OpenWithTarget): List<OpenHandler> {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(target.uri, target.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    @Suppress("DEPRECATION")
    val resolved = runCatching {
        context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
    }.getOrDefault(emptyList())
    return resolved.mapNotNull { info ->
        runCatching {
            val label = info.loadLabel(context.packageManager).toString()
            val icon = info.loadIcon(context.packageManager).toBitmap(96, 96)
                .asImageBitmap()
            OpenHandler(
                label = label,
                iconBitmap = icon,
                component = android.content.ComponentName(info.activityInfo.packageName, info.activityInfo.name)
            )
        }.getOrNull()
    }.distinctBy { it.component }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenWithSheet(target: OpenWithTarget, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val handlers = remember(target) { queryOpenHandlers(context, target) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "Open with",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Text(
                target.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            if (handlers.isEmpty()) {
                Text(
                    "No app on this device can open this file.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(target.uri, target.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(Intent.createChooser(intent, "Open ${target.fileName}"))
                        onDismiss()
                    } catch (_: Exception) {
                        Toast.makeText(context, "No app can open this file.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Try system chooser") }
            } else {
                handlers.forEach { handler ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(target.uri, target.mimeType)
                                    component = handler.component
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Couldn't open with ${handler.label}.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = handler.iconBitmap,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(handler.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
