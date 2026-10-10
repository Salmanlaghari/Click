package com.click.browser.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.LocationGuard
import com.click.browser.engine.ModePersonalization
import com.click.browser.engine.QuickSiteDef
import kotlinx.coroutines.launch
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.NewsArticle
import com.click.browser.engine.NewsCategory
import com.click.browser.engine.NewsFeed
import com.click.browser.engine.NewsImageCache
import com.click.browser.engine.NewsResult
import com.click.browser.engine.NewsStatus
import java.util.Calendar

/**
 * Premium home screen — "Click — AI Privacy Browser" identity (Phase 1).
 *
 * Pixel-faithful to Prince's approved promo reference:
 *  top logo row (Click + Protected pill + avatar) · pill search bar
 *  (G icon, mic, QR) · 2×4 quick-site tiles · category pill tabs ·
 *  news cards (hero + list) · 2×2 quick actions.
 *
 * The caller passes a mode-wired [ModeTheme]:
 *  SIMPLE → light, DEVELOPER → dark glass, HACK → OLED black neon.
 */
@Composable
fun ClickHomeScreen(
    theme: ModeTheme,
    activeMode: BrowserMode,
    adBlockerEnabled: Boolean,
    blockedCount: Int,
    wallpaperUri: String? = null,
    shieldActive: Boolean = false,
    onNavigate: (String) -> Unit,
    onOpenAiChat: () -> Unit,
    onTranslate: () -> Unit,
    onReaderMode: () -> Unit,
    onQrClick: () -> Unit,
    onProfileClick: () -> Unit,
    onV9ShieldClick: () -> Unit = {},
    onGamesClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isHackNeon = activeMode == BrowserMode.HACK
    val isGlass = activeMode == BrowserMode.DEVELOPER
    // Frosted-glass surfaces for Developer; flat soft cards for Simple; pure black for Hack.
    val cardBg = when {
        isHackNeon -> Color(0xFF0A0A12)
        isGlass -> theme.surface.copy(alpha = 0.72f)
        else -> theme.surface
    }
    val cardBorder = when {
        isHackNeon -> theme.glow.copy(alpha = 0.35f)
        isGlass -> Color.White.copy(alpha = 0.08f)
        else -> theme.onSurface.copy(alpha = 0.08f)
    }

    // Custom wallpaper (decoded off the main thread). When set, it replaces
    // the theme background with a dimmed photo so content stays readable
    // in all 3 modes.
    val context = LocalContext.current
    var wallpaperBitmap by remember(wallpaperUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(wallpaperUri) {
        wallpaperBitmap = withContext(Dispatchers.IO) {
            try {
                wallpaperUri?.let { uriStr ->
                    context.contentResolver.openInputStream(android.net.Uri.parse(uriStr))?.use { input ->
                        android.graphics.BitmapFactory.decodeStream(input)
                    }
                }
            } catch (_: Exception) { null }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Background: custom wallpaper (dimmed) or the per-mode theme color.
        val wallpaper = wallpaperBitmap
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.background.copy(alpha = 0.6f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.background)
            )
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        HomeTopRow(
            theme = theme,
            adBlockerEnabled = adBlockerEnabled,
            blockedCount = blockedCount,
            neon = isHackNeon,
            onProfileClick = onProfileClick
        )

        SmartSearchBar(
            theme = theme,
            neon = isHackNeon,
            onNavigate = onNavigate,
            onQrClick = onQrClick
        )

        // V9 Shield: big prominent VPN toggle card (Prince couldn't find it).
        // One tap opens the Shield screen; shows live ON/OFF state.
        V9ShieldCard(
            theme = theme,
            active = shieldActive,
            neon = isHackNeon,
            cardBg = cardBg,
            cardBorder = cardBorder,
            onClick = onV9ShieldClick
        )

        QuickSitesGrid(
            mode = activeMode,
            cardBg = cardBg,
            cardBorder = cardBorder,
            onSurface = theme.onSurface,
            onNavigate = onNavigate
        )

        TodayUpdateCard(
            theme = theme,
            cardBg = cardBg,
            cardBorder = cardBorder,
            onGamesClick = onGamesClick
        )

        NewsSection(
            theme = theme,
            cardBg = cardBg,
            cardBorder = cardBorder,
            neon = isHackNeon,
            onNavigate = onNavigate
        )

        QuickActionsGrid(
            cardBg = cardBg,
            cardBorder = cardBorder,
            onSurface = theme.onSurface,
            onOpenAiChat = onOpenAiChat,
            onTranslate = onTranslate,
            onReaderMode = onReaderMode
        )

        // Bottom nav overlays the content (aligned BottomCenter) — without this
        // padding the last items hide behind it with nothing to scroll.
        Spacer(modifier = Modifier.height(96.dp))
    }
    } // Box: wallpaper background + content
}

