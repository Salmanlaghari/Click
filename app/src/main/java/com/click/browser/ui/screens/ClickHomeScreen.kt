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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.click.browser.engine.SearchEngineBrands
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
 * Aligned with Prince's approved browser reference:
 *  time-aware greeting and live privacy status · pill search bar
 *  (active search-provider mark, mic) · real quick-site tiles · category pill tabs ·
 *  news cards (hero + list) · 2×2 quick actions.
 *
 * All profiles share Click's warm cream interface while retaining their own
 * browsing data and feature sets.
 */
@Composable
fun ClickHomeScreen(
    theme: ModeTheme,
    activeMode: BrowserMode,
    searchEngine: String,
    adBlockerEnabled: Boolean,
    blockedCount: Int,
    wallpaperUri: String? = null,
    shieldActive: Boolean = false,
    onNavigate: (String) -> Unit,
    onOpenAiChat: () -> Unit,
    onTranslate: () -> Unit,
    onReaderMode: () -> Unit,
    onProfileClick: () -> Unit,
    onV9ShieldClick: () -> Unit = {},
    onShieldToggle: () -> Unit = {},
    onGamesClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardBg = theme.surface
    val cardBorder = theme.onSurface.copy(alpha = 0.08f)

    // Custom wallpaper (decoded off the main thread). When set, it replaces
    // the theme background with a dimmed photo so content stays readable
    // in all four profiles.
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
                    .background(theme.background.copy(alpha = 0.84f))
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
            .padding(horizontal = 16.dp)
            // The home bottom bar overlays this viewport; keep the final cards
            // in the scrollable content area above its tap targets.
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        HomeTopRow(
            theme = theme,
            activeMode = activeMode,
            adBlockerEnabled = adBlockerEnabled,
            blockedCount = blockedCount,
            neon = false,
            onProfileClick = onProfileClick
        )

        if (activeMode == BrowserMode.HACK) {
            PowerModeHomeHeader(theme)
        }

        SmartSearchBar(
            theme = theme,
            activeMode = activeMode,
            searchEngine = searchEngine,
            onNavigate = onNavigate
        )

        if (activeMode == BrowserMode.DEVELOPER) {
            ModeFocusCard(
                theme = theme,
                marker = "</>",
                eyebrow = "DEVELOPER WORKSPACE",
                headline = "Build, debug, and learn",
                detail = "Shortcuts for GitHub, MDN, and Stack Overflow.",
                actionLabel = "Open MDN",
                onClick = { onNavigate("https://developer.mozilla.org/") }
            )
        }
        if (activeMode == BrowserMode.ADVANCED) {
            ModeFocusCard(
                theme = theme,
                marker = "R",
                eyebrow = "RESEARCH DESK",
                headline = "Latest AI/ML papers",
                detail = "Recent arXiv research updates are featured below.",
                actionLabel = "Browse recent papers",
                onClick = { onNavigate("https://arxiv.org/list/cs.AI/recent") }
            )
            NewsSection(
                theme = theme,
                activeMode = activeMode,
                cardBg = cardBg,
                cardBorder = cardBorder,
                neon = false,
                onNavigate = onNavigate
            )
        }

        // V9 Shield is DNS-only; the card retains the existing detail and toggle actions.
        V9ShieldCard(
            theme = theme,
            active = shieldActive,
            neon = false,
            cardBg = cardBg,
            cardBorder = cardBorder,
            onClick = onV9ShieldClick,
            onToggle = onShieldToggle
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

        if (activeMode != BrowserMode.ADVANCED) {
            NewsSection(
                theme = theme,
                activeMode = activeMode,
                cardBg = cardBg,
                cardBorder = cardBorder,
                neon = false,
                onNavigate = onNavigate
            )
        }

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

/** V9 Shield: DNS-only status card. Its existing toggle behavior is unchanged. */
@Composable
private fun V9ShieldCard(
    theme: ModeTheme,
    active: Boolean,
    neon: Boolean,
    cardBg: Color,
    cardBorder: Color,
    onClick: () -> Unit,
    onToggle: () -> Unit = {}
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
                    "V9 Shield",
                    color = theme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Text(
                    if (active) "DNS-only protection on · not a VPN"
                    else "DNS-only protection off · not a VPN",
                    color = if (active) Color(0xFF22C55E) else theme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            // The pill toggles DNS protection; the card body opens its settings.
            Box(
                modifier = Modifier
                    .background(
                        if (active) Color(0xFF22C55E) else theme.onSurface.copy(alpha = 0.15f),
                        RoundedCornerShape(50)
                    )
                    .clickable(onClick = onToggle)
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

/** Greeting, live ad-block status, and a profile shortcut. */
@Composable
private fun HomeTopRow(
    theme: ModeTheme,
    activeMode: BrowserMode,
    adBlockerEnabled: Boolean,
    blockedCount: Int,
    neon: Boolean,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                homeGreeting(),
                color = theme.onBackground,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp
            )
            Text(
                when (activeMode) {
                    BrowserMode.SIMPLE -> "Everyday browsing, kept straightforward."
                    BrowserMode.DEVELOPER -> "Inspect pages and build with developer tools."
                    BrowserMode.HACK -> "Wide pages and focused workflows."
                    BrowserMode.ADVANCED -> "An isolated workspace for research and exploration."
                },
                color = theme.onSurface.copy(alpha = 0.72f),
                fontSize = 13.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (adBlockerEnabled) Color(0xFF16A34A) else theme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (adBlockerEnabled) {
                        if (blockedCount > 0) "$blockedCount trackers blocked" else "Ad and tracker blocking is on"
                    } else "Ad and tracker blocking is off",
                    color = theme.onSurface.copy(alpha = 0.62f),
                    fontSize = 10.sp
                )
            }
        }

        // Profile remains one tap away, in the same position as the approved design.
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

@Composable
private fun PowerModeHomeHeader(theme: ModeTheme) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = theme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            theme.modeAccent.copy(alpha = 0.28f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF92400E), theme.modeAccent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "P",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 19.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "POWER MODE",
                    color = theme.modeAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    "Desktop-style page layout",
                    color = theme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    "Wider viewport for web pages",
                    color = theme.onSurface.copy(alpha = 0.62f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ModeFocusCard(
    theme: ModeTheme,
    marker: String,
    eyebrow: String,
    headline: String,
    detail: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.modeAccent.copy(alpha = 0.32f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Brush.linearGradient(listOf(theme.modeAccent, theme.primary))),
                contentAlignment = Alignment.Center
            ) {
                Text(marker, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(eyebrow, color = theme.modeAccent, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                Text(headline, color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(detail, color = theme.onSurface.copy(alpha = 0.68f), fontSize = 11.sp)
                Text(actionLabel, color = theme.modeAccent, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
        }
    }
}

/** Pill search bar: selected search-provider mark · hint · mic. (QR scanner removed: no CameraX /
 * MLKit-barcode stack in the app — the button only showed a "coming soon"
 * toast, so the dead control is gone instead of fake.) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartSearchBar(
    theme: ModeTheme,
    activeMode: BrowserMode,
    searchEngine: String,
    onNavigate: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val submit = {
        if (query.isNotBlank()) {
            val submittedQuery = query.trim()
            query = ""
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            onNavigate(submittedQuery)
        }
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = theme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp, theme.onSurface.copy(alpha = 0.12f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchEngineBadge(engine = searchEngine, theme = theme, compact = false)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search the web or enter address", fontSize = 14.sp, color = theme.onSurface.copy(alpha = 0.45f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
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
        }
    }
}

@Composable
private fun SearchEngineBadge(engine: String, theme: ModeTheme, compact: Boolean) {
    val brand = SearchEngineBrands.forEngine(engine)
    Row(
        modifier = Modifier
            .padding(horizontal = if (compact) 2.dp else 4.dp)
            .widthIn(max = if (compact) 82.dp else 124.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 23.dp else 34.dp)
                .clip(CircleShape)
                .background(Color(brand.backgroundArgb)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                brand.monogram,
                color = Color(brand.foregroundArgb),
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (compact) 10.sp else 16.sp
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            brand.shortName,
            color = theme.onSurface,
            fontSize = if (compact) 9.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = if (compact) 52.dp else 78.dp)
        )
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
        tiles.chunked(5).forEach { row ->
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
                                .clip(RoundedCornerShape(14.dp))
                                .then(
                                    if (isAdd) Modifier.border(1.5.dp, onSurface.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
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
                repeat(5 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
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
    categories: List<NewsCategory>,
    onSelect: (NewsCategory) -> Unit,
    theme: ModeTheme
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(categories) { cat ->
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
    activeMode: BrowserMode,
    cardBg: Color,
    cardBorder: Color,
    neon: Boolean,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf(NewsCategory.ALL) }
    var refreshKey by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<NewsResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    val visibleCategories = remember(activeMode) {
        NewsCategory.values().filter { activeMode == BrowserMode.ADVANCED || it != NewsCategory.RESEARCH }
    }

    LaunchedEffect(activeMode) {
        if (activeMode != BrowserMode.ADVANCED && category == NewsCategory.RESEARCH) {
            category = NewsCategory.ALL
        }
    }

    LaunchedEffect(category, refreshKey) {
        loading = true
        result = NewsFeed.getArticles(context, category)
        loading = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (category == NewsCategory.RESEARCH) {
            Text(
                "arXiv AI & ML · preprints are not necessarily peer-reviewed",
                color = theme.onSurface.copy(alpha = 0.62f),
                fontSize = 11.sp
            )
        }
        CategoryTabs(selected = category, categories = visibleCategories, onSelect = { category = it }, theme = theme)

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
                        if (category != NewsCategory.RESEARCH) Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            when (category) {
                                NewsCategory.RESEARCH -> when (result?.status) {
                                    NewsStatus.OFFLINE -> "You're offline. No recent cached research papers are available."
                                    NewsStatus.ERROR -> "Couldn't verify current arXiv results. Older papers are hidden. Tap Retry."
                                    else -> "No recent AI/ML papers are available. Older arXiv items are hidden."
                                }
                                else -> when (result?.status) {
                                    NewsStatus.OFFLINE -> "You're offline — news will appear when you're back online."
                                    NewsStatus.ERROR -> "Couldn't load news. Check your connection and tap Retry."
                                    else -> "Couldn't load news right now."
                                }
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
                            ) {
                                if (category == NewsCategory.RESEARCH) {
                                    scope.launch {
                                        NewsFeed.invalidateResearchCache(context)
                                        refreshKey++
                                    }
                                } else {
                                    refreshKey++
                                }
                            }
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
                        hero.thumbnailUrl?.let { url ->
                            NewsThumbnail(
                                url = url,
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            )
                        }
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
                            if (!article.thumbnailUrl.isNullOrBlank()) {
                                NewsThumbnail(
                                    url = article.thumbnailUrl,
                                    modifier = Modifier.size(72.dp, 56.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
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
            }
        }
    }
}

/** Source thumbnail with memory+disk cache; omit the image area rather than invent a placeholder. */
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
    Surface(
        color = theme.topBarBg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, theme.onSurface.copy(alpha = 0.08f)
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
                        Text(tabBadgeText(tabCount), fontSize = 9.sp)
                    }
                },
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabs() }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(Icons.Default.Tab, contentDescription = "Tabs", tint = theme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                    Text("Tabs", fontSize = 10.sp, color = theme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                }
            }

            // Center: LARGE glowing AI button
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .shadow(10.dp, CircleShape, spotColor = theme.primary)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF72583D), Color(0xFFAA865C), Color(0xFFC7AE8A))))
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
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
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
 *  compact URL bar · WebView focus · floating AI button. Simple uses a
 *  compact Home + Previous/Next footer; other profiles retain the full nav.
 * --------------------------------------------------------------------------- */

