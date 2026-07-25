package com.click.browser

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.click.browser.data.Bookmark
import com.click.browser.data.BrowserRepository
import com.click.browser.data.HistoryItem
import com.click.browser.data.DownloadItem
import com.click.browser.engine.*
import com.click.browser.ui.screens.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var modeManager: ModeManager
    private lateinit var repository: BrowserRepository

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        modeManager = ModeManager(this)
        repository = BrowserRepository(this)

        setContent {
            val scope = rememberCoroutineScope()
            val activeMode by modeManager.modeFlow.collectAsState(initial = BrowserMode.SIMPLE)

            // UI States
            var webViewInstance by remember { mutableStateOf<WebView?>(null) }
            var currentUrl by remember { mutableStateOf("about:blank") }
            var pageTitle by remember { mutableStateOf("Click Browser") }
            var canGoBack by remember { mutableStateOf(false) }
            var canGoForward by remember { mutableStateOf(false) }
            var showHome by remember { mutableStateOf(true) }
            var showBookmarks by remember { mutableStateOf(false) }
            var showHistory by remember { mutableStateOf(false) }
            var showDownloads by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(false) }

            // Developer Mode State
            var elementInspectorEnabled by remember { mutableStateOf(false) }
            var deviceEmulatorMode by remember { mutableStateOf("Mobile") } // Mobile, Tablet, Desktop
            var pageLoadTime by remember { mutableStateOf(0L) }
            var lastPageStart by remember { mutableStateOf(0L) }
            var showDebugOverlay by remember { mutableStateOf(true) }

            val logs = remember { mutableStateListOf<LogEntry>() }
            val networkRequests = remember { mutableStateListOf<NetworkRequest>() }
            var domHtml by remember { mutableStateOf("") }
            val sourcesList = remember { mutableStateListOf<String>() }

            // Hack Mode State
            var antiDetectionEnabled by remember { mutableStateOf(true) }
            var forceDesktopMode by remember { mutableStateOf(true) }
            var spoofedUAIndex by remember { mutableStateOf(0) }
            val detectedVideos = remember { mutableStateListOf<String>() }
            var showDownloaderDialog by remember { mutableStateOf(false) }

            // Theme Setting
            var currentThemeSetting by remember { mutableStateOf("Light") }
            var currentSearchEngineSetting by remember { mutableStateOf("Google") }
            var currentHomepageSetting by remember { mutableStateOf("https://www.google.com") }

            // Colors depending on active browser mode
            val themeColors = when (activeMode) {
                BrowserMode.SIMPLE -> if (currentThemeSetting == "Dark") darkColorScheme(primary = Color(0xFF1E88E5)) else lightColorScheme(primary = Color(0xFF1E88E5))
                BrowserMode.DEVELOPER -> if (currentThemeSetting == "Dark") darkColorScheme(primary = Color(0xFF7B1FA2)) else lightColorScheme(primary = Color(0xFF7B1FA2))
                BrowserMode.HACK -> darkColorScheme(
                    primary = Color(0xFFFF5722),
                    background = Color(0xFF0D0D0D),
                    surface = Color(0xFF121212),
                    onBackground = Color(0xFF00FF00),
                    onSurface = Color(0xFF00FF00)
                )
            }

            MaterialTheme(colorScheme = themeColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {

                        // Main Layout Column
                        Column(modifier = Modifier.fillMaxSize()) {

                            // 1. TOP BAR: Mode Selector & Mode Indicators
                            TopBarComponent(
                                activeMode = activeMode,
                                onModeSelected = { mode ->
                                    scope.launch {
                                        modeManager.setMode(mode)
                                        webViewInstance?.let { webView ->
                                            modeManager.applySettings(webView, mode, forceDesktopMode)
                                            if (!showHome) {
                                                webView.reload()
                                            }
                                        }
                                    }
                                }
                            )

                            // 2. ADDRESS BAR with navigation and state info
                            AddressBarComponent(
                                activeMode = activeMode,
                                currentUrl = currentUrl,
                                pageTitle = pageTitle,
                                canGoBack = canGoBack,
                                canGoForward = canGoForward,
                                onBack = { webViewInstance?.goBack() },
                                onForward = { webViewInstance?.goForward() },
                                onRefresh = { webViewInstance?.reload() },
                                onNavigate = { input ->
                                    val destination = formatUrl(input, currentSearchEngineSetting)
                                    showHome = false
                                    webViewInstance?.loadUrl(destination)
                                },
                                // Dev additions
                                elementInspectorEnabled = elementInspectorEnabled,
                                onToggleInspector = {
                                    elementInspectorEnabled = !elementInspectorEnabled
                                    webViewInstance?.evaluateJavascript(
                                        if (elementInspectorEnabled) DevToolsInjections.ELEMENT_INSPECTOR_ENABLE else DevToolsInjections.ELEMENT_INSPECTOR_DISABLE,
                                        null
                                    )
                                },
                                deviceEmulatorMode = deviceEmulatorMode,
                                onToggleEmulator = {
                                    deviceEmulatorMode = when (deviceEmulatorMode) {
                                        "Mobile" -> "Tablet"
                                        "Tablet" -> "Desktop"
                                        else -> "Mobile"
                                    }
                                    webViewInstance?.let { webView ->
                                        modeManager.applySettings(webView, activeMode, deviceEmulatorMode == "Desktop" || forceDesktopMode)
                                        webView.reload()
                                    }
                                }
                            )

                            // 3. CONTENT AREA
                            Box(modifier = Modifier.weight(1f)) {
                                if (showHome) {
                                    // Welcome / Landing Screen depending on mode
                                    HomeScreen(
                                        activeMode = activeMode,
                                        onSearch = { input ->
                                            val destination = formatUrl(input, currentSearchEngineSetting)
                                            showHome = false
                                            webViewInstance?.loadUrl(destination)
                                        },
                                        onNavigate = { url ->
                                            showHome = false
                                            webViewInstance?.loadUrl(url)
                                        },
                                        onBookmarksClick = { showBookmarks = true },
                                        onHistoryClick = { showHistory = true },
                                        onDownloadsClick = { showDownloads = true },
                                        onSettingsClick = { showSettings = true },
                                        // Hack mode actions
                                        antiDetectionEnabled = antiDetectionEnabled,
                                        onToggleAntiDetection = { antiDetectionEnabled = !antiDetectionEnabled },
                                        forceDesktopMode = forceDesktopMode,
                                        onToggleForceDesktop = {
                                            forceDesktopMode = !forceDesktopMode
                                            webViewInstance?.let { webView ->
                                                modeManager.applySettings(webView, activeMode, forceDesktopMode)
                                            }
                                        },
                                        spoofedUAIndex = spoofedUAIndex,
                                        onCycleUA = {
                                            spoofedUAIndex = (spoofedUAIndex + 1) % 4
                                            val uaStr = when (spoofedUAIndex) {
                                                0 -> ModeManager.UA_HACK // Win11 Chrome
                                                1 -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15" // Mac Safari
                                                2 -> "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/125.0" // Linux Firefox
                                                else -> ModeManager.UA_SIMPLE // Android Chrome
                                            }
                                            webViewInstance?.settings?.userAgentString = uaStr
                                        }
                                    )
                                } else {
                                    // Android WebView Wrapper with custom dimensions for Desktop / Emulator spoofing
                                    val emulatorWidthModifier = when (deviceEmulatorMode) {
                                        "Tablet" -> Modifier.fillMaxHeight().width(768.dp)
                                        "Desktop" -> Modifier.fillMaxHeight().width(1024.dp)
                                        else -> Modifier.fillMaxSize()
                                    }

                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AndroidView(
                                            modifier = emulatorWidthModifier,
                                            factory = { ctx ->
                                                WebView(ctx).apply {
                                                    webViewClient = object : WebViewClient() {
                                                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                                            super.onPageStarted(view, url, favicon)
                                                            currentUrl = url ?: ""
                                                            lastPageStart = System.currentTimeMillis()
                                                            canGoBack = canGo()
                                                            canGoForward = canGoF()

                                                            // Clear page data on reload/navigation
                                                            networkRequests.clear()
                                                            sourcesList.clear()
                                                            detectedVideos.clear()
                                                        }

                                                        override fun onPageFinished(view: WebView?, url: String?) {
                                                            super.onPageFinished(view, url)
                                                            pageTitle = view?.title ?: ""
                                                            pageLoadTime = System.currentTimeMillis() - lastPageStart
                                                            canGoBack = canGo()
                                                            canGoForward = canGoF()

                                                            // Add to history
                                                            if (url != null && url != "about:blank") {
                                                                scope.launch {
                                                                    repository.addHistoryItem(HistoryItem(pageTitle, url, System.currentTimeMillis()))
                                                                }
                                                            }

                                                            // Injections based on mode
                                                            if (activeMode == BrowserMode.DEVELOPER) {
                                                                view?.evaluateJavascript(DevToolsInjections.CONSOLE_HIJACK, null)
                                                                view?.evaluateJavascript(DevToolsInjections.NETWORK_INTERCEPT, null)
                                                                view?.evaluateJavascript(DevToolsInjections.GET_DOM, null)
                                                                view?.evaluateJavascript(DevToolsInjections.GET_SOURCES, null)
                                                            } else if (activeMode == BrowserMode.HACK) {
                                                                if (antiDetectionEnabled) {
                                                                    view?.evaluateJavascript(AntiDetectionInjections.INJECT_10_LAYERS, null)
                                                                }
                                                                view?.evaluateJavascript(AntiDetectionInjections.VIDEO_GRABBER_JS, null)
                                                            }
                                                        }

                                                        private fun canGo() = this@apply.canGoBack()
                                                        private fun canGoF() = this@apply.canGoForward()
                                                    }

                                                    webChromeClient = object : WebChromeClient() {
                                                        override fun onReceivedTitle(view: WebView?, title: String?) {
                                                            super.onReceivedTitle(view, title)
                                                            pageTitle = title ?: ""
                                                        }
                                                    }

                                                    // Setup Bridges
                                                    addJavascriptInterface(
                                                        DevToolsBridge(
                                                            onLogAdded = { logs.add(it) },
                                                            onNetworkAdded = { networkRequests.add(it) },
                                                            onDomUpdated = { domHtml = it },
                                                            onSourcesUpdated = { list ->
                                                                sourcesList.clear()
                                                                sourcesList.addAll(list)
                                                            }
                                                        ),
                                                        "DevToolsBridge"
                                                    )

                                                    addJavascriptInterface(
                                                        VideoGrabberBridge(
                                                            onVideosDetected = { list ->
                                                                detectedVideos.clear()
                                                                detectedVideos.addAll(list)
                                                            }
                                                        ),
                                                        "VideoGrabberBridge"
                                                    )

                                                    modeManager.applySettings(this, activeMode, forceDesktopMode)
                                                    webViewInstance = this
                                                }
                                            },
                                            update = { _ ->
                                                // General updates
                                            }
                                        )
                                    }
                                }

                                // 4. FLOATING DEV DEBUG OVERLAY
                                if (activeMode == BrowserMode.DEVELOPER && showDebugOverlay && !showHome) {
                                    FloatingDebugOverlay(
                                        pageLoadTime = pageLoadTime,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(16.dp)
                                    )
                                }

                                // 5. FLOATING HACK DOWNLOAD TRIGGER
                                if (activeMode == BrowserMode.HACK && detectedVideos.isNotEmpty()) {
                                    FloatingActionButton(
                                        onClick = { showDownloaderDialog = true },
                                        containerColor = Color(0xFFFF5722),
                                        contentColor = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(16.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = "Grab Video")
                                    }
                                }
                            }

                            // 6. BOTTOM PANELS (DevTools / Mode controls)
                            if (activeMode == BrowserMode.DEVELOPER && !showHome) {
                                DevToolsPanel(
                                    logs = logs,
                                    networkRequests = networkRequests,
                                    domHtml = domHtml,
                                    sourcesList = sourcesList,
                                    onClearLogs = { logs.clear() },
                                    onEvalJs = { code ->
                                        webViewInstance?.evaluateJavascript(code, null)
                                    }
                                )
                            }
                        }

                        // Bottom Navigation Shortcuts trigger overlays
                        if (showHome) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                IconButton(onClick = { showHome = true }) {
                                    Icon(Icons.Default.Home, contentDescription = "Home", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = {
                                    // Save current to bookmarks
                                    if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
                                        scope.launch {
                                            repository.addBookmark(Bookmark(pageTitle, currentUrl))
                                            Toast.makeText(this@MainActivity, "Bookmark Saved", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        showBookmarks = true
                                    }
                                }) {
                                    Icon(Icons.Default.Bookmark, contentDescription = "Add Bookmark")
                                }
                                IconButton(onClick = { showHistory = true }) {
                                    Icon(Icons.Default.History, contentDescription = "History")
                                }
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            }
                        }

                        // Floating Overlays for Screens
                        if (showBookmarks) {
                            BookmarksScreen(
                                repository = repository,
                                onNavigate = { url ->
                                    showHome = false
                                    webViewInstance?.loadUrl(url)
                                },
                                onClose = { showBookmarks = false }
                            )
                        }

                        if (showHistory) {
                            HistoryScreen(
                                repository = repository,
                                onNavigate = { url ->
                                    showHome = false
                                    webViewInstance?.loadUrl(url)
                                },
                                onClose = { showHistory = false }
                            )
                        }

                        if (showDownloads) {
                            DownloadsScreen(
                                repository = repository,
                                onClose = { showDownloads = false }
                            )
                        }

                        if (showSettings) {
                            SettingsScreen(
                                currentTheme = currentThemeSetting,
                                onThemeChange = { currentThemeSetting = it },
                                currentSearchEngine = currentSearchEngineSetting,
                                onSearchEngineChange = { currentSearchEngineSetting = it },
                                currentHomepage = currentHomepageSetting,
                                onHomepageChange = { currentHomepageSetting = it },
                                onClearData = {
                                    scope.launch {
                                        repository.clearHistory()
                                        Toast.makeText(this@MainActivity, "Browser Data Cleared", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onClose = { showSettings = false }
                            )
                        }

                        // Universal Video Downloader Dialog
                        if (showDownloaderDialog) {
                            AlertDialog(
                                onDismissRequest = { showDownloaderDialog = false },
                                title = { Text("Universal Video Downloader", color = Color(0xFFFF5722)) },
                                text = {
                                    Column {
                                        Text("Detected Videos on page:")
                                        Spacer(modifier = Modifier.height(8.dp))
                                        detectedVideos.forEachIndexed { idx, url ->
                                            Text(
                                                text = "Video Source ${idx + 1}: ${url.take(60)}...",
                                                fontSize = 12.sp,
                                                color = Color.Green,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        // Fake download and save as requested
                                                        val targetFolder = File(this@MainActivity.getExternalFilesDir(null), "Movies/ClickBrowser")
                                                        targetFolder.mkdirs()
                                                        val targetFile = File(targetFolder, "video_${System.currentTimeMillis()}.mp4")
                                                        targetFile.writeText("Fake video downloader payload from url: $url")

                                                        scope.launch {
                                                            repository.addDownloadItem(
                                                                DownloadItem(
                                                                    fileName = targetFile.name,
                                                                    url = url,
                                                                    path = targetFile.absolutePath,
                                                                    timestamp = System.currentTimeMillis()
                                                                )
                                                            )
                                                        }
                                                        Toast.makeText(this@MainActivity, "Downloading to Movies/ClickBrowser...", Toast.LENGTH_SHORT).show()
                                                        showDownloaderDialog = false
                                                    }
                                                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                                    .padding(6.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showDownloaderDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun formatUrl(input: String, searchEngine: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            return "https://$trimmed"
        }
        val query = java.net.URLEncoder.encode(trimmed, "UTF-8")
        return when (searchEngine) {
            "Bing" -> "https://www.bing.com/search?q=$query"
            "DuckDuckGo" -> "https://duckduckgo.com/?q=$query"
            else -> "https://www.google.com/search?q=$query"
        }
    }
}

@Composable
fun TopBarComponent(
    activeMode: BrowserMode,
    onModeSelected: (BrowserMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF121212))
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Click Browser",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        // Pill Switchers
        Row(
            modifier = Modifier
                .background(Color(0xFF262626), shape = RoundedCornerShape(24.dp))
                .padding(4.dp)
        ) {
            BrowserMode.values().forEach { mode ->
                val activeBgColor = when (mode) {
                    BrowserMode.SIMPLE -> Color(0xFF1E88E5)
                    BrowserMode.DEVELOPER -> Color(0xFF7B1FA2)
                    BrowserMode.HACK -> Color(0xFFFF5722)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (activeMode == mode) activeBgColor else Color.Transparent)
                        .clickable { onModeSelected(mode) }
                        .padding(vertical = 6.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = mode.name.first() + mode.name.substring(1).lowercase(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AddressBarComponent(
    activeMode: BrowserMode,
    currentUrl: String,
    @Suppress("UNUSED_PARAMETER") pageTitle: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onNavigate: (String) -> Unit,
    // Dev inspect additions
    elementInspectorEnabled: Boolean,
    onToggleInspector: () -> Unit,
    deviceEmulatorMode: String,
    onToggleEmulator: () -> Unit
) {
    var textInput by remember(currentUrl) { mutableStateOf(currentUrl) }

    val activeBadgeColor = when (activeMode) {
        BrowserMode.SIMPLE -> Color(0xFF1E88E5)
        BrowserMode.DEVELOPER -> Color(0xFF7B1FA2)
        BrowserMode.HACK -> Color(0xFFFF5722)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .shadow(4.dp)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }

            // URL input field
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(fontSize = 12.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onNavigate(textInput) }),
                leadingIcon = {
                    if (currentUrl.startsWith("https")) {
                        Icon(Icons.Default.Lock, contentDescription = "Secure Connection", tint = Color.Green, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Default.LockOpen, contentDescription = "Not Secure", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                },
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .background(activeBadgeColor, shape = RoundedCornerShape(12.dp))
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        Text(activeMode.name, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Mode specific extras on address bar
        if (activeMode == BrowserMode.DEVELOPER) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onToggleInspector,
                    colors = ButtonDefaults.buttonColors(containerColor = if (elementInspectorEnabled) Color.Magenta else Color.DarkGray),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(if (elementInspectorEnabled) "Inspect ON" else "Inspect OFF", fontSize = 10.sp)
                }

                Button(
                    onClick = onToggleEmulator,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Emulator: $deviceEmulatorMode", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    activeMode: BrowserMode,
    onSearch: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onBookmarksClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onSettingsClick: () -> Unit,
    // Hack Mode extras
    @Suppress("UNUSED_PARAMETER") antiDetectionEnabled: Boolean,
    @Suppress("UNUSED_PARAMETER") onToggleAntiDetection: () -> Unit,
    forceDesktopMode: Boolean,
    onToggleForceDesktop: () -> Unit,
    spoofedUAIndex: Int,
    onCycleUA: () -> Unit
) {
    var searchInput by remember { mutableStateOf("") }

    val shortcuts = listOf(
        ShortcutItem("YouTube", "https://www.youtube.com", Icons.Default.PlayArrow),
        ShortcutItem("Google", "https://www.google.com", Icons.Default.Search),
        ShortcutItem("Facebook", "https://www.facebook.com", Icons.Default.Face),
        ShortcutItem("Instagram", "https://www.instagram.com", Icons.Default.CameraAlt),
        ShortcutItem("X", "https://www.x.com", Icons.Default.Share),
        ShortcutItem("TikTok", "https://www.tiktok.com", Icons.Default.MusicNote),
        ShortcutItem("Gmail", "https://mail.google.com", Icons.Default.Email),
        ShortcutItem("Maps", "https://maps.google.com", Icons.Default.LocationOn)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (activeMode == BrowserMode.HACK) {
            MatrixGridAnimation()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Mode Header Badge Display
            Text(
                text = "Click",
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                color = when (activeMode) {
                    BrowserMode.SIMPLE -> Color(0xFF1E88E5)
                    BrowserMode.DEVELOPER -> Color(0xFF7B1FA2)
                    BrowserMode.HACK -> Color(0xFF00FF00)
                }
            )

            Text(
                text = when (activeMode) {
                    BrowserMode.SIMPLE -> "The Premium Mobile Browser"
                    BrowserMode.DEVELOPER -> "Developer Edition • Integrated DevTools"
                    BrowserMode.HACK -> "Hack Mode • Anonymous Anti-Detection"
                },
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Mode specific configurations
            if (activeMode == BrowserMode.HACK) {
                // Anti-Detection Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF00FF00), shape = RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🛡️ Anti-Detection: ACTIVE", color = Color(0xFF00FF00), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("10-Layers Protection", color = Color.White, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Hack grid controls
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.clickable { onToggleForceDesktop() },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B2B))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("🖥️ Desktop Mode", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                                Text(if (forceDesktopMode) "FORCE ON" else "OFF", color = Color.Green, fontSize = 11.sp)
                            }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.clickable { onCycleUA() },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B2B))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("🎭 UA Spoof", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                                val uaText = when (spoofedUAIndex) {
                                    0 -> "Win11 Chrome"
                                    1 -> "Mac Safari"
                                    2 -> "Linux Firefox"
                                    else -> "Android Chrome"
                                }
                                Text(uaText, color = Color.Yellow, fontSize = 11.sp)
                            }
                        }
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B2B))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("🧩 Extensions", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                                Text("Placeholder UI", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B2B))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("🔒 VPN Mode", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                                Text("Proxy: Default", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Google-style Search Bar
            OutlinedTextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                placeholder = { Text("Search Google or enter address...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(searchInput) }),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchInput.isNotEmpty()) {
                        IconButton(onClick = { searchInput = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 4x4 Shortcut grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(shortcuts) { shortcut ->
                    Column(
                        modifier = Modifier
                            .clickable { onNavigate(shortcut.url) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = if (activeMode == BrowserMode.HACK) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = shortcut.icon,
                                contentDescription = shortcut.name,
                                tint = if (activeMode == BrowserMode.HACK) Color(0xFF00FF00) else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = shortcut.name,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            color = if (activeMode == BrowserMode.HACK) Color(0xFF00FF00) else Color.Unspecified
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Access Buttons below shortcuts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(onClick = onBookmarksClick, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Bookmarks", fontSize = 12.sp)
                }
                Button(onClick = onHistoryClick, modifier = Modifier.padding(end = 8.dp)) {
                    Text("History", fontSize = 12.sp)
                }
                Button(onClick = onDownloadsClick) {
                    Text("Downloads", fontSize = 12.sp)
                }
            }
        }
    }
}

data class ShortcutItem(val name: String, val url: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