/** V9 Shield: big prominent VPN card on the home screen. One tap toggles. */
@Composable
private fun V9ShieldCard(
    theme: ModeTheme,
    active: Boolean,
    neon: Boolean,
    cardBg: Color,
    cardBorder: Color,
    onClick: () -> Unit
) {
    val accent = if (active) Color(0xFF22C55E) else theme.primary
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (active) Color(0xFF22C55E).copy(alpha = 0.6f) else cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accent,
                                if (neon) Color(0xFF22D3EE) else theme.primary.copy(alpha = 0.6f)
                            )
                        ),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("🛡️", fontSize = 26.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "V9 Shield VPN",
                    color = theme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Text(
                    if (active) "● Protected — encrypted DNS active"
                    else "Tap to enable private browsing protection",
                    color = if (active) Color(0xFF22C55E) else theme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            // Toggle pill.
            Box(
                modifier = Modifier
                    .background(
                        if (active) Color(0xFF22C55E) else theme.onSurface.copy(alpha = 0.15f),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    if (active) "ON" else "OFF",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/** Top row: gradient "C Click" logo · Protected pill · avatar. */
@Composable
private fun HomeTopRow(
    theme: ModeTheme,
    adBlockerEnabled: Boolean,
    blockedCount: Int,
    neon: Boolean,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gradient "C" logo + wordmark
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("C", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text("Click", color = theme.onBackground, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)

        Spacer(modifier = Modifier.weight(1f))

        // Protected pill badge
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF16A34A).copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, Color(0xFF16A34A).copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        "Protected",
                        color = Color(0xFF16A34A),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 11.sp
                    )
                    Text(
                        if (adBlockerEnabled) "$blockedCount trackers blocked" else "AdBlock off",
                        color = theme.onSurface.copy(alpha = 0.6f),
                        fontSize = 8.5.sp,
                        lineHeight = 9.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Avatar circle
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(theme.primary.copy(alpha = 0.5f), theme.secondary.copy(alpha = 0.5f)))
                )
                .border(1.5.dp, if (neon) theme.glow else theme.primary.copy(alpha = 0.4f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onProfileClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** Pill search bar: G icon · hint · mic · QR. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartSearchBar(
    theme: ModeTheme,
    neon: Boolean,
    onNavigate: (String) -> Unit,
    onQrClick: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val submit = {
        if (query.isNotBlank()) {
            onNavigate(query.trim())
            query = ""
        }
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = theme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (neon) theme.glow.copy(alpha = 0.7f) else theme.onSurface.copy(alpha = 0.12f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (neon) Modifier.shadow(12.dp, RoundedCornerShape(28.dp), spotColor = theme.glow) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // G icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text("G", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFF4285F4))
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search or enter address", fontSize = 14.sp, color = theme.onSurface.copy(alpha = 0.45f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = theme.primary,
                    focusedTextColor = theme.onSurface,
                    unfocusedTextColor = theme.onSurface
                ),
                modifier = Modifier.weight(1f)
            )
            VoiceMicButton(theme = theme, onHeard = { heard ->
                onNavigate(heard)
            })
            IconButton(onClick = onQrClick) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = theme.primary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Mic button with runtime permission + SpeechRecognizer (same pattern as AI chat). */
@Composable
private fun VoiceMicButton(
    theme: ModeTheme,
    onHeard: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var listening by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun stopListening() {
        listening = false
        try { recognizer?.destroy() } catch (_: Exception) { }
        recognizer = null
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "Speech recognition isn't available on this device.", Toast.LENGTH_SHORT).show()
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(
            VoiceInputListener(
                onResult = { heard -> onHeard(heard) },
                onEnd = { stopListening() }
            )
        )
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            r.startListening(intent)
            recognizer = r
            listening = true
        } catch (_: Exception) {
            stopListening()
            Toast.makeText(context, "Couldn't start voice input.", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
        else Toast.makeText(context, "Microphone permission denied — voice input is off.", Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(Unit) { onDispose { stopListening() } }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Microphone access") },
            text = { Text("Voice search uses your microphone only to turn speech into text. Nothing is recorded or uploaded by Click Browser itself.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Allow") }
            },
            dismissButton = { TextButton(onClick = { showRationale = false }) { Text("Not now") } }
        )
    }

    IconButton(onClick = {
        if (listening) {
            try { recognizer?.stopListening() } catch (_: Exception) { stopListening() }
            return@IconButton
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) startListening()
        else if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)) showRationale = true
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }) {
        Icon(
            Icons.Default.Mic,
            contentDescription = if (listening) "Stop listening" else "Voice search",
            tint = if (listening) Color(0xFFEF4444) else theme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Per-mode quick-site tiles — each V9 mode gets its own set, fully user-customizable. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickSitesGrid(
    mode: BrowserMode,
    cardBg: Color,
    cardBorder: Color,
    onSurface: Color,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sites by ModePersonalization.quickSitesFlow(context, mode)
        .collectAsState(initial = ModePersonalization.defaultQuickSites(mode))
    var showAddDialog by remember(mode) { mutableStateOf(false) }
    var manageSite by remember(mode) { mutableStateOf<QuickSiteDef?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // null entry = the trailing "Add" tile.
        val tiles: List<QuickSiteDef?> = sites + listOf(null)
        tiles.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { site ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (site == null) showAddDialog = true
                                    else onNavigate(site.url)
                                },
                                onLongClick = { if (site != null) manageSite = site }
                            )
                    ) {
                        val isAdd = site == null
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isAdd) Modifier.border(1.5.dp, onSurface.copy(alpha = 0.25f), CircleShape)
                                    else Modifier.background(Color(site!!.bgArgb))
                                )
                                .then(
                                    if (!isAdd && site!!.label == "Instagram") Modifier.background(
                                        Brush.linearGradient(listOf(Color(0xFFFEDA75), Color(0xFFD62976), Color(0xFF962FBF)))
                                    ) else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                site?.glyph ?: "+",
                                color = if (site == null) onSurface else Color(site.glyphColorArgb),
                                fontWeight = FontWeight.Black,
                                fontSize = if (isAdd) 24.sp else 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            site?.label ?: "Add",
                            fontSize = 10.5.sp,
                            color = onSurface.copy(alpha = 0.75f),
                            maxLines = 1
                        )
                    }
                }
                // Pad short rows so tiles keep even spacing.
                repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }

    if (showAddDialog) {
        QuickSiteEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { label, url ->
                scope.launch {
                    ModePersonalization.addQuickSite(
                        context, mode,
                        QuickSiteDef(label, url, 0xFF3A3A4A, label.take(1).uppercase(), 0xFFFFFFFF)
                    )
                }
                showAddDialog = false
            }
        )
    }

    manageSite?.let { target ->
        var editing by remember(target) { mutableStateOf(false) }
        if (editing) {
            QuickSiteEditDialog(
                initial = target,
                onDismiss = { editing = false },
                onSave = { label, url ->
                    scope.launch {
                        ModePersonalization.removeQuickSite(context, mode, target.label)
                        ModePersonalization.addQuickSite(
                            context, mode,
                            QuickSiteDef(label, url, target.bgArgb, target.glyph, target.glyphColorArgb)
                        )
                    }
                    manageSite = null
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { manageSite = null },
                title = { Text(target.label) },
                text = { Text(target.url, fontSize = 12.sp) },
                confirmButton = {
                    TextButton(onClick = { editing = true }) { Text("Edit") }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                scope.launch { ModePersonalization.resetQuickSites(context, mode) }
                                manageSite = null
                                Toast.makeText(context, "Quick sites reset to defaults", Toast.LENGTH_SHORT).show()
                            }
                        ) { Text("Reset all") }
                        TextButton(
                            onClick = {
                                scope.launch { ModePersonalization.removeQuickSite(context, mode, target.label) }
                                manageSite = null
                                Toast.makeText(context, "\"${target.label}\" removed", Toast.LENGTH_SHORT).show()
                            }
                        ) { Text("Remove") }
                    }
                }
            )
        }
    }
}

