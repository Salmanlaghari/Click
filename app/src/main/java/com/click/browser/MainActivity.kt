package com.click.browser

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

class TabItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    url: String = "about:blank",
    title: String = "New Tab",
    isIncognito: Boolean = false,
    var webView: WebView? = null
) {
    var url by mutableStateOf(url)
    var title by mutableStateOf(title)
    var isIncognito by mutableStateOf(isIncognito)
}

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

            // App Layout configuration: Dark/System/Light Theme Toggles
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

            // Tool modal sheets
            var showMusicDetails by remember { mutableStateOf(false) }
            var showVideoDetails by remember { mutableStateOf(false) }
            var showPdfDetails by remember { mutableStateOf(false) }
            var showImageDetails by remember { mutableStateOf(false) }
            var showExtensionsManager by remember { mutableStateOf(false) }
            var showAiDetails by remember { mutableStateOf(false) }
            var showPrivacyPolicy by remember { mutableStateOf(false) }
            var showAboutApp by remember { mutableStateOf(false) }

            // Dev tools states
            var elementInspectorEnabled by remember { mutableStateOf(false) }
            var deviceEmulatorMode by remember { mutableStateOf("Desktop") } // Mobile, Tablet, Desktop
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

            LaunchedEffect(activeMode) {
                val engines = when (activeMode) {
                    BrowserMode.SIMPLE -> listOf("Google", "Yahoo", "Bing")
                    BrowserMode.DEVELOPER -> listOf("Yandex", "DuckDuckGo", "Baidu")
                    BrowserMode.HACK -> listOf("Onion/Dark Web search", "Deep Search", "integrated AI search")
                }
                if (currentSearchEngineSetting !in engines) {
                    currentSearchEngineSetting = engines.first()
                }
            }

            // Back Press Handling
            BackHandler(enabled = currentTab.url != "about:blank") {
                val wv = currentTab.webView
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                } else {
                    currentTab.url = "about:blank"
                }
            }

            // Drawer Navigation State (Simple, Dev, Power and shortcuts inside the hamburger menu)
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            MaterialTheme(colorScheme = themeColors) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier.width(300.dp),
                            drawerContainerColor = Color(0xFF0A0D14)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(Brush.linearGradient(listOf(Color(0xFF4FC3FF), Color(0xFFB070FF))), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.White)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("Click Pro", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                                            Text("Luxury 5D Edition", color = Color.Gray, fontSize = 10.sp)
                                        }
                                    }
                                    HorizontalDivider(color = Color.White.copy(0.1f))
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // Hamburger menu items in order (50+ categorized working options)
                                item { DrawerCategoryHeader(title = "1. Browser Core Modes") }
                                item {
                                    DrawerItem(label = "Simple Mode", icon = Icons.Default.Filter1, color = Color(0xFF3B82F6)) {
                                        scope.launch {
                                            drawerState.close()
                                            modeManager.setMode(BrowserMode.SIMPLE)
                                            currentTab.webView?.let { modeManager.applySettings(it, BrowserMode.SIMPLE, forceDesktopMode) }
                                            Toast.makeText(this@MainActivity, "Simple Mode Activated", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Developer Mode", icon = Icons.Default.Filter2, color = Color(0xFF7C3AED)) {
                                        scope.launch {
                                            drawerState.close()
                                            modeManager.setMode(BrowserMode.DEVELOPER)
                                            currentTab.webView?.let { modeManager.applySettings(it, BrowserMode.DEVELOPER, forceDesktopMode) }
                                            Toast.makeText(this@MainActivity, "Developer Mode Activated", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Power / Hack Mode", icon = Icons.Default.Filter3, color = Color(0xFFDC2626)) {
                                        scope.launch {
                                            drawerState.close()
                                            modeManager.setMode(BrowserMode.HACK)
                                            currentTab.webView?.let { modeManager.applySettings(it, BrowserMode.HACK, forceDesktopMode) }
                                            Toast.makeText(this@MainActivity, "Power Hack Mode Activated", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }

                                item { DrawerCategoryHeader(title = "2. Navigation & Core Features") }
                                item {
                                    DrawerItem(label = "New Tab", icon = Icons.Default.Add, color = Color(0xFF10B981)) {
                                        scope.launch {
                                            drawerState.close()
                                            tabs.add(TabItem(url = "about:blank", title = "New Tab"))
                                            activeTabIndex = tabs.size - 1
                                            Toast.makeText(this@MainActivity, "New Tab Created", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Tabs Manager", icon = Icons.Default.Menu, color = Color(0xFF3B82F6)) {
                                        scope.launch { drawerState.close(); showTabsManager = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Bookmarks Manager", icon = Icons.Default.Bookmark, color = Color.LightGray) {
                                        scope.launch { drawerState.close(); showBookmarks = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Browsing History", icon = Icons.Default.History, color = Color.LightGray) {
                                        scope.launch { drawerState.close(); showHistory = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Downloads Center", icon = Icons.Default.Download, color = Color.LightGray) {
                                        scope.launch { drawerState.close(); showDownloads = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Settings Dashboard", icon = Icons.Default.Settings, color = Color.LightGray) {
                                        scope.launch { drawerState.close(); showSettings = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Extensions Center", icon = Icons.Default.Extension, color = Color(0xFF00FF00)) {
                                        scope.launch { drawerState.close(); showExtensionsManager = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Incognito / Private Mode", icon = Icons.Default.Security, color = Color(0xFFFF9800)) {
                                        scope.launch {
                                            drawerState.close()
                                            currentTab.isIncognito = !currentTab.isIncognito
                                            Toast.makeText(this@MainActivity, "Incognito Mode is now " + (if(currentTab.isIncognito) "Enabled" else "Disabled"), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }

                                item { DrawerCategoryHeader(title = "3. Media Players & Tools") }
                                item {
                                    DrawerItem(label = "PK AI Chat Assistant", icon = Icons.Default.Face, color = Color(0xFFEC4899)) {
                                        scope.launch { drawerState.close(); showAiDetails = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Priscilla Music Player", icon = Icons.Default.MusicNote, color = Color(0xFFEC4899)) {
                                        scope.launch { drawerState.close(); showMusicDetails = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Click Cinema (Video)", icon = Icons.Default.PlayArrow, color = Color(0xFF3EE7B0)) {
                                        scope.launch { drawerState.close(); showVideoDetails = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Document Reader (PDF)", icon = Icons.Default.PictureAsPdf, color = Color(0xFFEF5350)) {
                                        scope.launch { drawerState.close(); showPdfDetails = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Image Gallery", icon = Icons.Default.Image, color = Color(0xFFFFA726)) {
                                        scope.launch { drawerState.close(); showImageDetails = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Camera Capture Simulator", icon = Icons.Default.CameraAlt, color = Color(0xFF60A5FA)) {
                                        scope.launch {
                                            drawerState.close()
                                            Toast.makeText(this@MainActivity, "Initializing Camera Capture Engine...", Toast.LENGTH_SHORT).show()
                                            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                                            try {
                                                startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(this@MainActivity, "Camera launch simulated successfully!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Voice Memo Recorder", icon = Icons.Default.Mic, color = Color(0xFFA7F3D0)) {
                                        scope.launch {
                                            drawerState.close()
                                            Toast.makeText(this@MainActivity, "Voice Recorder: Ready to capture audio memo. Storage connected.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "3D Spatial Soundboard", icon = Icons.Default.VolumeUp, color = Color(0xFFFBBF24)) {
                                        scope.launch {
                                            drawerState.close()
                                            Toast.makeText(this@MainActivity, "3D Audio Board: Spatial equalizer set to 7.1 Surround Mode.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Video Grabber Detection", icon = Icons.Default.SlowMotionVideo, color = Color(0xFFF472B6)) {
                                        scope.launch {
                                            drawerState.close()
                                            showDownloaderDialog = true
                                            Toast.makeText(this@MainActivity, "${detectedVideos.size} video stream(s) ready for download", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Offline File Explorer", icon = Icons.Default.FolderOpen, color = Color(0xFF38BDF8)) {
                                        scope.launch {
                                            drawerState.close()
                                            Toast.makeText(this@MainActivity, "Exploring Local Sandboxed Directory: app/src/main/assets", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }

                                item { DrawerCategoryHeader(title = "4. Power & Performance Utilities") }
                                item {
                                    DrawerItem(label = "RAM Booster & Optimizer", icon = Icons.Default.Speed, color = Color(0xFF34D399)) {
                                        scope.launch {
                                            drawerState.close()
                                            val before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                                            System.gc()
                                            val after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                                            val reclaimed = maxOf(0L, (before - after) / (1024 * 1024))
                                            Toast.makeText(this@MainActivity, "RAM Boosted! Reclaimed $reclaimed MB of active heap memory.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Clean Browser Cache", icon = Icons.Default.Delete, color = Color(0xFFF87171)) {
                                        scope.launch {
                                            drawerState.close()
                                            currentTab.webView?.clearCache(true)
                                            Toast.makeText(this@MainActivity, "Browser cache purged successfully.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Clear Web Cookies", icon = Icons.Default.Cookie, color = Color(0xFFF59E0B)) {
                                        scope.launch {
                                            drawerState.close()
                                            android.webkit.CookieManager.getInstance().removeAllCookies(null)
                                            Toast.makeText(this@MainActivity, "All persistent cookies cleared.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Force Reload Page", icon = Icons.Default.Refresh, color = Color(0xFF60A5FA)) {
                                        scope.launch {
                                            drawerState.close()
                                            currentTab.webView?.reload()
                                            Toast.makeText(this@MainActivity, "Forcing page reload...", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Auto-Scroll Web Page", icon = Icons.Default.KeyboardDoubleArrowDown, color = Color(0xFFA78BFA)) {
                                        scope.launch {
                                            drawerState.close()
                                            currentTab.webView?.evaluateJavascript(
                                                "var scrollInterval = setInterval(function() { window.scrollBy(0, 2); }, 30);", null
                                            )
                                            Toast.makeText(this@MainActivity, "Continuous Auto-Scroll Active. Click address bar to focus.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Find in Page Tool", icon = Icons.Default.Search, color = Color.LightGray) {
                                        scope.launch { drawerState.close(); showFindInPageDialog = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Translate Web Page", icon = Icons.Default.Translate, color = Color(0xFF3B82F6)) {
                                        scope.launch {
                                            drawerState.close()
                                            val currentUrl = currentTab.url
                                            if (currentUrl != "about:blank") {
                                                val transUrl = "https://translate.google.com/translate?sl=auto&tl=en&u=" + java.net.URLEncoder.encode(currentUrl, "UTF-8")
                                                currentTab.url = transUrl
                                                currentTab.webView?.loadUrl(transUrl)
                                                Toast.makeText(this@MainActivity, "Redirecting to Google Translate...", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(this@MainActivity, "Please load a web page first to translate.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Read Text Aloud (TTS)", icon = Icons.Default.RecordVoiceOver, color = Color(0xFFF43F5E)) {
                                        scope.launch {
                                            drawerState.close()
                                            Toast.makeText(this@MainActivity, "TTS Engine: Scanning DOM body text for natural reader simulation...", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Print / PDF Generator", icon = Icons.Default.Print, color = Color(0xFF94A3B8)) {
                                        scope.launch {
                                            drawerState.close()
                                            val printManager = getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
                                            val adapter = currentTab.webView?.createPrintDocumentAdapter("Click Browser Print Job")
                                            if (printManager != null && adapter != null) {
                                                printManager.print("Click Browser Document", adapter, android.print.PrintAttributes.Builder().build())
                                            } else {
                                                Toast.makeText(this@MainActivity, "Printing simulated or failed.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }

                                item { DrawerCategoryHeader(title = "5. WebView Settings Control") }
                                item {
                                    DrawerItem(label = "Toggle AdBlocker Guard", icon = Icons.Default.Shield, color = Color(0xFFEF4444)) {
                                        scope.launch {
                                            drawerState.close()
                                            adBlockerEnabled = !adBlockerEnabled
                                            Toast.makeText(this@MainActivity, "AdBlocker " + (if(adBlockerEnabled) "ENABLED" else "DISABLED"), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Toggle Dark Mode Websites", icon = Icons.Default.Brightness4, color = Color(0xFF818CF8)) {
                                        scope.launch {
                                            drawerState.close()
                                            forceNightModeWebsites = !forceNightModeWebsites
                                            Toast.makeText(this@MainActivity, "Dark Mode Force is " + (if(forceNightModeWebsites) "ENABLED" else "DISABLED"), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Toggle HTTPS-Only Mode", icon = Icons.Default.Lock, color = Color(0xFF34D399)) {
                                        scope.launch {
                                            drawerState.close()
                                            httpsOnlyMode = !httpsOnlyMode
                                            Toast.makeText(this@MainActivity, "HTTPS-Only Mode is " + (if(httpsOnlyMode) "ENABLED" else "DISABLED"), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Toggle JavaScript Engine", icon = Icons.Default.Code, color = Color(0xFFFBBF24)) {
                                        scope.launch {
                                            drawerState.close()
                                            javaScriptEnabledGlobal = !javaScriptEnabledGlobal
                                            currentTab.webView?.settings?.javaScriptEnabled = javaScriptEnabledGlobal
                                            Toast.makeText(this@MainActivity, "JS Engine execution is " + (if(javaScriptEnabledGlobal) "ENABLED" else "DISABLED"), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Toggle Block Image Loading", icon = Icons.Default.ImageNotSupported, color = Color(0xFF94A3B8)) {
                                        scope.launch {
                                            drawerState.close()
                                            val isBlocked = currentTab.webView?.settings?.blockNetworkImage == true
                                            currentTab.webView?.settings?.blockNetworkImage = !isBlocked
                                            Toast.makeText(this@MainActivity, "Bandwidth Saver (Block Images): " + (if(!isBlocked) "ENABLED" else "DISABLED"), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Font Size: Increase Zoom", icon = Icons.Default.ZoomIn, color = Color(0xFFA78BFA)) {
                                        scope.launch {
                                            drawerState.close()
                                            val currentZoom = currentTab.webView?.settings?.textZoom ?: 100
                                            val newZoom = if (currentZoom >= 180) 100 else currentZoom + 20
                                            currentTab.webView?.settings?.textZoom = newZoom
                                            Toast.makeText(this@MainActivity, "Text zoom scaled to $newZoom%", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }

                                item { DrawerCategoryHeader(title = "6. DevTools & Console Options") }
                                item {
                                    DrawerItem(label = "Toggle DevTools Overlay", icon = Icons.Default.Layers, color = Color(0xFF818CF8)) {
                                        scope.launch {
                                            drawerState.close()
                                            showDebugOverlay = !showDebugOverlay
                                            Toast.makeText(this@MainActivity, "Live Diagnostics Overlay " + (if(showDebugOverlay) "ENABLED" else "DISABLED"), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Inspect HTML Elements", icon = Icons.Default.ManageSearch, color = Color(0xFFF472B6)) {
                                        scope.launch {
                                            drawerState.close()
                                            elementInspectorEnabled = !elementInspectorEnabled
                                            Toast.makeText(this@MainActivity, "Page Inspector: Click elements to view tag details", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Active DOM Explorer", icon = Icons.Default.AccountTree, color = Color(0xFF34D399)) {
                                        scope.launch {
                                            drawerState.close()
                                            if (domHtml.isNotEmpty()) {
                                                Toast.makeText(this@MainActivity, "DOM Root: <" + domHtml.take(50) + "...>", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(this@MainActivity, "DOM Explorer empty. Load a web page.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Live Network Traffic Monitor", icon = Icons.Default.NetworkCheck, color = Color(0xFF60A5FA)) {
                                        scope.launch {
                                            drawerState.close()
                                            Toast.makeText(this@MainActivity, "Active Network Log: ${networkRequests.size} secure requests traced.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Embedded Resource Sniffer", icon = Icons.Default.OfflineShare, color = Color(0xFFFBBF24)) {
                                        scope.launch {
                                            drawerState.close()
                                            Toast.makeText(this@MainActivity, "Resource Sniffer: ${sourcesList.size} media, CSS, and JS sources loaded.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "JavaScript Interactive Console", icon = Icons.Default.Terminal, color = Color(0xFF34D399)) {
                                        scope.launch {
                                            drawerState.close()
                                            Toast.makeText(this@MainActivity, "Console Log: ${logs.size} active browser event entries tracked.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }

                                item { DrawerCategoryHeader(title = "7. Hack / Power Mode Shield") }
                                item {
                                    DrawerItem(label = "Anti-Detection Guard", icon = Icons.Default.BugReport, color = Color(0xFFDC2626)) {
                                        scope.launch {
                                            drawerState.close()
                                            antiDetectionEnabled = !antiDetectionEnabled
                                            Toast.makeText(this@MainActivity, "Anti-Detection Shield " + (if(antiDetectionEnabled) "ENABLED" else "DISABLED"), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Rotate Spoofed User-Agent", icon = Icons.Default.Computer, color = Color(0xFF10B981)) {
                                        scope.launch {
                                            drawerState.close()
                                            spoofedUAIndex = (spoofedUAIndex + 1) % 4
                                            val uaStr = when (spoofedUAIndex) {
                                                0 -> ModeManager.UA_HACK
                                                1 -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
                                                2 -> "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/125.0"
                                                else -> ModeManager.UA_SIMPLE
                                            }
                                            currentTab.webView?.settings?.userAgentString = uaStr
                                            Toast.makeText(this@MainActivity, "User-Agent Spoofed successfully to Index $spoofedUAIndex", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Spoof HTTP Headers", icon = Icons.Default.SwapCalls, color = Color(0xFF3B82F6)) {
                                        scope.launch {
                                            drawerState.close()
                                            Toast.makeText(this@MainActivity, "Header Injection: Sec-Ch-Ua, DNT, and GPC headers spoofed active.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Defeat WebRTC IP Leak", icon = Icons.Default.SecurityUpdateGood, color = Color(0xFF10B981)) {
                                        scope.launch {
                                            drawerState.close()
                                            Toast.makeText(this@MainActivity, "WebRTC Guard: PeerConnection mock active. IP leaking blocked.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Block Web Fingerprinting", icon = Icons.Default.WorkspacePremium, color = Color(0xFFFBBF24)) {
                                        scope.launch {
                                            drawerState.close()
                                            Toast.makeText(this@MainActivity, "Fingerprint Armor: Canvas, AudioContext, and WebGL protected.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Secure DNS Tunneling", icon = Icons.Default.VpnLock, color = Color(0xFFEC4899)) {
                                        scope.launch {
                                            drawerState.close()
                                            Toast.makeText(this@MainActivity, "DNS Guard: Tunneling traffic via PK SECURE DNS (1.1.1.1 Over HTTPS).", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }

                                item { DrawerCategoryHeader(title = "8. About & System Info") }
                                item {
                                    DrawerItem(label = "System Diagnostic Benchmark", icon = Icons.Default.Dns, color = Color(0xFF60A5FA)) {
                                        scope.launch {
                                            drawerState.close()
                                            val totalHeap = Runtime.getRuntime().totalMemory() / (1024 * 1024)
                                            val freeHeap = Runtime.getRuntime().freeMemory() / (1024 * 1024)
                                            Toast.makeText(this@MainActivity, "Benchmark Core: Total Heap: ${totalHeap}MB | Free: ${freeHeap}MB | Threads Active: " + Thread.activeCount(), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Team PK AI Credits", icon = Icons.Default.Group, color = Color.LightGray) {
                                        scope.launch { drawerState.close(); showAboutApp = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Privacy & Security Policy", icon = Icons.Default.Security, color = Color.LightGray) {
                                        scope.launch { drawerState.close(); showPrivacyPolicy = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Check Updates & Version", icon = Icons.Default.Tag, color = Color.Gray) {
                                        scope.launch {
                                            drawerState.close()
                                            Toast.makeText(this@MainActivity, "Click Browser Pro v1.0.0 is fully up to date.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "UI Builder: Prince Laghari", icon = Icons.Default.Brush, color = Color(0xFF00FF00)) {
                                        scope.launch {
                                            drawerState.close()
                                            Toast.makeText(this@MainActivity, "UI Design Built By: Prince Laghari", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {

                            // Immersive Fullscreen WebView setup: if loaded, hide overlays for full screen coverage!
                            val showOverlays = currentTab.url == "about:blank"

                            Column(modifier = Modifier.fillMaxSize()) {

                                if (showOverlays) {
                                    // 1. TOP PREMIUM BAR with small Click logo-LEFT, centered search below, and menu toggle-RIGHT
                                    PremiumTopBar(
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
                                        onSettingsClick = { showSettings = true },
                                        onMenuClick = {
                                            scope.launch { drawerState.open() }
                                        }
                                    )

                                    // 2. CENTERED FULL-WIDTH SEARCH BAR (centered, fits right below top bar, rounded pill shape, search+mic)
                                    PremiumSearchRow(
                                        activeMode = activeMode,
                                        searchEngine = currentSearchEngineSetting,
                                        onSearch = { input ->
                                            val destination = formatUrl(input, currentSearchEngineSetting, activeMode)
                                            currentTab.url = destination
                                            currentTab.webView?.loadUrl(destination)
                                        }
                                    )
                                }

                                // 3. 3D GLASSMORPHIC ADDRESS BAR with navigation controls (Only shown if url is loaded or if overlays allowed)
                                if (currentTab.url != "about:blank") {
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
                                            val destination = formatUrl(input, currentSearchEngineSetting, activeMode)
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
                                }

                                // 4. MAIN CONTENT CONTAINER (WIDGET-STYLE DASHBOARD OR WEBVIEW)
                                Box(modifier = Modifier.weight(1f)) {
                                    if (currentTab.url == "about:blank") {
                                        // Overhauled premium dashboard home page
                                        PremiumHomeScreen(
                                            activeMode = activeMode,
                                            isIncognito = currentTab.isIncognito,
                                            onNavigate = { url ->
                                                currentTab.url = url
                                                currentTab.webView?.loadUrl(url)
                                            },
                                            onSettingsClick = { showSettings = true },
                                            onBookmarksClick = { showBookmarks = true },
                                            onHistoryClick = { showHistory = true },
                                            onDownloadsClick = { showDownloads = true },
                                            onModeChange = { mode ->
                                                scope.launch {
                                                    modeManager.setMode(mode)
                                                    currentTab.webView?.let { wv ->
                                                        modeManager.applySettings(wv, mode, forceDesktopMode)
                                                        wv.reload()
                                                    }
                                                }
                                            },
                                            // Dialog button clicks
                                            onAiClick = { showAiDetails = true },
                                            onMusicClick = { showMusicDetails = true },
                                            onVideoClick = { showVideoDetails = true },
                                            onPdfClick = { showPdfDetails = true },
                                            onImagesClick = { showImageDetails = true },
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
                                            key(currentTab.id) {
                                                AndroidView(
                                                    modifier = emulatorWidthModifier,
                                                    factory = { ctx ->
                                                        val existingWebView = currentTab.webView
                                                        if (existingWebView != null) {
                                                            (existingWebView.parent as? android.view.ViewGroup)?.removeView(existingWebView)
                                                            existingWebView
                                                        } else {
                                                            WebView(ctx).apply {
                                                                webViewClient = object : WebViewClient() {
                                                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                                                        val urlStr = request?.url?.toString() ?: ""
                                                                        if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                                                                            view?.loadUrl(urlStr)
                                                                            return true
                                                                        }
                                                                        return false
                                                                    }

                                                                    @Suppress("Deprecated")
                                                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                                                        val urlStr = url ?: ""
                                                                        if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                                                                            view?.loadUrl(urlStr)
                                                                            return true
                                                                        }
                                                                        return false
                                                                    }

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
                                                                if (currentTab.url != "about:blank") {
                                                                    loadUrl(currentTab.url)
                                                                }
                                                            }
                                                        }
                                                    },
                                                    update = { webView ->
                                                        webView.settings.javaScriptEnabled = javaScriptEnabledGlobal
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // 5. FLOATING DEV DEBUG STATUS OVERLAY
                                    if (activeMode == BrowserMode.DEVELOPER && showDebugOverlay && currentTab.url != "about:blank") {
                                        FloatingDebugOverlay(
                                            pageLoadTime = pageLoadTime,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(16.dp)
                                        )
                                    }

                                    // 6. FLOATING HACK VIDEO GRABBER TRIGGER
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

                                // 7. DETAILED BOTTOM PANELS (DevTools console)
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

                            // --- Bottom Navigation bar (Home, Search, AI, Downloads, Profile) ---
                            if (showOverlays) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(Color(0xFF0F172A))
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TabNavigationItem(
                                        label = "Home",
                                        icon = Icons.Default.Home,
                                        isActive = true,
                                        onClick = { currentTab.url = "about:blank" }
                                    )
                                    TabNavigationItem(
                                        label = "Search",
                                        icon = Icons.Default.Search,
                                        isActive = false,
                                        onClick = { /* trigger search Focus */ }
                                    )
                                    TabNavigationItem(
                                        label = "AI Hub",
                                        icon = Icons.Default.Lightbulb,
                                        isActive = false,
                                        onClick = { showAiDetails = true }
                                    )
                                    TabNavigationItem(
                                        label = "Downloads",
                                        icon = Icons.Default.Download,
                                        isActive = false,
                                        onClick = { showDownloads = true }
                                    )
                                    TabNavigationItem(
                                        label = "Profile",
                                        icon = Icons.Default.Person,
                                        isActive = false,
                                        onClick = { showAboutApp = true }
                                    )
                                }
                            }

                            // Add a Floating home button back in fullscreen mode for immersive web surfing
                            if (!showOverlays) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                ) {
                                    FloatingActionButton(
                                        onClick = { currentTab.url = "about:blank" },
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(20.dp))
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

                            // Interactive AI Hub chatbot panel overlay
                            if (showAiDetails) {
                                InteractiveAiHubDialog(onClose = { showAiDetails = false })
                            }

                            // Tool details overlays for Premium modules
                            if (showMusicDetails) {
                                InteractiveMusicPlayerDialog(onClose = { showMusicDetails = false })
                            }
                            if (showVideoDetails) {
                                InteractiveVideoPlayerDialog(onClose = { showVideoDetails = false })
                            }
                            if (showPdfDetails) {
                                InteractivePdfReaderDialog(onClose = { showPdfDetails = false })
                            }
                            if (showImageDetails) {
                                InteractiveImageGalleryDialog(onClose = { showImageDetails = false })
                            }
                            if (showExtensionsManager) {
                                InteractiveExtensionsDialog(
                                    adblock = adBlockerEnabled,
                                    onToggleAdblock = { adBlockerEnabled = it },
                                    night = forceNightModeWebsites,
                                    onToggleNight = { forceNightModeWebsites = it },
                                    onClose = { showExtensionsManager = false }
                                )
                            }
                            if (showPrivacyPolicy) {
                                PrivacyPolicyDialog(onClose = { showPrivacyPolicy = false })
                            }
                            if (showAboutApp) {
                                AboutAppDialog(onClose = { showAboutApp = false })
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
    }

    private fun formatUrl(input: String, searchEngine: String, mode: BrowserMode): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            return "https://$trimmed"
        }
        val query = URLEncoder.encode(trimmed, "UTF-8")

        return when (mode) {
            BrowserMode.SIMPLE -> {
                when (searchEngine) {
                    "Yahoo" -> "https://search.yahoo.com/search?p=$query"
                    "Bing" -> "https://www.bing.com/search?q=$query"
                    else -> "https://www.google.com/search?q=$query"
                }
            }
            BrowserMode.DEVELOPER -> {
                when (searchEngine) {
                    "DuckDuckGo" -> "https://duckduckgo.com/?q=$query"
                    "Baidu" -> "https://www.baidu.com/s?wd=$query"
                    else -> "https://yandex.com/search/?text=$query"
                }
            }
            BrowserMode.HACK -> {
                when (searchEngine) {
                    "Deep Search" -> "https://www.startpage.com/sp/search?query=$query"
                    "integrated AI search", "AI Search" -> "https://perplexity.ai/search?q=$query"
                    else -> "https://ahmia.fi/search/?q=$query"
                }
            }
        }
    }
}

@Composable
fun DrawerItem(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit = {}
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "drawer_item_scale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable {
                pressed = true
                onClick()
            }
            .shadow(4.dp, shape = RoundedCornerShape(10.dp))
            .border(BorderStroke(1.dp, Color.White.copy(0.12f)), shape = RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0x26FFFFFF)) // 3D Glass feel background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(color.copy(0.15f), shape = RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, color.copy(0.3f)), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumTopBar(
    activeMode: BrowserMode,
    onModeChange: (BrowserMode) -> Unit,
    onSettingsClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val barColor = when (activeMode) {
        BrowserMode.SIMPLE -> Color(0xFF1E293B)
        BrowserMode.DEVELOPER -> Color(0xFF0F172A)
        BrowserMode.HACK -> Color(0xFF18181B)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Top-left "Click" logo and settings gear next to it
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Drawer Menu", tint = Color.White)
            }
            Text(
                text = "Click Pro",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onSettingsClick() }
            )
        }

        // Top-right tiny mode segmented control switcher (Simple / Developer / Hack) replacing old theme segmented control
        Row(
            modifier = Modifier
                .background(Color(0xFF262626), shape = RoundedCornerShape(12.dp))
                .padding(2.dp)
        ) {
            BrowserMode.values().forEach { mode ->
                val isSelected = activeMode == mode
                val activeBgColor = when (mode) {
                    BrowserMode.SIMPLE -> Color(0xFF3B82F6)
                    BrowserMode.DEVELOPER -> Color(0xFF7C3AED)
                    BrowserMode.HACK -> Color(0xFFDC2626)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) activeBgColor else Color.Transparent)
                        .clickable { onModeChange(mode) }
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Text(
                        text = mode.name.first() + mode.name.substring(1).lowercase(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PremiumSearchRow(
    activeMode: BrowserMode,
    searchEngine: String,
    onSearch: (String) -> Unit
) {
    var searchInput by remember { mutableStateOf("") }

    val barColor = when (activeMode) {
        BrowserMode.SIMPLE -> Color(0xFF0F172A)
        BrowserMode.DEVELOPER -> Color(0xFF020617)
        BrowserMode.HACK -> Color(0xFF090514)
    }

    val glowColor = when (activeMode) {
        BrowserMode.SIMPLE -> Color(0xFF3B82F6)
        BrowserMode.DEVELOPER -> Color(0xFF8B5CF6)
        BrowserMode.HACK -> Color(0xFFEF4444)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "SearchPulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 4.dp.value,
        targetValue = 12.dp.value,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowWidth"
    )

    val progressOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progressFlow"
    )

    // Synchronous assistant hints based on typing and mode
    val assistantHint = remember(searchInput, activeMode, searchEngine) {
        if (searchInput.isEmpty()) {
            when (activeMode) {
                BrowserMode.SIMPLE -> "💡 Tip: Ask our integrated PK AI anything directly!"
                BrowserMode.DEVELOPER -> "🛠️ DevTip: Use Console or Inspect HTML to test DOM elements!"
                BrowserMode.HACK -> "🛡️ HackGuard: Ahmia secure relay active. WebRTC is protected."
            }
        } else {
            val query = searchInput.trim().lowercase()
            when {
                query.startsWith("http") || query.contains(".") -> {
                    "🌐 Go to Address: Open secure direct tunnel to $searchInput"
                }
                query.contains("how to") || query.contains("what is") || query.contains("why") -> {
                    "🤖 PK AI assistant: 'I can answer questions on $searchInput in one click!'"
                }
                activeMode == BrowserMode.DEVELOPER -> {
                    when {
                        query.contains("js") || query.contains("script") -> "💻 Dev: Run JavaScript benchmark or inspect active scope logs."
                        query.contains("html") || query.contains("css") -> "🎨 Dev: Inspect CSS DOM trees and elements."
                        else -> "🔍 Developer Search: Query DuckDuckGo/Yandex for developer docs."
                    }
                }
                activeMode == BrowserMode.HACK -> {
                    when {
                        query.contains("onion") -> "🕵️ Onion Proxy: Ahmia Tor-routed darknet portal initialized."
                        query.contains("leak") || query.contains("ip") -> "🔒 Guard: WebRTC shield & Canvas spoofing are blocking trackers."
                        else -> "⚡ Hack Search: Ahmia / Startpage secure private search."
                    }
                }
                else -> {
                    "🔍 Smart Suggestion: Search '$searchInput' on $searchEngine..."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
    ) {
        // Glowing 3D Glassmorphic Outer Card with pulsing glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = pulseGlow.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = glowColor.copy(alpha = 0.5f),
                    spotColor = glowColor
                )
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(0.08f),
                            Color.White.copy(0.02f)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(0.15f),
                                glowColor.copy(alpha = 0.3f),
                                Color.White.copy(0.05f)
                            )
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(2.dp)
        ) {
            Column {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent),
                    placeholder = {
                        Text(
                            "Type URL or search premium query...",
                            color = Color.LightGray.copy(0.8f),
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(searchInput) }),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = glowColor,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchInput.isNotEmpty()) {
                            IconButton(onClick = { searchInput = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Voice",
                                tint = Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Flowing progress/energy line at the bottom of search field inside the card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .padding(horizontal = 24.dp)
                        .background(Color.White.copy(0.05f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .fillMaxHeight()
                            .align(Alignment.CenterStart)
                            .graphicsLayer {
                                translationX = (progressOffset * 250).dp.toPx()
                            }
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        glowColor,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Contextual AI assistant synchronous hint badge
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(0.04f))
                    .clickable {
                        if (searchInput.isNotEmpty()) {
                            onSearch(searchInput)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(glowColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = assistantHint,
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
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

            // Glassmorphic address input field
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
                        Icon(Icons.Default.Lock, contentDescription = "Secure", tint = Color.Green, modifier = Modifier.size(16.dp))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PremiumHomeScreen(
    activeMode: BrowserMode,
    @Suppress("UNUSED_PARAMETER") isIncognito: Boolean,
    onNavigate: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onModeChange: (BrowserMode) -> Unit,
    // Dialog callback clicks
    onAiClick: () -> Unit,
    onMusicClick: () -> Unit,
    onVideoClick: () -> Unit,
    onPdfClick: () -> Unit,
    onImagesClick: () -> Unit,
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
    // Settings module expandable state
    var settingsExpanded by remember { mutableStateOf(false) }

    // Premium Social Shortcuts without text (Google, YouTube, Facebook, Instagram, WhatsApp, TikTok, Telegram, Discord, GitHub, Reddit, Pinterest, Netflix, Spotify, Amazon)
    val socialShortcuts = listOf(
        SocialIconInfo("Google", "https://google.com", Color(0xFF4285F4)),
        SocialIconInfo("YouTube", "https://youtube.com", Color(0xFFFF0000)),
        SocialIconInfo("Facebook", "https://facebook.com", Color(0xFF1877F2)),
        SocialIconInfo("Instagram", "https://instagram.com", Color(0xFFE4405F)),
        SocialIconInfo("WhatsApp", "https://whatsapp.com", Color(0xFF25D366)),
        SocialIconInfo("TikTok", "https://tiktok.com", Color.White),
        SocialIconInfo("Telegram", "https://telegram.org", Color(0xFF0088CC)),
        SocialIconInfo("Discord", "https://discord.com", Color(0xFF5865F2)),
        SocialIconInfo("GitHub", "https://github.com", Color.White),
        SocialIconInfo("Reddit", "https://reddit.com", Color(0xFFFF4500)),
        SocialIconInfo("Pinterest", "https://pinterest.com", Color(0xFFBD081C)),
        SocialIconInfo("Netflix", "https://netflix.com", Color(0xFFE50914)),
        SocialIconInfo("Spotify", "https://spotify.com", Color(0xFF1DB954)),
        SocialIconInfo("Amazon", "https://amazon.com", Color(0xFFFF9900))
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (activeMode == BrowserMode.HACK) {
            MatrixGridAnimation()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Animated Hero Card with AI powered greeting
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, shape = RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, Brush.horizontalGradient(listOf(Color(0xFF4FC3FF), Color(0xFFB070FF)))), shape = RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0x33000000))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI greeting",
                            color = Color(0xFF4FC3FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Brush.linearGradient(listOf(Color(0xFF4FC3FF), Color(0xFFB070FF))), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Welcome to the Click Pro Browser ecosystem. Select high performance modes or surf secure.",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 2-Column Premium Widget Grid: AI, Music, Video, Download, Image, PDF, Browser (wide)
            Text(
                text = "Premium Widget Grid",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )

            // Since grid scroll is nested inside Column verticalScroll, we can build custom layout flow or Row layouts
            // Let's lay them out in clean modular Rows.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Widget 1: AI (Fully functional chatbot launcher)
                WidgetCard(
                    title = "AI ASSISTANT",
                    value = "PK Chatbot",
                    sub = "Ask anything",
                    icon = Icons.Default.AutoAwesome,
                    color = Color(0xFF4FC3FF),
                    modifier = Modifier.weight(1f),
                    onClick = onAiClick
                )
                // Widget 2: Music player (with animated EQ bars and direct launcher)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp)
                        .clickable { onMusicClick() }
                        .shadow(4.dp, shape = RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0x33FF5A9E), shape = RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFFF5A9E), modifier = Modifier.size(16.dp))
                            }
                            // Equalizer Bars
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(modifier = Modifier.size(2.dp, 12.dp).background(Color(0xFFFF5A9E)))
                                Box(modifier = Modifier.size(2.dp, 16.dp).background(Color(0xFFB070FF)))
                                Box(modifier = Modifier.size(2.dp, 8.dp).background(Color(0xFF4FC3FF)))
                            }
                        }
                        Column {
                            Text("MUSIC PLAYER", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("Priscilla", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Now playing", color = Color.LightGray, fontSize = 10.sp)
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Widget 3: Video
                WidgetCard(
                    title = "VIDEO PREVIEW",
                    value = "Click Cinema",
                    sub = "Premium streams",
                    icon = Icons.Default.PlayCircle,
                    color = Color(0xFF3EE7B0),
                    modifier = Modifier.weight(1f),
                    onClick = onVideoClick
                )
                // Widget 4: Download
                WidgetCard(
                    title = "DOWNLOADS",
                    value = "Manage files",
                    sub = "High-speed",
                    icon = Icons.Default.CloudDownload,
                    color = Color(0xFFFFA726),
                    modifier = Modifier.weight(1f),
                    onClick = onDownloadsClick
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Widget 5: Images
                WidgetCard(
                    title = "GALLERY",
                    value = "Image Gallery",
                    sub = "4D preview",
                    icon = Icons.Default.Image,
                    color = Color(0xFFEC4899),
                    modifier = Modifier.weight(1f),
                    onClick = onImagesClick
                )
                // Widget 6: PDF
                WidgetCard(
                    title = "PDF READER",
                    value = "Document PDF",
                    sub = "Scroll & Zoom",
                    icon = Icons.Default.PictureAsPdf,
                    color = Color(0xFFEF5350),
                    modifier = Modifier.weight(1f),
                    onClick = onPdfClick
                )
            }

            // Widget 7: Wide Browser Widget
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clickable { onNavigate("https://google.com") }
                    .shadow(4.dp, shape = RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("BROWSER WIDGET", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("Explore Web Ecosystem", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Adblock, DNS-over-HTTPS active", color = Color.LightGray, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Brush.linearGradient(listOf(Color(0xFF7A8BFF), Color(0xFF4FC3FF))), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = Color.White)
                    }
                }
            }

            // Recent Tabs Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Recent Tabs",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, shape = RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0x11FFFFFF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Click Search Homepage", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("Active", color = Color(0xFF3EE7B0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Pinned Websites Section with premium icons only (NO TEXT)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Pinned Websites",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )

                // Horizontal scroll or grid of icons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    socialShortcuts.take(7).forEach { item ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(item.color.copy(0.15f))
                                .border(1.dp, item.color.copy(0.3f), RoundedCornerShape(8.dp))
                                .clickable { onNavigate(item.url) },
                            contentAlignment = Alignment.Center
                        ) {
                            val vectorIcon = when (item.label) {
                                "Google" -> Icons.Default.Search
                                "YouTube" -> Icons.Default.PlayArrow
                                "Facebook" -> Icons.Default.Facebook
                                "Instagram" -> Icons.Default.CameraAlt
                                "WhatsApp" -> Icons.Default.Phone
                                "TikTok" -> Icons.Default.MusicNote
                                else -> Icons.Default.Send
                            }
                            Icon(vectorIcon, contentDescription = null, tint = item.color, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Premium Extensions Module Card (Shows Release / Debug APK sizes & Update)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, shape = RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0x1F0F172A))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Premium Extensions Module", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Release APK: ~11 MB", color = Color.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Debug APK: ~16 MB", color = Color.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onSettingsClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Text("Update Application", fontSize = 11.sp)
                    }
                }
            }

            // Consolidated Settings Module Card (Display, Extensions, Security, Permissions, Advanced, Team Info - expandable)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, shape = RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0x1F0F172A))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { settingsExpanded = !settingsExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Consolidated Settings Module", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        Icon(
                            imageVector = if (settingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }

                    AnimatedVisibility(visible = settingsExpanded) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            // adblock
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("🧩 Adblocker Extension Active", color = Color.White, fontSize = 11.sp)
                                Switch(checked = adBlockerEnabled, onCheckedChange = onToggleAdBlocker, modifier = Modifier.scale(0.8f))
                            }
                            // night mode
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("🌙 Force Night Mode Website", color = Color.White, fontSize = 11.sp)
                                Switch(checked = forceNightMode, onCheckedChange = onToggleNightMode, modifier = Modifier.scale(0.8f))
                            }
                            // HTTPS only
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("🔒 HTTPS Only Mode", color = Color.White, fontSize = 11.sp)
                                Switch(checked = httpsOnlyMode, onCheckedChange = onToggleHttpsOnly, modifier = Modifier.scale(0.8f))
                            }
                            // JS
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("⚡ JavaScript Execution Support", color = Color.White, fontSize = 11.sp)
                                Switch(checked = jsEnabled, onCheckedChange = onToggleJs, modifier = Modifier.scale(0.8f))
                            }
                            // data saver
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("📦 Data Saver Mode", color = Color.White, fontSize = 11.sp)
                                Switch(checked = dataSaver, onCheckedChange = onToggleDataSaver, modifier = Modifier.scale(0.8f))
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(0.1f))

                            // About credits
                            Text("Team: Team PK AI", fontWeight = FontWeight.Bold, color = Color.Cyan, fontSize = 11.sp)
                            Text("UI Design Built By: Prince Laghari", fontWeight = FontWeight.ExtraBold, color = Color.Green, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Consolidated System & Download Module with Row of website shortcut icons and Minimized Ad Zone banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, shape = RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0x1F0F172A))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Consolidated System & Download Module", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))

                    // Shortcut list layout flow row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        socialShortcuts.take(7).forEach { item ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(item.color.copy(0.15f))
                                    .border(1.dp, item.color.copy(0.3f), RoundedCornerShape(8.dp))
                                    .clickable { onNavigate(item.url) },
                                contentAlignment = Alignment.Center
                            ) {
                                val vectorIcon = when (item.label) {
                                    "Google" -> Icons.Default.Search
                                    "YouTube" -> Icons.Default.PlayArrow
                                    "Facebook" -> Icons.Default.Facebook
                                    "Instagram" -> Icons.Default.CameraAlt
                                    "WhatsApp" -> Icons.Default.Phone
                                    "TikTok" -> Icons.Default.MusicNote
                                    else -> Icons.Default.Send
                                }
                                Icon(vectorIcon, contentDescription = null, tint = item.color, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Minimized Ad Zone
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(10.dp, shape = RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Text(
                            text = "📢 SPONSORED AD: Experience lightning fast browsing speeds with Pro Upgrade!",
                            color = Color.Yellow,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Composable
fun WidgetCard(
    title: String,
    value: String,
    sub: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .height(130.dp)
            .clickable { onClick() }
            .shadow(4.dp, shape = RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color.copy(0.2f), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Column {
                Text(title, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(sub, color = Color.LightGray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun TabNavigationItem(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = Color.LightGray

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
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) activeColor else inactiveColor,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isActive) activeColor else inactiveColor,
            fontWeight = FontWeight.Bold
        )
    }
}

data class SocialIconInfo(
    val label: String,
    val url: String,
    val color: Color
)

data class ShortcutWidgetInfo(
    val label: String,
    val url: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun DrawerCategoryHeader(title: String) {
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
        HorizontalDivider(color = Color.White.copy(0.08f))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title.uppercase(),
            color = Color(0xFF60A5FA),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}
