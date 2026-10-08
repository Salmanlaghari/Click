package com.click.browser.ui.screens

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Real on-device audio player: lists the device's music library (MediaStore)
// and plays it with MediaPlayer. Empty library => honest empty state.
// ---------------------------------------------------------------------------

private data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val durationMs: Long
)

private fun queryDeviceAudio(context: Context): List<AudioTrack> {
    val out = mutableListOf<AudioTrack>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    try {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                out.add(
                    AudioTrack(
                        id = id,
                        title = c.getString(titleCol) ?: "Unknown track",
                        artist = c.getString(artistCol) ?: "Unknown artist",
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        durationMs = c.getLong(durCol)
                    )
                )
            }
        }
    } catch (_: Exception) { }
    return out
}

private fun formatMs(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveMusicPlayerDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var currentIndex by remember { mutableStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(0) }

    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            try { player.release() } catch (_: Exception) { }
        }
    }

    LaunchedEffect(Unit) {
        tracks = withContext(Dispatchers.IO) { queryDeviceAudio(context) }
        loading = false
    }

    // Poll the real playback position while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(500)
            try {
                if (player.isPlaying) positionMs = player.currentPosition
            } catch (_: Exception) { }
        }
    }

    fun playAt(index: Int) {
        val track = tracks.getOrNull(index) ?: return
        try {
            player.reset()
            player.setDataSource(context, track.uri)
            player.prepare()
            player.setOnCompletionListener {
                isPlaying = false
                if (index + 1 < tracks.size) playAt(index + 1)
            }
            durationMs = player.duration
            positionMs = 0
            player.start()
            currentIndex = index
            isPlaying = true
        } catch (_: Exception) { }
    }

    fun togglePlayPause() {
        if (currentIndex !in tracks.indices) {
            if (tracks.isNotEmpty()) playAt(0)
            return
        }
        try {
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.start()
                isPlaying = true
            }
        } catch (_: Exception) { }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Device Music Player", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    loading -> {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    tracks.isEmpty() -> {
                        Text(
                            "No music found on this device. Copy MP3 files to the device and allow media access, then reopen.",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                    else -> {
                        val current = tracks.getOrNull(currentIndex)
                        Text(
                            current?.title ?: "Pick a track below",
                            fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1
                        )
                        Text(
                            current?.artist ?: "${tracks.size} tracks on device",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = positionMs.toFloat(),
                            valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
                            onValueChange = { v ->
                                try {
                                    player.seekTo(v.toInt())
                                    positionMs = v.toInt()
                                } catch (_: Exception) { }
                            }
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatMs(positionMs), fontSize = 10.sp)
                            Text(formatMs(durationMs), fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { if (currentIndex > 0) playAt(currentIndex - 1) }) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .clickable { togglePlayPause() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { if (currentIndex + 1 < tracks.size) playAt(currentIndex + 1) }) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            tracks.forEachIndexed { i, t ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { playAt(i) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote, contentDescription = null,
                                        tint = if (i == currentIndex) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(t.title, fontSize = 13.sp, fontWeight = if (i == currentIndex) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                                        Text(t.artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClose) { Text("Close") }
        }
    )
}

// ---------------------------------------------------------------------------
// Real video player: plays a video file picked from the device (or a supplied
// URL, e.g. from the video grabber) using VideoView + MediaController.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveVideoPlayerDialog(onClose: () -> Unit, initialVideoUrl: String? = null) {
    val context = LocalContext.current
    var videoUri by remember { mutableStateOf<Uri?>(initialVideoUrl?.let { Uri.parse(it) }) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) videoUri = uri
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Video Player", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val uri = videoUri
                if (uri == null) {
                    Text(
                        "No video selected.",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    Button(onClick = { picker.launch("video/*") }) {
                        Text("Pick a video file")
                    }
                    Text(
                        "Tip: in Hack mode the floating download button grabs videos detected on the page.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else {
                    key(uri) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(uri)
                                    setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                                    setOnPreparedListener { start() }
                                    setOnErrorListener { _, _, _ -> true }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { picker.launch("video/*") }) {
                        Text("Choose a different video")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClose) { Text("Close") }
        }
    )
}

// ---------------------------------------------------------------------------
// Real PDF reader: pick a PDF from the device and render its actual pages
// with PdfRenderer.
// ---------------------------------------------------------------------------

private fun queryDisplayName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "document.pdf"
    } catch (_: Exception) { "document.pdf" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractivePdfReaderDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var pageIndex by remember { mutableStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var rendering by remember { mutableStateOf(false) }

    fun renderPage(index: Int) {
        val r = renderer ?: return
        if (index !in 0 until r.pageCount) return
        rendering = true
        scope.launch(Dispatchers.IO) {
            try {
                r.openPage(index).use { page ->
                    // Render at 2x for readability on small screens
                    val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    withContext(Dispatchers.Main) {
                        pageBitmap?.recycle()
                        pageBitmap = bmp
                        pageIndex = index
                    }
                }
            } catch (_: Exception) { }
            withContext(Dispatchers.Main) { rendering = false }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            try { renderer?.close() } catch (_: Exception) { }
            renderer = null
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val r = PdfRenderer(pfd) // PdfRenderer takes ownership of the descriptor
                renderer = r
                pageCount = r.pageCount
                fileName = queryDisplayName(context, uri)
                renderPage(0)
            }
        } catch (_: Exception) { }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { renderer?.close() } catch (_: Exception) { }
            pageBitmap?.recycle()
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("PDF Reader", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (renderer == null) {
                    Text("No PDF opened.", fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                    Button(onClick = { picker.launch("application/pdf") }) {
                        Text("Pick a PDF file")
                    }
                } else {
                    Text(fileName ?: "", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Page ${pageIndex + 1} of $pageCount", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = pageBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "PDF page ${pageIndex + 1}",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (rendering) {
                            CircularProgressIndicator()
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { renderPage(pageIndex - 1) }, enabled = pageIndex > 0) { Text("Prev") }
                        TextButton(onClick = { picker.launch("application/pdf") }) { Text("Open another") }
                        Button(onClick = { renderPage(pageIndex + 1) }, enabled = pageIndex + 1 < pageCount) { Text("Next") }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClose) { Text("Close") }
        }
    )
}

// ---------------------------------------------------------------------------
// Real image gallery: shows the device's actual photos (MediaStore) with
// working rotate / zoom on the selected image.
// ---------------------------------------------------------------------------

private data class DeviceImage(val id: Long, val uri: Uri)

private fun queryDeviceImages(context: Context, limit: Int = 120): List<DeviceImage> {
    val out = mutableListOf<DeviceImage>()
    val projection = arrayOf(MediaStore.Images.Media._ID)
    try {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext() && out.size < limit) {
                val id = c.getLong(idCol)
                out.add(DeviceImage(id, ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)))
            }
        }
    } catch (_: Exception) { }
    return out
}