/** Add / edit dialog for a quick-site tile. */
@Composable
private fun QuickSiteEditDialog(
    initial: QuickSiteDef?,
    onDismiss: () -> Unit,
    onSave: (label: String, url: String) -> Unit
) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add shortcut" else "Edit shortcut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(18) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanLabel = label.trim()
                    var cleanUrl = url.trim()
                    if (cleanLabel.isEmpty() || cleanUrl.isEmpty()) return@TextButton
                    if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                        cleanUrl = "https://$cleanUrl"
                    }
                    onSave(cleanLabel, cleanUrl)
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


/** Category pill tabs: All (selected blue), News, Tech, AI, Sports, trailing ☰. */
@Composable
private fun CategoryTabs(
    selected: NewsCategory,
    onSelect: (NewsCategory) -> Unit,
    theme: ModeTheme
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(NewsCategory.values()) { cat ->
            val isSel = cat == selected
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isSel) theme.primary else theme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onSelect(cat) }
            ) {
                Text(
                    cat.label,
                    color = if (isSel) Color.White else theme.onSurface.copy(alpha = 0.7f),
                    fontSize = 12.5.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        item {
            IconButton(onClick = { /* category overflow — reserved */ }) {
                Icon(Icons.Default.Menu, contentDescription = "More categories", tint = theme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

/** News section: hero 16:9 card + small list cards, RSS-backed. */
@Composable
private fun NewsSection(
    theme: ModeTheme,
    cardBg: Color,
    cardBorder: Color,
    neon: Boolean,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    var category by remember { mutableStateOf(NewsCategory.ALL) }
    var refreshKey by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<NewsResult?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(category, refreshKey) {
        loading = true
        result = NewsFeed.getArticles(context, category)
        loading = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CategoryTabs(selected = category, onSelect = { category = it }, theme = theme)

        when {
            loading -> {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = theme.primary, modifier = Modifier.size(28.dp))
                }
            }
            result == null || result!!.articles.isEmpty() -> {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📰", fontSize = 28.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            when (result?.status) {
                                NewsStatus.OFFLINE -> "You're offline — news will appear when you're back online."
                                NewsStatus.ERROR -> "Couldn't load news. Check your connection and tap Retry."
                                else -> "Couldn't load news right now."
                            },
                            color = theme.onSurface.copy(alpha = 0.7f),
                            fontSize = 12.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Retry",
                            color = theme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { refreshKey++ }
                        )
                    }
                }
            }
            else -> {
                val articles = result!!.articles
                // Hero card: big 16:9 image + bold title + source · time + ⋮
                val hero = articles.first()
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (neon) Modifier.shadow(10.dp, RoundedCornerShape(16.dp), spotColor = theme.glow.copy(alpha = 0.35f)) else Modifier)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onNavigate(hero.link) }
                ) {
                    Column {
                        NewsThumbnail(
                            url = hero.thumbnailUrl,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        )
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    hero.title,
                                    color = theme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${hero.source} · ${NewsFeed.timeAgo(hero.publishedAt)}",
                                    color = theme.onSurface.copy(alpha = 0.55f),
                                    fontSize = 11.sp
                                )
                            }
                            Icon(Icons.Default.MoreVert, contentDescription = null, tint = theme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                // AdMob native ad, styled as a news card and labeled "Ad" per policy.
                // Placed between the hero and the list with the column's 10.dp spacing
                // so it never sits flush against tappable cards.
                AdMobNativeAd(
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    onSurface = theme.onSurface
                )
                // Small list cards: thumbnail left, title + source right
                articles.drop(1).take(5).forEach { article ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onNavigate(article.link) }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NewsThumbnail(
                                url = article.thumbnailUrl,
                                modifier = Modifier.size(72.dp, 56.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    article.title,
                                    color = theme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 17.sp
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    "${article.source} · ${NewsFeed.timeAgo(article.publishedAt)}",
                                    color = theme.onSurface.copy(alpha = 0.55f),
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                }
                // AdMob banner ad after the news list (Prince's AdMob account).
                // Extra spacing keeps the ad clear of tappable cards per AdMob policy.
                Spacer(modifier = Modifier.height(4.dp))
                AdMobBannerAd()
            }
        }
    }
}