/**
 * 2026-style premium address bar for the browsing surface.
 *
 * Layout: [back · forward · reload/new tab · tabs · add-on icon · three-dot menu]
 *          [compact address/search box]
 * - The hamburger is kept on the home/new-tab surface; loaded pages use the
 *   three-dot menu and edge-swipe for navigation-drawer access.
 * - Add-on action labels stay in one dropdown anchored to the extension icon.
 * - Search-provider branding appears on the new-tab search field; loaded pages
 *   show their site security affordance instead.
 * - Tab counter button for quick tab switching.
 * - The address field is 46dp high; mode-aware accent colors keep touch targets clear.
 *
 * The URL field is directly editable (tap, type, Go).
 */
/** Kilo: single source of truth for tab-count badge text — "99+" past 99. */
internal fun tabBadgeText(count: Int): String =
    if (count > 99) "99+" else count.coerceAtLeast(0).toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactBrowseBar(
    theme: ModeTheme,
    activeMode: BrowserMode,
    currentUrl: String,
    searchEngine: String,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onNewTab: () -> Unit,
    onMenuClick: () -> Unit,
    onSiteSettingsClick: () -> Unit = {},
    // Kilo: these are required (no defaults) so missing wiring fails at
    // compile time instead of silently doing nothing.
    onDrawerClick: () -> Unit,
    canGoBack: Boolean,
    onBack: () -> Unit,
    canGoForward: Boolean,
    onForward: () -> Unit,
    tabCount: Int,
    onTabsClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Location indicator: null = no indicator; otherwise the effective mode.
    locationMode: LocationGuard.LocationMode? = null,
    onLocationClick: () -> Unit = {},
    showDeveloperTools: Boolean = false,
    onDeveloperToolsClick: () -> Unit = {},
    showExtensionButton: Boolean = false,
    onAddonsClick: () -> Unit = {}
) {
    var textInput by remember(currentUrl) { mutableStateOf(currentUrl) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val submit = {
        val submittedText = textInput.trim()
        if (submittedText.isNotEmpty()) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            onNavigate(submittedText)
        }
    }
    val isHttps = currentUrl.startsWith("https://")
    // Keep toolbar actions on their own row so the address field can use the
    // full available width and a generous touch target.
    Surface(
        color = theme.topBarBg,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (currentUrl.isBlank()) {
                    IconButton(onClick = onDrawerClick, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Open sidebar menu",
                            tint = theme.onTopBar,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) theme.onTopBar else theme.onTopBar.copy(alpha = 0.34f),
                        modifier = Modifier.size(21.dp)
                    )
                }
                IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) theme.onTopBar else theme.onTopBar.copy(alpha = 0.34f),
                        modifier = Modifier.size(21.dp)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = theme.modeAccent.copy(alpha = 0.12f),
                    contentColor = theme.modeAccent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, theme.modeAccent.copy(alpha = 0.28f)
                    )
                ) {
                    Text(
                        when (activeMode) {
                            BrowserMode.SIMPLE -> "SIMPLE"
                            BrowserMode.DEVELOPER -> "DEV"
                            BrowserMode.HACK -> "POWER"
                            BrowserMode.ADVANCED -> "ADVANCE"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // Simple mode consistently offers New tab; reload remains in the browser drawer.
                if (activeMode == BrowserMode.SIMPLE) {
                    IconButton(onClick = onNewTab, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "New tab",
                            tint = theme.onTopBar,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onReload, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = theme.onTopBar,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                IconButton(onClick = onTabsClick, modifier = Modifier.size(40.dp)) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = theme.primary,
                                contentColor = Color.White,
                                modifier = Modifier.offset(x = 6.dp, y = (-6).dp)
                            ) {
                                Text(
                                    tabBadgeText(tabCount),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Tab,
                            contentDescription = "Tabs ($tabCount)",
                            tint = theme.onTopBar,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                    if (showDeveloperTools) {
                        IconButton(
                            onClick = onDeveloperToolsClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = "Open Developer Console",
                                tint = theme.onTopBar,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                    if (showExtensionButton) {
                    Box {
                        IconButton(
                            onClick = onAddonsClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = "Installed add-ons",
                                tint = theme.onTopBar,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = onMenuClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Browser menu",
                        tint = theme.onTopBar,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = theme.surfaceVariant,
                shadowElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, theme.primary.copy(alpha = 0.28f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .animateContentSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // On a new tab this is a neutral search affordance;
                    // on real pages it opens site settings and reflects URL scheme only.
                    if (currentUrl.isBlank()) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = theme.onSurface.copy(alpha = 0.58f),
                            modifier = Modifier.padding(start = 8.dp).size(18.dp)
                        )
                    } else {
                        IconButton(
                            onClick = onSiteSettingsClick,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = if (isHttps) "HTTPS URL — tap for site settings" else "HTTP URL (not encrypted) — tap for site settings",
                                tint = if (isHttps) Color(0xFF22C55E) else Color(0xFFF59E0B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (locationMode == LocationGuard.LocationMode.BLOCK && currentUrl.isNotBlank()) {
                        IconButton(
                            onClick = onLocationClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOff,
                                contentDescription = "Location requests blocked for this site",
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (currentUrl.isBlank()) {
                        SearchEngineBadge(engine = searchEngine, theme = theme, compact = true)
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            color = theme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (textInput.isBlank()) {
                                    Text(
                                        "Search or type URL",
                                        color = theme.onSurface.copy(alpha = 0.48f),
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = { submit() },
                            onSearch = { submit() },
                            onDone = { submit() }
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                    if (textInput.isNotEmpty()) {
                        IconButton(
                            onClick = { textInput = "" },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = theme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
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
                .shadow(10.dp, RoundedCornerShape(28.dp), spotColor = theme.primary)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF72583D), Color(0xFFAA865C))))
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
 * Bottom nav for the browsing surface. Back/forward stay in the address bar;
 * this footer contains only the home, tabs, drawer, and menu actions.
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
    activeMode: BrowserMode,
    tabCount: Int,
    onDrawerClick: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        color = theme.topBarBg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, theme.onSurface.copy(alpha = 0.08f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (activeMode == BrowserMode.SIMPLE) 12.dp else 2.dp,
                    vertical = if (activeMode == BrowserMode.SIMPLE) 6.dp else 3.dp
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (activeMode == BrowserMode.SIMPLE) {
                BrowseNavButton(
                    icon = Icons.Default.Home,
                    label = "Home",
                    enabled = true,
                    theme = theme,
                    onClick = onHome
                )
            } else {
                BrowseNavButton(
                    icon = Icons.Default.Menu, label = "Menu",
                    enabled = true, theme = theme, onClick = onDrawerClick
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
                            Text(tabBadgeText(tabCount), fontSize = 9.sp)
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
}

@Composable
private fun BrowseNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    theme: ModeTheme,
    onClick: () -> Unit,
    highlight: Boolean = false,
    emphasized: Boolean = false
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(
                if (emphasized) Modifier
                    .clip(shape)
                    .background(
                        if (enabled) theme.primary.copy(alpha = 0.11f)
                        else theme.surfaceVariant.copy(alpha = 0.45f)
                    )
                    .border(1.dp, theme.primary.copy(alpha = if (enabled) 0.24f else 0.09f), shape)
                else Modifier
            )
            .clickable(
                enabled = enabled,
                onClickLabel = label,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .sizeIn(minHeight = 48.dp, minWidth = 48.dp)
    ) {
        BrowseNavContent(
            icon = icon,
            label = label,
            enabled = enabled,
            theme = theme,
            highlight = highlight,
            emphasized = emphasized
        )
    }
}

@Composable
private fun BrowseNavContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    theme: ModeTheme,
    highlight: Boolean = false,
    emphasized: Boolean = false
) {
    // Home is highlighted in the active accent (per the 3-mode reference image).
    val tint = when {
        highlight -> theme.primary
        emphasized && enabled -> theme.primary
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