@Suppress("DEPRECATION")
@Composable
private fun DeviceImageThumb(image: DeviceImage, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val context = LocalContext.current
    var bmp by remember(image.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(image.id) {
        bmp = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(image.uri, Size(256, 256), null)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Thumbnails.getThumbnail(
                        context.contentResolver, image.id,
                        MediaStore.Images.Thumbnails.MINI_KIND, null
                    )
                }
            } catch (_: Exception) { null }
        }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(), contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable { onClick() }
        )
    } else {
        Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveImageGalleryDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var images by remember { mutableStateOf<List<DeviceImage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<DeviceImage?>(null) }
    var fullBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rotation by remember { mutableStateOf(0f) }
    var zoomScale by remember { mutableStateOf(1f) }

    LaunchedEffect(Unit) {
        images = withContext(Dispatchers.IO) { queryDeviceImages(context) }
        loading = false
    }

    // Load the full image when one is selected
    LaunchedEffect(selected) {
        fullBitmap?.recycle()
        fullBitmap = null
        rotation = 0f
        zoomScale = 1f
        val sel = selected ?: return@LaunchedEffect
        fullBitmap = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(sel.uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
        }
    }

    DisposableEffect(Unit) {
        onDispose { fullBitmap?.recycle() }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Device Image Gallery", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    loading -> {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    images.isEmpty() -> {
                        Text(
                            "No photos found on this device. Allow media access and add photos, then reopen.",
                            fontSize = 12.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                    selected == null -> {
                        Text("${images.size} photos on device — tap to view", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(images, key = { it.id }) { img ->
                                DeviceImageThumb(
                                    image = img,
                                    modifier = Modifier.aspectRatio(1f).fillMaxWidth(),
                                    onClick = { selected = img }
                                )
                            }
                        }
                    }
                    else -> {
                        val bmp = fullBitmap
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    rotationZ = rotation
                                }
                                .clickable { selected = null },
                            contentAlignment = Alignment.Center
                        ) {
                            if (bmp != null) {
                                Image(bmp.asImageBitmap(), contentDescription = "Selected photo", modifier = Modifier.fillMaxSize())
                            } else {
                                CircularProgressIndicator()
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Button(onClick = { rotation += 90f }) { Text("Rotate") }
                            Button(onClick = { zoomScale = if (zoomScale == 1f) 1.5f else 1f }) { Text("Zoom") }
                            TextButton(onClick = { selected = null }) { Text("Back") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClose) { Text("Close") }
        }
    )
}

// ---------------------------------------------------------------------------
// Quick toggles (previously mislabeled "Extensions Manager"): the two switches
// are real and wired to the WebView in MainActivity.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveExtensionsDialog(
    adblock: Boolean,
    onToggleAdblock: (Boolean) -> Unit,
    night: Boolean,
    onToggleNight: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Quick Toggles", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🧩 AdBlocker", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Switch(checked = adblock, onCheckedChange = onToggleAdblock)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🌙 Dark Mode for websites", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Switch(checked = night, onCheckedChange = onToggleNight)
                }
                Text(
                    "More toggles (HTTPS-Only, Data Saver, JavaScript) live in the home screen Settings module.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onClose) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Click Browser Privacy Policy\n\n" +
                        "• Your bookmarks, history and download records are stored only on this device (app-private storage). Nothing is uploaded to our servers — we operate no analytics or account backend.\n\n" +
                        "• Pages load through Android's system WebView; network requests go directly to the websites you visit.\n\n" +
                        "• HTTPS-Only mode (when enabled) upgrades http:// links to https:// and blocks mixed content. It is enforced per navigation.\n\n" +
                        "• The Anti-Detection Guard (Hack mode) spoofs fingerprintable browser values locally with JavaScript (user-agent, screen, WebGL, canvas and audio noise). It does not route traffic through any proxy, VPN or DNS tunnel.\n\n" +
                        "• Incognito tabs skip history recording on this device.",
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = onClose) { Text("Accept") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAppDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Click Browser", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text("Ecosystem built by: Team PK AI", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("UI Design Built By: Prince Laghari", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "A multi-mode Android browser (Simple / Developer / Hack) with ad-blocking, devtools, anti-detection spoofing and a real video downloader.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onClose) { Text("Close") }
        }
    )
}