/** News thumbnail with memory+disk cache; gradient placeholder while loading. */
@Composable
private fun NewsThumbnail(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = if (url.isNullOrBlank()) null else NewsImageCache.get(context, url)
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(listOf(Color(0xFF2A3A5F), Color(0xFF16224A)))
            ),
            contentAlignment = Alignment.Center
        ) {
            Text("📰", fontSize = 26.sp)
        }
    }
}

private data class QuickAction(val title: String, val sub: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tint: Color)

/** 2×2 quick actions: AI Assistant, Summarize Page, Translate, Reader Mode. */
@Composable
private fun QuickActionsGrid(
    cardBg: Color,
    cardBorder: Color,
    onSurface: Color,
    onOpenAiChat: () -> Unit,
    onTranslate: () -> Unit,
    onReaderMode: () -> Unit
) {
    val actions = listOf(
        QuickAction("AI Assistant", "Ask anything", Icons.Default.AutoAwesome, Color(0xFF8B5CF6)),
        QuickAction("Summarize Page", "Get key points", Icons.Default.Description, Color(0xFF3B82F6)),
        QuickAction("Translate", "Any language", Icons.Default.Translate, Color(0xFFF59E0B)),
        QuickAction("Reader Mode", "Clean view", Icons.Default.Description, Color(0xFF16A34A))
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Quick Actions", color = onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        actions.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { action ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                when (action.title) {
                                    "AI Assistant", "Summarize Page" -> onOpenAiChat()
                                    "Translate" -> onTranslate()
                                    else -> onReaderMode()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(action.tint.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(action.icon, contentDescription = null, tint = action.tint, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(action.title, color = onSurface, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
                                Text(action.sub, color = onSurface.copy(alpha = 0.55f), fontSize = 10.5.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bottom navigation: Home · Tabs (count badge) · big glowing AI center ·
 * Bookmarks · Menu. Shown on the home surface (about:blank).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickBottomNav(
    theme: ModeTheme,
    tabCount: Int,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onAi: () -> Unit,
    onBookmarks: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val neon = theme.mode == BrowserMode.HACK
    Surface(
        color = if (neon) Color(0xFF000000) else theme.topBarBg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (neon) theme.glow.copy(alpha = 0.3f) else theme.onSurface.copy(alpha = 0.08f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClickNavItem(icon = Icons.Default.Home, label = "Home", selected = true, theme = theme, onClick = onHome)

            // Tabs with count badge
            BadgedBox(
                badge = {
                    Badge(containerColor = theme.primary, contentColor = Color.White) {
                        Text(tabCount.toString(), fontSize = 9.sp)
                    }
                },
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onTabs() }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                    Icon(Icons.Default.Tab, contentDescription = "Tabs", tint = theme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                    Text("Tabs", fontSize = 10.sp, color = theme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                }
            }

            // Center: LARGE glowing AI button
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .then(
                        if (neon) Modifier.shadow(18.dp, CircleShape, spotColor = theme.glow)
                        else Modifier.shadow(10.dp, CircleShape, spotColor = theme.primary)
                    )
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFD946EF))))
                    .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onAi() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Ask Click AI", tint = Color.White, modifier = Modifier.size(24.dp))
                    Text("AI", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }

            ClickNavItem(icon = Icons.Default.Bookmark, label = "Bookmarks", selected = false, theme = theme, onClick = onBookmarks)
            ClickNavItem(icon = Icons.Default.Menu, label = "Menu", selected = false, theme = theme, onClick = onMenu)
        }
    }
}

@Composable
private fun ClickNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    theme: ModeTheme,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) theme.primary else theme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            label,
            fontSize = 10.sp,
            color = if (selected) theme.primary else theme.onSurface.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold
        )
    }
}

