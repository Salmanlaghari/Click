package com.click.browser.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.ModeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

/**
 * Lightweight in-app PDF viewer using Android's built-in PdfRenderer —
 * no heavy third-party library. Pages render on demand as you scroll.
 */
@Composable
fun PdfViewerScreen(
    pdfFile: File,
    theme: ModeTheme,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pageCount by remember { mutableStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    // Keyed by pdfFile: opening a different PDF must not reuse the
    // previous document's bitmaps, aspect ratios, or semaphore state.
    val pageBitmaps = remember(pdfFile) { mutableStateMapOf<Int, Bitmap>() }
    val pageAspects = remember(pdfFile) { mutableStateMapOf<Int, Float>() }
    // Cap concurrent page renders: fast scrolling must not spawn dozens of
    // simultaneous bitmap renders (OOM/jank risk).
    val renderSemaphore = remember(pdfFile) { Semaphore(3) }

    // Open the renderer once for the screen's lifetime.
    val rendererState = remember(pdfFile) { mutableStateOf<PdfRenderer?>(null) }
    DisposableEffect(pdfFile) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            pageCount = renderer.pageCount
            rendererState.value = renderer
            // PdfRenderer takes ownership of the fd; keep pfd open until close.
        } catch (e: Exception) {
            loadError = "Couldn't open this PDF: ${e.message?.take(80)}"
            try { pfd?.close() } catch (e: Exception) { }
        }
        onDispose {
            pageBitmaps.values.forEach { try { it.recycle() } catch (e: Exception) {} }
            pageBitmaps.clear()
            pageAspects.clear()
            try { renderer?.close() } catch (e: Exception) { }
            // pfd is closed by renderer.close()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = theme.onBackground)
            }
            Icon(
                Icons.Filled.PictureAsPdf,
                contentDescription = null,
                tint = theme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    pdfFile.name.take(32),
                    color = theme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (pageCount > 0) "$pageCount pages · in Click viewer" else "PDF",
                    color = theme.onBackground.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }
        }

        when {
            loadError != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text(loadError!!, color = theme.onBackground) }

            pageCount == 0 -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = theme.primary) }

            else -> {
                val listState = rememberLazyListState()
                // Show current page in the header via derived state is nice-to-have;
                // keep it simple: pages render lazily below.
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                ) {
                    items(pageCount, key = { it }) { index ->
                        PdfPageItem(
                            index = index,
                            rendererState = rendererState,
                            cache = pageBitmaps,
                            aspectCache = pageAspects,
                            renderSemaphore = renderSemaphore,
                            theme = theme
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    index: Int,
    rendererState: androidx.compose.runtime.State<PdfRenderer?>,
    cache: MutableMap<Int, Bitmap>,
    aspectCache: MutableMap<Int, Float>,
    renderSemaphore: Semaphore,
    theme: ModeTheme
) {
    var bitmap by remember(index) { mutableStateOf(cache[index]) }
    var aspectRatio by remember(index) { mutableStateOf(aspectCache[index]) }
    LaunchedEffect(index, rendererState.value) {
        val renderer = rendererState.value ?: return@LaunchedEffect
        // Fetch the true page aspect first (cheap metadata read) so the
        // placeholder already has the final size — no layout jump later.
        if (aspectRatio == null) {
            val ratio = withContext(Dispatchers.IO) { pageAspectRatio(renderer, index) }
            if (ratio != null) {
                aspectCache[index] = ratio
                aspectRatio = ratio
            }
        }
        if (bitmap == null) {
            renderSemaphore.withPermit {
                val bmp = withContext(Dispatchers.IO) {
                    renderPage(renderer, index)
                }
                if (bmp != null) {
                    cache[index] = bmp
                    bitmap = bmp
                }
            }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Page ${index + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(theme.surface)
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio ?: 0.72f)
                .clip(RoundedCornerShape(8.dp))
                .background(theme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = theme.primary, modifier = Modifier.size(28.dp))
        }
    }
}

/** Cheap metadata read: page width/height without rendering pixels. */
private fun pageAspectRatio(renderer: PdfRenderer?, index: Int): Float? {
    if (renderer == null || index < 0 || index >= renderer.pageCount) return null
    return try {
        renderer.openPage(index).use { page ->
            if (page.height > 0) page.width.toFloat() / page.height else null
        }
    } catch (e: Exception) {
        null
    }
}

/** Renders one page at ~2x for crisp text. Called off the main thread. */
private fun renderPage(renderer: PdfRenderer?, index: Int): Bitmap? {
    if (renderer == null || index < 0 || index >= renderer.pageCount) return null
    return try {
        renderer.openPage(index).use { page ->
            val scale = 2f
            val bmp = Bitmap.createBitmap(
                (page.width * scale).toInt(),
                (page.height * scale).toInt(),
                Bitmap.Config.ARGB_8888
            )
            // White background so transparent PDFs don't render black.
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    } catch (e: Exception) {
        null
    }
}
