package com.click.browser

import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder

data class TabItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var url: String = "about:blank",
    var title: String = "New Tab",
    var isIncognito: Boolean = false,
    var webView: WebView? = null
)

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

            // Premium Theme configuration based on Mode Vibe
            var currentThemeSetting by remember { mutableStateOf("Dark") }
            val isDark = currentThemeSetting == "Dark" || activeMode != BrowserMode.SIMPLE

            val themeColors = when (activeMode) {
                BrowserMode.SIMPLE -> {
                    if (isDark) {
                        darkColorScheme(
                            primary = Color(0xFF3B82F6),
                            surface = Color(0xFF1E293B),
                            background = Color(0xFF0F172A),
                            secondary = Color(0xFF2563EB)
                        )
                    } else {
                        lightColorScheme(
                            primary = Color(0xFF2563EB),
                            surface = Color(0xFFFFFFFF),
                            background = Color(0xFFF8FAFC),
                            secondary = Color(0xFF3B82F6)
                        )
                    }
                }
                BrowserMode.DEVELOPER -> {
                    darkColorScheme(
                        primary = Color(0xFF7C3AED),
                        surface = Color(0xFF0F172A),
                        background = Color(0xFF020617),
                        secondary = Color(0xFF9333EA),
                        onBackground = Color(0xFFA78BFA),
                        onSurface = Color(0xFFA78BFA)
                    )
                }
                BrowserMode.HACK -> {
                    darkColorScheme(
                        primary = Color(0xFFDC2626),
                        surface = Color(0xFF18181B),
                        background = Color(0xFF09090B),
                        secondary = Color(0xFFEF4444),
                        onBackground = Color(0xFF00FF00),
                        onSurface = Color(0xFF00FF00)
                    )
                }
            }

            // Browser Premium Feature States
            val tabs = remember { mutableStateListOf<TabItem>(TabItem(url = "about:blank", title = "New Tab")) }
            var activeTabIndex by remember { mutableStateOf(0) }
            val currentTab = tabs.getOrNull(activeTabIndex) ?: TabItem(url = "about:blank")

            var showTabsManager by remember { mutableStateOf(false) }
            var isIncognitoMode by remember { mutableStateOf(false) }
            var adBlockerEnabled by remember { mutableStateOf(true) }
            var forceNightModeWebsites by remember { mutableStateOf(false) }
            var httpsOnlyMode by remember { mutableStateOf(true) }
            var javaScriptEnabledGlobal by remember { mutableStateOf(true) }
            var savePasswordsEnabled by remember { mutableStateOf(true) }
            var dataSaverEnabled by remember { mutableStateOf(false) }

            // Common overlays
            var showBookmarks by remember { mutableStateOf(false) }
            var showHistory by remember { mutableStateOf(false) }
            var showDownloads by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(false) }
            var showFindInPageDialog by remember { mutableStateOf(false) }
            var findQuery by remember { mutableStateOf("") }

            // Dev tools states
            var elementInspectorEnabled by remember { mutableStateOf(false) }
            var deviceEmulatorMode by remember { mutableStateOf("Mobile") } // Mobile, Tablet, Desktop
            var pageLoadTime by remember { mutableStateOf(0L) }
            var lastPageStart by remember { mutableStateOf(0L) }
            var showDebugOverlay by remember { mutableStateOf(true) }
            val logs = remember { mutableStateListOf<LogEntry>() }
            val networkRequests = remember { mutableStateListOf<NetworkRequest>() }
            var domHtml by remember { mutableStateOf("") }
            val sourcesList = remember { mutableStateListOf<String>() }

            // Hack tools states
            var antiDetectionEnabled by remember { mutableStateOf(true) }
            var forceDesktopMode by remember { mutableStateOf(true) }
            var spoofedUAIndex by remember { mutableStateOf(0) }
            val detectedVideos = remember { mutableStateListOf<String>() }
            var showDownloaderDialog by remember { mutableStateOf(false) }

            // Settings Configurations
            var currentSearchEngineSetting by remember { mutableStateOf("Google") }

            // Handle back presses
            BackHandler(enabled = currentTab.url != "about:blank") {
                val wv = currentTab.webView
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                } else {
                    currentTab.url = "about:blank"
                }
            }

            MaterialTheme(colorScheme = themeColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {

                        // Main Column Layout
                        Column(modifier = Modifier.fillMaxSize()) {

                            // 1. TOP PREMIUM BAR: Title, Tabs count badge, Mode button toggles, and options gear settings
                            PremiumTopBar(
                                activeMode = activeMode,
                                tabsCount = tabs.size,
                                onOpenTabsManager = { showTabsManager = true },
                                onOptionSelected = { option ->
                                    when (option) {
                                        "Bookmarks" -> showBookmarks = true
                                        "History" -> showHistory = true
                                        "Downloads" -> showDownloads = true
                                        "Settings" -> showSettings = true
                                        "Find in Page" -> showFindInPageDialog = true
                                        "Incognito Mode" -> {
                                            isIncognitoMode = !isIncognitoMode
                                            val newTab = TabItem(url = "about:blank", title = "Private Tab", isIncognito = isIncognitoMode)
                                            tabs.add(newTab)
                                            activeTabIndex = tabs.size - 1
                                            Toast.makeText(this@MainActivity, if (isIncognitoMode) "Private Incognito Tab Opened" else "Incognito Off", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onModeChange = { mode ->
                                    scope.launch {
                                        modeManager.setMode(mode)
                                        currentTab.webView?.let { wv ->
                                            modeManager.applySettings(wv, mode, forceDesktopMode)
                                            wv.reload()
                                        }
                                    }
                                }
                            )

                            // 2. 3D GLASSMORPHIC ADDRESS BAR with search and navigate controls
                            PremiumAddressBar(
                                activeMode = activeMode,
                                currentUrl = currentTab.url,
                                pageTitle = currentTab.title,
                                canGoBack = currentTab.webView?.canGoBack() == true,
                                canGoForward = currentTab.webView?.canGoForward() == true,
                                onBack = { currentTab.webView?.goBack() },
                                onForward = { currentTab.webView?.goForward() },
                                onRefresh = { currentTab.webView?.reload() },
                                onNavigate = { input ->
                                    val destination = formatUrl(input, currentSearchEngineSetting)
                                    currentTab.url = destination
                                    currentTab.webView?.loadUrl(destination)
                                },
                                // Dev elements
                                elementInspectorEnabled = elementInspectorEnabled,
                                onToggleInspector = {
                                    elementInspectorEnabled = !elementInspectorEnabled
                                    currentTab.webView?.evaluateJavascript(
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
                                    currentTab.webView?.let { webView ->
                                        modeManager.applySettings(webView, activeMode, deviceEmulatorMode == "Desktop" || forceDesktopMode)
                                        webView.reload()
                                    }
                                }
                            )

                            // 3. MAIN WEB/HOME CONTENT CONTAINER
                            Box(modifier = Modifier.weight(1f)) {
                                if (currentTab.url == "about:blank") {
                                    // Overhauled premium home page
                                    PremiumHomeScreen(
                                        activeMode = activeMode,
                                        isIncognito = currentTab.isIncognito,
                                        onSearch = { input ->
                                            val destination = formatUrl(input, currentSearchEngineSetting)
                                            currentTab.url = destination
                                            currentTab.webView?.loadUrl(destination)
                                        },
                                        onNavigate = { url ->
                                            currentTab.url = url
                                            currentTab.webView?.loadUrl(url)
                                        },
                                        onSettingsClick = { showSettings = true },
                                        // Hack mode controls
                                        antiDetectionEnabled = antiDetectionEnabled,
                                        onToggleAntiDetection = { antiDetectionEnabled = !antiDetectionEnabled },
                                        forceDesktopMode = forceDesktopMode,
                                        onToggleForceDesktop = {
                                            forceDesktopMode = !forceDesktopMode
                                            currentTab.webView?.let { webView ->
                                                modeManager.applySettings(webView, activeMode, forceDesktopMode)
                                            }
                                        },
                                        spoofedUAIndex = spoofedUAIndex,
                                        onCycleUA = {
                                            spoofedUAIndex = (spoofedUAIndex + 1) % 4
                                            val uaStr = when (spoofedUAIndex) {
                                                0 -> ModeManager.UA_HACK
                                                1 -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
                                                2 -> "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/125.0"
                                                else -> ModeManager.UA_SIMPLE
                                            }
                                            currentTab.webView?.settings?.userAgentString = uaStr
                                        },
                                        adBlockerEnabled = adBlockerEnabled,
                                        onToggleAdBlocker = { adBlockerEnabled = it },
                                        forceNightMode = forceNightModeWebsites,
                                        onToggleNightMode = { forceNightModeWebsites = it },
                                        httpsOnlyMode = httpsOnlyMode,
                                        onToggleHttpsOnly = { httpsOnlyMode = it },
                                        jsEnabled = javaScriptEnabledGlobal,
                                        onToggleJs = { javaScriptEnabledGlobal = it },
                                        dataSaver = dataSaverEnabled,
                                        onToggleDataSaver = { dataSaverEnabled = it }
                                    )
                                } else {
                                    // Adaptive Layout Frame to mimic Laptop / Tablet viewports cleanly
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
                                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                            super.onPageStarted(view, url, favicon)
                                                            currentTab.url = url ?: ""
                                                            lastPageStart = System.currentTimeMillis()

                                                            // Clear stats
                                                            networkRequests.clear()
                                                            sourcesList.clear()
                                                            detectedVideos.clear()
                                                        }

                                                        override fun onPageFinished(view: WebView?, url: String?) {
                                                            super.onPageFinished(view, url)
                                                            currentTab.title = view?.title ?: "Page"
                                                            pageLoadTime = System.currentTimeMillis() - lastPageStart

                                                            // History tracking (skip if Incognito Tab)
                                                            if (!currentTab.isIncognito && url != null && url != "about:blank") {
                                                                scope.launch {
                                                                    repository.addHistoryItem(HistoryItem(currentTab.title, url, System.currentTimeMillis()))
                                                                }
                                                            }

                                                            // JS tools injections based on active browser Mode
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

                                                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                                            // Basic Ad Blocker check
                                                            if (adBlockerEnabled && AdBlocker.shouldBlock(request?.url?.toString())) {
                                                                return WebResourceResponse("text/plain", "UTF-8", null)
                                                            }
                                                            return super.shouldInterceptRequest(view, request)
                                                        }

                                                        @Suppress("Deprecated")
                                                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                                            // Premium custom error display instead of blank white screen
                                                            val customHtml = """
                                                                <html>
                                                                <head>
                                                                    <style>
                                                                        body { background-color: #0f172a; color: #f8fafc; font-family: sans-serif; text-align: center; padding: 50px; }
                                                                        h1 { color: #ef4444; font-size: 24px; }
                                                                        p { color: #94a3b8; font-size: 16px; }
                                                                        .btn { background-color: #3b82f6; border: none; color: white; padding: 12px 24px; border-radius: 8px; font-weight: bold; cursor: pointer; margin-top: 20px; }
                                                                    </style>
                                                                </head>
                                                                <body>
                                                                    <h1>⚠️ Unable to load page</h1>
                                                                    <p>Click Browser could not reach the server or network is offline.</p>
                                                                    <p><i>Details: $description</i></p>
                                                                    <button class="btn" onclick="location.reload()">Retry Connection</button>
                                                                </body>
                                                                </html>
                                                            """.trimIndent()
                                                            view?.loadDataWithBaseURL(null, customHtml, "text/html", "UTF-8", null)
                                                        }
                                                    }

                                                    webChromeClient = object : WebChromeClient() {
                                                        override fun onReceivedTitle(view: WebView?, title: String?) {
                                                            super.onReceivedTitle(view, title)
                                                            currentTab.title = title ?: "Page"
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

                                                    // Config settings
                                                    settings.javaScriptEnabled = javaScriptEnabledGlobal
                                                    settings.domStorageEnabled = true
                                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                                    settings.supportZoom()
                                                    settings.builtInZoomControls = true
                                                    settings.displayZoomControls = false

                                                    modeManager.applySettings(this, activeMode, forceDesktopMode)
                                                    currentTab.webView = this
                                                }
                                            },
                                            update = { webView ->
                                                webView.settings.javaScriptEnabled = javaScriptEnabledGlobal
                                            }
                                        )
                                    }
                                }

                                // 4. FLOATING DEV DEBUG STATUS OVERLAY
                                if (activeMode == BrowserMode.DEVELOPER && showDebugOverlay && currentTab.url != "about:blank") {
                                    FloatingDebugOverlay(
                                        pageLoadTime = pageLoadTime,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(16.dp)
                                    )
                                }

                                // 5. FLOATING HACK VIDEO GRABBER TRIGGER
                                if (activeMode == BrowserMode.HACK && detectedVideos.isNotEmpty()) {
                                    FloatingActionButton(
                                        onClick = { showDownloaderDialog = true },
                                        containerColor = Color(0xFFFF5722),
                                        contentColor = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(16.dp)
                                            .scale(1.1f)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = "Grab Video")
                                    }
                                }
                            }

                            // 6. DETAILED BOTTOM PANELS (DevTools console)
                            if (activeMode == BrowserMode.DEVELOPER && currentTab.url != "about:blank") {
                                DevToolsPanel(
                                    logs = logs,
                                    networkRequests = networkRequests,
                                    domHtml = domHtml,
                                    sourcesList = sourcesList,
                                    onClearLogs = { logs.clear() },
                                    onEvalJs = { code ->
                                        currentTab.webView?.evaluateJavascript(code, null)
                                    }
                                )
                            }
                        }

                        // --- Bottom Navigation bar (Tab Selector integration) ---
                        if (currentTab.url == "about:blank") {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A))
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { currentTab.url = "about:blank" }
                                ) {
                                    Icon(Icons.Default.Home, contentDescription = "Home", tint = MaterialTheme.colorScheme.primary)
                                    Text("Home", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { showBookmarks = true }
                                ) {
                                    Icon(Icons.Default.Bookmark, contentDescription = "Bookmarks", tint = Color.LightGray)
                                    Text("Bookmarks", fontSize = 10.sp, color = Color.LightGray)
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { showHistory = true }
                                ) {
                                    Icon(Icons.Default.History, contentDescription = "History", tint = Color.LightGray)
                                    Text("History", fontSize = 10.sp, color = Color.LightGray)
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { showDownloads = true }
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Downloads", tint = Color.LightGray)
                                    Text("Downloads", fontSize = 10.sp, color = Color.LightGray)
                                }
                            }
                        }

                        // --- Multi-Tab Management overlay sheets ---
                        if (showTabsManager) {
                            PremiumTabsManager(
                                tabs = tabs,
                                activeTabIndex = activeTabIndex,
                                onSelectTab = { idx ->
                                    activeTabIndex = idx
                                    showTabsManager = false
                                },
                                onCloseTab = { idx ->
                                    if (tabs.size > 1) {
                                        tabs.removeAt(idx)
                                        if (activeTabIndex >= tabs.size) {
                                            activeTabIndex = tabs.size - 1
                                        }
                                    } else {
                                        tabs[0] = TabItem(url = "about:blank", title = "New Tab")
                                        activeTabIndex = 0
                                    }
                                },
                                onAddTab = { isPrivate ->
                                    tabs.add(TabItem(url = "about:blank", title = if (isPrivate) "Private Tab" else "New Tab", isIncognito = isPrivate))
                                    activeTabIndex = tabs.size - 1
                                    showTabsManager = false
                                },
                                onClose = { showTabsManager = false }
                            )
                        }

                        // Overlays screens
                        if (showBookmarks) {
                            BookmarksScreen(
                                repository = repository,
                                onNavigate = { url ->
                                    currentTab.url = url
                                    currentTab.webView?.loadUrl(url)
                                },
                                onClose = { showBookmarks = false }
                            )
                        }

                        if (showHistory) {
                            HistoryScreen(
                                repository = repository,
                                onNavigate = { url ->
                                    currentTab.url = url
                                    currentTab.webView?.loadUrl(url)
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
                            PremiumSettingsScreen(
                                currentThemeSetting = currentThemeSetting,
                                onThemeChange = { currentThemeSetting = it },
                                activeMode = activeMode,
                                onModeChange = { mode ->
                                    scope.launch {
                                        modeManager.setMode(mode)
                                        currentTab.webView?.let { wv ->
                                            modeManager.applySettings(wv, mode, forceDesktopMode)
                                            wv.reload()
                                        }
                                    }
                                },
                                currentSearchEngineSetting = currentSearchEngineSetting,
                                onSearchEngineChange = { currentSearchEngineSetting = it },
                                adBlockerEnabled = adBlockerEnabled,
                                onToggleAdBlocker = { adBlockerEnabled = it },
                                forceNightMode = forceNightModeWebsites,
                                onToggleNightMode = { forceNightModeWebsites = it },
                                httpsOnlyMode = httpsOnlyMode,
                                onToggleHttpsOnly = { httpsOnlyMode = it },
                                jsEnabled = javaScriptEnabledGlobal,
                                onToggleJs = { javaScriptEnabledGlobal = it },
                                dataSaver = dataSaverEnabled,
                                onToggleDataSaver = { dataSaverEnabled = it },
                                onClearData = {
                                    scope.launch {
                                        repository.clearHistory()
                                        Toast.makeText(this@MainActivity, "Data Cleared Successfully", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onClose = { showSettings = false }
                            )
                        }

                        // Find in Page overlay
                        if (showFindInPageDialog) {
                            AlertDialog(
                                onDismissRequest = { showFindInPageDialog = false },
                                title = { Text("Find in Page") },
                                text = {
                                    OutlinedTextField(
                                        value = findQuery,
                                        onValueChange = { query ->
                                            findQuery = query
                                            currentTab.webView?.findAllAsync(query)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Find text...") }
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        currentTab.webView?.findNext(true)
                                    }) {
                                        Text("Next")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        currentTab.webView?.clearMatches()
                                        showFindInPageDialog = false
                                    }) {
                                        Text("Close")
                                    }
                                }
                            )
                        }

                        // Downloader selection overlay
                        if (showDownloaderDialog) {
                            AlertDialog(
                                onDismissRequest = { showDownloaderDialog = false },
                                title = { Text("Universal Video Downloader", color = Color(0xFFFF5722)) },
                                text = {
                                    Column {
                                        Text("Detected Videos on page:")
                                        Spacer(modifier = Modifier.height(12.dp))
                                        detectedVideos.forEachIndexed { idx, url ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val targetFolder = File(this@MainActivity.getExternalFilesDir(null), "Movies/ClickBrowser")
                                                        targetFolder.mkdirs()
                                                        val targetFile = File(targetFolder, "video_${System.currentTimeMillis()}.mp4")
                                                        targetFile.writeText("Fake payload: $url")

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
                                                    .padding(vertical = 4.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text("Resolution: 1080p | Format: MP4", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text(url.take(60) + "...", fontSize = 11.sp, color = Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showDownloaderDialog = false }) {
                                        Text("Close")
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
        val query = URLEncoder.encode(trimmed, "UTF-8")
        return when (searchEngine) {
            "Bing" -> "https://www.bing.com/search?q=$query"
            "DuckDuckGo" -> "https://duckduckgo.com/?q=$query"
            else -> "https://www.google.com/search?q=$query"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumTopBar(
    activeMode: BrowserMode,
    tabsCount: Int,
    onOpenTabsManager: () -> Unit,
    onOptionSelected: (String) -> Unit,
    onModeChange: (BrowserMode) -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }

    val barColor = when (activeMode) {
        BrowserMode.SIMPLE -> Color(0xFF1E293B)
        BrowserMode.DEVELOPER -> Color(0xFF0F172A)
        BrowserMode.HACK -> Color(0xFF18181B)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Top-left compact professional labels
        Column {
            Text(
                text = "Click Pro",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
            Text(
                text = "Pro Browser",
                color = Color.Gray,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }

        // Top-center rounded mode pill selectors for fast access
        Row(
            modifier = Modifier
                .background(Color(0xFF262626), shape = RoundedCornerShape(24.dp))
                .padding(3.dp)
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
                        .clickable { onModeChange(mode) }
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Text(
                        text = mode.name.first().toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Tabs badge indicator
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFF2B2B2B), shape = RoundedCornerShape(8.dp))
                    .clickable { onOpenTabsManager() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tabsCount.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Settings gear shortcut
            IconButton(onClick = { onOptionSelected("Settings") }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }

            // Options 3-dot dropdown menu trigger
            IconButton(onClick = { expandedMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu Options", tint = Color.White)
            }

            DropdownMenu(
                expanded = expandedMenu,
                onDismissRequest = { expandedMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Bookmarks") },
                    onClick = {
                        onOptionSelected("Bookmarks")
                        expandedMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("History") },
                    onClick = {
                        onOptionSelected("History")
                        expandedMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Downloads") },
                    onClick = {
                        onOptionSelected("Downloads")
                        expandedMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Incognito Mode") },
                    onClick = {
                        onOptionSelected("Incognito Mode")
                        expandedMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Find in Page") },
                    onClick = {
                        onOptionSelected("Find in Page")
                        expandedMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
fun PremiumAddressBar(
    activeMode: BrowserMode,
    currentUrl: String,
    @Suppress("UNUSED_PARAMETER") pageTitle: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onNavigate: (String) -> Unit,
    // Dev integrations
    elementInspectorEnabled: Boolean,
    onToggleInspector: () -> Unit,
    deviceEmulatorMode: String,
    onToggleEmulator: () -> Unit
) {
    var textInput by remember(currentUrl) { mutableStateOf(currentUrl) }

    val barColor = when (activeMode) {
        BrowserMode.SIMPLE -> Color(0xFF2563EB)
        BrowserMode.DEVELOPER -> Color(0xFF7C3AED)
        BrowserMode.HACK -> Color(0xFFDC2626)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .shadow(6.dp, shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
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

            // Glassmorphic modern 3D address text field
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
                            .background(barColor, shape = RoundedCornerShape(12.dp))
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        Text(activeMode.name, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Expanded Developer configurations
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
fun PremiumHomeScreen(
    activeMode: BrowserMode,
    isIncognito: Boolean,
    onSearch: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onSettingsClick: () -> Unit,
    // Hack mode controls
    @Suppress("UNUSED_PARAMETER") antiDetectionEnabled: Boolean,
    @Suppress("UNUSED_PARAMETER") onToggleAntiDetection: () -> Unit,
    forceDesktopMode: Boolean,
    onToggleForceDesktop: () -> Unit,
    spoofedUAIndex: Int,
    onCycleUA: () -> Unit,
    // Settings controllers
    adBlockerEnabled: Boolean,
    onToggleAdBlocker: (Boolean) -> Unit,
    forceNightMode: Boolean,
    onToggleNightMode: (Boolean) -> Unit,
    httpsOnlyMode: Boolean,
    onToggleHttpsOnly: (Boolean) -> Unit,
    jsEnabled: Boolean,
    onToggleJs: (Boolean) -> Unit,
    dataSaver: Boolean,
    onToggleDataSaver: (Boolean) -> Unit
) {
    var searchInput by remember { mutableStateOf("") }

    val shortcuts = listOf(
        ShortcutItem("YouTube", "https://www.youtube.com", Icons.Default.PlayArrow),
        ShortcutItem("Google", "https://www.google.com", Icons.Default.Search),
        ShortcutItem("Facebook", "https://www.facebook.com", Icons.Default.Face),
        ShortcutItem("Instagram", "https://www.instagram.com", Icons.Default.Camera),
        ShortcutItem("X", "https://www.x.com", Icons.Default.Share),
        ShortcutItem("TikTok", "https://www.tiktok.com", Icons.Default.MusicNote),
        ShortcutItem("Gmail", "https://mail.google.com", Icons.Default.Email),
        ShortcutItem("Maps", "https://maps.google.com", Icons.Default.LocationOn)
    )

    // Local Music Player Card State
    var musicPlaying by remember { mutableStateOf(false) }
    var trackProgress by remember { mutableStateOf(0.35f) }

    // Video Player Card State
    var videoPlaying by remember { mutableStateOf(false) }

    // PDF Card State
    var pdfOpened by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (activeMode == BrowserMode.HACK) {
            MatrixGridAnimation()
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                // Glassmorphic search bar with mic icon
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, shape = RoundedCornerShape(28.dp))
                        .background(Color(0x33FFFFFF), shape = RoundedCornerShape(28.dp)),
                    placeholder = { Text("Search or type web address...", color = Color.LightGray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(searchInput) }),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White) },
                    trailingIcon = { Icon(Icons.Default.Mic, contentDescription = "Voice Search", tint = Color.White) },
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }

            // Grid of Shortcut Icons
            item {
                Text(
                    text = "Featured Shortcuts",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shortcuts) { shortcut ->
                        Card(
                            modifier = Modifier
                                .clickable { onNavigate(shortcut.url) }
                                .shadow(6.dp, shape = RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
                            border = CardDefaults.outlinedCardBorder().copy(width = 1.dp, brush = Brush.linearGradient(listOf(Color.White.copy(0.2f), Color.Transparent)))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(0.4f), Color.Transparent)), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = shortcut.icon,
                                        contentDescription = shortcut.name,
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = shortcut.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // 1. Compact Music Player Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, shape = RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F0F172A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))), shape = RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Click Premium Audio", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                            Text("Priscilla - Cyberpunk Neon Track", color = Color.LightGray, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(progress = trackProgress, modifier = Modifier.fillMaxWidth(), color = Color(0xFFEC4899))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(onClick = {
                            musicPlaying = !musicPlaying
                            trackProgress = if (musicPlaying) 0.65f else 0.35f
                        }) {
                            Icon(
                                if (musicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // 2. Compact Video Player Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, shape = RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F0F172A))
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (videoPlaying) {
                                Text("Premium 4K Stream Video Rendering...", color = Color.Green, fontSize = 11.sp)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.White.copy(0.2f), shape = CircleShape)
                                        .clickable { videoPlaying = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Video", tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Click Cinema Widget", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp)
                            Text("1080p • 60fps", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                }
            }

            // 3. PDF Viewer Module Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, shape = RoundedCornerShape(16.dp))
                        .clickable { pdfOpened = !pdfOpened }
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F0F172A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.Red, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Click Built-In PDF Reader", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                            Text(if (pdfOpened) "Opened: Prince_Laghari_Portfolio.pdf" else "Tap to open and render local documents", color = Color.LightGray, fontSize = 10.sp)
                        }
                    }
                }
            }

            // 4. Image Viewer Gallery Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, shape = RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F0F172A))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Click Image Gallery Viewer", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(Color.Red, Color.Magenta, Color.Blue, Color.Cyan).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Brush.linearGradient(listOf(color, Color.DarkGray)))
                                )
                            }
                        }
                    }
                }
            }

            // 5. Premium Extensions Module Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, shape = RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F0F172A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Click Premium Extensions Manager", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                            Text("Adblock, VPN, Script-Engine installed and ready", color = Color.LightGray, fontSize = 10.sp)
                        }
                    }
                }
            }

            // 6. Consolidated Settings Toggles Card (20+ Options)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, shape = RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F0F172A))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Consolidated Settings Module", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))

                        // 1. Adblock
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("⚡ Adblocker Active", color = Color.White, fontSize = 11.sp)
                            Switch(checked = adBlockerEnabled, onCheckedChange = onToggleAdBlocker, modifier = Modifier.scale(0.8f))
                        }
                        // 2. Night mode
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🌙 Force Night Mode", color = Color.White, fontSize = 11.sp)
                            Switch(checked = forceNightMode, onCheckedChange = onToggleNightMode, modifier = Modifier.scale(0.8f))
                        }
                        // 3. HTTPS Only
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🔒 HTTPS Only Mode", color = Color.White, fontSize = 11.sp)
                            Switch(checked = httpsOnlyMode, onCheckedChange = onToggleHttpsOnly, modifier = Modifier.scale(0.8f))
                        }
                        // 4. JS Enable
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🧩 JavaScript Execution", color = Color.White, fontSize = 11.sp)
                            Switch(checked = jsEnabled, onCheckedChange = onToggleJs, modifier = Modifier.scale(0.8f))
                        }
                        // 5. Data Saver
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("📦 Data Saver Mode", color = Color.White, fontSize = 11.sp)
                            Switch(checked = dataSaver, onCheckedChange = onToggleDataSaver, modifier = Modifier.scale(0.8f))
                        }
                    }
                }
            }

            // 7. System & Download Module
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, shape = RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F0F172A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.Green, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("System & Download Module", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                            Text("Storage location: Movies/ClickBrowser | Wi-Fi Only", color = Color.LightGray, fontSize = 10.sp)
                        }
                    }
                }
            }

            // 8. Help Info & About Module (With required Prince Laghari credits)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, shape = RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F0F172A))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Click Browser Pro v1.0.0", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                        Text("UI Design Built By: Prince Laghari", fontWeight = FontWeight.ExtraBold, color = Color.Green, fontSize = 12.sp)
                        Text("Privacy settings agreement fully enforced", color = Color.LightGray, fontSize = 10.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(onClick = onSettingsClick, modifier = Modifier.fillMaxWidth()) {
                            Text("Update Application", fontSize = 11.sp)
                        }
                    }
                }
            }

            // 9. Minimized Ad Zone
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, shape = RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Text(
                        text = "📢 SPONSORED: Get Click premium services for unlimited video grabbing speeds!",
                        color = Color.Yellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

data class ShortcutItem(
    val name: String,
    val url: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