/** Time-aware greeting for the home header. */
fun homeGreeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}

/* ---------------------------------------------------------------------------
 * SURFACE 2 — Browsing (webpage open). Distinct from Home:
 *  compact URL bar · privacy strip · WebView focus · floating AI button ·
 *  bottom nav = Back/Forward/Home/Tabs/Menu (no center AI tab).
 * --------------------------------------------------------------------------- */

/**
 * 2026-style premium address bar for the browsing surface.
 *
 * Layout: [☰ Sidebar] [🔒 URL field] [↻] [▭ Tabs] [⋮]
 * - Hamburger opens the navigation drawer (sidebar) — restores sidebar access
 *   directly from the top bar, 2026 browser style.
 * - Elevated URL field with soft shadow (not just a border).
 * - Tab counter button for quick tab switching.
 * - Mode-aware accent colors, refined touch targets.
 *
 * The URL field is directly editable (tap, type, Go).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactBrowseBar(
    theme: ModeTheme,
    currentUrl: String,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onMenuClick: () -> Unit,
    onSiteSettingsClick: () -> Unit = {},
    onDrawerClick: () -> Unit = {},
    tabCount: Int = 1,
    onTabsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    // LocationGuard indicator: null = no indicator; otherwise the effective
    // mode for the current site (BLOCK/Spoof shown, ASK hidden).
    locationMode: LocationGuard.LocationMode? = null,
    locationSpoofLabel: String = "",
    onLocationClick: () -> Unit = {}
) {
    var textInput by remember(currentUrl) { mutableStateOf(currentUrl) }
    val isHttps = currentUrl.startsWith("https://")
    // 2026 style: elevated bar with soft shadow, rounded container.
    Surface(
        color = theme.topBarBg,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sidebar (drawer) button — 2026 style: hamburger opens the
            // navigation sidebar with all browser options.
            IconButton(
                onClick = onDrawerClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Open sidebar menu",
                    tint = theme.onTopBar,
                    modifier = Modifier.size(22.dp)
                )
            }
            // URL field: elevated pill with soft shadow, 2026 premium look.
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = theme.surfaceVariant.copy(alpha = 0.65f),
                shadowElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, theme.primary.copy(alpha = 0.18f)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onSiteSettingsClick,
                        modifier = Modifier.padding(start = 4.dp).size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = if (isHttps) "Secure connection — site settings" else "Connection — site settings",
                            tint = if (isHttps) Color(0xFF22C55E) else theme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    // LocationGuard indicator: shows when this site's location is
                    // blocked or spoofed, so the user always knows the state.
                    if (locationMode == LocationGuard.LocationMode.BLOCK ||
                        locationMode == LocationGuard.LocationMode.SPOOF
                    ) {
                        IconButton(
                            onClick = onLocationClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (locationMode == LocationGuard.LocationMode.BLOCK)
                                    Icons.Default.LocationOff else Icons.Default.LocationOn,
                                contentDescription =
                                    if (locationMode == LocationGuard.LocationMode.BLOCK)
                                        "Location blocked for this site"
                                    else "Location spoofed for this site: $locationSpoofLabel",
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            color = theme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onNavigate(textInput) }),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                    // Clear button when there's text (2026 UX pattern).
                    if (textInput.isNotEmpty() && textInput != currentUrl) {
                        IconButton(
                            onClick = { textInput = "" },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = theme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            // Reload button.
            IconButton(onClick = {
                textInput = currentUrl
                onReload()
            }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reload",
                    tint = theme.onTopBar,
                    modifier = Modifier.size(22.dp)
                )
            }
            // Tab counter button (2026 style: quick tab access from top bar).
            IconButton(onClick = onTabsClick, modifier = Modifier.size(40.dp)) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = theme.primary,
                            contentColor = Color.White,
                            modifier = Modifier.offset(x = 6.dp, y = (-6).dp)
                        ) {
                            Text(
                                tabCount.coerceAtMost(99).toString(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.FilterNone,
                        contentDescription = "Tabs ($tabCount)",
                        tint = theme.onTopBar,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            // Browser menu button.
            IconButton(onClick = onMenuClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Browser menu",
                    tint = theme.onTopBar,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** Slim privacy strip under the compact bar: 🛡 Protected · N trackers blocked. */
