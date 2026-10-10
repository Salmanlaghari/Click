package com.click.browser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.click.browser.engine.PlaylistAddResult
import com.click.browser.engine.PlaylistItem
import com.click.browser.engine.PlaylistManager
import com.click.browser.engine.PlaylistPlaybackService
import kotlinx.coroutines.launch

/**
 * Playlist screen — save direct media links for later and play them with
 * background audio.
 *
 * Play-policy honesty, enforced in UI copy:
 * - Direct file links only (mp3/mp4/…). Nothing is downloaded — streamed.
 * - YouTube / streaming-service links are refused with an honest message.
 * - No "download", "save video", or service-specific wording anywhere.
 *
 * @param seedUrl one-shot URL from the long-press menu ("Add to Playlist");
 *   consumed once on first composition, null on normal opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistManager: PlaylistManager,
    onClose: () -> Unit,
    seedUrl: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val items by playlistManager.itemsFlow.collectAsState(initial = emptyList())
    val playerState by PlaylistPlaybackService.state.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    fun submitUrl(rawUrl: String) {
        scope.launch {
            when (playlistManager.addItem(rawUrl)) {
                PlaylistAddResult.Added ->
                    snackbar.showSnackbar("Added to playlist.")
                PlaylistAddResult.Duplicate ->
                    snackbar.showSnackbar("This link is already in your playlist.")
                PlaylistAddResult.InvalidUrl ->
                    snackbar.showSnackbar("That doesn't look like a valid link.")
                PlaylistAddResult.UnsupportedService ->
                    snackbar.showSnackbar(
                        "Streaming-service links aren't supported — " +
                            "add a direct audio/video file link instead."
                    )
            }
        }
    }

    // One-shot seed from the long-press menu: consumed once, never re-added.
    var seedConsumed by remember { mutableStateOf(false) }
    if (seedUrl != null && !seedConsumed) {
        seedConsumed = true
        submitUrl(seedUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playlist") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                PlaylistPlaybackService.stop(context)
                                playlistManager.clearAll()
                                snackbar.showSnackbar("Playlist cleared.")
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear playlist")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add link")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Honest scope note — always visible, never buried.
            Text(
                text = "Direct audio/video file links only. Streamed, never downloaded. " +
                    "Streaming-service links (like YouTube) aren't supported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Your playlist is empty.",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap + to add a direct mp3/mp4 link.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(items, key = { it.id }) { item ->
                        PlaylistRow(
                            item = item,
                            isCurrent = item.url == playerState.currentUrl &&
                                playerState.status != PlaylistPlaybackService.Status.IDLE,
                            isPlaying = item.url == playerState.currentUrl &&
                                playerState.status == PlaylistPlaybackService.Status.PLAYING,
                            onPlay = { PlaylistPlaybackService.play(context, item) },
                            onRemove = {
                                scope.launch {
                                    if (item.url == playerState.currentUrl) {
                                        PlaylistPlaybackService.stop(context)
                                    }
                                    playlistManager.removeItem(item.id)
                                }
                            }
                        )
                    }
                }
            }

            // Now-playing bar.
            if (playerState.status != PlaylistPlaybackService.Status.IDLE) {
                NowPlayingBar(
                    title = playerState.currentTitle ?: "Playlist",
                    status = playerState.status,
                    errorMessage = playerState.errorMessage,
                    onPlayPause = {
                        if (playerState.status == PlaylistPlaybackService.Status.PLAYING) {
                            PlaylistPlaybackService.pause(context)
                        } else {
                            PlaylistPlaybackService.resume(context)
                        }
                    },
                    onNext = { PlaylistPlaybackService.next(context) },
                    onPrev = { PlaylistPlaybackService.prev(context) },
                    onStop = { PlaylistPlaybackService.stop(context) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddLinkDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url ->
                showAddDialog = false
                submitUrl(url)
            }
        )
    }
}

@Composable
private fun PlaylistRow(
    item: PlaylistItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            IconButton(onClick = onPlay) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove")
            }
        }
    )
}

@Composable
private fun NowPlayingBar(
    title: String,
    status: PlaylistPlaybackService.Status,
    errorMessage: String?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status == PlaylistPlaybackService.Status.PREPARING) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        when (status) {
                            PlaylistPlaybackService.Status.PLAYING ->
                                "Playing — keeps playing in background"
                            PlaylistPlaybackService.Status.PAUSED -> "Paused"
                            PlaylistPlaybackService.Status.PREPARING -> "Loading…"
                            PlaylistPlaybackService.Status.ERROR ->
                                errorMessage ?: "Couldn't play this link."
                            PlaylistPlaybackService.Status.IDLE -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (status == PlaylistPlaybackService.Status.PLAYING)
                            Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }
            }
        }
    }
}

@Composable
private fun AddLinkDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                Text(
                    "Paste a direct audio/video file link (mp3, mp4…). " +
                        "Streaming-service links aren't supported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Link") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(url) },
                enabled = url.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