@Composable
fun PrivacyStrip(
    theme: ModeTheme,
    adBlockerEnabled: Boolean,
    blockedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = theme.topBarBg,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = Color(0xFF22C55E),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                if (adBlockerEnabled) "Protected · $blockedCount trackers blocked"
                else "AdBlock off",
                color = theme.onSurface.copy(alpha = 0.65f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Floating pill-shaped "✨ Ask AI" button (bottom-right) for the browsing surface,
 * per the "Same Website. 3 Beautiful Modes." reference image.
 *
 * - Tap the pill → opens the AI chat (Ask Click AI).
 * - Tap the chevron → quick popup: Ask Click AI · Summarize · Translate
 *   (existing quick actions preserved).
 * - Static glow (no continuous animation — battery-safe per Prince's requirement).
 */
@Composable
fun AiQuickFab(
    theme: ModeTheme,
    onAskAi: () -> Unit,
    onSummarize: () -> Unit,
    onTranslate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val neon = theme.mode == BrowserMode.HACK
    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    AiPopupItem(icon = Icons.Default.AutoAwesome, label = "Ask Click AI", theme = theme) {
                        expanded = false; onAskAi()
                    }
                    AiPopupItem(icon = Icons.Default.Description, label = "Summarize", theme = theme) {
                        expanded = false; onSummarize()
                    }
                    AiPopupItem(icon = Icons.Default.Translate, label = "Translate", theme = theme) {
                        expanded = false; onTranslate()
                    }
                }
            }
        }
        // Pill: ✨ Ask AI  | chevron
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .then(
                    if (neon) Modifier.shadow(18.dp, RoundedCornerShape(28.dp), spotColor = theme.glow)
                    else Modifier.shadow(10.dp, RoundedCornerShape(28.dp), spotColor = theme.primary)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))))
                .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onAskAi() }
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp)
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Ask AI",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            // Chevron opens the quick-actions popup (Ask · Summarize · Translate).
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = if (expanded) "Hide quick actions" else "Show quick actions",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AiPopupItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    theme: ModeTheme,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = theme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Bottom nav for the browsing surface: ☰ Menu(drawer) · ‹ Back · › Forward ·
 * ⌂ Home · ▭ Tabs (live count badge) · ⋮ More.
 * Per the "Same Website. 3 Beautiful Modes." reference image — compact icons +
 * labels, mode-aware colors. (No center AI tab here — AI lives in the floating pill.)
 *
 * Battery-safe animations only: the tab-count badge does a one-shot spring "pop"
 * when the count changes — no continuous animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseBottomNav(
    theme: ModeTheme,
    tabCount: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onDrawerClick: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val neon = theme.mode == BrowserMode.HACK

    // One-shot badge pop when the tab count changes (battery-safe: no loop).
    val badgeScale = remember { androidx.compose.animation.core.Animatable(1f) }
    var firstBadgeFrame by remember { mutableStateOf(true) }
    LaunchedEffect(tabCount) {
        if (firstBadgeFrame) {
            firstBadgeFrame = false
        } else {
            badgeScale.snapTo(1.5f)
            badgeScale.animateTo(
                1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                )
            )
        }
    }

    Surface(
        color = if (neon) Color(0xFF000000) else theme.topBarBg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (neon) theme.glow.copy(alpha = 0.3f) else theme.onSurface.copy(alpha = 0.08f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrowseNavButton(
                icon = Icons.Default.Menu, label = "Menu",
                enabled = true, theme = theme, onClick = onDrawerClick
            )
            BrowseNavButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack, label = "Back",
                enabled = canGoBack, theme = theme, onClick = onBack
            )
            BrowseNavButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward, label = "Forward",
                enabled = canGoForward, theme = theme, onClick = onForward
            )
            BrowseNavButton(
                icon = Icons.Default.Home, label = "Home",
                enabled = true, theme = theme, onClick = onHome,
                highlight = true
            )
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = theme.primary,
                        contentColor = Color.White,
                        modifier = Modifier.graphicsLayer {
                            scaleX = badgeScale.value
                            scaleY = badgeScale.value
                        }
                    ) {
                        Text(tabCount.toString(), fontSize = 9.sp)
                    }
                },
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onTabs() }
            ) {
                BrowseNavContent(icon = Icons.Default.Tab, label = "Tabs", enabled = true, theme = theme)
            }
            BrowseNavButton(
                icon = Icons.Default.MoreVert, label = "More",
                enabled = true, theme = theme, onClick = onMenu
            )
        }
    }
}

@Composable
private fun BrowseNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    theme: ModeTheme,
    onClick: () -> Unit,
    highlight: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { if (enabled) onClick() }
            .padding(2.dp)
    ) {
        BrowseNavContent(icon = icon, label = label, enabled = enabled, theme = theme, highlight = highlight)
    }
}

@Composable
private fun BrowseNavContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    theme: ModeTheme,
    highlight: Boolean = false
) {
    // Home is highlighted in the active accent (per the 3-mode reference image).
    val tint = when {
        highlight -> theme.primary
        enabled -> theme.onSurface.copy(alpha = 0.85f)
        else -> theme.onSurface.copy(alpha = 0.3f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(2.dp)
            .sizeIn(minHeight = 48.dp, minWidth = 48.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
        Text(
            label, fontSize = 10.sp, color = tint,
            fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.Bold
        )
    }
}
