package com.click.browser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebSettingsCompat
import androidx.annotation.RequiresApi
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.click.browser.extensions.ExtensionBrowserActivity
import com.click.browser.extensions.ExtensionBrowserRuntime
import com.click.browser.extensions.ExtensionInstallPolicy
import com.click.browser.extensions.InstalledAddonPanelAction
import com.click.browser.extensions.InstalledAddonPanelItem
import com.click.browser.extensions.InstalledAddonsPanel
import com.click.browser.extensions.safeBuildInstalledAddonPanelItems
import com.click.browser.extensions.NormalTabExtensionAction
import com.click.browser.extensions.NormalTabActionDispatchResult
import com.click.browser.extensions.NormalTabExtensionPopup
import com.click.browser.extensions.NormalTabExtensionRouter
import com.click.browser.extensions.extensionPolicyBlockers
import com.click.browser.ui.screens.*
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

class TabItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    url: String = "about:blank",
    title: String = "New Tab",
    isIncognito: Boolean = false,
    initialWebView: WebView? = null,
    initialEngine: BrowserTabEngine = defaultBrowserTabEngine(isIncognito),
    initialGeckoSessionState: String? = null,
    initialGeckoSession: org.mozilla.geckoview.GeckoSession? = null
) {
    /** Regular tabs default to GeckoView; private tabs stay on WebView. */
    var engine by mutableStateOf(initialEngine)

    internal var browserSession: BrowserTabSession? by mutableStateOf(
        initialWebView?.let(::WebViewBrowserTabSession)
    )
        private set

    internal fun attachBrowserSession(session: BrowserTabSession?) {
        browserSession = session
    }

    internal var pendingGeckoSession: org.mozilla.geckoview.GeckoSession? = initialGeckoSession
        private set

    internal fun takePendingGeckoSession(): org.mozilla.geckoview.GeckoSession? =
        pendingGeckoSession.also { pendingGeckoSession = null }

    /** Compatibility access for existing WebView-only UI/features. */
    var webView: WebView?
        get() = (browserSession as? WebViewBrowserTabSession)?.webView
        set(value) {
            browserSession = value?.let(::WebViewBrowserTabSession)
        }

    var url by mutableStateOf(url)
    var connectionSecurity by mutableStateOf(ConnectionSecurityPolicy.unknown(url))
    var title by mutableStateOf(title)
    var isIncognito by mutableStateOf(isIncognito)
    /** Private-tab website microphone choices are memory-only and disappear with this tab. */
    internal val privateWebsiteMicrophonePermissions = mutableStateMapOf<String, Boolean>()
    /** Gecko's serialized history/scroll/form snapshot; never written for private tabs. */
    var geckoSessionState by mutableStateOf(initialGeckoSessionState)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    /** 0..100 page-load progress (drives the thin progress bar under the top bar). */
    var loadProgress by mutableStateOf(0)
    var isPageLoading by mutableStateOf(false)
    var hasCommittedContent by mutableStateOf(false)
    internal val passwordAutofillPromptGuard =
        com.click.browser.engine.PasswordAccessPolicy.AutofillPromptGuard()
    var rendererRecoveryRequired by mutableStateOf(false)
    internal var geckoNavigationFailure by mutableStateOf<GeckoNavigationFailure?>(null)
    var geckoNavigationGeneration by mutableStateOf(-1L)
}

/** Keeps the same logical tab selected after a close, without indexing past the list. */
internal fun activeTabIndexAfterClose(activeIndex: Int, closedIndex: Int, remainingCount: Int): Int {
    if (remainingCount <= 0) return 0
    return when {
        closedIndex < activeIndex -> (activeIndex - 1).coerceIn(0, remainingCount - 1)
        closedIndex == activeIndex -> activeIndex.coerceAtMost(remainingCount - 1)
        else -> activeIndex.coerceIn(0, remainingCount - 1)
    }
}

private data class ClosedTabUndo(
    val title: String,
    val url: String,
    val index: Int,
    val engine: BrowserTabEngine,
    val mode: BrowserMode
)

private data class WebsiteMicrophoneConsentUiState(
    val requestId: String,
    val tabId: String,
    val host: String,
    val privateTab: Boolean,
    val onAllow: () -> Unit,
    val onBlock: () -> Unit,
    val onDismiss: () -> Unit
)

private data class WebsiteMicrophoneRecoveryUiState(
    val origin: String,
    val tabId: String,
    val privateTab: Boolean
)

private data class PendingWebsiteMicrophoneAndroidResult(
    val requestId: String,
    val tabId: String,
    val onResult: (Boolean) -> Unit,
    val onCancel: () -> Unit
)

private data class PendingBrowserDownload(
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val userAgent: String?,
    val mode: BrowserMode,
    val responseBody: java.io.InputStream? = null
)

private data class PendingPasswordSaveOffer(
    val tabId: String,
    val sourceOrigin: String,
    val credential: SavedPassword
)

private data class PendingGeckoPasswordSaveOffer(
    val tabId: String,
    val session: org.mozilla.geckoview.GeckoSession,
    val request: org.mozilla.geckoview.GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSaveOption>,
    val option: org.mozilla.geckoview.Autocomplete.LoginSaveOption,
    val completion: org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse>,
    val credential: SavedPassword
)

private data class PendingAiSelectedTextOffer(
    val selectionId: String,
    val tabId: String,
    val pageUrl: String,
    val selection: AiSelectedTextPolicy.PreparedSelection
)

private data class PendingSelectedTextTranslation(
    val selectionId: String,
    val tabId: String,
    val pageUrl: String,
    val selection: AiSelectedTextPolicy.PreparedSelection
)


/**
 * Page transition wrapper — subtle fade + slide on every navigation start.
 * Extracted to a top-level composable because [androidx.compose.animation.AnimatedVisibility]
 * can't be called by implicit receiver inside a ColumnScope (ambiguous with
 * ColumnScope.AnimatedVisibility). Purely visual: the WebView keeps loading
 * underneath, navigation is never blocked. Durations collapse to 0 when
 * [animationsEnabled] is false.
 */
@Composable
private fun PageTransitionWrapper(
    tick: Int,
    animationsEnabled: Boolean,
    content: @Composable () -> Unit
) {
    val pageAnimMs = if (animationsEnabled) 220 else 0
    val pageTransitionState = remember(tick) {
        MutableTransitionState(false).apply { targetState = true }
    }
    AnimatedVisibility(
        visibleState = pageTransitionState,
        enter = fadeIn(tween(pageAnimMs)) +
                slideInHorizontally(tween(pageAnimMs)) { it / 14 },
        modifier = Modifier.fillMaxSize()
    ) {
        content()
    }
}

// FragmentActivity (extends ComponentActivity) — keeps the page-transition
// animations from main AND the Fragment support BiometricPrompt needs.
class MainActivity : FragmentActivity() {

    // App language (English / اردو / Roman Urdu) applies before anything inflates.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.click.browser.engine.AppLanguage.wrapContext(newBase))
    }

    private lateinit var modeManager: ModeManager
    private lateinit var repository: BrowserRepository
    private lateinit var playlistManager: PlaylistManager

    // Cached copy of the "background audio" setting for onPause/onResume,
    // which run outside composition. Synced from the composable state.
    @Volatile private var backgroundAudioEnabledCached: Boolean = false

    // Deep-link URL from a news notification tap (NewsNotifications.EXTRA_OPEN_URL).
    // Set from onCreate (cold start) or onNewIntent (warm, singleTask); consumed
    // once by the composable, which opens it in a new tab.
    private val notificationOpenUrl = mutableStateOf<String?>(null)

    // Live copies of composable state for use inside WebViewClient callbacks,
    // which are created once and would otherwise capture stale values.
    private var liveMode: BrowserMode = BrowserMode.SIMPLE
    private var liveNightMode = false
    private var liveDataSaver = false
    // Advance Mode intro animation toggle (Settings; default ON).
    private var liveAdvanceIntroEnabled = true
    // Brave-inspired privacy quick wins (live copies for WebViewClient callbacks).
    private var liveStripTrackingParams = true
    private var liveForgetfulBrowsing = false
    private var liveForgetfulExceptions: Set<String> = emptySet()
    // Accessibility: force zoom / reader mode / reduced motion.
    private var liveForceZoom = false
    private var liveReaderMode = false
    private var liveReducedMotion = false
    // Sharp View: full-density WebView rendering. Default ON.
    private var liveSharpViewEnabled = true
    // HTTPS mode: "off" | "standard" | "strict" + per-site Strict exceptions.
    private var liveHttpsMode = "standard"
    private var liveHttpsStrictExceptions: Set<String> = emptySet()
    // DNT + GPC privacy signals (opt-in, default off).
    private var liveDntEnabled = false
    private var liveGpcEnabled = false
    // Live copy of explicit per-site wide-layout preferences. Updating this
    // immediately after a DataStore write prevents reloads from seeing stale state.
    private var liveDesktopOverrides: Map<String, Boolean> = emptyMap()
    private var liveForceDesktopMode = false
    // Live copy of supported per-site content settings, persisted per host
    // and engine mode via BrowserRepository.
    private var liveSiteSettings: Map<String, com.click.browser.engine.SiteSettings> = emptyMap()
    // Live copy of the global ad-block toggle for WebViewClient callbacks.
    private var liveAdBlockerEnabled: Boolean = true
    // Live copy of experimental flags (click://flags) for WebViewClient callbacks.
    private var liveFlags = ExperimentalFlags()
    // Biometric private-tab lock: live copies for onPause(), which runs
    // outside compose scope.
    private var liveBiometricLockEnabled = false
    private var liveHasIncognitoTabs = false
    private val privateLockedFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val passwordManagerAuthenticatedFlow = MutableStateFlow(false)
    private var passwordAuthenticationInFlight = false
    // Default-browser flow: pending external link from an ACTION_VIEW intent
    // (drained into a new tab by the composable) + the current
    // default-browser status shown in Settings and used for the one-time
    // first-launch prompt.
    private val pendingExternalUrl = MutableStateFlow<String?>(null)
    private val pendingOpenDownloads = MutableStateFlow(false)
    private val activityResumeCount = MutableStateFlow(0L)
    private val isDefaultBrowserState = MutableStateFlow(false)
    private val defaultBrowserLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // The system role dialog closed (granted, denied or dismissed):
            // re-check the truth rather than trusting the result code.
            isDefaultBrowserState.value =
                DefaultBrowserHelper.isDefaultBrowser(this)
        }
    // Text scaling live copies for WebViewClient callbacks (per-host map).
    private var liveGlobalTextScale = com.click.browser.engine.TextScaleStore.DEFAULT
    private var liveHostTextScales: Map<String, Int> = emptyMap()
    // DevTools captures only while its Developer-mode panel is open. The
    // target WebView is volatile because shouldInterceptRequest runs off UI.
    private val devToolsLogs = androidx.compose.runtime.mutableStateListOf<com.click.browser.engine.LogEntry>()
    private val devToolsNetwork = androidx.compose.runtime.mutableStateListOf<com.click.browser.engine.NetworkRequest>()
    private val devToolsDomHtml = androidx.compose.runtime.mutableStateOf("")
    private val devToolsSources = androidx.compose.runtime.mutableStateListOf<String>()
    @Volatile private var devToolsCaptureEnabled: Boolean = false
    @Volatile private var devToolsTargetWebView: WebView? = null

    /** Thread-safe DevTools log append (caps at 500 entries, drops oldest). */
    private fun addDevToolsLog(entry: com.click.browser.engine.LogEntry) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            devToolsLogs.add(entry)
            if (devToolsLogs.size > 500) devToolsLogs.removeAt(0)
        } else {
            runOnUiThread {
                devToolsLogs.add(entry)
                if (devToolsLogs.size > 500) devToolsLogs.removeAt(0)
            }
        }
    }

    /** Apply a pure network-log update on the UI thread; the model enforces its cap. */
    private fun updateDevToolsNetwork(
        update: (List<com.click.browser.engine.NetworkRequest>) -> List<com.click.browser.engine.NetworkRequest>
    ) {
        val apply = {
            val updated = update(devToolsNetwork.toList())
            devToolsNetwork.clear()
            devToolsNetwork.addAll(updated)
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) apply()
        else runOnUiThread { apply() }
    }

    private fun recordDevToolsRequest(
        view: WebView?,
        request: WebResourceRequest?,
        blockedByClick: Boolean = false
    ) {
        if (!devToolsCaptureEnabled || view == null || view !== devToolsTargetWebView || request == null) return
        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return
        val method = request.method.orEmpty().ifBlank { "GET" }
        val isMainFrame = request.isForMainFrame
        val webViewKey = System.identityHashCode(view)
        updateDevToolsNetwork { entries ->
            if (blockedByClick) {
                com.click.browser.engine.DevToolsNetworkLog.blocked(
                    entries, method, url, isMainFrame, webViewKey
                )
            } else {
                com.click.browser.engine.DevToolsNetworkLog.observed(
                    entries, method, url, isMainFrame, webViewKey
                )
            }
        }
    }

    private fun recordDevToolsHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        response: WebResourceResponse?
    ) {
        if (!devToolsCaptureEnabled || view == null || view !== devToolsTargetWebView || request == null || response == null) return
        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return
        val method = request.method.orEmpty().ifBlank { "GET" }
        val isMainFrame = request.isForMainFrame
        val webViewKey = System.identityHashCode(view)
        updateDevToolsNetwork { entries ->
            com.click.browser.engine.DevToolsNetworkLog.httpError(
                entries, method, url, isMainFrame, webViewKey,
                response.statusCode, response.reasonPhrase
            )
        }
    }

    private fun recordDevToolsLoadError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        if (!devToolsCaptureEnabled || view == null || view !== devToolsTargetWebView || request == null) return
        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return
        val method = request.method.orEmpty().ifBlank { "GET" }
        val isMainFrame = request.isForMainFrame
        val webViewKey = System.identityHashCode(view)
        val description = listOfNotNull(
            error?.errorCode?.let { "WebView error $it" },
            error?.description?.toString()?.takeIf { it.isNotBlank() }
        ).joinToString(": ").ifBlank { "WebView load error" }
        updateDevToolsNetwork { entries ->
            com.click.browser.engine.DevToolsNetworkLog.loadError(
                entries, method, url, isMainFrame, webViewKey, description
            )
        }
    }
    // Crash-restore: latest restorable-tab snapshot for this boot mode, kept
    // outside composable scope so onDestroy() and v9SwitchMode() can persist
    // it and mark clean exits without touching UI state. The mirror is updated
    // after composition; only the DataStore write is debounced.
    private var liveRestoreTabs: List<SavedTab> = emptyList()
    private var liveRestoreActiveIndex: Int = 0
    @Volatile private var modeSwitchInProgress: Boolean = false

    // Registry of live WebViews for flag-driven cleanup (clear-on-exit).
    private val liveWebViews = mutableListOf<android.webkit.WebView>()
    // Engine-neutral lifecycle registry for regular Gecko tabs. Private tabs
    // remain on WebView until Gecko's private-profile cleanup is verified.
    private val liveBrowserSessions = mutableListOf<BrowserTabSession>()
    // Main-thread set of exact Gecko storage contexts used during this Activity.
    private val geckoContextIdsUsedInActivity = linkedSetOf<String>()
    private val geckoTabActionsState = androidx.compose.runtime.mutableStateOf(
        emptyMap<String, List<NormalTabExtensionAction>>()
    )
    private val geckoTabPopupState = androidx.compose.runtime.mutableStateOf<NormalTabExtensionPopup?>(null)
    private var normalTabExtensionRouter: NormalTabExtensionRouter? = null

    private fun normalTabExtensionRouter(): NormalTabExtensionRouter =
        normalTabExtensionRouter ?: synchronized(this) {
            normalTabExtensionRouter ?: NormalTabExtensionRouter(
                runtime = ExtensionBrowserRuntime.get(applicationContext),
                onActionsChanged = { tabId, actions ->
                    val update = {
                        geckoTabActionsState.value = geckoTabActionsState.value.toMutableMap().apply {
                            if (actions.isEmpty()) remove(tabId) else put(tabId, actions)
                        }
                    }
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) update()
                    else runOnUiThread { update() }
                },
                onPopupChanged = { popup ->
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                        geckoTabPopupState.value = popup
                    } else {
                        runOnUiThread { geckoTabPopupState.value = popup }
                    }
                }
            ).also { normalTabExtensionRouter = it }
        }

    private fun destroyLiveGeckoSessions() {
        val geckoSessions = liveBrowserSessions.filterIsInstance<GeckoBrowserTabSession>()
        geckoSessions.forEach { session -> runCatching { session.destroy() } }
        liveBrowserSessions.removeAll(geckoSessions.toSet())
        geckoTabActionsState.value = emptyMap()
    }

    private fun recordGeckoRendererDiagnostic(
        tabId: String,
        url: String?,
        code: com.click.browser.engine.ClickCatLogCode,
        eventDetails: String,
        error: Throwable? = null
    ) {
        val memoryInfo = runCatching {
            getSystemService(android.app.ActivityManager::class.java)?.let { manager ->
                android.app.ActivityManager.MemoryInfo().also { info ->
                    manager.getMemoryInfo(info)
                }
            }
        }.getOrNull()
        val availableMemoryMb = memoryInfo?.availMem?.div(1024L * 1024L)
        val memoryThresholdMb = memoryInfo?.threshold?.div(1024L * 1024L)
        com.click.browser.engine.ClickDiagnosticsLog.record(
            context = this,
            code = code,
            toMode = liveMode,
            rendererDetails = com.click.browser.engine.ClickRendererDiagnosticDetails(
                tabId = tabId,
                url = url,
                geckoViewVersion = BuildConfig.GECKOVIEW_VERSION,
                androidVersion = "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
                availableMemoryMb = availableMemoryMb,
                eventDetails = eventDetails,
                exceptionType = error?.javaClass?.name,
                deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim(),
                systemLowMemory = memoryInfo?.lowMemory,
                memoryThresholdMb = memoryThresholdMb,
                exceptionDetails = com.click.browser.engine.safeRendererExceptionDetails(error)
            )
        )
    }

    private fun requestGeckoModeDataClear(mode: BrowserMode) {
        requestGeckoContextDataClear(geckoContextIdFor(mode))
    }

    private fun requestGeckoContextDataClear(contextId: String) {
        runCatching {
            ExtensionBrowserRuntime.get(applicationContext).storageController
                .clearDataForSessionContext(contextId)
        }
    }

    private var ttsEngine: TextToSpeech? = null

    /**
     * Fullscreen video exit hook. Set by the active WebChromeClient when a
     * page enters fullscreen video (onShowCustomView); cleared on exit.
     * The BackHandler invokes this first so back exits fullscreen video
     * instead of navigating back.
     */
    var exitFullscreenVideo: (() -> Unit)? by androidx.compose.runtime.mutableStateOf(null)

    override fun onPause() {
        super.onPause()
        passwordManagerAuthenticatedFlow.value = false
        normalTabExtensionRouter?.deactivateActiveSession()
        // Standard browser behavior: pause live tab sessions when the app is
        // backgrounded, so page audio/video stops — UNLESS the user
        // explicitly enabled "Background audio" in Settings (a generic
        // media setting, never tied to any specific site or service).
        if (!backgroundAudioEnabledCached) {
            liveBrowserSessions.forEach { session ->
                try { session.onPause() } catch (_: Exception) { }
                try { session.pauseTimers() } catch (_: Exception) { }
            }
        }
        // Biometric private-tab lock: whenever the app goes to background
        // while the lock is enabled and at least one incognito tab exists,
        // the private tabs re-lock. The overlay + BiometricPrompt handle
        // the unlock on return.
        if (liveBiometricLockEnabled && liveHasIncognitoTabs) {
            privateLockedFlow.value = true
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumeCount.value += 1L
        // The fallback default-apps page (pre-Android 10) does not return an
        // ActivityResult, so always refresh the role state when we come back.
        isDefaultBrowserState.value = DefaultBrowserHelper.isDefaultBrowser(this)
        liveBrowserSessions.forEach { session ->
            try { session.onResume() } catch (_: Exception) { }
            try { session.resumeTimers() } catch (_: Exception) { }
        }
        normalTabExtensionRouter?.refreshAll()
    }

    /**
     * Warm-path deep links: singleTask delivers intents here instead of
     * creating a second MainActivity.
     * - News notification tap: EXTRA_OPEN_URL → open article in a new tab.
     * - External ACTION_VIEW link: open in a new foreground tab.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeOpenDownloadsIntent(intent)
        consumeNotificationUrl(intent)
        // Link delivered to the already-running instance: open it in a new
        // foreground tab via the pending-URL flow.
        externalViewUrl(intent)?.let { pendingExternalUrl.value = it }
    }

    private fun consumeOpenDownloadsIntent(intent: Intent) {
        if (!intent.getBooleanExtra(com.click.browser.engine.CustomDownloadNotifications.EXTRA_OPEN_DOWNLOADS, false)) {
            return
        }
        intent.removeExtra(com.click.browser.engine.CustomDownloadNotifications.EXTRA_OPEN_DOWNLOADS)
        pendingOpenDownloads.value = true
    }

    /**
     * Pulls [NewsNotifications.EXTRA_OPEN_URL] out of [intent] (once) into
     * [notificationOpenUrl]. Only http(s) URLs are accepted — the extra
     * comes from our own notification, but intents are an exposed surface.
     */
    private fun consumeNotificationUrl(intent: Intent) {
        val url = intent.getStringExtra(NewsNotifications.EXTRA_OPEN_URL)
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return
        intent.removeExtra(NewsNotifications.EXTRA_OPEN_URL)
        notificationOpenUrl.value = url
    }

    override fun onDestroy() {
        if (V9Engine.isBootBlocked) {
            super.onDestroy()
            return
        }
        try { ttsEngine?.shutdown() } catch (_: Exception) { }
        normalTabExtensionRouter?.closePopup()
        destroyLiveGeckoSessions()
        // Experimental flags: clear data / history on exit.
        if (liveFlags.clearOnExit) {
            // GeckoView requires sessions for a context to be closed before its
            // persistent context data can be fully removed.
            geckoContextIdsToClearOnExit(V9Engine.bootMode, geckoContextIdsUsedInActivity)
                .forEach(::requestGeckoContextDataClear)
            try {
                liveWebViews.forEach { it.clearCache(true) }
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.WebStorage.getInstance().deleteAllData()
            } catch (_: Exception) { }
        }
        if (liveFlags.clearHistoryOnExit) {
            val modeToClear = V9Engine.bootMode
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    repository.clearHistoryFor(modeToClear)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        RecentlyClosedTabs.clearForHistoryScope(modeToClear)
                    }
                } catch (_: Exception) { }
            }
        }
        // Crash-restore: an explicit finish() (Exit menu / back-out) is a
        // clean exit — no restore prompt next launch. Rotation and other
        // config changes have isFinishing=false, so they stay "unclean"
        // and correctly keep their snapshot for a later crash.
        // Non-blocking fire-and-forget on IO: runBlocking here would block
        // the main thread and risk ANR under I/O pressure. If the process
        // dies before the write lands, the flag simply stays "unclean" and
        // the (harmless) restore prompt may appear once — safe default.
        if (isFinishing) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    SessionRestore.markCleanExit(this@MainActivity, V9Engine.bootMode, true)
                } catch (_: Exception) { }
            }
        }
        super.onDestroy()
    }

    /** True for direct PDF links — intercepted for the in-app viewer offer. */
    private fun isPdfUrl(url: String): Boolean {
        return try {
            val path = android.net.Uri.parse(url).path?.lowercase().orEmpty()
            (url.startsWith("http://") || url.startsWith("https://")) &&
                (path.endsWith(".pdf"))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Long-press menu helpers (links + images). All actions are real —
     * no dead menu items.
     */
    private fun copyLongPressText(text: String, toastMsg: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Click Browser", text))
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Copy failed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareLongPressText(text: String, chooserTitle: String) {
        try {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(share, chooserTitle))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Saves a long-pressed image to Downloads/ClickBrowser via DownloadManager
     * (same established pattern as other browser downloads — no storage permission
     * needed on API 29+). blob:/data: URLs can't be fetched by DownloadManager,
     * so those get an honest toast instead of a dead button.
     */
    private fun saveLongPressImage(url: String) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            Toast.makeText(this, "This image can't be saved directly.", Toast.LENGTH_LONG).show()
            return
        }
        var fileName = url.substringAfterLast("/").substringBefore("?").take(64)
        if (fileName.isBlank() || !fileName.contains(".")) {
            val ext = when {
                url.contains(".png", ignoreCase = true) -> "png"
                url.contains(".webp", ignoreCase = true) -> "webp"
                url.contains(".gif", ignoreCase = true) -> "gif"
                else -> "jpg"
            }
            fileName = "click_image_${System.currentTimeMillis()}.$ext"
        }
        // Honors the user's download-location setting (phone / app-private / custom).
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            enqueueAndRecordDownload(url, fileName)
        }
    }

    /** Custom-folder transfers record on completion; DownloadManager tracks other destinations. */
    private suspend fun enqueueAndRecordDownload(
        url: String,
        fileName: String,
        locationOverride: String? = null,
        userAgent: String? = null,
        mode: BrowserMode = liveMode
    ) {
        val saved = DownloadHelper.enqueue(
            context = this,
            url = url,
            fileName = fileName,
            locationOverride = locationOverride,
            userAgent = userAgent,
            mode = mode
        ) ?: return
        repository.addDownloadItemFor(
            mode,
            DownloadItem(
                fileName = saved.fileName,
                url = url,
                path = saved.location,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Downloads a PDF to the app cache dir for the in-app viewer.
     * Returns the file, or null on failure. Call off the main thread.
     */
    private fun downloadPdfToCache(url: String): java.io.File? {
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val req = okhttp3.Request.Builder().url(url).get()
                .header("User-Agent", android.webkit.WebSettings.getDefaultUserAgent(applicationContext))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                // Sanity cap: 50 MB.
                val file = java.io.File(cacheDir, "click_pdf_${System.currentTimeMillis()}.pdf")
                file.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val n = body.byteStream().read(buf)
                        if (n < 0) break
                        total += n
                        if (total > 50L * 1024 * 1024) {
                            file.delete()
                            return null
                        }
                        out.write(buf, 0, n)
                    }
                }
                // Verify it looks like a PDF.
                val header = ByteArray(5)
                java.io.FileInputStream(file).use { it.read(header) }
                if (String(header) != "%PDF-") {
                    file.delete()
                    return null
                }
                file
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Applies the privacy toggles to a WebView's settings. Idempotent. */    private fun applyPrivacyToggles(
        webView: WebView,
        httpsMode: String = liveHttpsMode,
        nightMode: Boolean = liveNightMode,
        dataSaver: Boolean = liveDataSaver
    ) {
        val s = webView.settings
        // Standard + Strict both forbid mixed content; Off allows compat mode.
        s.mixedContentMode = if (httpsMode == "off") {
            WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        } else {
            WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        s.blockNetworkImage = dataSaver
        s.loadsImagesAutomatically = !dataSaver
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, nightMode)
        }
        // Safe Browsing: Google's harmful-site protection, enforced on every
        // WebView. Hits surface via framework onSafeBrowsingHit (API 27+).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(s, true)
        }
    }

    /**
     * Query-param stripping (Brave-style): removes tracking params from a
     * navigation URL when the toggle is on. Returns the original URL when
     * stripping is off or nothing was stripped.
     */
    private fun cleanTrackingUrl(url: String): String {
        if (!liveStripTrackingParams) return url
        return QueryParamStripper.strip(url) ?: url
    }


    /**
     * Applies the experimental flags (click://flags) to a WebView's settings.
     * Idempotent — safe to call on every recomposition.
     */
    private fun applyExperimentalFlags(webView: WebView) {
        val s = webView.settings
        val f = liveFlags
        // OR with dataSaver: the flag adds blocking, never removes it.
        s.blockNetworkImage = s.blockNetworkImage || f.blockImages
        s.javaScriptCanOpenWindowsAutomatically = !f.blockPopups
        s.mediaPlaybackRequiresUserGesture = f.autoplayBlock
        if (f.forceZoom) {
            s.setSupportZoom(true)
            s.builtInZoomControls = true
            s.displayZoomControls = false
            s.loadWithOverviewMode = true
            s.useWideViewPort = true
        }
        s.textZoom = if (f.textZoomLarge) 125 else 100
        // Only overrides when the flag explicitly allows mixed content.
        if (f.mixedContentAllow) {
            s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        val cm = android.webkit.CookieManager.getInstance()
        cm.setAcceptCookie(f.cookieAccept)
        try {
            cm.setAcceptThirdPartyCookies(webView, !f.blockThirdPartyCookies)
        } catch (_: Exception) { }
        webView.overScrollMode = if (f.overscrollGlow) {
            android.view.View.OVER_SCROLL_ALWAYS
        } else {
            android.view.View.OVER_SCROLL_NEVER
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(s, f.safeBrowsing)
        }
        AdBlocker.aggressive = f.aggressiveAdblock
    }

    /** Applies FLAG_SECURE from the block-screenshots flag. */
    private fun applyScreenshotFlag() {
        if (liveFlags.blockScreenshots) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    /** Applies the resolved per-site layout, preserving profile and global defaults. */
    private fun applyPerSiteDesktop(webView: WebView, url: String?) {
        val host = try {
            android.net.Uri.parse(url ?: "").host?.lowercase().orEmpty()
        } catch (e: Exception) {
            ""
        }
        val desktop = DesktopLayoutPolicy.enabledForHost(
            host = host,
            overrides = liveDesktopOverrides,
            mode = liveMode,
            forceDesktopMode = liveForceDesktopMode,
            desktopDefault = liveFlags.desktopDefault
        )
        modeManager.applyDesktopOverride(webView, liveMode, desktop)
    }

    /** Resolve the persisted viewport preference for a URL on either browser engine. */
    private fun wideLayoutEnabledForUrl(url: String): Boolean {
        val host = try {
            android.net.Uri.parse(url).host?.lowercase().orEmpty()
        } catch (_: Exception) {
            ""
        }
        return DesktopLayoutPolicy.enabledForHost(
            host = host,
            overrides = liveDesktopOverrides,
            mode = liveMode,
            forceDesktopMode = liveForceDesktopMode,
            desktopDefault = liveFlags.desktopDefault
        )
    }

    private fun applyWideLayoutToTab(tab: TabItem, enabled: Boolean, reload: Boolean = true) {
        tab.browserSession?.setWideLayout(enabled)
        if (reload) tab.browserSession?.reload()
    }

    /** Persist and publish the choice before a caller reloads the current page. */
    private suspend fun persistDesktopOverride(host: String, desktop: Boolean) {
        val normalizedHost = host.trim().trimEnd('.').lowercase()
        repository.setDesktopOverride(normalizedHost, desktop)
        liveDesktopOverrides = liveDesktopOverrides + (normalizedHost to desktop)
    }

    /**
     * Applies the per-site settings overrides for the given URL on top of the
     * mode settings. Called at WebView creation and before every navigation
     * (same call sites as applyPerSiteDesktop).
     *
     * Honest scope per toggle:
     * - JavaScript: real WebSettings toggle per WebView.
     * - Text zoom: real WebSettings.textZoom per WebView.
     * - Third-party cookies: real per-WebView CookieManager toggle.
     * - Images: loadsImagesAutomatically per WebView.
     * - Pop-ups: javaScriptCanOpenWindowsAutomatically per WebView.
     * - Ad-block is stored here for shouldInterceptRequest. Website camera,
     *   microphone and notification permissions are not granted by this build.
     * Wide layout stays in the existing per-site preference path.
     */
    private fun applyPerSiteSettings(
        webView: WebView,
        url: String?,
        override: com.click.browser.engine.SiteSettings? = null
    ) {
        val host = try {
            android.net.Uri.parse(url ?: "").host?.lowercase().orEmpty()
        } catch (e: Exception) {
            ""
        }
        if (host.isEmpty()) return
        // `override` carries the just-saved settings so the sheet's Apply
        // doesn't race the DataStore flow emission.
        val site = override ?: liveSiteSettings.forHost(host) ?: return
        val s = webView.settings
        site.javaScript?.let { s.javaScriptEnabled = it }
        site.textZoom?.let { s.textZoom = it }
        site.thirdPartyCookies?.let { allow ->
            try {
                android.webkit.CookieManager.getInstance()
                    .setAcceptThirdPartyCookies(webView, allow)
            } catch (_: Exception) {
            }
        }
        // Chrome-style v2: per-site images + pop-ups.
        site.images?.let {
            s.loadsImagesAutomatically = it
            s.blockNetworkImage = !it
        }
        site.popups?.let { allowPopups ->
            s.javaScriptCanOpenWindowsAutomatically = allowPopups
            try {
                s.setSupportMultipleWindows(allowPopups)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Per-site ad-block decision for a page host: site override wins,
     * otherwise the global adBlockerEnabled applies.
     */
    private fun isAdBlockEnabledForHost(host: String?): Boolean {
        if (host.isNullOrEmpty()) return adBlockerEnabledGlobal()
        val site = liveSiteSettings.forHost(host.lowercase())
        return site?.adBlock ?: adBlockerEnabledGlobal()
    }

    /** Reads the global ad-block toggle without touching composable state. */
    private fun adBlockerEnabledGlobal(): Boolean = liveAdBlockerEnabled

    /** Shared pretty error page used by both onReceivedError variants. */
    /** Extracts the lowercase host from a URL ("" on failure). */
    private fun hostOfUrl(url: String): String = try {
        android.net.Uri.parse(url).host?.lowercase().orEmpty()
    } catch (_: Exception) {
        ""
    }

    /** Recognizes valid Google OAuth authorization requests; account pages stay in the current tab. */
    private fun isGoogleAuthUrl(url: String): Boolean =
        GoogleAuthPolicy.shouldOpenInCustomTab(url)

    /**
     * Opens [url] in the system-provided Custom Tab for a recognized Google
     * OAuth authorization request, as required by Google's secure-browser policy.
     */
    private fun openInCustomTab(url: String) {
        try {
            val intent = androidx.browser.customtabs.CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            intent.launchUrl(this, android.net.Uri.parse(url))
        } catch (e: Exception) {
            // Fallback: open in external browser.
            try {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
            } catch (_: Exception) {
                android.widget.Toast.makeText(this, "Could not open sign-in page", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Returns true if [host] is an IPv4 address (e.g. 182.180.50.52).
     * IP-address hosts must NEVER be upgraded to HTTPS — most IP servers
     * have no TLS certificate, so the upgrade breaks them. HTTP is loaded
     * directly with a warning (not blocked).
     */
    private fun isIpAddress(host: String): Boolean {
        if (host.isBlank()) return false
        val parts = host.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() && part.all { it.isDigit() } &&
                try { part.toInt() in 0..255 } catch (_: Exception) { false }
        }
    }

    /**
     * HTTPS-Strict block page: shown instead of loading a plain-http URL
     * when HTTPS mode is Strict and the host has no exception. Offers a
     * one-tap HTTPS retry and points at the per-site exception list in
     * Settings → Privacy & Security.
     */
    private fun showHttpsBlockedPage(view: WebView?, blockedUrl: String, host: String) {
        val safeUrl = blockedUrl.take(300).replace("<", "&lt;").replace(">", "&gt;")
        val safeHost = host.take(120).replace("<", "&lt;").replace(">", "&gt;")
        val httpsTry = ("https://" + blockedUrl.removePrefix("http://")).replace("'", "%27")
        val customHtml = """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { background-color: #0f172a; color: #f8fafc; font-family: sans-serif; text-align: center; padding: 50px 24px; }
                    h1 { color: #f59e0b; font-size: 22px; }
                    p { color: #94a3b8; font-size: 15px; line-height: 1.5; }
                    code { color: #f8fafc; font-size: 13px; word-break: break-all; }
                    .btn { background-color: #3b82f6; border: none; color: white; padding: 12px 24px; border-radius: 8px; font-weight: bold; cursor: pointer; margin-top: 16px; font-size: 15px; }
                    .note { margin-top: 24px; font-size: 13px; color: #64748b; }
                </style>
            </head>
            <body>
                <h1>🔒 Blocked: insecure connection</h1>
                <p>HTTPS Strict mode blocked this page because it uses plain <b>http</b> (not encrypted).</p>
                <p><code>$safeUrl</code></p>
                <button class="btn" onclick="location.href='$httpsTry'">Try HTTPS anyway</button>
                <p class="note">Trust this site over http? Add <b>$safeHost</b> to the HTTPS-Strict exception list in Settings → Privacy &amp; Security.</p>
            </body>
            </html>
        """.trimIndent()
        view?.loadDataWithBaseURL(null, customHtml, "text/html", "UTF-8", null)
    }

    private fun showBrowserErrorPage(view: WebView?, description: String?, retryUrl: String?) {
        view?.loadDataWithBaseURL(null, browserErrorPageHtml(description, retryUrl), "text/html", "UTF-8", null)
    }

    /** Reads text aloud with the system TTS engine. */
    private fun speakOutLoud(text: String) {
        try {
            val engine = ttsEngine
            if (engine == null) {
                ttsEngine = TextToSpeech(this) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        ttsEngine?.language = Locale.getDefault()
                        ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "click_tts")
                    } else {
                        Toast.makeText(this, "TTS engine failed to start", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "click_tts")
            }
            Toast.makeText(this, "Reading page aloud…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "TTS not available: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Starts a device-credential/strong-biometric gate for a sensitive password action. */
    private fun promptPasswordAction(
        title: String,
        subtitle: String,
        onCancelled: () -> Unit = {},
        onAuthenticated: () -> Unit
    ): Boolean {
        if (!com.click.browser.engine.PrivateTabLock.canLock(this) || passwordAuthenticationInFlight) {
            return false
        }
        passwordAuthenticationInFlight = true
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    passwordAuthenticationInFlight = false
                    onAuthenticated()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    passwordAuthenticationInFlight = false
                    onCancelled()
                }
            }
        )
        val infoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            infoBuilder.setAllowedAuthenticators(com.click.browser.engine.PrivateTabLock.AUTHENTICATORS)
        } else {
            @Suppress("DEPRECATION")
            infoBuilder.setDeviceCredentialAllowed(true)
        }
        return try {
            prompt.authenticate(infoBuilder.build())
            true
        } catch (_: Exception) {
            passwordAuthenticationInFlight = false
            false
        }
    }

    /** Shows the AndroidX BiometricPrompt to unlock private tabs. */
    fun promptUnlockPrivateTabs() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    privateLockedFlow.value = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Stay locked — the user can tap Unlock to retry. No-op
                    // on user-cancel so we don't nag.
                }
            }
        )
        val infoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock private tabs")
            .setSubtitle("Authenticate to view your private tabs")
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            infoBuilder.setAllowedAuthenticators(com.click.browser.engine.PrivateTabLock.AUTHENTICATORS)
        } else {
            // API 29 and below: combined authenticators aren't supported —
            // fall back to the device-credential-allowed prompt.
            @Suppress("DEPRECATION")
            infoBuilder.setDeviceCredentialAllowed(true)
        }
        val info = infoBuilder.build()
        try {
            prompt.authenticate(info)
        } catch (_: Exception) {
            // Biometric stack hiccup — stay locked; retry via the button.
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // API 35+ enforces edge-to-edge for targetSdk 35+ — opt in and handle
        // WindowInsets in Compose so no chrome is hidden behind system bars.
        enableEdgeToEdge()

        if (V9Engine.isBootBlocked) {
            val recoveryModeManager = ModeManager(this)
            setContent {
                val scope = rememberCoroutineScope()
                var simpleModeSaved by rememberSaveable { mutableStateOf(false) }
                var isSaving by remember { mutableStateOf(false) }
                var recoveryMessage by remember { mutableStateOf<String?>(null) }

                MaterialTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Browser startup paused",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Click couldn't initialize the selected browser profile. Browsing is paused to avoid opening data from a different mode.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (simpleModeSaved) {
                                    "Simple mode is saved. Close and reopen Click to continue in a fresh process."
                                } else {
                                    "You can choose Simple mode below. Click will save that choice, then you can reopen the app."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            recoveryMessage?.let { message ->
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = message,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                            Button(
                                enabled = !isSaving,
                                onClick = {
                                    if (simpleModeSaved) {
                                        finishAffinity()
                                    } else {
                                        isSaving = true
                                        recoveryMessage = null
                                        scope.launch {
                                            try {
                                                recoveryModeManager.setMode(BrowserMode.SIMPLE)
                                                simpleModeSaved = true
                                            } catch (_: Exception) {
                                                recoveryMessage = "Click couldn't save Simple mode. Please try again."
                                            } finally {
                                                isSaving = false
                                            }
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    when {
                                        isSaving -> "Saving Simple mode…"
                                        simpleModeSaved -> "Close Click"
                                        else -> "Switch to Simple mode"
                                    }
                                )
                            }
                            if (!simpleModeSaved) {
                                TextButton(onClick = { finishAffinity() }) {
                                    Text("Exit")
                                }
                            }
                        }
                    }
                }
            }
            return
        }

        consumeOpenDownloadsIntent(intent)

        // News notification deep link (cold start): consume the article URL
        // once so the composable below opens it in a new tab. Skipped on
        // recreation (rotation / process death) — the URL was already
        // consumed or is preserved by the tab-restore path.
        if (savedInstanceState == null) {
            consumeNotificationUrl(intent)
        }

        modeManager = ModeManager(this)
        repository = BrowserRepository(this)
        playlistManager = PlaylistManager(this)
        // Launched from another app's link (Click is / was picked as default
        // browser): use the link as the first tab's URL so the user lands on
        // it instead of the homepage.
        val startViewUrl = externalViewUrl(intent)
        // Bundled ad/tracker filter lists (assets) + weekly remote updates.
        com.click.browser.engine.FilterListManager.init(this)
        // Translate language list (asset-overridable, built-in fallback).
        com.click.browser.engine.MlKitTranslator.loadLanguages(this)

        setContent {
            // First-run feature cards replace the repeated full-screen intro.
            var onboardingReady by rememberSaveable { mutableStateOf(false) }
            var showOnboarding by rememberSaveable { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            fun finishFirstRunOnboarding() {
                showOnboarding = false
                scope.launch {
                    dataStore.edit {
                        it[AppSettings.ONBOARDING_COMPLETED] = true
                        // The tour already explains Advance, so do not stack its old
                        // first-entry specification sheet immediately afterward.
                        it[AppSettings.ADVANCE_SPECS_SEEN] = true
                    }
                }
            }
            BackHandler(enabled = onboardingReady && showOnboarding) {
                finishFirstRunOnboarding()
            }
            val activeMode by modeManager.modeFlow.collectAsState(initial = V9Engine.bootMode)
            val appResumeCount by activityResumeCount.collectAsState()
            // Palette selection is shared across profiles; each mode keeps its own label and behavior.
            var lightThemeVariant by rememberSaveable { mutableStateOf(LightThemeVariant.CREAM) }
            var autoRotateThemes by rememberSaveable { mutableStateOf(false) }
            var wallpaperUri by remember { mutableStateOf<String?>(null) }
            val theme = remember(activeMode, lightThemeVariant) {
                ModeThemes.forMode(activeMode, dark = false, lightVariant = lightThemeVariant)
            }

            val themeColors = if (theme.dark) {
                darkColorScheme(
                    primary = theme.primary,
                    surface = theme.surface,
                    background = theme.background,
                    secondary = theme.secondary,
                    onBackground = theme.onBackground,
                    onSurface = theme.onSurface
                )
            } else {
                lightColorScheme(
                    primary = theme.primary,
                    surface = theme.surface,
                    background = theme.background,
                    secondary = theme.secondary,
                    onBackground = theme.onBackground,
                    onSurface = theme.onSurface
                )
            }

            // Browser Premium Feature States
            // Cold start with an external link: it becomes the first tab.
            val tabs = remember { mutableStateListOf<TabItem>(TabItem(url = startViewUrl ?: homeUrl(), title = "New Tab")) }
            var activeTabIndex by remember { mutableStateOf(0) }
            val currentTab = tabs.getOrNull(activeTabIndex) ?: TabItem(url = "about:blank")
            LaunchedEffect(activeTabIndex, currentTab.id, tabs.size) {
                tabs.forEachIndexed { index, tab ->
                    tab.browserSession?.setSelected(index == activeTabIndex)
                }
            }
            // Only address/search submissions request keyboard dismissal after page load;
            // do not hide keyboards opened by page forms or unrelated tab navigation.
            var pendingKeyboardDismissTabId by remember { mutableStateOf<String?>(null) }
            SideEffect {
                val (snapshot, active) = buildRestorableSnapshot(
                    tabs.map {
                        RestorableTabState(
                            it.url, it.title, it.isIncognito, it.engine, it.geckoSessionState
                        )
                    },
                    activeTabIndex
                )
                liveRestoreTabs = snapshot
                liveRestoreActiveIndex = active
            }

            var showTabsManager by remember { mutableStateOf(false) }
            // Chrome/Mises-style browser menu (bottom sheet) + recent tabs +
            // delete-browsing-data confirmation.
            var showBrowserMenu by remember { mutableStateOf(false) }
            // Customizable menu: user-defined order + hidden items.
            var menuOrder by remember {
                mutableStateOf(MenuCustomization.DEFAULT_ORDER)
            }
            var menuHidden by remember {
                mutableStateOf(setOf<MenuCustomization.MenuItemId>())
            }
            var showMenuCustomize by remember { mutableStateOf(false) }
            // Premium UI v2: bottom-left FAB feature menu (always visible).
            var showFeatureMenu by remember { mutableStateOf(false) }
            // Tab-close snackbar with UNDO (Premium UI v2).
            val snackbarHostState = remember { SnackbarHostState() }
            var lastClosedTab by remember { mutableStateOf<ClosedTabUndo?>(null) }
            var tabUndoSnackbarJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
            var showRecentTabs by remember { mutableStateOf(false) }
            var showDeleteBrowsingConfirm by remember { mutableStateOf(false) }
            // Bumps every time a tab thumbnail is captured → recomposes the
            // visual tab switcher if it happens to be open.
            var thumbnailVersion by remember { mutableStateOf(0) }
            val approvedExtensionOptionsBasesByTab = remember {
                androidx.compose.runtime.mutableStateMapOf<String, String>()
            }

            fun cancelPendingTabUndoSnackbar() {
                tabUndoSnackbarJob?.cancel()
                tabUndoSnackbarJob = null
                val currentSnackbar = snackbarHostState.currentSnackbarData
                if (currentSnackbar != null &&
                    currentSnackbar.visuals.message == "Tab closed" &&
                    currentSnackbar.visuals.actionLabel == "UNDO"
                ) {
                    currentSnackbar.dismiss()
                }
            }

            fun reopenClosedTab(closedTab: ClosedTab) {
                if (closedTab.mode != activeMode) return
                val pendingUndo = lastClosedTab
                if (pendingUndo?.url == closedTab.url && pendingUndo.mode == closedTab.mode) {
                    lastClosedTab = null
                    cancelPendingTabUndoSnackbar()
                }
                tabs.add(
                    TabItem(url = closedTab.url, title = closedTab.title, initialEngine = closedTab.engine)
                )
                activeTabIndex = tabs.size - 1
                RecentlyClosedTabs.remove(closedTab)
            }

            fun discardPendingTabUndoForMode(mode: BrowserMode) {
                val pendingUndoMode = lastClosedTab?.mode ?: return
                if (pendingUndoMode !in RecentlyClosedTabs.historyScopeModesFor(mode)) return
                lastClosedTab = null
                cancelPendingTabUndoSnackbar()
            }

            /**
             * Single close-tab path used by the top tab strip AND the visual
             * switcher: remembers the tab for "Recent tabs" (never private
             * ones), drops its thumbnail, and keeps one blank tab minimum.
             * Premium UI v2: also offers UNDO via a bottom snackbar.
             */
            fun closeTabAt(idx: Int) {
                if (idx !in tabs.indices) return
                val closed = tabs[idx]
                approvedExtensionOptionsBasesByTab.remove(closed.id)
                RecentlyClosedTabs.push(
                    title = closed.title,
                    url = closed.url,
                    isIncognito = closed.isIncognito,
                    mode = activeMode,
                    engine = closed.engine
                )
                TabThumbnailStore.remove(closed.id)
                // Drop the closed tab from live registries so cleanup doesn't
                // iterate stale, detached views or sessions.
                closed.browserSession?.let { session ->
                    liveBrowserSessions.remove(session)
                    try { session.detachFromParent() } catch (_: Exception) { }
                    try { session.destroy() } catch (_: Exception) { }
                }
                closed.takePendingGeckoSession()?.let { pending ->
                    runCatching { pending.close() }
                }
                closed.webView?.let { liveWebViews.remove(it) }
                // Remember for UNDO (not for private tabs — privacy first).
                val undoInfo: ClosedTabUndo? =
                    if (closed.isIncognito) null
                    else ClosedTabUndo(
                        closed.title, closed.url, idx.coerceAtMost(tabs.size - 1), closed.engine, activeMode
                    )
                if (tabs.size > 1) {
                    tabs.removeAt(idx)
                    activeTabIndex = activeTabIndexAfterClose(activeTabIndex, idx, tabs.size)
                } else {
                    tabs[0] = TabItem(url = homeUrl(), title = "New Tab")
                    activeTabIndex = 0
                }
                // Forgetful Browsing: if the closed tab was the last one
                // showing its site (non-incognito), the site's cookies and
                // web storage are wiped — unless the user undoes the close.
                val closedHost = ForgetfulBrowsing.hostOf(closed.url)
                val forgetCandidate = !closed.isIncognito &&
                    ForgetfulBrowsing.shouldForget(
                        liveForgetfulBrowsing,
                        liveForgetfulExceptions,
                        closedHost,
                        tabs.map { ForgetfulBrowsing.hostOf(it.url) }
                    )
                // Premium UI v2: "Tab closed" snackbar with UNDO (4s).
                if (undoInfo != null) {
                    cancelPendingTabUndoSnackbar()
                    lastClosedTab = undoInfo
                    tabUndoSnackbarJob = scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Tab closed",
                            actionLabel = "UNDO",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            lastClosedTab?.let { closedTab ->
                                val restored = TabItem(
                                    url = closedTab.url,
                                    title = closedTab.title.ifBlank { "New Tab" },
                                    initialEngine = closedTab.engine
                                )
                                val insertAt = closedTab.index.coerceIn(0, tabs.size)
                                tabs.add(insertAt, restored)
                                activeTabIndex = insertAt
                                RecentlyClosedTabs.removeForMode(closedTab.url, closedTab.mode)
                            }
                            lastClosedTab = null
                        } else if (
                            lastClosedTab == undoInfo &&
                            activeMode == undoInfo.mode &&
                            forgetCandidate &&
                            closedHost != null
                        ) {
                            // Snackbar dismissed / timed out without UNDO:
                            // the site is truly gone — forget it now.
                            lastClosedTab = null
                            ForgetfulBrowsing.forgetHost(closedHost)
                        }
                    }
                }
            }

            var isIncognitoMode by remember { mutableStateOf(false) }
            // Crash / force-close session restore state.
            var showRestoreDialog by remember { mutableStateOf(false) }
            var crashedTabs by remember { mutableStateOf<List<SavedTab>>(emptyList()) }
            var crashedActiveIndex by remember { mutableStateOf(0) }
            var sessionStartupReady by remember { mutableStateOf(false) }
            // Gated by the flags-loading effect below: the session saver and
            // the restore check must not read liveFlags.clearOnExit before
            // DataStore has delivered the real value (LaunchedEffect order
            // is not a reliable signal).
            var flagsLoaded by remember { mutableStateOf(false) }

            // LRU tab unloading (memory): with more than 10 tabs open,
            // destroy the oldest background (non-active, non-incognito)
            // browser sessions. Tab entries retain URL/title/engine and reload
            // through their selected provider. Incognito tabs are never unloaded.
            LaunchedEffect(tabs.size, activeTabIndex) {
                if (tabs.size > 10) {
                    val excess = tabs.size - 10
                    tabs.indices
                        .filter {
                            it != activeTabIndex && !tabs[it].isIncognito &&
                                tabs[it].browserSession != null
                        }
                        .take(excess)
                        .forEach { idx ->
                            val tab = tabs.getOrNull(idx) ?: return@forEach
                            val session = tab.browserSession ?: return@forEach
                            try {
                                session.detachFromParent()
                                tab.webView?.let { liveWebViews.remove(it) }
                                liveBrowserSessions.remove(session)
                                session.destroy()
                            } catch (_: Exception) { }
                            tab.attachBrowserSession(null)
                        }
                }
            }

            // Restore the current profile's own tabs after an intentional mode
            // switch; only an unclean exit shows the crash-recovery prompt.
            // The session saver waits for this decision so it cannot overwrite
            // a snapshot with the initial New Tab before it is restored.
            LaunchedEffect(Unit) {
                snapshotFlow { flagsLoaded }.first { it }
                val bootMode = V9Engine.bootMode
                val clean = SessionRestore.wasCleanExit(this@MainActivity, bootMode)
                val modeSwitchPending = SessionRestore.consumeModeSwitchRestore(
                    this@MainActivity, bootMode
                )
                val (saved, savedActive) = SessionRestore.loadSession(this@MainActivity, bootMode)
                // From here on, assume this run may die uncleanly.
                SessionRestore.markCleanExit(this@MainActivity, bootMode, false)
                when (
                    sessionRestoreStartupAction(
                        previousExitWasClean = clean,
                        modeSwitchRestorePending = modeSwitchPending,
                        savedTabCount = saved.size,
                        clearOnExit = liveFlags.clearOnExit,
                        externalStartUrlPresent = startViewUrl != null
                    )
                ) {
                    SessionRestoreStartupAction.AUTOMATIC_MODE_RESTORE -> {
                        tabs.clear()
                        tabs.addAll(saved.map {
                            TabItem(
                                url = it.url,
                                title = it.title,
                                initialEngine = it.engine,
                                initialGeckoSessionState = it.geckoSessionState
                            )
                        })
                        activeTabIndex = savedActive.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
                        sessionStartupReady = true
                    }
                    SessionRestoreStartupAction.CRASH_RECOVERY_PROMPT -> {
                        crashedTabs = saved
                        crashedActiveIndex = savedActive
                        showRestoreDialog = true
                    }
                    SessionRestoreStartupAction.NONE -> sessionStartupReady = true
                }
            }

            // Keeps the crash-restore snapshot fresh: debounced write of the
            // open restorable tabs (non-incognito, http/https only) into this
            // boot mode's profile store. With "clear data on exit" on, no
            // snapshot is kept at all (privacy first).
            // The in-memory mirror (liveRestoreTabs) is updated synchronously
            // on every tab change — only the DataStore write is debounced —
            // so v9SwitchMode() always persists a fresh snapshot.
            LaunchedEffect(Unit) {
                // Wait for experimental flags: reading liveFlags.clearOnExit
                // before DataStore loads would use the default (false).
                snapshotFlow { flagsLoaded }.first { it }
                snapshotFlow { sessionStartupReady }.first { it }
                snapshotFlow {
                    tabs.map {
                        RestorableTabState(
                            it.url, it.title, it.isIncognito, it.engine, it.geckoSessionState
                        )
                    } to activeTabIndex
                }
                    .collect { (tabStates, activeIdx) ->
                        val (snapshot, active) = buildRestorableSnapshot(tabStates, activeIdx)
                        // Biometric lock bookkeeping: does an incognito tab exist right now?
                        liveHasIncognitoTabs = tabStates.any { it.isIncognito }
                        delay(1000) // trailing-edge debounce: rapid edits collapse into one write
                        if (modeSwitchInProgress) return@collect
                        val bootMode = V9Engine.bootMode
                        if (liveFlags.clearOnExit) {
                            SessionRestore.clearSession(this@MainActivity, bootMode)
                            liveRestoreTabs = emptyList()
                            liveRestoreActiveIndex = 0
                        } else {
                            SessionRestore.saveSession(this@MainActivity, bootMode, snapshot, active)
                            // Re-arm crash detection: clearSession() resets the
                            // clean-exit flag when the user discards a restore
                            // offer, so every real save must mark unclean again.
                            SessionRestore.markCleanExit(this@MainActivity, bootMode, false)
                        }
                    }
            }
            var adBlockerEnabled by remember { mutableStateOf(true) }
            // Page transition animation: bumped on every main-frame navigation
            // start; drives a subtle fade+slide over the WebView (never blocks
            // loading, purely visual). Disabled via the Settings toggle.
            var pageTransitionTick by remember { mutableStateOf(0) }
            val filterListReadiness by com.click.browser.engine.FilterListManager.readinessFlow.collectAsState()
            // V9 Shield DNS-only service running state (for the home shield card).
            val shieldActive by com.click.browser.engine.V9VpnController.isRunning.collectAsState()
            var forceNightModeWebsites by remember { mutableStateOf(false) }
            // HTTPS mode: "off" | "standard" | "strict" (Brave-hardening).
            var httpsMode by remember { mutableStateOf("standard") }
            var httpsStrictExceptions by remember { mutableStateOf(emptyList<String>()) }
            var javaScriptEnabledGlobal by remember { mutableStateOf(true) }
            var dataSaverEnabled by remember { mutableStateOf(false) }
            // Advance Mode 5s intro animation on engine boot (default ON).
            var advanceIntroEnabled by remember { mutableStateOf(true) }
            // Sharp View: full-density WebView rendering (default ON).
            var sharpViewEnabled by remember { mutableStateOf(true) }
            // Brave-inspired privacy quick wins (DataStore-persisted below).
            var stripTrackingParams by remember { mutableStateOf(true) }
            var forgetfulBrowsing by remember { mutableStateOf(false) }
            var forgetfulExceptions by remember { mutableStateOf(setOf<String>()) }
            // Accessibility settings (Settings → Browser → Accessibility).
            var forceZoomEnabled by remember { mutableStateOf(false) }
            var readerModeEnabled by remember { mutableStateOf(false) }
            var reducedMotionEnabled by remember { mutableStateOf(false) }

            // Click does not request device location; the sheet explains that all site requests are blocked.
            var showLocationSettings by remember { mutableStateOf(false) }

            // Common overlays
            var showBookmarks by remember { mutableStateOf(false) }
            var showHistory by remember { mutableStateOf(false) }
            var showDownloads by remember { mutableStateOf(false) }
            val openDownloadsRequested by pendingOpenDownloads.collectAsState()
            LaunchedEffect(openDownloadsRequested) {
                if (openDownloadsRequested) {
                    showDownloads = true
                    pendingOpenDownloads.value = false
                }
            }
            var showPlaylist by remember { mutableStateOf(false) }
            var showStreamPlayer by remember { mutableStateOf(false) }
            // One-shot URL from the long-press menu ("Add to Playlist").
            var playlistSeedUrl by remember { mutableStateOf<String?>(null) }
            // Generic "background audio for web pages" setting (default off).
            var backgroundAudioEnabled by remember { mutableStateOf(false) }
            // In-app Home feed is a separate opt-in from background alerts.
            var homeNewsFeedEnabled by remember { mutableStateOf(false) }
            fun setHomeNewsFeedEnabled(enabled: Boolean) {
                homeNewsFeedEnabled = enabled
                scope.launch {
                    dataStore.edit { prefs ->
                        prefs[AppSettings.HOME_NEWS_FEED_ENABLED] = enabled
                    }
                }
            }
            // Background news notifications: opt-in (default OFF). When the
            // user enables them we schedule the battery-friendly WorkManager
            // check; disabling cancels it.
            var newsNotificationsEnabled by remember { mutableStateOf(false) }
            var newsTopics by remember { mutableStateOf(NewsNotifications.DEFAULT_TOPICS) }
            /**
             * Persists the opt-in and schedules the periodic news check.
             * Shared by the permission-grant callback and the pre-granted path.
             */
            fun enableNewsNotifications() {
                newsNotificationsEnabled = true
                scope.launch {
                    dataStore.edit { prefs ->
                        prefs[AppSettings.NEWS_NOTIFICATIONS_ENABLED] = true
                    }
                    NewsNotificationScheduler.schedule(this@MainActivity)
                }
                Toast.makeText(
                    this@MainActivity,
                    "News alerts on — you'll be notified about fresh stories",
                    Toast.LENGTH_SHORT
                ).show()
            }
            // Android 13+ runtime grant for POST_NOTIFICATIONS, requested only
            // when the user opts in to news alerts (never at startup).
            val newsPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    enableNewsNotifications()
                } else {
                    // Honest: without the grant there are no alerts — the
                    // toggle stays off and nothing is scheduled.
                    Toast.makeText(
                        this@MainActivity,
                        "Notifications need permission — allow them in App info → Notifications to get news alerts.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            fun syncV9ShieldFlag(expectedOn: Boolean) {
                scope.launch {
                    val confirmedRunning =
                        com.click.browser.engine.V9VpnController.awaitRunningState(expectedOn)
                    if (confirmedRunning == null) {
                        Toast.makeText(
                            this@MainActivity,
                            "DNS protection status not confirmed yet. Check V9 Shield again shortly.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    dataStore.edit {
                        it[com.click.browser.engine.AppSettings.V9_VPN_ENABLED] = confirmedRunning
                    }
                    Toast.makeText(
                        this@MainActivity,
                        if (expectedOn) "V9 Shield on · DNS-only protection"
                        else "V9 Shield off · DNS-only protection",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            // VPN permission launcher: VpnService.prepare() returns an intent
            // when permission is not yet granted. On RESULT_OK, start VPN.
            // Kilo fix: verify the service actually started before setting the
            // V9_VPN_ENABLED flag — start() is async (Intent to service), so
            // the flag must reflect the true running state, not the request.
            val vpnPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val started = com.click.browser.engine.V9VpnController.start(this@MainActivity)
                    if (!started) {
                        Toast.makeText(
                            this@MainActivity,
                            "Click VPN is active. Disconnect it before enabling V9 Shield.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        syncV9ShieldFlag(expectedOn = true)
                    }
                } else {
                    Toast.makeText(this@MainActivity, "DNS protection permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            // Toggles the V9 Shield DNS-only service from the home card.
            // Kilo fix: verify actual service state before updating the flag.
            fun toggleDnsShield() {
                val isOn = com.click.browser.engine.V9VpnController.isRunning.value
                if (isOn) {
                    com.click.browser.engine.V9VpnController.stop(this@MainActivity)
                    syncV9ShieldFlag(expectedOn = false)
                } else {
                    if (com.click.browser.engine.V9VpnController.isBlockedByClickVpn()) {
                        Toast.makeText(
                            this@MainActivity,
                            "Click VPN is active. Disconnect it before enabling V9 Shield.",
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }
                    val intent = com.click.browser.engine.V9VpnController.prepareIntent(this@MainActivity)
                    if (intent == null) {
                        if (!com.click.browser.engine.V9VpnController.start(this@MainActivity)) {
                            Toast.makeText(
                                this@MainActivity,
                                "Click VPN is active. Disconnect it before enabling V9 Shield.",
                                Toast.LENGTH_LONG
                            ).show()
                            return
                        }
                        syncV9ShieldFlag(expectedOn = true)
                    } else {
                        vpnPermissionLauncher.launch(intent)
                    }
                }
            }
            /**
             * Settings toggle handler: enabling on Android 13+ goes through
             * the runtime permission first (the toggle only flips once the
             * grant lands); disabling cancels the scheduled work.
             */
            fun setNewsNotificationsEnabled(wantOn: Boolean) {
                if (wantOn == newsNotificationsEnabled) return
                if (!wantOn) {
                    newsNotificationsEnabled = false
                    scope.launch {
                        dataStore.edit { prefs ->
                            prefs[AppSettings.NEWS_NOTIFICATIONS_ENABLED] = false
                        }
                        NewsNotificationScheduler.cancel(this@MainActivity)
                    }
                    return
                }
                val needsGrant = android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                if (needsGrant) {
                    newsPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    enableNewsNotifications()
                }
            }
            // News notification deep link: open the tapped article's URL in a
            // new tab (cold start via onCreate, warm via onNewIntent).
            val pendingNewsUrl = notificationOpenUrl.value
            LaunchedEffect(pendingNewsUrl) {
                if (pendingNewsUrl != null) {
                    notificationOpenUrl.value = null
                    tabs.add(TabItem(url = pendingNewsUrl, title = "New Tab"))
                    activeTabIndex = tabs.size - 1
                }
            }
            var showSettings by remember { mutableStateOf(false) }
            var showClickDiagnostics by remember { mutableStateOf(false) }
            var showFindInPageDialog by remember { mutableStateOf(false) }
            var findQuery by remember { mutableStateOf("") }

            // Tool modal sheets
            var showMusicDetails by remember { mutableStateOf(false) }
            var showVideoDetails by remember { mutableStateOf(false) }
            var showPdfDetails by remember { mutableStateOf(false) }
            var showImageDetails by remember { mutableStateOf(false) }
            var showQuickTogglesDialog by remember { mutableStateOf(false) }
            var showPostInstallGeckoSwitchConfirmation by remember { mutableStateOf(false) }
            var pendingGeckoSwitchTabId by remember { mutableStateOf<String?>(null) }
            var pendingInstalledExtensionName by remember { mutableStateOf<String?>(null) }
            var showInstalledAddonsPanel by remember { mutableStateOf(false) }
            var installedAddonPanelItems by remember { mutableStateOf<List<InstalledAddonPanelItem>>(emptyList()) }
            var installedAddonPanelLoading by remember { mutableStateOf(false) }
            var installedAddonPanelError by remember { mutableStateOf<String?>(null) }
            var installedAddonPanelRequestId by remember { mutableIntStateOf(0) }
            LaunchedEffect(geckoTabPopupState.value) {
                if (geckoTabPopupState.value != null) showInstalledAddonsPanel = false
            }
            fun refreshInstalledAddonPanel() {
                val requestId = ++installedAddonPanelRequestId
                installedAddonPanelLoading = true
                installedAddonPanelError = null
                try {
                    ExtensionBrowserRuntime.get(applicationContext).webExtensionController.list().accept(
                        { extensions ->
                            val itemResult = safeBuildInstalledAddonPanelItems {
                                extensions.orEmpty()
                                    .filterNot { it.isBuiltIn }
                                    .map { extension ->
                                        val metadata = extension.metaData
                                        val safeOptionsUrl = ExtensionInstallPolicy.safeExtensionOptionsPageUrl(
                                            metadata.baseUrl,
                                            metadata.optionsPageUrl
                                        )
                                        InstalledAddonPanelItem(
                                            id = extension.id,
                                            name = metadata.name?.takeIf(String::isNotBlank) ?: "Unnamed add-on",
                                            version = metadata.version.takeIf(String::isNotBlank) ?: "Unknown",
                                            enabled = metadata.enabled,
                                            policyBlockers = extensionPolicyBlockers(extension),
                                            description = metadata.description?.takeIf(String::isNotBlank),
                                            optionsPageUrl = safeOptionsUrl,
                                            optionsPageBaseUrl = metadata.baseUrl.takeIf { safeOptionsUrl != null },
                                            hasDeclaredOptionsPage = !metadata.optionsPageUrl.isNullOrBlank()
                                        )
                                    }
                                    .sortedBy { it.name.lowercase(Locale.ROOT) }
                            }
                            itemResult.fold(
                                onSuccess = { items ->
                                    runOnUiThread {
                                        if (requestId == installedAddonPanelRequestId) {
                                            installedAddonPanelItems = items
                                            installedAddonPanelLoading = false
                                            installedAddonPanelError = null
                                        }
                                    }
                                },
                                onFailure = {
                                    runOnUiThread {
                                        if (requestId == installedAddonPanelRequestId) {
                                            installedAddonPanelLoading = false
                                            installedAddonPanelError = "Couldn't load installed add-ons. Please try again."
                                        }
                                    }
                                }
                            )
                        },
                        { error ->
                            runOnUiThread {
                                if (requestId == installedAddonPanelRequestId) {
                                    installedAddonPanelLoading = false
                                    installedAddonPanelError = "Couldn't load installed add-ons: ${error?.message ?: "GeckoView error"}"
                                }
                            }
                        }
                    )
                } catch (error: Exception) {
                    installedAddonPanelLoading = false
                    installedAddonPanelError = "Couldn't load installed add-ons: ${error.message ?: "GeckoView error"}"
                }
            }
            var showWebViewSwitchConfirmation by remember { mutableStateOf(false) }
            val extensionStoreLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val data = result.data ?: return@rememberLauncherForActivityResult
                    val originTabId = data.getStringExtra(ExtensionBrowserActivity.EXTRA_RETURN_TAB_ID)
                    val installedIds = data.getStringArrayListExtra(
                        ExtensionBrowserActivity.EXTRA_INSTALLED_EXTENSION_IDS
                    ).orEmpty()
                    val selectedTab = tabs.getOrNull(activeTabIndex) ?: return@rememberLauncherForActivityResult
                    val currentUrl = (selectedTab.browserSession as? WebViewBrowserTabSession)?.webView?.url
                        ?.takeIf { it.isNotBlank() } ?: selectedTab.url
                    val shouldOfferSwitch = shouldOfferPostInstallGeckoSwitch(
                            originTabIsStillSelected = originTabId != null && selectedTab.id == originTabId,
                            hasEnabledInstalledExtension = installedIds.isNotEmpty(),
                            engine = selectedTab.engine,
                            isPrivate = selectedTab.isIncognito,
                            url = currentUrl
                        )
                    if (shouldOfferSwitch) {
                        pendingGeckoSwitchTabId = selectedTab.id
                        pendingInstalledExtensionName = data.getStringExtra(
                            ExtensionBrowserActivity.EXTRA_INSTALLED_EXTENSION_NAME
                        )
                        showPostInstallGeckoSwitchConfirmation = true
                    } else if (installedIds.isNotEmpty() && originTabId == selectedTab.id && !selectedTab.isIncognito) {
                        refreshInstalledAddonPanel()
                        showInstalledAddonsPanel = true
                    }
                }
            }
            var pendingWebViewSwitchTabId by remember { mutableStateOf<String?>(null) }
            var showPrivacyPolicy by remember { mutableStateOf(false) }
            var showAboutApp by remember { mutableStateOf(false) }
            var showLicenses by remember { mutableStateOf(false) }

            // AI chat + privacy guards
            var showAiChat by remember { mutableStateOf(false) }
            var pendingAiSelectedText by remember { mutableStateOf<PendingAiSelectedTextOffer?>(null) }
            var pendingSelectedTextTranslation by remember {
                mutableStateOf<PendingSelectedTextTranslation?>(null)
            }
            var showPrivacyGuards by remember { mutableStateOf(false) }
            // click:// internal pages (chrome://-style). Holds the page key or null.
            var showClickPage by remember { mutableStateOf<String?>(null) }
            // Games: full-screen player for a bundled offline mini-game (game id or null).
            var showGamePlayer by remember { mutableStateOf<String?>(null) }
            // Experimental flags (click://flags) — UI mirror of liveFlags.
            var flagsUi by remember { mutableStateOf(ExperimentalFlags()) }
            // Confirm-exit dialog (flag).
            var showExitConfirm by remember { mutableStateOf(false) }
            // V9 Shield and Power Mode intro animation states.
            var showV9Shield by remember { mutableStateOf(false) }
            // Click VPN screen (full IP tunnel via sing-box).
            var showVpn by remember { mutableStateOf(false) }
            // Help & Feedback screen.
            var showHelp by remember { mutableStateOf(false) }
            var showHackIntro by remember {
                mutableStateOf(intent.getBooleanExtra(V9Engine.EXTRA_HACK_INTRO, false))
            }
            // Advance Mode signature moment: full-screen 5s teal intro
            // after an Advance engine boot. Tap to skip.
            var showAdvanceIntro by remember {
                mutableStateOf(intent.getBooleanExtra(V9Engine.EXTRA_ADVANCE_INTRO, false))
            }
            // "About Advance Mode" specifications sheet.
            var showAdvanceSpecs by remember { mutableStateOf(false) }
            // First entry into Advance mode: show the specs sheet once
            // (after the intro overlay closes, if it is showing), so the
            // user learns what is special about Advance mode. The intro
            // overlay is full-screen, so the sheet waits for it.
            LaunchedEffect(activeMode, showAdvanceIntro, onboardingReady, showOnboarding) {
                if (onboardingReady && !showOnboarding && activeMode == BrowserMode.ADVANCED && !showAdvanceIntro) {
                    val seen = try {
                        dataStore.data.first()[AppSettings.ADVANCE_SPECS_SEEN] == true
                    } catch (_: Exception) { true }
                    if (!seen) {
                        try {
                            dataStore.edit { prefs ->
                                prefs[AppSettings.ADVANCE_SPECS_SEEN] = true
                            }
                        } catch (_: Exception) { }
                        showAdvanceSpecs = true
                    }
                }
            }
            // Password manager: save-offer dialog state.
            var showPasswordSaveDialog by remember { mutableStateOf(false) }
            var pendingPasswordSave by remember {
                mutableStateOf<PendingPasswordSaveOffer?>(null)
            }
            var pendingGeckoPasswordSave by remember {
                mutableStateOf<PendingGeckoPasswordSaveOffer?>(null)
            }
            // Password manager: saved-logins management screen.
            var showPasswordManager by remember { mutableStateOf(false) }
            val passwordManagerAuthenticated by passwordManagerAuthenticatedFlow.collectAsState(initial = false)
            fun requestPasswordManagerAccess(onAuthenticated: () -> Unit = {}) {
                val authAvailable = com.click.browser.engine.PrivateTabLock.canLock(this@MainActivity)
                if (!authAvailable) {
                    val reason = com.click.browser.engine.PrivateTabLock.unavailableReason(this@MainActivity)
                    Toast.makeText(
                        this@MainActivity,
                        "Set up a device lock to access saved passwords. $reason",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
                val started = promptPasswordAction(
                    title = "Unlock saved passwords",
                    subtitle = "Authenticate to view or use logins stored in Click"
                ) {
                    val canOpen = com.click.browser.engine.PasswordAccessPolicy.canOpenVault(
                        deviceAuthenticationAvailable =
                            com.click.browser.engine.PrivateTabLock.canLock(this@MainActivity),
                        deviceAuthenticated = true
                    )
                    if (canOpen) {
                        passwordManagerAuthenticatedFlow.value = true
                        showPasswordManager = true
                        onAuthenticated()
                    } else {
                        passwordManagerAuthenticatedFlow.value = false
                    }
                }
                if (!started) {
                    Toast.makeText(
                        this@MainActivity,
                        "Authentication could not be started. Try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            // Cookie manager: per-site cookie viewer.
            var showCookieManager by remember { mutableStateOf(false) }
            // All-sites settings list (Settings -> Site settings).
            var showSiteSettingsList by remember { mutableStateOf(false) }
            var siteSettingsReturnToAdvancedSecurity by remember { mutableStateOf(false) }
            // Settings suite: tab groups, accessibility, language, downloads, reports.
            var showTabGroups by remember { mutableStateOf(false) }
            var showAccessibility by remember { mutableStateOf(false) }
            var showLanguage by remember { mutableStateOf(false) }
            var showDownloadSettings by remember { mutableStateOf(false) }
            var pendingBrowserDownload by remember { mutableStateOf<PendingBrowserDownload?>(null) }
            var downloadDestination by remember { mutableStateOf(DownloadSettings.LOCATION_PHONE) }
            var customDownloadFolderAvailable by remember { mutableStateOf(false) }
            var showReports by remember { mutableStateOf(false) }
            // Privacy & Security suite (Google Sign-In, passwords, Safety Check, autofill, cards).
            var showPrivacySecurity by remember { mutableStateOf(false) }
            var showAdvancedSecurity by remember { mutableStateOf(false) }
            var showPaymentMethods by remember { mutableStateOf(false) }
            // Per-site settings sheet (opened from the lock icon in the address bar).
            var showSiteSettings by remember { mutableStateOf(false) }
            var websiteMicrophoneConsent by remember {
                mutableStateOf<WebsiteMicrophoneConsentUiState?>(null)
            }
            var websiteMicrophoneRecovery by remember {
                mutableStateOf<WebsiteMicrophoneRecoveryUiState?>(null)
            }
            var pendingWebsiteMicrophoneAndroidResult by remember {
                mutableStateOf<PendingWebsiteMicrophoneAndroidResult?>(null)
            }
            val pendingWebViewMicrophoneRequestIds = remember {
                java.util.WeakHashMap<android.webkit.PermissionRequest, String>()
            }
            val websiteMicrophoneAndroidPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                val pendingResult = pendingWebsiteMicrophoneAndroidResult
                pendingWebsiteMicrophoneAndroidResult = null
                pendingResult?.onResult?.invoke(granted)
            }
            var showClearSiteDataConfirm by remember { mutableStateOf(false) }
            var clearSiteDataInProgress by remember { mutableStateOf(false) }
            var browserSessionGeneration by remember { mutableStateOf(0) }
            // Mode-switch confirmation: pending target mode, null = no dialog.
            // Mode changes require a process restart (WebView data dir is pinned
            // at boot), so the user must confirm before switching.
            var pendingModeSwitch by remember { mutableStateOf<BrowserMode?>(null) }
            // Built-in engines (Safe Browsing / Translate / PDF)
            var showTranslateSheet by remember { mutableStateOf(false) }
            var pdfOfferUrl by remember { mutableStateOf<String?>(null) }
            var viewingPdfFile by remember { mutableStateOf<java.io.File?>(null) }
            var pdfDownloading by remember { mutableStateOf(false) }
            val safeBrowsingHit by com.click.browser.engine.SafeBrowsingManager.pendingHit
                .collectAsState(initial = null)
            // Tamper detection (decompile guard): release builds verify the
            // signing certificate on start; mismatch disables AI chat.
            var tamperBlocked by remember { mutableStateOf(false) }
            var showTamperDialog by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                tamperBlocked = !TamperCheck.isReleaseSignatureValid(this@MainActivity)
                if (tamperBlocked) showTamperDialog = true
            }
            fun selectedTextPageUrl(tab: TabItem): String = when (tab.engine) {
                BrowserTabEngine.WEBVIEW -> tab.webView?.url?.takeIf { it.isNotBlank() } ?: tab.url
                BrowserTabEngine.GECKOVIEW -> tab.url
            }
            fun activeSelectedTextSource(
                sourceTabId: String,
                sourceEngine: BrowserTabEngine,
                sourcePageUrl: String
            ): TabItem? {
                val activeTab = tabs.getOrNull(activeTabIndex) ?: return null
                if (activeTab.id != sourceTabId || activeTab.engine != sourceEngine || activeTab.isIncognito ||
                    selectedTextPageUrl(activeTab) != sourcePageUrl ||
                    !(sourcePageUrl.startsWith("http://", ignoreCase = true) ||
                        sourcePageUrl.startsWith("https://", ignoreCase = true))
                ) return null
                return activeTab
            }
            fun openSelectedTextForAi(
                selection: AiSelectedTextPolicy.PreparedSelection,
                sourceTabId: String,
                sourcePageUrl: String
            ) {
                if (tamperBlocked) {
                    showTamperDialog = true
                    return
                }
                val sourceTab = activeSelectedTextSource(
                    sourceTabId = sourceTabId,
                    sourceEngine = selection.sourceEngine,
                    sourcePageUrl = sourcePageUrl
                ) ?: return
                val verifiedSelection = AiSelectedTextPolicy.prepareSelection(
                    rawText = selection.text,
                    engine = sourceTab.engine,
                    isPrivateTab = sourceTab.isIncognito
                ) ?: return
                pendingAiSelectedText = PendingAiSelectedTextOffer(
                    selectionId = java.util.UUID.randomUUID().toString(),
                    tabId = sourceTabId,
                    pageUrl = sourcePageUrl,
                    selection = verifiedSelection.copy(
                        wasTruncated = selection.wasTruncated || verifiedSelection.wasTruncated
                    )
                )
                showAiChat = true
            }
            fun openSelectedTextForTranslation(
                selection: AiSelectedTextPolicy.PreparedSelection,
                sourceTabId: String,
                sourcePageUrl: String
            ) {
                val sourceTab = activeSelectedTextSource(
                    sourceTabId = sourceTabId,
                    sourceEngine = selection.sourceEngine,
                    sourcePageUrl = sourcePageUrl
                ) ?: return
                if (!AiSelectedTextPolicy.canOfferSelection(
                        engine = sourceTab.engine,
                        isPrivateTab = sourceTab.isIncognito
                    )
                ) return
                val verifiedSelection = AiSelectedTextPolicy.prepareSelection(
                    rawText = selection.text,
                    engine = sourceTab.engine,
                    isPrivateTab = sourceTab.isIncognito
                ) ?: return
                pendingSelectedTextTranslation = PendingSelectedTextTranslation(
                    selectionId = java.util.UUID.randomUUID().toString(),
                    tabId = sourceTabId,
                    pageUrl = sourcePageUrl,
                    selection = verifiedSelection.copy(
                        wasTruncated = selection.wasTruncated || verifiedSelection.wasTruncated
                    )
                )
                showTranslateSheet = true
            }
            LaunchedEffect(
                pendingAiSelectedText?.selectionId,
                currentTab.id,
                currentTab.url,
                currentTab.engine,
                currentTab.isIncognito
            ) {
                val pending = pendingAiSelectedText ?: return@LaunchedEffect
                val stillEligible = AiSelectedTextPolicy.canOfferSelection(
                    engine = currentTab.engine,
                    isPrivateTab = currentTab.isIncognito
                )
                if (pending.tabId != currentTab.id || pending.selection.sourceEngine != currentTab.engine ||
                    pending.pageUrl != selectedTextPageUrl(currentTab) || !stillEligible
                ) {
                    pendingAiSelectedText = null
                    showAiChat = false
                }
            }
            LaunchedEffect(
                pendingSelectedTextTranslation?.selectionId,
                currentTab.id,
                selectedTextPageUrl(currentTab),
                currentTab.engine,
                currentTab.isIncognito
            ) {
                val pending = pendingSelectedTextTranslation ?: return@LaunchedEffect
                val stillEligible = activeSelectedTextSource(
                    sourceTabId = pending.tabId,
                    sourceEngine = pending.selection.sourceEngine,
                    sourcePageUrl = pending.pageUrl
                ) != null && AiSelectedTextPolicy.canOfferSelection(
                    engine = currentTab.engine,
                    isPrivateTab = currentTab.isIncognito
                )
                if (!stillEligible) {
                    pendingSelectedTextTranslation = null
                    showTranslateSheet = false
                }
            }
            var aiApiKey by remember { mutableStateOf("") }
            var aiProvider by remember { mutableStateOf("groq") }
            var aiModel by remember { mutableStateOf("") }
            // DNT + GPC privacy signals (opt-in, default off).
            var dntEnabled by remember { mutableStateOf(false) }
            var gpcEnabled by remember { mutableStateOf(false) }
            var secureDnsEnabled by remember { mutableStateOf(false) }
            // Biometric private-tab lock + text scaling (accessibility).
            var biometricLockEnabled by remember { mutableStateOf(false) }
            var hasStrongBiometric by remember { mutableStateOf(false) }
            var globalTextScale by remember { mutableStateOf(com.click.browser.engine.TextScaleStore.DEFAULT) }
            var hostTextScales by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
            var showTextScaleSheet by remember { mutableStateOf(false) }
            var sheetScale by remember { mutableStateOf(com.click.browser.engine.TextScaleStore.DEFAULT) }
            val privateLocked by privateLockedFlow.collectAsState()
            var webrtcTestRunning by remember { mutableStateOf(false) }
            var webrtcResult by remember { mutableStateOf<WebRtcTestResult?>(null) }
            var webrtcRunId by remember { mutableStateOf<String?>(null) }


            // Dev tools states
            var deviceEmulatorMode by remember { mutableStateOf("Mobile") } // Mobile, Tablet, Desktop
            var pageLoadTime by remember { mutableStateOf(0L) }
            var lastPageStart by remember { mutableStateOf(0L) }
            var showDebugOverlay by remember { mutableStateOf(true) }
            // DevTools log state lives at Activity level (devToolsLogs etc.)
            // so WebView callbacks and this UI can never diverge. Local
            // aliases keep the existing code below unchanged.
            val logs = devToolsLogs
            val networkRequests = devToolsNetwork
            val domHtml: String by devToolsDomHtml
            val sourcesList = devToolsSources

            // Power-mode desktop layout controls. The system WebView User-Agent
            // is used in every profile; desktop layout does not impersonate a device.
            var forceDesktopMode by remember { mutableStateOf(false) }
            // Long-press context menu (links + images on web pages).
            // Set from the WebView's OnLongClickListener via requestFocusNodeHref.
            var longPressLinkUrl by remember { mutableStateOf<String?>(null) }
            var longPressImageUrl by remember { mutableStateOf<String?>(null) }
            var showLongPressMenu by remember { mutableStateOf(false) }

            // DevTools panel tab (0=Elements, 1=Console, 2=Network, 3=Sources, 4=Device)
            var devToolsTab by remember { mutableStateOf(0) }
            // DevTools bottom sheet visibility — the panel is a dismissible sheet
            // so the website stays visible (never a fixed top overlay).
            var showDevToolsSheet by remember { mutableStateOf(false) }
            // Live viewport/device facts for the DevTools Device tab.
            var devToolsDeviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }

            LaunchedEffect(
                showDevToolsSheet,
                activeMode,
                activeTabIndex,
                currentTab.engine,
                currentTab.browserSession
            ) {
                val capture = showDevToolsSheet &&
                    DevToolsAvailabilityPolicy.canCapture(activeMode, currentTab.engine)
                if (capture) {
                    if (devToolsTargetWebView !== currentTab.webView) {
                        devToolsLogs.clear()
                        devToolsNetwork.clear()
                    }
                    devToolsTargetWebView = currentTab.webView
                    devToolsCaptureEnabled = true
                } else {
                    devToolsCaptureEnabled = false
                    devToolsTargetWebView = null
                }
            }

            // Full-view / immersive browsing: MANUAL fullscreen toggle only.
            // (Prince: no auto-hide on scroll — user control via the drawer toggle.)
            var immersiveMode by remember { mutableStateOf(false) }
            var videoFullscreenActive by remember { mutableStateOf(false) }
            LaunchedEffect(currentTab.id) {
                if (videoFullscreenActive) runCatching { exitFullscreenVideo?.invoke() }
                videoFullscreenActive = false
                exitFullscreenVideo = null
            }

            // These page tools are backed by android.webkit.WebView. Quick Toggles
            // remains open on GeckoView and disables only the WebView-only actions.
            LaunchedEffect(
                currentTab.engine,
                showCookieManager,
                showTextScaleSheet,
                showDevToolsSheet,
                activeMode,
                currentTab.url
            ) {
                if (currentTab.engine == BrowserTabEngine.GECKOVIEW) {
                    val unavailable = when {
                        showCookieManager -> { showCookieManager = false; "Cookie manager" }
                        showTextScaleSheet -> { showTextScaleSheet = false; "Page text zoom" }
                        showDevToolsSheet -> { showDevToolsSheet = false; "DevTools" }
                        else -> null
                    }
                    unavailable?.let {
                        Toast.makeText(
                            this@MainActivity,
                            "$it is not yet connected to GeckoView; WebView data/settings were not used.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            // Pull-to-refresh state for web pages.
            var isRefreshing by remember { mutableStateOf(false) }
            // Per-site layout preference (true or false override, persisted per host).
            val desktopOverrides by repository.desktopOverridesFlow.collectAsState(initial = emptyMap())
            // Per-site settings overrides (persisted per host, per engine mode).
            val siteSettings by repository.siteSettingsFlow.collectAsState(initial = emptyMap())
            val websiteMicrophonePermissions by repository.websiteMicrophonePermissionsFlow
                .collectAsState(initial = emptyMap())
            val latestWebsiteMicrophonePermissions = rememberUpdatedState(websiteMicrophonePermissions)

            LaunchedEffect(
                tabs.map { it.id },
                websiteMicrophoneConsent?.requestId,
                pendingWebsiteMicrophoneAndroidResult?.requestId,
                websiteMicrophoneRecovery?.tabId
            ) {
                val consent = websiteMicrophoneConsent
                if (consent != null && tabs.none { it.id == consent.tabId }) {
                    websiteMicrophoneConsent = null
                    runCatching { consent.onDismiss() }
                }
                val pending = pendingWebsiteMicrophoneAndroidResult
                if (pending != null && tabs.none { it.id == pending.tabId }) {
                    pendingWebsiteMicrophoneAndroidResult = null
                    runCatching { pending.onCancel() }
                }
                val recovery = websiteMicrophoneRecovery
                if (recovery != null && tabs.none { it.id == recovery.tabId }) {
                    websiteMicrophoneRecovery = null
                }
            }

            fun handleWebsiteMicrophoneRequest(
                tab: TabItem?,
                requestedOrigin: String,
                grant: () -> Unit,
                reject: () -> Unit,
                requestId: String = java.util.UUID.randomUUID().toString()
            ) {
                val origin = canonicalWebsiteMicrophoneOrigin(requestedOrigin)
                if (origin == null || tab == null || !tabs.contains(tab)) {
                    reject()
                    return
                }
                val privateTab = tab.isIncognito
                val decision = if (privateTab) {
                    tab.privateWebsiteMicrophonePermissions[origin]
                } else {
                    latestWebsiteMicrophonePermissions.value[origin]
                }

                fun requestAndroidPermissionAfterConsent() {
                    if (!tabs.contains(tab)) {
                        reject()
                        return
                    }
                    val alreadyGranted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (alreadyGranted) {
                        runCatching { grant() }.onFailure { runCatching { reject() } }
                        return
                    }
                    if (pendingWebsiteMicrophoneAndroidResult != null) {
                        reject()
                        return
                    }
                    pendingWebsiteMicrophoneAndroidResult = PendingWebsiteMicrophoneAndroidResult(
                        requestId = requestId,
                        tabId = tab.id,
                        onResult = { granted ->
                            if (!tabs.contains(tab)) {
                                runCatching { reject() }
                            } else if (granted) {
                                runCatching { grant() }.onFailure { runCatching { reject() } }
                            } else {
                                runCatching { reject() }
                                websiteMicrophoneRecovery = WebsiteMicrophoneRecoveryUiState(
                                    origin = origin,
                                    tabId = tab.id,
                                    privateTab = privateTab
                                )
                            }
                        },
                        onCancel = { runCatching { reject() } }
                    )
                    try {
                        websiteMicrophoneAndroidPermissionLauncher.launch(
                            android.Manifest.permission.RECORD_AUDIO
                        )
                    } catch (_: Exception) {
                        if (pendingWebsiteMicrophoneAndroidResult?.requestId == requestId) {
                            pendingWebsiteMicrophoneAndroidResult = null
                        }
                        runCatching { reject() }
                        websiteMicrophoneRecovery = WebsiteMicrophoneRecoveryUiState(
                            origin = origin,
                            tabId = tab.id,
                            privateTab = privateTab
                        )
                    }
                }

                fun saveDecision(allowed: Boolean) {
                    if (privateTab) {
                        tab.privateWebsiteMicrophonePermissions[origin] = allowed
                    } else {
                        scope.launch {
                            repository.setWebsiteMicrophonePermission(origin, allowed)
                        }
                    }
                }

                when (decision) {
                    false -> reject()
                    true -> requestAndroidPermissionAfterConsent()
                    null -> {
                        if (websiteMicrophoneConsent != null) {
                            reject()
                            return
                        }
                        val host = android.net.Uri.parse(origin).host ?: origin
                        websiteMicrophoneConsent = WebsiteMicrophoneConsentUiState(
                            requestId = requestId,
                            tabId = tab.id,
                            host = host,
                            privateTab = privateTab,
                            onAllow = {
                                if (websiteMicrophoneConsent?.requestId == requestId) {
                                    websiteMicrophoneConsent = null
                                    if (tabs.contains(tab)) {
                                        saveDecision(true)
                                        requestAndroidPermissionAfterConsent()
                                    } else {
                                        runCatching { reject() }
                                    }
                                }
                            },
                            onBlock = {
                                if (websiteMicrophoneConsent?.requestId == requestId) {
                                    websiteMicrophoneConsent = null
                                    saveDecision(false)
                                }
                                runCatching { reject() }
                            },
                            onDismiss = {
                                if (websiteMicrophoneConsent?.requestId == requestId) {
                                    websiteMicrophoneConsent = null
                                }
                                runCatching { reject() }
                            }
                        )
                    }
                }
            }

            fun cancelWebsiteMicrophoneRequest(requestId: String) {
                if (websiteMicrophoneConsent?.requestId == requestId) {
                    websiteMicrophoneConsent = null
                }
                val pending = pendingWebsiteMicrophoneAndroidResult
                if (pending?.requestId == requestId) {
                    pendingWebsiteMicrophoneAndroidResult = null
                }
            }

            fun openWebsiteMicrophoneAndroidSettings() {
                runCatching {
                    this@MainActivity.startActivity(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", packageName, null)
                        }
                    )
                }.onFailure {
                    Toast.makeText(
                        this@MainActivity,
                        "Open Click's app settings in Android to enable microphone access.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            val adBlockStatus = com.click.browser.engine.AdBlockStatusPolicy.forPage(
                engine = currentTab.engine,
                globalEnabled = adBlockerEnabled,
                siteOverride = siteSettings.forHost(hostOfUrl(currentTab.url))?.adBlock,
                readiness = filterListReadiness
            )

            /**
             * Premium UI v2: wires the 32 bottom-left FAB menu features to the
             * existing screens/actions. Reuses current handlers — no feature is
             * reimplemented. Closes the FAB panel first.
             */
            fun handleFeature(id: FeatureId) {
                showFeatureMenu = false
                val capability = ModeCapabilityRegistry.availability(
                    feature = id,
                    mode = activeMode,
                    engine = currentTab.engine,
                    hasLoadedPage = currentTab.url.isNotBlank() && currentTab.url != "about:blank"
                )
                if (!capability.visible) return
                if (!capability.enabled) {
                    Toast.makeText(
                        this@MainActivity,
                        capability.explanation ?: "This action is not available in the current mode.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
                when (id) {
                    FeatureId.HISTORY -> showHistory = true
                    FeatureId.SETTINGS -> showSettings = true
                    FeatureId.STORAGE, FeatureId.CLEAR_DATA -> showDeleteBrowsingConfirm = true
                    FeatureId.PASSWORDS -> {
                        requestPasswordManagerAccess {
                            if (currentTab.engine == BrowserTabEngine.GECKOVIEW) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Saved logins are available; page autofill/save capture is not wired to GeckoView yet.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                    FeatureId.COOKIES -> showCookieManager = true
                    FeatureId.TOOLS -> showQuickTogglesDialog = true
                    FeatureId.DEVTOOLS -> {
                        // Opens the DevTools bottom sheet (website stays visible).
                        // The FPS diagnostics overlay has its own drawer toggle.
                        showDevToolsSheet = true
                    }
                    FeatureId.DOWNLOADS -> showDownloads = true
                    FeatureId.BOOKMARKS -> showBookmarks = true
                    FeatureId.AI_CHAT -> {
                        // Same tamper gate as the drawer entry (Play-policy critical).
                        if (tamperBlocked) showTamperDialog = true else showAiChat = true
                    }
                    FeatureId.TRANSLATE -> {
                        val url = currentTab.url
                        if (url == "about:blank" || !url.startsWith("http")) {
                            Toast.makeText(this@MainActivity, "Open a page first to translate it.", Toast.LENGTH_SHORT).show()
                        } else {
                            // On-device ML Kit translation sheet (models download
                            // on demand — no Google Translate proxy tab anymore).
                            showTranslateSheet = true
                        }
                    }
                    FeatureId.DESKTOP -> {
                        val host = try { android.net.Uri.parse(currentTab.url).host?.lowercase().orEmpty() } catch (_: Exception) { "" }
                        scope.launch {
                            if (host.isEmpty() || currentTab.url == "about:blank") {
                                Toast.makeText(this@MainActivity, "Open a page first.", Toast.LENGTH_SHORT).show()
                            } else {
                                val isWideLayout = DesktopLayoutPolicy.enabledForHost(
                                    host, liveDesktopOverrides, activeMode,
                                    forceDesktopMode, liveFlags.desktopDefault
                                )
                                persistDesktopOverride(host, !isWideLayout)
                                val nextWideLayout = DesktopLayoutPolicy.enabledForHost(
                                    host, liveDesktopOverrides, activeMode,
                                    forceDesktopMode, liveFlags.desktopDefault
                                )
                                applyWideLayoutToTab(currentTab, nextWideLayout)
                                Toast.makeText(
                                    this@MainActivity,
                                    if (nextWideLayout) "Wide layout ON" else "Wide layout OFF",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    FeatureId.NEW_TAB -> {
                        tabs.add(TabItem(url = homeUrl(), title = "New Tab"))
                        activeTabIndex = tabs.size - 1
                    }
                    FeatureId.PRIVATE_TAB -> {
                        tabs.add(TabItem(url = "about:blank", title = "Private Tab", isIncognito = true))
                        activeTabIndex = tabs.size - 1
                        Toast.makeText(this@MainActivity, "Private tab opened — history is not recorded.", Toast.LENGTH_SHORT).show()
                    }
                    FeatureId.TABS -> showTabsManager = true
                    FeatureId.RECENT_TABS -> showRecentTabs = true
                    FeatureId.SHARE -> {
                        val url = currentTab.url
                        if (url == "about:blank") {
                            Toast.makeText(this@MainActivity, "Nothing to share yet.", Toast.LENGTH_SHORT).show()
                        } else {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, currentTab.title)
                                putExtra(Intent.EXTRA_TEXT, "${currentTab.title}\n$url")
                            }
                            startActivity(Intent.createChooser(share, "Share page via"))
                        }
                    }
                    FeatureId.FIND_IN_PAGE -> {
                        findQuery = ""
                        showFindInPageDialog = true
                    }
                    FeatureId.ADBLOCK -> {
                        adBlockerEnabled = !adBlockerEnabled
                        Toast.makeText(
                            this@MainActivity,
                            if (adBlockerEnabled) "AdBlock ON" else "AdBlock OFF",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // Reader mode is real: AccessibilityJs.READER_MODE_JS is
                    // injected on page load when liveReaderMode is true
                    // (see the WebViewClient). Toggle + persist + reload.
                    FeatureId.READER -> {
                        readerModeEnabled = !readerModeEnabled
                        scope.launch {
                            dataStore.edit { prefs ->
                                prefs[AppSettings.READER_MODE] = readerModeEnabled
                            }
                        }
                        currentTab.browserSession?.reload()
                        Toast.makeText(
                            this@MainActivity,
                            if (readerModeEnabled) "Reader mode ON" else "Reader mode OFF",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // Real screenshot: capture the visible WebView into
                    // Pictures/ClickBrowser via MediaStore (no permission
                    // needed on API 29+).
                    FeatureId.SCREENSHOT -> {
                        val wv = currentTab.webView
                        if (wv == null || wv.width == 0 || wv.height == 0) {
                            Toast.makeText(this@MainActivity, "Nothing to capture yet.", Toast.LENGTH_SHORT).show()
                        } else {
                            try {
                                val bmp = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                                wv.draw(android.graphics.Canvas(bmp))
                                val name = "Click_${System.currentTimeMillis()}.png"
                                val values = android.content.ContentValues().apply {
                                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ClickBrowser")
                                }
                                val uri = contentResolver.insert(
                                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                                )
                                if (uri != null) {
                                    contentResolver.openOutputStream(uri)?.use { out ->
                                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Screenshot saved to Pictures/ClickBrowser",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(this@MainActivity, "Could not save screenshot.", Toast.LENGTH_SHORT).show()
                                }
                                bmp.recycle()
                            } catch (_: Exception) {
                                Toast.makeText(this@MainActivity, "Screenshot failed.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    FeatureId.SAVE_PDF -> {
                        val geckoSession = currentTab.browserSession as? GeckoBrowserTabSession
                        if (geckoSession != null) {
                            runCatching { geckoSession.session.printPageContent() }
                                .onFailure {
                                    Toast.makeText(this@MainActivity, "Gecko print could not start.", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            val printManager = getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
                            val adapter = currentTab.webView?.createPrintDocumentAdapter("Click Browser Print Job")
                            if (printManager != null && adapter != null) {
                                printManager.print("Click Browser Document", adapter, android.print.PrintAttributes.Builder().build())
                            } else {
                                Toast.makeText(this@MainActivity, "Printing not available.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    // Real Add to Home: pinned shortcut to the current page
                    // via ShortcutManagerCompat. The launcher asks the user
                    // to confirm; tapping the shortcut opens the URL here
                    // (ACTION_VIEW is handled in onNewIntent).
                    FeatureId.ADD_HOME -> {
                        val url = currentTab.url
                        if (url.isBlank() || url == "about:blank") {
                            Toast.makeText(this@MainActivity, "Open a page first.", Toast.LENGTH_SHORT).show()
                        } else if (!androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(this@MainActivity)) {
                            Toast.makeText(this@MainActivity, "Home shortcuts not supported on this launcher.", Toast.LENGTH_SHORT).show()
                        } else {
                            try {
                                val shortcutIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                                    setPackage(packageName)
                                }
                                val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(
                                    this@MainActivity, "click_home_${url.hashCode()}"
                                )
                                    .setShortLabel(currentTab.title.take(20).ifBlank { url })
                                    .setLongLabel(currentTab.title.ifBlank { url })
                                    .setIcon(
                                        androidx.core.graphics.drawable.IconCompat.createWithResource(
                                            this@MainActivity, R.mipmap.ic_launcher
                                        )
                                    )
                                    .setIntent(shortcutIntent)
                                    .build()
                                androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(
                                    this@MainActivity, shortcut, null
                                )
                            } catch (_: Exception) {
                                Toast.makeText(this@MainActivity, "Could not add shortcut.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    FeatureId.SITE_INFO -> {
                        showSiteSettings = true
                    }
                    FeatureId.PRIVACY_GUARDS -> showPrivacyGuards = true
                    // V9: Shield screen (built-in VPN and DNS controls).
                    FeatureId.V9_SHIELD -> showV9Shield = true
                    FeatureId.HELP_FEEDBACK -> showHelp = true
                    FeatureId.FULLSCREEN -> immersiveMode = !immersiveMode
                    FeatureId.TEXT_SIZE -> {
                        // Open the text-size sheet: live slider + optional
                        // per-site persistence (accessibility).
                        sheetScale = com.click.browser.engine.TextScaleStore.clamp(
                            currentTab.webView?.settings?.textZoom ?: liveGlobalTextScale
                        )
                        showTextScaleSheet = true
                    }
                    FeatureId.ABOUT -> showAboutApp = true
                    FeatureId.FLAGS -> showClickPage = "flags"
                    FeatureId.VERSION -> showClickPage = "version"
                    FeatureId.GAMES -> showClickPage = "games"
                }
            }

            // Bookmarks (for the address-bar bookmark star)
            val bookmarks by repository.bookmarksFlow.collectAsState(initial = emptyList())

            // Keep the WebViewClient-safe live copies in sync with composable state
            LaunchedEffect(activeMode) { liveMode = activeMode }
            LaunchedEffect(httpsMode) { liveHttpsMode = httpsMode }
            LaunchedEffect(httpsStrictExceptions) { liveHttpsStrictExceptions = httpsStrictExceptions.toSet() }
            LaunchedEffect(dntEnabled) { liveDntEnabled = dntEnabled }
            LaunchedEffect(gpcEnabled) { liveGpcEnabled = gpcEnabled }
            LaunchedEffect(forceNightModeWebsites) { liveNightMode = forceNightModeWebsites }
            LaunchedEffect(dataSaverEnabled) { liveDataSaver = dataSaverEnabled }
            LaunchedEffect(advanceIntroEnabled) { liveAdvanceIntroEnabled = advanceIntroEnabled }
            // Privacy quick wins: keep WebViewClient-safe live copies in sync.
            LaunchedEffect(stripTrackingParams) { liveStripTrackingParams = stripTrackingParams }
            LaunchedEffect(forgetfulBrowsing) { liveForgetfulBrowsing = forgetfulBrowsing }
            LaunchedEffect(forgetfulExceptions) { liveForgetfulExceptions = forgetfulExceptions }
            // Accessibility: keep WebViewClient-safe live copies in sync.
            LaunchedEffect(forceZoomEnabled) { liveForceZoom = forceZoomEnabled }
            LaunchedEffect(readerModeEnabled) { liveReaderMode = readerModeEnabled }
            LaunchedEffect(reducedMotionEnabled) { liveReducedMotion = reducedMotionEnabled }
            LaunchedEffect(sharpViewEnabled) { liveSharpViewEnabled = sharpViewEnabled }
            LaunchedEffect(desktopOverrides) { liveDesktopOverrides = desktopOverrides }
            LaunchedEffect(forceDesktopMode) { liveForceDesktopMode = forceDesktopMode }
            LaunchedEffect(adBlockerEnabled) { liveAdBlockerEnabled = adBlockerEnabled }
            LaunchedEffect(siteSettings) { liveSiteSettings = siteSettings }

            // Runs the real WebRTC leak test in the current tab.
            fun runWebrtcLeakTest() {
                val wv = currentTab.webView
                if (wv == null || currentTab.url.isBlank() || currentTab.url == "about:blank") {
                    Toast.makeText(
                        this@MainActivity,
                        "Open a website first, then run the test.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                val runId = java.util.UUID.randomUUID().toString()
                webrtcTestRunning = true
                webrtcResult = null
                webrtcRunId = runId
                WebRtcTestRunGate.begin(runId)
                wv.evaluateJavascript(PrivacyGuards.webRtcLeakTestScript(runId), null)
                scope.launch {
                    delay(9000)
                    if (webrtcRunId == runId) {
                        WebRtcTestRunGate.cancel(runId)
                        webrtcRunId = null
                        webrtcTestRunning = false
                        webrtcResult = WebRtcTestResult(WebRtcTestOutcome.TIMED_OUT)
                    }
                }
            }

            // Load AI + privacy + appearance settings after the application-level migration.
            LaunchedEffect(Unit) {
                dataStore.data.first().let { prefs ->
                    aiApiKey = prefs[AppSettings.AI_API_KEY].orEmpty()
                    aiProvider = prefs[AppSettings.AI_PROVIDER] ?: "groq"
                    aiModel = prefs[AppSettings.AI_MODEL].orEmpty()
                    val autoRotate = prefs[AppSettings.AUTO_ROTATE_THEMES] ?: false
                    val storedVariantId = prefs[AppSettings.LIGHT_THEME_VARIANT]
                    val launchVariant = ThemeController.variantForLaunch(
                        storedVariantId,
                        autoRotate && savedInstanceState == null
                    )
                    val needsLightThemeMigration =
                        prefs[AppSettings.APPEARANCE_MODE] != "light" ||
                            prefs[AppSettings.THEME_MODE] != "light" ||
                            prefs[AppSettings.UI_DARK_MODE] == true ||
                            BrowserMode.values().any { prefs[AppSettings.darkModeKey(it)] == true }
                    val needsThemePreferenceWrite = needsLightThemeMigration ||
                        storedVariantId != launchVariant.id ||
                        prefs[AppSettings.AUTO_ROTATE_THEMES] == null
                    if (needsThemePreferenceWrite) {
                        dataStore.edit { appearance ->
                            appearance[AppSettings.APPEARANCE_MODE] = "light"
                            appearance[AppSettings.THEME_MODE] = "light"
                            appearance[AppSettings.UI_DARK_MODE] = false
                            appearance[AppSettings.LIGHT_THEME_VARIANT] = launchVariant.id
                            appearance[AppSettings.AUTO_ROTATE_THEMES] = autoRotate
                            BrowserMode.values().forEach { mode ->
                                appearance[AppSettings.darkModeKey(mode)] = false
                            }
                        }
                    }
                    lightThemeVariant = launchVariant
                    autoRotateThemes = autoRotate
                    // HTTPS mode (default "standard" = historical HTTPS-Only upgrade behavior).
                    httpsMode = AppSettings.normalizeMode(prefs[AppSettings.HTTPS_MODE])
                    httpsStrictExceptions = AppSettings.parseHostList(prefs[AppSettings.HTTPS_STRICT_EXCEPTIONS])
                    dntEnabled = prefs[AppSettings.DNT_ENABLED] == true || prefs[ExperimentalFlags.K_DNT_HEADER] == true
                    gpcEnabled = prefs[AppSettings.GPC_ENABLED] == true
                    secureDnsEnabled = prefs[AppSettings.SECURE_DNS_ENABLED] == true
                    backgroundAudioEnabled = prefs[AppSettings.BACKGROUND_AUDIO_ENABLED] == true
                    // Home feed: opt-in, separate from background notifications.
                    homeNewsFeedEnabled = com.click.browser.engine.HomeNewsFeedPolicy.isEnabled(
                        prefs[AppSettings.HOME_NEWS_FEED_ENABLED]
                    )
                    // Background news notifications (opt-in, default OFF).
                    newsNotificationsEnabled = prefs[AppSettings.NEWS_NOTIFICATIONS_ENABLED] == true
                    newsTopics = prefs[AppSettings.NEWS_NOTIFICATION_TOPICS]
                        ?.takeIf { it.isNotEmpty() }
                        ?: NewsNotifications.DEFAULT_TOPICS
                    advanceIntroEnabled = prefs[AppSettings.ADVANCE_INTRO_ENABLED] ?: true
                    sharpViewEnabled = prefs[AppSettings.SHARP_VIEW_ENABLED] != false
                    // Brave-inspired privacy quick wins.
                    stripTrackingParams = prefs[AppSettings.STRIP_TRACKING_PARAMS] ?: true
                    forgetfulBrowsing = prefs[AppSettings.FORGETFUL_BROWSING] == true
                    forgetfulExceptions = prefs[AppSettings.FORGETFUL_BROWSING_EXCEPTIONS] ?: emptySet()
                    // Accessibility settings.
                    forceZoomEnabled = prefs[AppSettings.FORCE_ZOOM] == true
                    readerModeEnabled = prefs[AppSettings.READER_MODE] == true
                    reducedMotionEnabled = prefs[AppSettings.REDUCED_MOTION] == true
                    wallpaperUri = prefs[AppSettings.WALLPAPER_URI]
                    showOnboarding = prefs[AppSettings.ONBOARDING_COMPLETED] != true
                    onboardingReady = true
                    // Biometric private-tab lock + text scaling.
                    biometricLockEnabled = prefs[AppSettings.BIOMETRIC_TAB_LOCK] == true
                    liveBiometricLockEnabled = biometricLockEnabled
                    hasStrongBiometric = com.click.browser.engine.PrivateTabLock.hasStrongBiometric(this@MainActivity)
                    val (gScale, hScales) = com.click.browser.engine.TextScaleStore.snapshot(this@MainActivity)
                    globalTextScale = gScale
                    hostTextScales = hScales
                    liveGlobalTextScale = gScale
                    liveHostTextScales = hScales
                    // Customizable browser menu: order + hidden items.
                    menuOrder = MenuCustomization.loadOrder(prefs[MenuCustomization.MENU_ORDER_JSON])
                    menuHidden = MenuCustomization.loadHidden(prefs[MenuCustomization.MENU_HIDDEN_JSON])
                    // Experimental flags (click://flags).
                    liveFlags = ExperimentalFlags.load(prefs)
                    // Signal the crash-restore saver/check: clearOnExit is
                    // now the real DataStore value, safe to read.
                    flagsLoaded = true
                    flagsUi = liveFlags
                    applyScreenshotFlag()
                }
            }

            // Keep the activity-level cache in sync for onPause/onResume.
            LaunchedEffect(backgroundAudioEnabled) {
                backgroundAudioEnabledCached = backgroundAudioEnabled
            }

            // Search preference is profile-specific and drives both the visible brand mark
            // and the URL used when a query (rather than a website address) is submitted.
            var currentSearchEngineSetting by remember(activeMode) {
                mutableStateOf(ModePersonalization.defaultSearchEngine(activeMode))
            }
            LaunchedEffect(activeMode) {
                currentSearchEngineSetting = try {
                    ModePersonalization.getSearchEngine(this@MainActivity, activeMode)
                } catch (_: Exception) {
                    ModePersonalization.defaultSearchEngine(activeMode)
                }
            }

            // Back Press Handling
            // Fullscreen video: back exits fullscreen first.
            BackHandler(enabled = exitFullscreenVideo != null) {
                exitFullscreenVideo?.invoke()
            }
            BackHandler(enabled = currentTab.url != "about:blank" && exitFullscreenVideo == null) {
                // If there is no page history (including after returning from
                // Google sign-in), keep the rendered page instead of blanking it.
                currentTab.browserSession.handleBrowserBackPress()
            }

            // click:// page open: back closes the native page first.
            BackHandler(enabled = showClickPage != null) {
                showClickPage = null
            }

            // Game player open: back closes the player first.
            BackHandler(enabled = showGamePlayer != null) {
                showGamePlayer = null
            }

            // Confirm-exit flag: ask before closing the browser from home.
            BackHandler(enabled = showClickPage == null && currentTab.url == "about:blank" && flagsUi.confirmExit) {
                showExitConfirm = true
            }

            // Drawer Navigation State (Simple, Dev, Power and shortcuts inside the hamburger menu)
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            fun openExtensionStore(tab: TabItem) {
                if (tab.isIncognito) {
                    Toast.makeText(
                        this@MainActivity,
                        "Extensions are unavailable in Private tabs. Open a regular tab.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
                val intent = Intent(this@MainActivity, ExtensionBrowserActivity::class.java)
                    .putExtra(ExtensionBrowserActivity.EXTRA_START_URL, com.click.browser.extensions.FirefoxAmoCatalog.ANDROID_STORE_URL)
                    .putExtra(ExtensionBrowserActivity.EXTRA_RETURN_TAB_ID, tab.id)
                    .putExtra(ExtensionBrowserActivity.EXTRA_SHOW_MANAGER, false)
                extensionStoreLauncher.launch(intent)
            }
            fun clearGeckoSiteData(host: String) {
                val normalizedHost = host.trim().lowercase().trimEnd('.')
                if (normalizedHost.isBlank() || clearSiteDataInProgress) return
                clearSiteDataInProgress = true
                normalTabExtensionRouter?.closePopup()
                tabs.forEach { tab ->
                    if (tab.engine != BrowserTabEngine.GECKOVIEW) return@forEach
                    val browserSession = tab.browserSession
                    if (browserSession != null) {
                        runCatching { browserSession.stopLoading() }
                        runCatching { browserSession.setSelected(false) }
                        runCatching { browserSession.detachFromParent() }
                        liveBrowserSessions.remove(browserSession)
                        runCatching { browserSession.destroy() }
                        tab.attachBrowserSession(null)
                    }
                    tab.takePendingGeckoSession()?.let { pending -> runCatching { pending.close() } }
                    tab.canGoBack = false
                    tab.canGoForward = false
                }
                val result = try {
                    ExtensionBrowserRuntime.get(applicationContext).storageController
                        .clearDataFromHost(
                            normalizedHost, org.mozilla.geckoview.StorageController.ClearFlags.SITE_DATA
                        )
                } catch (error: Exception) {
                    browserSessionGeneration += 1
                    clearSiteDataInProgress = false
                    showClearSiteDataConfirm = false
                    Toast.makeText(
                        this@MainActivity,
                        "Could not start site-data clearing: ${error.message ?: "GeckoView error"}",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
                result.accept(
                    {
                        runOnUiThread {
                            browserSessionGeneration += 1
                            clearSiteDataInProgress = false
                            showClearSiteDataConfirm = false
                            showSiteSettings = false
                            scope.launch {
                                repository.clearWebsiteMicrophonePermissionsForHostAcrossProfiles(
                                    normalizedHost
                                )
                            }
                            Toast.makeText(
                                this@MainActivity,
                                "Gecko site data cleared for $normalizedHost. Normal Gecko tabs are reopening; new site data can be created as you browse.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    { error ->
                        runOnUiThread {
                            browserSessionGeneration += 1
                            clearSiteDataInProgress = false
                            showClearSiteDataConfirm = false
                            Toast.makeText(
                                this@MainActivity,
                                "Could not confirm site-data clearing: ${error?.message ?: "GeckoView error"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }

            fun replaceTabEngine(tab: TabItem, targetEngine: BrowserTabEngine): Boolean {
                if (tab.engine == targetEngine) return true
                if (targetEngine == BrowserTabEngine.GECKOVIEW && tab.isIncognito) return false

                val previousSession = tab.browserSession
                val currentUrl = (previousSession as? WebViewBrowserTabSession)?.webView?.url
                    ?.takeIf { it.isNotBlank() } ?: tab.url
                previousSession?.let { session ->
                    runCatching { session.stopLoading() }
                    runCatching { session.setSelected(false) }
                    runCatching { session.detachFromParent() }
                    liveBrowserSessions.remove(session)
                    (session as? WebViewBrowserTabSession)?.webView?.let { liveWebViews.remove(it) }
                    runCatching { session.destroy() }
                }
                tab.attachBrowserSession(null)
                tab.url = currentUrl
                tab.engine = targetEngine
                tab.loadProgress = 0
                tab.isPageLoading = currentUrl != "about:blank"
                tab.hasCommittedContent = false
                return true
            }

            fun requestExtensionsForTab(tab: TabItem) {
                when (extensionTabEntryAction(tab.engine, tab.isIncognito)) {
                    ExtensionTabEntryAction.PRIVATE_TAB_UNAVAILABLE -> {
                        Toast.makeText(
                            this@MainActivity,
                            "Extensions are unavailable in Private tabs. Open a regular tab.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    ExtensionTabEntryAction.OPEN_AMO_STORE -> {
                        refreshInstalledAddonPanel()
                        showInstalledAddonsPanel = true
                    }
                }
            }
            /**
             * Reads live viewport/device facts from the current page via JS.
             * Updates [devToolsDeviceInfo]; safe to call when no page is loaded
             * (clears the info instead of crashing). Declared before
             * [openDevToolsTab] (Kotlin local funs need declaration-before-use).
             */
            fun refreshDevToolsDeviceInfo() {
                val wv = currentTab.webView
                if (wv == null || currentTab.url == "about:blank") {
                    devToolsDeviceInfo = null
                    return
                }
                wv.evaluateJavascript(
                    """(function(){
                        try {
                            return JSON.stringify({
                                viewport: window.innerWidth + 'x' + window.innerHeight,
                                dpr: String(window.devicePixelRatio || '?'),
                                ua: navigator.userAgent || '',
                                screen: screen.width + 'x' + screen.height,
                                platform: navigator.platform || '',
                                lang: navigator.language || '',
                                touch: ('ontouchstart' in window) ? 'yes' : 'no',
                                cookies: navigator.cookieEnabled ? 'yes' : 'no'
                            });
                        } catch(e) { return '{}'; }
                    })()"""
                ) { result ->
                    devToolsDeviceInfo = try {
                        // evaluateJavascript returns the JS string as a JSON string
                        // literal (quoted + escaped). Decode via a JSON array wrapper.
                        val inner = if (result.isNullOrBlank() || result == "null") "{}"
                        else org.json.JSONArray("[$result]").optString(0, "{}")
                        val json = org.json.JSONObject(inner)
                        DeviceInfo(
                            viewport = json.optString("viewport"),
                            devicePixelRatio = json.optString("dpr"),
                            userAgent = json.optString("ua"),
                            screenSize = json.optString("screen"),
                            platform = json.optString("platform"),
                            language = json.optString("lang"),
                            touchSupport = json.optString("touch"),
                            cookiesEnabled = json.optString("cookies")
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            /** Reads a bounded, read-only page snapshot only when the user opens/refreshes DevTools. */
            fun refreshDevToolsPageSnapshot() {
                val wv = currentTab.webView
                val pageUrl = wv?.url.orEmpty()
                if (wv == null || pageUrl.isBlank() || pageUrl == "about:blank") {
                    devToolsDomHtml.value = ""
                    devToolsSources.clear()
                    return
                }
                wv.evaluateJavascript(
                    "(function(){try{return document.documentElement ? document.documentElement.outerHTML.slice(0,100000) : ''; }catch(e){return '';}})()"
                ) { result ->
                    val html = try {
                        if (result.isNullOrBlank() || result == "null") ""
                        else org.json.JSONArray("[$result]").optString(0, "")
                    } catch (_: Exception) { "" }
                    if (currentTab.webView === wv && wv.url == pageUrl) {
                        devToolsDomHtml.value = html
                    }
                }
                wv.evaluateJavascript(
                    "(function(){try{return Array.from(document.querySelectorAll('script[src],link[rel=\\\"stylesheet\\\"],img[src]')).slice(0,500).map(function(el){return el.src||el.href||'';}).filter(Boolean);}catch(e){return [];}})()"
                ) { result ->
                    val sources = try {
                        val array = org.json.JSONArray(result ?: "[]")
                        (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
                    } catch (_: Exception) { emptyList() }
                    if (currentTab.webView === wv && wv.url == pageUrl) {
                        devToolsSources.clear()
                        devToolsSources.addAll(sources)
                    }
                }
            }

            // Opens the real DevTools bottom sheet only when the current mode
            // and tab engine support it. This launcher never switches modes or engines.
            fun openDevToolsTab(tab: Int) {
                scope.launch {
                    drawerState.close()
                    val capability = ModeCapabilityRegistry.availability(
                        feature = FeatureId.DEVTOOLS,
                        mode = activeMode,
                        engine = currentTab.engine,
                        hasLoadedPage = currentTab.url.isNotBlank() && currentTab.url != "about:blank"
                    )
                    if (!capability.visible || !capability.enabled) {
                        val reason = capability.explanation
                            ?: "DevTools is only available in Developer mode."
                        Toast.makeText(this@MainActivity, reason, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    devToolsTab = tab
                    if (activeMode == BrowserMode.DEVELOPER && (tab == 0 || tab == 3)) {
                        refreshDevToolsPageSnapshot()
                    }
                    // Refresh device facts when the Device tab is requested.
                    if (tab == 4) refreshDevToolsDeviceInfo()
                    showDevToolsSheet = true
                }
            }

            MaterialTheme(colorScheme = themeColors) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    // Gestures OFF: the drawer opens ONLY via the hamburger icon.
                    // (Edge-swipe was misfiring on vertical page scrolls and
                    // opening the drawer by itself — Prince's bug report.)
                    gesturesEnabled = false,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier.width(300.dp),
                            drawerContainerColor = theme.surface,
                            drawerContentColor = theme.onSurface
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    // Drawer header — logo + name + visible close button
                                    // (Prince: no "Click Pro / Luxury 5D Edition").
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(theme.primary, theme.secondary)
                                                    ),
                                                    RoundedCornerShape(12.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.FlashOn,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Click Browser",
                                                fontWeight = FontWeight.Bold,
                                                color = theme.onSurface,
                                                fontSize = 17.sp
                                            )
                                            Text(
                                                theme.modePillText.lowercase()
                                                    .replaceFirstChar { it.uppercase() },
                                                color = theme.primary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        // Visible close affordance (Prince: drawer had no
                                        // close button — previously only outside-tap worked).
                                        IconButton(
                                            onClick = { scope.launch { drawerState.close() } },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Close menu",
                                                tint = theme.onSurface.copy(alpha = 0.7f),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = theme.onSurface.copy(0.1f))
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // Hamburger menu items in order (50+ categorized working options)
                                item { DrawerCategoryHeader(title = "1. Browser Core Modes") }
                                item {
                                    DrawerItem(
                                        label = BrowserMode.SIMPLE.display().title,
                                        subtitle = BrowserMode.SIMPLE.display().tagline,
                                        icon = Icons.Default.Filter1, color = Color(0xFF3B82F6)
                                    ) {
                                        scope.launch {
                                            drawerState.close()
                                            // Mode switch needs a restart — ask first.
                                            // Same-mode taps are a no-op (no dialog).
                                            if (activeMode != BrowserMode.SIMPLE) {
                                                pendingModeSwitch = BrowserMode.SIMPLE
                                            } else {
                                                Toast.makeText(this@MainActivity, "Already in Simple Mode", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(
                                        label = BrowserMode.DEVELOPER.display().title,
                                        subtitle = BrowserMode.DEVELOPER.display().tagline,
                                        icon = Icons.Default.Filter2, color = Color(0xFF7C3AED)
                                    ) {
                                        scope.launch {
                                            drawerState.close()
                                            if (activeMode != BrowserMode.DEVELOPER) {
                                                pendingModeSwitch = BrowserMode.DEVELOPER
                                            } else {
                                                Toast.makeText(this@MainActivity, "Already in Developer Mode", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(
                                        label = BrowserMode.HACK.display().title,
                                        subtitle = BrowserMode.HACK.display().tagline,
                                        icon = Icons.Default.Filter3, color = Color(0xFFDC2626)
                                    ) {
                                        scope.launch {
                                            drawerState.close()
                                            if (activeMode != BrowserMode.HACK) {
                                                pendingModeSwitch = BrowserMode.HACK
                                            } else {
                                                Toast.makeText(this@MainActivity, "Already in Power Mode", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(
                                        label = BrowserMode.ADVANCED.display().title,
                                        subtitle = BrowserMode.ADVANCED.display().tagline,
                                        icon = Icons.Default.Filter4, color = Color(0xFF06B6D4),
                                        onInfoClick = { showAdvanceSpecs = true }
                                    ) {
                                        scope.launch {
                                            drawerState.close()
                                            // Advance enters a fresh isolated space — nothing
                                            // carries over from the other modes.
                                            if (activeMode != BrowserMode.ADVANCED) {
                                                pendingModeSwitch = BrowserMode.ADVANCED
                                            } else {
                                                Toast.makeText(this@MainActivity, "Already in Advance Mode", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }

                                item { DrawerCategoryHeader(title = "2. Navigation & Core Features") }
                                item {
                                    DrawerItem(label = "New Tab", icon = Icons.Default.Add, color = Color(0xFF10B981)) {
                                        scope.launch {
                                            drawerState.close()
                                            tabs.add(TabItem(url = homeUrl(), title = "New Tab"))
                                            activeTabIndex = tabs.size - 1
                                            Toast.makeText(this@MainActivity, "New Tab Created", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Tabs", icon = Icons.Default.FilterNone, color = Color(0xFF3B82F6)) {
                                        scope.launch { drawerState.close(); showTabsManager = true }
                                    }
                                }
                                item {
                                    DrawerItem(
                                        label = "Extensions",
                                        subtitle = if (currentTab.isIncognito) "Unavailable in Private tabs" else "Installed add-ons and Mozilla Store",
                                        icon = Icons.Default.Extension,
                                        color = theme.primary
                                    ) {
                                        scope.launch {
                                            drawerState.close()
                                            requestExtensionsForTab(currentTab)
                                        }
                                    }
                                }
                                if (currentTab.engine == BrowserTabEngine.GECKOVIEW && !currentTab.isIncognito) {
                                    item {
                                        DrawerItem(
                                            label = "Switch this tab to WebView",
                                            subtitle = "Reload this page without add-on support",
                                            icon = Icons.Default.Web,
                                            color = Color(0xFF64748B)
                                        ) {
                                            scope.launch {
                                                drawerState.close()
                                                pendingWebViewSwitchTabId = currentTab.id
                                                showWebViewSwitchConfirmation = true
                                            }
                                        }
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
                                        scope.launch {
                                            drawerState.close()
                                            showDownloads = true
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Settings Dashboard", icon = Icons.Default.Settings, color = Color.LightGray) {
                                        scope.launch { drawerState.close(); showSettings = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Quick Toggles", icon = Icons.Default.Tune, color = Color(0xFF00FF00)) {
                                        scope.launch { drawerState.close(); showQuickTogglesDialog = true }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Incognito / Private Mode", icon = Icons.Default.Security, color = Color(0xFFFF9800)) {
                                        scope.launch {
                                            drawerState.close()
                                            if (currentTab.engine == BrowserTabEngine.GECKOVIEW) {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Private mode is not available in GeckoView tabs. Switch this tab to WebView first.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                currentTab.isIncognito = !currentTab.isIncognito
                                                Toast.makeText(this@MainActivity, "Incognito Mode is now " + (if(currentTab.isIncognito) "Enabled" else "Disabled"), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }

                                item { DrawerCategoryHeader(title = "3. Media Players & Tools") }
                                item {
                                    DrawerItem(label = "Device Music Player", icon = Icons.Default.MusicNote, color = Color(0xFFEC4899)) {
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
                                    DrawerItem(label = "Camera Capture", icon = Icons.Default.CameraAlt, color = Color(0xFF60A5FA)) {
                                        scope.launch {
                                            drawerState.close()
                                            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                                            try {
                                                startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(this@MainActivity, "No camera app found on this device.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Import from PowerCut Editor", icon = Icons.Default.VideoFile, color = Color(0xFFFF5722)) {
                                        scope.launch {
                                            drawerState.close()
                                            requestStoragePermissions()
                                            importFromPowerCut()
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
                                            if (currentTab.engine == BrowserTabEngine.GECKOVIEW) {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Per-category Gecko cache clearing is unavailable; use Clear all current mode data.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                currentTab.webView?.clearCache(true)
                                                Toast.makeText(this@MainActivity, "Browser cache purged successfully.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Clear Web Cookies", icon = Icons.Default.Cookie, color = Color(0xFFF59E0B)) {
                                        scope.launch {
                                            drawerState.close()
                                            if (currentTab.engine == BrowserTabEngine.GECKOVIEW) {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Per-category Gecko cookie clearing is unavailable; use Clear all current mode data.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                                                Toast.makeText(this@MainActivity, "All persistent WebView cookies cleared.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Force Reload Page", icon = Icons.Default.Refresh, color = Color(0xFF60A5FA)) {
                                        scope.launch {
                                            drawerState.close()
                                            currentTab.browserSession?.reload()
                                            Toast.makeText(this@MainActivity, "Forcing page reload...", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Auto-Scroll Web Page", icon = Icons.Default.KeyboardDoubleArrowDown, color = Color(0xFFA78BFA)) {
                                        scope.launch {
                                            drawerState.close()
                                            if (currentTab.engine == BrowserTabEngine.GECKOVIEW) {
                                                Toast.makeText(this@MainActivity, "Auto-scroll is not available in Gecko tabs yet.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                currentTab.webView?.evaluateJavascript(
                                                    "var scrollInterval = setInterval(function() { window.scrollBy(0, 2); }, 30);", null
                                                )
                                                Toast.makeText(this@MainActivity, "Continuous Auto-Scroll Active. Click address bar to focus.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Find in Page Tool", icon = Icons.Default.Search, color = Color.LightGray) {
                                        scope.launch { drawerState.close(); showFindInPageDialog = true }
                                    }
                                }
                                item {
                                    // Per-site wide-layout toggle (persisted per host).
                                    val pageHost = try {
                                        android.net.Uri.parse(currentTab.url).host?.lowercase().orEmpty()
                                    } catch (e: Exception) { "" }
                                    val isWideLayoutForSite = DesktopLayoutPolicy.enabledForHost(
                                        pageHost, desktopOverrides, activeMode,
                                        forceDesktopMode, liveFlags.desktopDefault
                                    )
                                    DrawerItem(
                                        label = if (isWideLayoutForSite) "Wide layout: ON (this site)" else "Wide layout (this site)",
                                        icon = Icons.Default.DesktopWindows,
                                        color = theme.secondary
                                    ) {
                                        scope.launch {
                                            drawerState.close()
                                            if (pageHost.isEmpty() || currentTab.url == "about:blank") {
                                                Toast.makeText(this@MainActivity, "Open a page first.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                persistDesktopOverride(pageHost, !isWideLayoutForSite)
                                                val nextWideLayout = DesktopLayoutPolicy.enabledForHost(
                                                    pageHost, liveDesktopOverrides, activeMode,
                                                    forceDesktopMode, liveFlags.desktopDefault
                                                )
                                                applyWideLayoutToTab(currentTab, nextWideLayout)
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    if (nextWideLayout) "Wide layout enabled for $pageHost"
                                                    else "Mobile layout restored for $pageHost",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Share This Page", icon = Icons.Default.Share, color = Color(0xFF34D399)) {
                                        scope.launch {
                                            drawerState.close()
                                            val url = currentTab.url
                                            if (url == "about:blank") {
                                                Toast.makeText(this@MainActivity, "Nothing to share yet.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val share = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, currentTab.title)
                                                    putExtra(Intent.EXTRA_TEXT, "${currentTab.title}\n$url")
                                                }
                                                startActivity(Intent.createChooser(share, "Share page via"))
                                            }
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Copy Page Link", icon = Icons.Default.ContentCopy, color = Color(0xFFFBBF24)) {
                                        scope.launch {
                                            drawerState.close()
                                            val url = currentTab.url
                                            if (url == "about:blank") {
                                                Toast.makeText(this@MainActivity, "Nothing to copy yet.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                cm.setPrimaryClip(android.content.ClipData.newPlainText("Click Browser link", url))
                                                Toast.makeText(this@MainActivity, "Link copied", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(
                                        label = if (immersiveMode) "Exit Fullscreen View" else "Fullscreen View",
                                        icon = Icons.Default.Fullscreen,
                                        color = Color(0xFFA78BFA)
                                    ) {
                                        scope.launch {
                                            drawerState.close()
                                            immersiveMode = !immersiveMode
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Translate Web Page", icon = Icons.Default.Translate, color = Color(0xFF3B82F6)) {
                                        scope.launch {
                                            drawerState.close()
                                            val currentUrl = currentTab.url
                                            if (currentUrl != "about:blank" && currentUrl.startsWith("http")) {
                                                // On-device ML Kit translation sheet.
                                                showTranslateSheet = true
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
                                            val wv = currentTab.webView
                                            if (wv == null || currentTab.url == "about:blank") {
                                                Toast.makeText(this@MainActivity, "Load a web page first.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                wv.evaluateJavascript(
                                                    "(function(){return document.body ? document.body.innerText.slice(0,1500) : ''})()"
                                                ) { result ->
                                                    val text = result
                                                        ?.removeSurrounding("\"")
                                                        ?.replace("\\n", " ")
                                                        ?.replace("\\\"", "\"")
                                                        ?.trim()
                                                        .orEmpty()
                                                    if (text.isBlank()) {
                                                        Toast.makeText(this@MainActivity, "No readable text on this page.", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        speakOutLoud(text)
                                                    }
                                                }
                                            }
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
                                    DrawerItem(
                                        label = "HTTPS Mode: ${httpsMode.replaceFirstChar { it.uppercase() }}",
                                        icon = Icons.Default.Lock, color = Color(0xFF34D399)
                                    ) {
                                        scope.launch {
                                            drawerState.close()
                                            // Cycle Off -> Standard -> Strict -> Off.
                                            httpsMode = when (httpsMode) {
                                                "standard" -> "strict"
                                                "strict" -> "off"
                                                else -> "standard"
                                            }
                                            dataStore.edit { prefs -> prefs[AppSettings.HTTPS_MODE] = httpsMode }
                                            currentTab.webView?.let {
                                                applyPrivacyToggles(it, httpsMode, forceNightModeWebsites, dataSaverEnabled)
                                            }
                                            val hint = when (httpsMode) {
                                                "strict" -> " — plain-http pages are BLOCKED"
                                                "off" -> " — http allowed"
                                                else -> " — http upgrades to https"
                                            }
                                            Toast.makeText(
                                                this@MainActivity,
                                                "HTTPS Mode: ${httpsMode.uppercase()}$hint",
                                                Toast.LENGTH_SHORT
                                            ).show()
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
                                    DrawerItem(label = "Font Size: Text Size", icon = Icons.Default.ZoomIn, color = Color(0xFFA78BFA)) {
                                        scope.launch {
                                            drawerState.close()
                                            sheetScale = com.click.browser.engine.TextScaleStore.clamp(
                                                currentTab.webView?.settings?.textZoom ?: liveGlobalTextScale
                                            )
                                            showTextScaleSheet = true
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
                                    DrawerItem(label = "Read-only DOM Snapshot", icon = Icons.Default.ManageSearch, color = Color(0xFFF472B6)) {
                                        openDevToolsTab(0)
                                    }
                                }
                                item {
                                    DrawerItem(label = "Live Network Traffic Monitor", icon = Icons.Default.NetworkCheck, color = Color(0xFF60A5FA)) {
                                        openDevToolsTab(2)
                                    }
                                }
                                item {
                                    DrawerItem(label = "Page Resources", icon = Icons.Default.OfflineShare, color = Color(0xFFFBBF24)) {
                                        openDevToolsTab(3)
                                    }
                                }

                                item { DrawerCategoryHeader(title = "7. Power Mode") }
                                item { DrawerCategoryHeader(title = "8. AI & Privacy Guards") }
                                item {
                                    DrawerItem(label = "AI Chat Assistant", icon = Icons.Default.AutoAwesome, color = Color(0xFF4FC3FF)) {
                                        scope.launch {
                                            drawerState.close()
                                            // Tampered/repackaged copy: warn instead of opening AI chat.
                                            if (tamperBlocked) showTamperDialog = true else showAiChat = true
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Privacy Guards", icon = Icons.Default.Shield, color = Color(0xFF34D399)) {
                                        scope.launch { drawerState.close(); showPrivacyGuards = true }
                                    }
                                }
                                item { DrawerCategoryHeader(title = "9. About & System Info") }
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
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Click Browser v${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE} build)",
                                                Toast.LENGTH_LONG
                                            ).show()
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
                        // Edge-to-edge (API 35+ enforced): respect system bars so no
                        // chrome is hidden behind the status/navigation bars.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                        ) {

                            // Immersive Fullscreen WebView setup: if loaded, hide overlays for full screen coverage!
                            val showOverlays = currentTab.url == "about:blank"

                            // Manual immersive mode and site video fullscreen both hide system bars.
                            LaunchedEffect(immersiveMode, videoFullscreenActive) {
                                val controller = WindowInsetsControllerCompat(window, window.decorView)
                                if (immersiveMode || videoFullscreenActive) {
                                    controller.hide(WindowInsetsCompat.Type.systemBars())
                                    controller.systemBarsBehavior =
                                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                } else {
                                    controller.show(WindowInsetsCompat.Type.systemBars())
                                }
                            }

                            // Bottom-address-bar flag: the bar renders below the page.
                            // Defined at this outer scope so both top and bottom
                            // call sites can see it.
                            //
                            // Browse-surface polish: toolbar auto-hides on scroll-down /
                            // reveals on scroll-up (event-driven, battery-safe), thin
                            // page-load progress bar, one-shot theme crossfade.
                            var toolbarVisible by remember { mutableStateOf(true) }
                            val toolbarScrollConnection = remember {
                                object : NestedScrollConnection {
                                    override fun onPreScroll(
                                        available: Offset,
                                        source: NestedScrollSource
                                    ): Offset {
                                        if (available.y < -8f) toolbarVisible = false
                                        else if (available.y > 8f) toolbarVisible = true
                                        return Offset.Zero
                                    }
                                }
                            }
                            // New page → reveal the compact top bar; page scrolling can still hide it.
                            LaunchedEffect(currentTab.url) { toolbarVisible = true }

                            // Periodic privacy-status popup (replaces the persistent
                            // strip): every ~18s a small glass card fades in for 2.5s
                            // with the live blocked count, then fades out. Battery-safe:
                            // one-shot show/hide, no loops; paused when the app is in
                            // the background; only on the browsing surface.
                            var showPrivacyPopup by remember { mutableStateOf(false) }
                            val popupActivity = this@MainActivity
                            LaunchedEffect(currentTab.url) {
                                showPrivacyPopup = false
                                if (currentTab.url == "about:blank") return@LaunchedEffect
                                var lastShownAt = android.os.SystemClock.uptimeMillis()
                                while (true) {
                                    ensureActive()
                                    kotlinx.coroutines.delay(18_000)
                                    ensureActive()
                                    val now = android.os.SystemClock.uptimeMillis()
                                    val tab = currentTab
                                    if (tab.url == "about:blank") break
                                    if (!popupActivity.lifecycle.currentState
                                            .isAtLeast(Lifecycle.State.RESUMED)
                                    ) {
                                        // Backgrounded: restart the 18s window so the
                                        // popup doesn't fire immediately on resume.
                                        lastShownAt = now
                                        continue
                                    }
                                    // Require a full 18s of foreground time between popups.
                                    if (now - lastShownAt < 18_000) continue
                                    lastShownAt = now
                                    showPrivacyPopup = true
                                    kotlinx.coroutines.delay(2_500)
                                    showPrivacyPopup = false
                                }
                            }

                            @Composable
                            fun BrowseTopBarBlock(showAddressBar: Boolean = true) {
                                val showExtensionButton = currentTab.engine == BrowserTabEngine.GECKOVIEW &&
                                    !currentTab.isIncognito && !showOverlays
                                Column {
                                    if (!immersiveMode && showOverlays) {
                                        TabStrip(
                                            tabs = tabs,
                                            activeTabIndex = activeTabIndex,
                                            theme = theme,
                                            onSelectTab = { idx -> activeTabIndex = idx },
                                            onCloseTab = { idx -> closeTabAt(idx) },
                                            onNewTab = {
                                                tabs.add(TabItem(url = homeUrl(), title = "New Tab"))
                                                activeTabIndex = tabs.lastIndex
                                            },
                                            onOpenTabSwitcher = { showTabsManager = true }
                                        )
                                    }
                                    AnimatedVisibility(
                                        visible = showAddressBar && !immersiveMode && toolbarVisible,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Crossfade(targetState = theme, label = "topbarTheme") { themed ->
                                            Column {
                                                CompactBrowseBar(
                                                    theme = themed,
                                                    activeMode = activeMode,
                                                    currentUrl = if (showOverlays) "" else currentTab.url,
                                                    isPageLoading = !showOverlays && currentTab.isPageLoading,
                                                    connectionSecurity = ConnectionSecurityPolicy.forPage(
                                                        currentTab.url, currentTab.connectionSecurity
                                                    ),
                                                    searchEngine = currentSearchEngineSetting,
                                                    canGoBack = !showOverlays && if (currentTab.engine == BrowserTabEngine.GECKOVIEW) {
                                                        currentTab.canGoBack
                                                    } else currentTab.browserSession?.canGoBack() == true,
                                                    onBack = { currentTab.browserSession.goBackIfPossible() },
                                                    canGoForward = !showOverlays && if (currentTab.engine == BrowserTabEngine.GECKOVIEW) {
                                                        currentTab.canGoForward
                                                    } else currentTab.browserSession?.canGoForward() == true,
                                                    onForward = { currentTab.browserSession.goForwardIfPossible() },
                                                    onNavigate = { input ->
                                                        pendingKeyboardDismissTabId = currentTab.id.takeIf {
                                                            currentTab.engine == BrowserTabEngine.WEBVIEW
                                                        }
                                                        dismissBrowserKeyboard()
                                                        val destination = cleanTrackingUrl(formatUrl(input, currentSearchEngineSetting))
                                                        currentTab.url = destination
                                                        currentTab.connectionSecurity = ConnectionSecurityPolicy.checking(destination)
                                                        currentTab.browserSession?.loadUrl(destination)
                                                    },
                                                    onReload = {
                                                        val failure = currentTab.geckoNavigationFailure
                                                        if (currentTab.engine == BrowserTabEngine.GECKOVIEW && failure != null) {
                                                            failure.retryUrl?.let { retryUrl ->
                                                                currentTab.geckoNavigationFailure = null
                                                                currentTab.browserSession?.loadUrl(retryUrl)
                                                            }
                                                        } else {
                                                            currentTab.browserSession?.reload()
                                                        }
                                                    },
                                                    canReload = currentTab.engine != BrowserTabEngine.GECKOVIEW ||
                                                        currentTab.geckoNavigationFailure == null ||
                                                        currentTab.geckoNavigationFailure?.retryUrl != null,
                                                    onStopLoading = {
                                                        val stopResult = runCatching {
                                                            currentTab.browserSession?.stopLoading()
                                                        }
                                                        if (stopResult.isSuccess) {
                                                            currentTab.isPageLoading = false
                                                            if (currentTab.engine == BrowserTabEngine.GECKOVIEW) {
                                                                if (!currentTab.hasCommittedContent) {
                                                                    currentTab.geckoNavigationFailure = geckoNavigationStopOutcome(
                                                                        success = false,
                                                                        failedUrl = currentTab.url,
                                                                        safeRetryUrl = null,
                                                                        wasCancelled = true
                                                                    ).failure
                                                                }
                                                            } else {
                                                                currentTab.hasCommittedContent = true
                                                            }
                                                            currentTab.loadProgress = 0
                                                        } else {
                                                            stopResult.exceptionOrNull()?.let { error ->
                                                                android.util.Log.w(
                                                                    "BrowserNavigation",
                                                                    "Toolbar stop-loading request failed",
                                                                    error
                                                                )
                                                                Toast.makeText(
                                                                    this@MainActivity,
                                                                    "Couldn't stop loading. Try again or close the tab.",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        }
                                                    },
                                                    onNewTab = {
                                                        tabs.add(TabItem(url = homeUrl(), title = "New Tab"))
                                                        activeTabIndex = tabs.lastIndex
                                                    },
                                                    onMenuClick = { showBrowserMenu = true },
                                                    onSiteSettingsClick = { showSiteSettings = true },
                                                    // 2026 style: sidebar (drawer) accessible from top bar.
                                                    onDrawerClick = { scope.launch { drawerState.open() } },
                                                    tabCount = tabs.size,
                                                    onTabsClick = { showTabsManager = true },
                                                    // Location requests are always blocked by Click.
                                                    locationMode = LocationGuard.LocationMode.BLOCK,
                                                    onLocationClick = { showLocationSettings = true },
                                                    showDeveloperTools = activeMode == BrowserMode.DEVELOPER,
                                                    onDeveloperToolsClick = { openDevToolsTab(1) },
                                                    showExtensionButton = showExtensionButton,
                                                    onAddonsClick = { requestExtensionsForTab(currentTab) }
                                                )
                                                // Thin page-load progress indicator.
                                                val progress = currentTab.loadProgress
                                                androidx.compose.animation.AnimatedVisibility(
                                                    visible = progress in 1..99,
                                                    enter = fadeIn(),
                                                    exit = fadeOut()
                                                ) {
                                                    LinearProgressIndicator(
                                                        progress = progress / 100f,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(2.dp),
                                                        color = themed.primary,
                                                        trackColor = themed.topBarBg
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Column(modifier = Modifier.fillMaxSize()) {

                                // The home screen owns its primary search field. Keep the tab
                                // strip there, but show the browser omnibox only on open pages
                                // (or at the bottom when that setting is selected).
                                if (!videoFullscreenActive && (!flagsUi.bottomAddressBar || showOverlays)) {
                                    BrowseTopBarBlock(showAddressBar = !showOverlays)
                                }

                                // 4. MAIN CONTENT CONTAINER (WIDGET-STYLE DASHBOARD OR WEBVIEW)
                                // Nested-scroll: drives the toolbar auto-hide on page scroll.
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .nestedScroll(toolbarScrollConnection)
                                ) {
                                    if (currentTab.url == "about:blank") {
                                        // Overhauled premium dashboard home page
                                        ClickHomeScreen(
                                            theme = theme,
                                            activeMode = activeMode,
                                            searchEngine = currentSearchEngineSetting,
                                            adBlockStatusLabel = adBlockStatus.label,
                                            adBlockStatusActive = adBlockStatus.active,
                                            homeNewsFeedEnabled = homeNewsFeedEnabled,
                                            onHomeNewsFeedEnabledChange = { enabled ->
                                                setHomeNewsFeedEnabled(enabled)
                                            },
                                            wallpaperUri = wallpaperUri,
                                            shieldActive = shieldActive,
                                            onV9ShieldClick = { showV9Shield = true },
                                            onShieldToggle = { toggleDnsShield() },
                                            onGamesClick = { showClickPage = "games" },
                                            onNavigate = { input ->
                                                pendingKeyboardDismissTabId = currentTab.id.takeIf {
                                                    currentTab.engine == BrowserTabEngine.WEBVIEW
                                                }
                                                dismissBrowserKeyboard()
                                                val destination = cleanTrackingUrl(formatUrl(input, currentSearchEngineSetting))
                                                currentTab.url = destination
                                                currentTab.browserSession?.loadUrl(destination)
                                            },
                                            onOpenAiChat = {
                                                if (tamperBlocked) showTamperDialog = true else showAiChat = true
                                            },
                                            onTranslate = { handleFeature(FeatureId.TRANSLATE) },
                                            onReaderMode = { handleFeature(FeatureId.READER) },
                                            onProfileClick = { showSettings = true },
                                            recentlyClosedTabs = RecentlyClosedTabs.list(activeMode),
                                            onReopenRecentTab = { reopenClosedTab(it) }
                                        )
                                    } else {
                                        // Pull-to-refresh flag: when off, the page renders
                                        // without the PullToRefreshBox wrapper at all.
                                        @Composable
                                        fun RefreshablePage(content: @Composable () -> Unit) {
                                            if (flagsUi.pullToRefreshEnabled) {
                                                // Pull-to-refresh on web pages (real WebView.reload()).
                                                // Nested-scroll aware: only triggers at the top of the page.
                                                PullToRefreshBox(
                                                    isRefreshing = isRefreshing,
                                                    onRefresh = {
                                                        isRefreshing = true
                                                        currentTab.browserSession?.reload()
                                                    },
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    content()
                                                }
                                            } else {
                                                Box(modifier = Modifier.fillMaxSize()) {
                                                    content()
                                                }
                                            }
                                        }
                                        RefreshablePage {
                                        // Adaptive Layout Frame to mimic Laptop / Tablet viewports cleanly
                                        val emulatorWidthModifier = when (deviceEmulatorMode) {
                                            "Tablet" -> Modifier.fillMaxHeight().width(768.dp)
                                            "Desktop" -> Modifier.fillMaxHeight().width(1024.dp)
                                            else -> Modifier.fillMaxSize()
                                        }

                                        // Page transition: subtle fade + slide on every
                                        // navigation start (see PageTransitionWrapper).
                                        PageTransitionWrapper(
                                            tick = pageTransitionTick,
                                            animationsEnabled = flagsUi.tabAnimations
                                        ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            key(currentTab.id, currentTab.engine, browserSessionGeneration) {
                                                AndroidView(
                                                    modifier = emulatorWidthModifier,
                                                    factory = { ctx ->
                                                        val existingSession = currentTab.browserSession
                                                        if (existingSession != null) {
                                                            existingSession.detachFromParent()
                                                            existingSession.view
                                                        } else if (currentTab.engine == BrowserTabEngine.GECKOVIEW) {
                                                            geckoContextIdsUsedInActivity +=
                                                                currentTab.pendingGeckoSession?.settings?.contextId
                                                                    ?: geckoContextIdFor(activeMode)
                                                            val tabId = currentTab.id
                                                            var createdGeckoSession: GeckoBrowserTabSession? = null
                                                            val geckoSession = GeckoBrowserTabSession(
                                                                context = ctx,
                                                                selectionActivity = this@MainActivity,
                                                                runtime = ExtensionBrowserRuntime.get(applicationContext),
                                                                tabId = tabId,
                                                                mode = activeMode,
                                                                initialUrl = currentTab.url,
                                                                initialSessionState = currentTab.geckoSessionState,
                                                                extensionRouter = if (currentTab.isIncognito) null else normalTabExtensionRouter(),
                                                                onWebsiteMicrophoneRequest = { requestTabId, origin, grant, reject ->
                                                                    runOnUiThread {
                                                                        handleWebsiteMicrophoneRequest(
                                                                            tab = tabs.firstOrNull { it.id == requestTabId },
                                                                            requestedOrigin = origin,
                                                                            grant = grant,
                                                                            reject = reject
                                                                        )
                                                                    }
                                                                },
                                                                initialGeckoSession = currentTab.takePendingGeckoSession(),
                                                                initiallySelected = true,
                                                                initiallyResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                                                                navigationTarget = navigation@{ requestedUrl ->
                                                                    var targetUrl = cleanTrackingUrl(requestedUrl)
                                                                    if (targetUrl == "about:blank") return@navigation targetUrl
                                                                    val approvedOptionsBase = approvedExtensionOptionsBasesByTab[tabId]
                                                                    if (approvedOptionsBase != null &&
                                                                        ExtensionInstallPolicy.isSafeExtensionOptionsNavigation(
                                                                            approvedOptionsBase,
                                                                            targetUrl
                                                                        )
                                                                    ) return@navigation targetUrl
                                                                    ClickInternalPages.interceptNavigation(targetUrl)?.let { pageKey ->
                                                                        showClickPage = pageKey
                                                                        return@navigation null
                                                                    }
                                                                    if (isGoogleAuthUrl(targetUrl)) {
                                                                        openInCustomTab(targetUrl)
                                                                        return@navigation null
                                                                    }
                                                                    if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                                                                        return@navigation null
                                                                    }
                                                                    if (targetUrl.startsWith("http://")) {
                                                                        val host = hostOfUrl(targetUrl)
                                                                        if (!isIpAddress(host)) {
                                                                            when (liveHttpsMode) {
                                                                                "strict" -> if (!liveHttpsStrictExceptions.contains(host)) {
                                                                                    return@navigation null
                                                                                }
                                                                                "standard" -> targetUrl = "https://" + targetUrl.removePrefix("http://")
                                                                            }
                                                                        }
                                                                    }
                                                                    if (liveMode == BrowserMode.HACK) targetUrl = forceDesktopHost(targetUrl)
                                                                    targetUrl
                                                                },
                                                                wideLayoutForUrl = ::wideLayoutEnabledForUrl,
                                                                onNavigationBlocked = { blockedUrl ->
                                                                    val scheme = runCatching {
                                                                        android.net.Uri.parse(blockedUrl).scheme?.lowercase()
                                                                    }.getOrNull()
                                                                    val message = when {
                                                                        scheme == "http" && liveHttpsMode == "strict" ->
                                                                            "Blocked by the current HTTPS policy."
                                                                        scheme == "http" && liveHttpsMode == "standard" ->
                                                                            "This new window needs a URL adjustment. Open the link in the current tab to apply browser rules."
                                                                        else -> "This destination is not available in the Gecko tab."
                                                                    }
                                                                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                                                                },
                                                                onNewSession = { uri, childSession ->
                                                                    val scheme = runCatching {
                                                                        android.net.Uri.parse(uri).scheme?.lowercase()
                                                                    }.getOrNull()
                                                                    if (scheme !in setOf("http", "https") && uri != "about:blank") {
                                                                        childSession.close()
                                                                        null
                                                                    } else {
                                                                        val childTab = TabItem(
                                                                            url = uri,
                                                                            title = "New Tab",
                                                                            isIncognito = currentTab.isIncognito,
                                                                            initialEngine = BrowserTabEngine.GECKOVIEW,
                                                                            initialGeckoSession = childSession
                                                                        )
                                                                        tabs.add(childTab)
                                                                        activeTabIndex = tabs.lastIndex
                                                                        org.mozilla.geckoview.GeckoResult.fromValue(childSession)
                                                                    }
                                                                },
                                                                onUrlChanged = { url ->
                                                                    tabs.firstOrNull { it.id == tabId }?.let { changedTab ->
                                                                        changedTab.passwordAutofillPromptGuard.onNavigationStarted()
                                                                        changedTab.url = url
                                                                    }
                                                                },
                                                                onConnectionSecurityChanged = { info ->
                                                                    val update = {
                                                                        tabs.firstOrNull { it.id == tabId }
                                                                            ?.connectionSecurity = info
                                                                    }
                                                                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) update()
                                                                    else runOnUiThread { update() }
                                                                },
                                                                onTitleChanged = { title ->
                                                                    tabs.firstOrNull { it.id == tabId }?.title = title
                                                                },
                                                                onPageLoaded = { url ->
                                                                    val loadedTab = tabs.firstOrNull { it.id == tabId }
                                                                    if (loadedTab != null &&
                                                                        (url.startsWith("http://") || url.startsWith("https://"))
                                                                    ) {
                                                                        val title = loadedTab.title.ifBlank { url }
                                                                        val isIncognitoTab = loadedTab.isIncognito
                                                                        scope.launch {
                                                                            repository.addHistoryItem(
                                                                                HistoryItem(title, url, System.currentTimeMillis()),
                                                                                isIncognitoTab = isIncognitoTab
                                                                            )
                                                                        }
                                                                    }
                                                                },
                                                                onSessionStateChanged = { encodedState ->
                                                                    val update = {
                                                                        tabs.firstOrNull { it.id == tabId }
                                                                            ?.geckoSessionState = encodedState
                                                                    }
                                                                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) update()
                                                                    else runOnUiThread { update() }
                                                                },
                                                                onNavigationStarted = { url, generation ->
                                                                    val update = {
                                                                        tabs.firstOrNull { it.id == tabId }?.let { tab ->
                                                                            if (createdGeckoSession != null && tab.browserSession !== createdGeckoSession) {
                                                                                return@let
                                                                            }
                                                                            if (generation < tab.geckoNavigationGeneration) return@let
                                                                            tab.geckoNavigationGeneration = generation
                                                                            tab.url = url
                                                                            tab.loadProgress = 0
                                                                            tab.isPageLoading = true
                                                                            tab.hasCommittedContent = false
                                                                            tab.geckoNavigationFailure = null
                                                                        }
                                                                    }
                                                                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) update()
                                                                    else runOnUiThread { update() }
                                                                },
                                                                onProgressChanged = { progress, generation ->
                                                                    val update = {
                                                                        tabs.firstOrNull { it.id == tabId }?.let { tab ->
                                                                            if (createdGeckoSession != null && tab.browserSession !== createdGeckoSession) {
                                                                                return@let
                                                                            }
                                                                            if (tab.geckoNavigationGeneration != generation || !tab.isPageLoading) {
                                                                                return@let
                                                                            }
                                                                            tab.loadProgress = progress
                                                                        }
                                                                    }
                                                                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) update()
                                                                    else runOnUiThread { update() }
                                                                },
                                                                onNavigationStopped = { completion ->
                                                                    val update = {
                                                                        tabs.firstOrNull { it.id == tabId }?.let { tab ->
                                                                            if (createdGeckoSession != null && tab.browserSession !== createdGeckoSession) {
                                                                                return@let
                                                                            }
                                                                            if (!completion.isCurrent || tab.geckoNavigationGeneration != completion.generation) {
                                                                                return@let
                                                                            }
                                                                            val outcome = completion.outcome
                                                                            tab.url = completion.url
                                                                            tab.isPageLoading = false
                                                                            tab.loadProgress = if (outcome.commitsContent) 100 else 0
                                                                            tab.hasCommittedContent = outcome.commitsContent
                                                                            tab.geckoNavigationFailure = outcome.failure
                                                                        }
                                                                    }
                                                                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) update()
                                                                    else runOnUiThread { update() }
                                                                },
                                                                onCanGoBackChanged = { canGoBack ->
                                                                    tabs.firstOrNull { it.id == tabId }?.canGoBack = canGoBack
                                                                },
                                                                onCanGoForwardChanged = { canGoForward ->
                                                                    tabs.firstOrNull { it.id == tabId }?.canGoForward = canGoForward
                                                                },
                                                                onFullscreenChanged = { active ->
                                                                    if (tabs.getOrNull(activeTabIndex)?.id == tabId) {
                                                                        videoFullscreenActive = active
                                                                        if (active) {
                                                                            showFeatureMenu = false
                                                                            showBrowserMenu = false
                                                                        }
                                                                        exitFullscreenVideo = if (active) {
                                                                            { createdGeckoSession?.session?.exitFullScreen() }
                                                                        } else null
                                                                    }
                                                                },
                                                                onRendererFailure = { event, error ->
                                                                    runOnUiThread {
                                                                        val failedTab = tabs.firstOrNull { it.id == tabId }
                                                                        if (failedTab != null) {
                                                                            failedTab.rendererRecoveryRequired =
                                                                                geckoRendererRecoveryRequired(
                                                                                    failedTab.rendererRecoveryRequired,
                                                                                    event
                                                                                )
                                                                            failedTab.isPageLoading = false
                                                                            failedTab.loadProgress = 0
                                                                        }
                                                                        val code = when (event) {
                                                                            GeckoRenderEvent.PROCESS_CRASHED ->
                                                                                com.click.browser.engine.ClickCatLogCode.GECKO_RENDERER_CRASHED
                                                                            GeckoRenderEvent.PROCESS_KILLED ->
                                                                                com.click.browser.engine.ClickCatLogCode.GECKO_RENDERER_KILLED
                                                                            GeckoRenderEvent.RECOVERY_FAILED ->
                                                                                com.click.browser.engine.ClickCatLogCode.GECKO_RECOVERY_FAILED
                                                                            else -> null
                                                                        }
                                                                        if (code != null) {
                                                                            val details = when (event) {
                                                                                GeckoRenderEvent.PROCESS_CRASHED ->
                                                                                    "GeckoSession.ContentDelegate.onCrash; session is closed; retry will reopen and restore saved state."
                                                                                GeckoRenderEvent.PROCESS_KILLED ->
                                                                                    "GeckoSession.ContentDelegate.onKill; session is closed; retry will reopen and restore saved state."
                                                                                else ->
                                                                                    "Session reopen, state restore, or reload failed; bounded exception class/cause and stack frames captured, messages omitted."
                                                                            }
                                                                            recordGeckoRendererDiagnostic(
                                                                                tabId = tabId,
                                                                                url = failedTab?.url,
                                                                                code = code,
                                                                                eventDetails = details,
                                                                                error = error
                                                                            )
                                                                        }
                                                                    }
                                                                },
                                                                onRendererRecovery = { event ->
                                                                    runOnUiThread {
                                                                        val recoveringTab = tabs.firstOrNull { it.id == tabId }
                                                                        val wasRecoveryRequired =
                                                                            recoveringTab?.rendererRecoveryRequired == true
                                                                        if (recoveringTab != null) {
                                                                            recoveringTab.rendererRecoveryRequired =
                                                                                geckoRendererRecoveryRequired(
                                                                                    wasRecoveryRequired,
                                                                                    event
                                                                                )
                                                                            if (event == GeckoRenderEvent.FIRST_COMPOSITE && wasRecoveryRequired) {
                                                                                recoveringTab.hasCommittedContent = true
                                                                                recoveringTab.isPageLoading = false
                                                                            }
                                                                        }
                                                                        if (event == GeckoRenderEvent.RECOVERY_RETRY_THROTTLED &&
                                                                            tabs.getOrNull(activeTabIndex)?.id == tabId
                                                                        ) {
                                                                            Toast.makeText(
                                                                                this@MainActivity,
                                                                                "Repeated renderer failures. Retry is paused temporarily; use Back or close the tab, or try again later.",
                                                                                Toast.LENGTH_LONG
                                                                            ).show()
                                                                        }
                                                                        val diagnostic = when (event) {
                                                                            GeckoRenderEvent.RECOVERY_STARTED ->
                                                                                com.click.browser.engine.ClickCatLogCode.GECKO_RECOVERY_STARTED to
                                                                                    "Retry requested; reopen the closed GeckoSession, restore saved state, or load the remembered URL."
                                                                            GeckoRenderEvent.RECOVERY_RELOAD_STARTED ->
                                                                                com.click.browser.engine.ClickCatLogCode.GECKO_RECOVERY_RELOAD_STARTED to
                                                                                    "Retry requested on the already-open GeckoSession; a close/open recovery cycle was not needed."
                                                                            GeckoRenderEvent.RECOVERY_RETRY_THROTTLED ->
                                                                                com.click.browser.engine.ClickCatLogCode.GECKO_RECOVERY_RETRY_THROTTLED to
                                                                                    "Retry was withheld after three recent renderer/recovery failures in the rolling 60-second window."
                                                                            GeckoRenderEvent.FIRST_COMPOSITE -> if (wasRecoveryRequired) {
                                                                                com.click.browser.engine.ClickCatLogCode.GECKO_RECOVERY_SUCCEEDED to
                                                                                    "GeckoSession.ContentDelegate.onFirstComposite; recovery frame confirmed and overlay cleared."
                                                                            } else null
                                                                            else -> null
                                                                        }
                                                                        if (diagnostic != null) {
                                                                            recordGeckoRendererDiagnostic(
                                                                                tabId = tabId,
                                                                                url = recoveringTab?.url,
                                                                                code = diagnostic.first,
                                                                                eventDetails = diagnostic.second
                                                                            )
                                                                        }
                                                                    }
                                                                },
                                                                onExternalResponse = { response ->
                                                                    val body = response.body
                                                                    val scheme = runCatching {
                                                                        android.net.Uri.parse(response.uri).scheme?.lowercase()
                                                                    }.getOrNull()
                                                                    if (body == null || response.statusCode !in 200..299 ||
                                                                        (scheme != "http" && scheme != "https")
                                                                    ) {
                                                                        runCatching { body?.close() }
                                                                        Toast.makeText(
                                                                            this@MainActivity,
                                                                            "This download response cannot be saved.",
                                                                            Toast.LENGTH_SHORT
                                                                        ).show()
                                                                    } else {
                                                                        response.setReadTimeoutMillis(120_000)
                                                                        val disposition = response.headers.entries
                                                                            .firstOrNull { it.key.equals("content-disposition", true) }
                                                                            ?.value
                                                                        val mimeType = response.headers.entries
                                                                            .firstOrNull { it.key.equals("content-type", true) }
                                                                            ?.value?.substringBefore(';')?.trim()
                                                                        val fileName = android.webkit.URLUtil.guessFileName(
                                                                            response.uri, disposition, mimeType
                                                                        )
                                                                        pendingBrowserDownload?.responseBody?.let {
                                                                            runCatching { it.close() }
                                                                        }
                                                                        scope.launch {
                                                                            val savedLocation = DownloadSettings.location(this@MainActivity)
                                                                            customDownloadFolderAvailable =
                                                                                DownloadSettings.customUri(this@MainActivity) != null
                                                                            downloadDestination = if (
                                                                                savedLocation == DownloadSettings.LOCATION_CUSTOM &&
                                                                                !customDownloadFolderAvailable
                                                                            ) DownloadSettings.LOCATION_PHONE else savedLocation
                                                                            pendingBrowserDownload = PendingBrowserDownload(
                                                                                url = response.uri,
                                                                                fileName = fileName,
                                                                                mimeType = mimeType,
                                                                                userAgent = null,
                                                                                mode = liveMode,
                                                                                responseBody = body
                                                                            )
                                                                        }
                                                                    }
                                                                },
                                                                onSelectedText = { selection, pageUrl ->
                                                                    openSelectedTextForAi(
                                                                        selection = selection,
                                                                        sourceTabId = tabId,
                                                                        sourcePageUrl = pageUrl
                                                                    )
                                                                },
                                                                onTranslateSelectedText = { selection, pageUrl ->
                                                                    openSelectedTextForTranslation(
                                                                        selection = selection,
                                                                        sourceTabId = tabId,
                                                                        sourcePageUrl = pageUrl
                                                                    )
                                                                },
                                                                onLoginSavePrompt = { promptTabId, promptSession, request ->
                                                                    val completion = org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse>()
                                                                    val option = request.options.firstOrNull()
                                                                    val login = option?.value
                                                                    val candidate = login?.let {
                                                                        com.click.browser.engine.geckoLoginSaveCandidate(
                                                                            it.origin, it.username, it.password
                                                                        )
                                                                    }
                                                                    if (option == null || candidate == null) {
                                                                        org.mozilla.geckoview.GeckoResult.fromValue(request.dismiss())
                                                                    } else {
                                                                        scope.launch {
                                                                            val managerEnabled = try {
                                                                                com.click.browser.engine.PasswordManager.isEnabled(this@MainActivity)
                                                                            } catch (_: Exception) { false }
                                                                            val offerTab = tabs.firstOrNull { it.id == promptTabId }
                                                                            val liveSession = offerTab?.browserSession as? GeckoBrowserTabSession
                                                                            val activeRegularTab = offerTab != null &&
                                                                                offerTab.engine == BrowserTabEngine.GECKOVIEW &&
                                                                                !offerTab.isIncognito &&
                                                                                currentTab.id == promptTabId &&
                                                                                tabs.indexOf(offerTab) == activeTabIndex &&
                                                                                liveSession?.session === promptSession
                                                                            val canOffer = com.click.browser.engine.shouldOfferGeckoLoginSave(
                                                                                managerEnabled, activeRegularTab, candidate
                                                                            ) && !showPasswordSaveDialog &&
                                                                                pendingPasswordSave == null &&
                                                                                pendingGeckoPasswordSave == null
                                                                            if (canOffer) {
                                                                                pendingGeckoPasswordSave = PendingGeckoPasswordSaveOffer(
                                                                                    tabId = promptTabId,
                                                                                    session = promptSession,
                                                                                    request = request,
                                                                                    option = option,
                                                                                    completion = completion,
                                                                                    credential = com.click.browser.engine.SavedPassword(
                                                                                        host = candidate.host,
                                                                                        username = candidate.username,
                                                                                        password = candidate.password,
                                                                                        origin = candidate.origin
                                                                                    )
                                                                                )
                                                                                showPasswordSaveDialog = true
                                                                            } else {
                                                                                completion.complete(request.dismiss())
                                                                            }
                                                                        }
                                                                        completion
                                                                    }
                                                                }
                                                            )
                                                            createdGeckoSession = geckoSession
                                                            currentTab.attachBrowserSession(geckoSession)
                                                            if (!liveBrowserSessions.contains(geckoSession)) {
                                                                liveBrowserSessions.add(geckoSession)
                                                            }
                                                            geckoSession.setSelected(true)
                                                            geckoSession.view
                                                        } else {
                                                            WebView(ctx).apply {
                                                                webViewClient = object : WebViewClient() {
                                                                    // Safe Browsing interstitial (Click's own premium UI).
                                                                    // Framework API 27+; kept on plain WebViewClient (NOT the
                                                                    // Compat wrapper — Compat's SHOULD_OVERRIDE_WITH_REDIRECTS
                                                                    // caused redirect reload loops that broke page scrolling).
                                                                    @RequiresApi(android.os.Build.VERSION_CODES.O_MR1)
                                                                    override fun onSafeBrowsingHit(
                                                                        view: WebView,
                                                                        request: WebResourceRequest,
                                                                        threatType: Int,
                                                                        callback: android.webkit.SafeBrowsingResponse
                                                                    ) {
                                                                        val url = request.url?.toString() ?: ""
                                                                        com.click.browser.engine.SafeBrowsingManager.reportHit(
                                                                            url, threatType, callback
                                                                        )
                                                                    }

                                                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                                                        // Never hijack subframes; never manually re-load a redirect
                                                                        // (reload loops reset scroll position — page feels unscrollable).
                                                                        if (request != null && !request.isForMainFrame) return false
                                                                        var urlStr = request?.url?.toString() ?: ""
                                                                        // Query-param stripping (Brave-style): drop tracking
                                                                        // params before anything else touches the URL.
                                                                        urlStr = cleanTrackingUrl(urlStr)
                                                                        // PDF: offer in-app viewing instead of navigating.
                                                                        if (isPdfUrl(urlStr)) {
                                                                            pdfOfferUrl = urlStr
                                                                            return true
                                                                        }
                                                                        // Only actual Google OAuth authorization requests use
                                                                        // the supported browser context, even after redirects.
                                                                        if (isGoogleAuthUrl(urlStr)) {
                                                                            openInCustomTab(urlStr)
                                                                            return true
                                                                        }
                                                                        if (request != null && request.isRedirect) return false
                                                                        // HTTPS mode handling FIRST (so http://click://
                                                                        // can't bypass it). Standard upgrades http->https;
                                                                        // Strict BLOCKS plain-http unless the host is
                                                                        // in the per-site exception list; Off allows it.
                                                                        // IP-ADDRESS hosts are NEVER upgraded or blocked:
                                                                        // most IP servers (e.g. 182.180.50.52) have no TLS
                                                                        // cert, so upgrading breaks them. HTTP loads directly.
                                                                        if (urlStr.startsWith("http://")) {
                                                                            val ipHost = hostOfUrl(urlStr)
                                                                            if (!isIpAddress(ipHost)) {
                                                                                when (liveHttpsMode) {
                                                                                    "strict" -> {
                                                                                        val host = ipHost
                                                                                        if (!liveHttpsStrictExceptions.contains(host)) {
                                                                                            showHttpsBlockedPage(view, urlStr, host)
                                                                                            return true
                                                                                        }
                                                                                    }
                                                                                    "standard" -> {
                                                                                        urlStr = "https://" + urlStr.removePrefix("http://")
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                        // Power mode host preference: some sites redirect to a
                                                                        // mobile-specific subdomain. Keep the selected host; wide
                                                                        // layout does not change the WebView identity.
                                                                        // so Power mode stays on the desktop host.
                                                                        if (liveMode == BrowserMode.HACK) {
                                                                            urlStr = forceDesktopHost(urlStr)
                                                                        }
                                                                        // click:// internal pages (chrome://-style): open natively.
                                                                        ClickInternalPages.interceptNavigation(urlStr)?.let { pageKey ->
                                                                            showClickPage = pageKey
                                                                            return true
                                                                        }
                                                                        if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                                                                            // Apply viewport/layout settings before loading the page.
                                                                            if (view != null) this@MainActivity.applyPerSiteDesktop(view, urlStr)
                                                                            if (view != null) this@MainActivity.applyPerSiteSettings(view, urlStr)
                                                                            // Optional DNT/GPC privacy signals ride on
                                                                            // navigations when enabled.
                                                                            val navHeaders = mutableMapOf<String, String>()
                                                                            if (liveFlags.dntHeader || liveDntEnabled) navHeaders["DNT"] = "1"
                                                                            if (liveGpcEnabled) navHeaders["Sec-GPC"] = "1"
                                                                            if (navHeaders.isNotEmpty()) {
                                                                                view?.loadUrl(urlStr, navHeaders)
                                                                            } else {
                                                                                view?.loadUrl(urlStr)
                                                                            }
                                                                            return true
                                                                        }
                                                                        return false
                                                                    }

                                                                    @Suppress("Deprecated")
                                                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                                                        var urlStr = url ?: ""
                                                                        // Query-param stripping (Brave-style).
                                                                        urlStr = cleanTrackingUrl(urlStr)
                                                                        // Only valid Google OAuth authorization requests hand off.
                                                                        if (isGoogleAuthUrl(urlStr)) {
                                                                            openInCustomTab(urlStr)
                                                                            return true
                                                                        }
                                                                        // HTTPS mode handling FIRST (see above).
                                                                        // PDF: offer in-app viewing instead of navigating.
                                                                        if (isPdfUrl(urlStr)) {
                                                                            pdfOfferUrl = urlStr
                                                                            return true
                                                                        }
                                                                        if (urlStr.startsWith("http://")) {
                                                                            // IP hosts never upgraded/blocked (see above).
                                                                            val ipHost2 = hostOfUrl(urlStr)
                                                                            if (!isIpAddress(ipHost2)) {
                                                                                when (liveHttpsMode) {
                                                                                    "strict" -> {
                                                                                        val host = ipHost2
                                                                                        if (!liveHttpsStrictExceptions.contains(host)) {
                                                                                            showHttpsBlockedPage(view, urlStr, host)
                                                                                            return true
                                                                                        }
                                                                                    }
                                                                                    "standard" -> {
                                                                                        urlStr = "https://" + urlStr.removePrefix("http://")
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                        // Power mode desktop host preference (see above).
                                                                        if (liveMode == BrowserMode.HACK) {
                                                                            urlStr = forceDesktopHost(urlStr)
                                                                        }
                                                                        // click:// internal pages (chrome://-style): open natively.
                                                                        ClickInternalPages.interceptNavigation(urlStr)?.let { pageKey ->
                                                                            showClickPage = pageKey
                                                                            return true
                                                                        }
                                                                        if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                                                                            if (view != null) this@MainActivity.applyPerSiteDesktop(view, urlStr)
                                                                            if (view != null) this@MainActivity.applyPerSiteSettings(view, urlStr)
                                                                            // Optional DNT/GPC signals ride on navigations
                                                                            // whenever enabled.
                                                                            val navHeaders = mutableMapOf<String, String>()
                                                                            if (liveFlags.dntHeader || liveDntEnabled) navHeaders["DNT"] = "1"
                                                                            if (liveGpcEnabled) navHeaders["Sec-GPC"] = "1"
                                                                            if (navHeaders.isNotEmpty()) {
                                                                                view?.loadUrl(urlStr, navHeaders)
                                                                            } else {
                                                                                view?.loadUrl(urlStr)
                                                                            }
                                                                            return true
                                                                        }
                                                                        return false
                                                                    }

                                                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                                super.onPageStarted(view, url, favicon)
                                                                val loadingTab = view?.let { target -> tabs.firstOrNull { it.webView === target } } ?: return
                                                                loadingTab.passwordAutofillPromptGuard.onNavigationStarted()
                                                                loadingTab.url = url ?: ""
                                                                loadingTab.connectionSecurity = ConnectionSecurityPolicy.checking(loadingTab.url)
                                                                loadingTab.isPageLoading = true
                                                                loadingTab.hasCommittedContent = false
                                                                loadingTab.loadProgress = 0
                                                                if (loadingTab === currentTab) {
                                                                    lastPageStart = System.currentTimeMillis()
                                                                    pageTransitionTick++
                                                                    networkRequests.clear()
                                                                    sourcesList.clear()
                                                                    devToolsDomHtml.value = ""
                                                                }
                                                            }

                                                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                                                super.onPageCommitVisible(view, url)
                                                                view?.let { target ->
                                                                    tabs.firstOrNull { it.webView === target }?.hasCommittedContent = true
                                                                }
                                                            }

                                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                                super.onPageFinished(view, url)
                                                                val finishedTab = view?.let { target -> tabs.firstOrNull { it.webView === target } } ?: return
                                                                finishedTab.url = url ?: finishedTab.url
                                                                if (finishedTab.connectionSecurity.status == ConnectionSecurityStatus.CHECKING) {
                                                                    @Suppress("DEPRECATION")
                                                                    val certificate = view?.certificate
                                                                    finishedTab.connectionSecurity = ConnectionSecurityPolicy.fromWebView(
                                                                        url = view?.url ?: url ?: finishedTab.url,
                                                                        pageFinished = true,
                                                                        certificatePresent = certificate != null,
                                                                        sslErrorObserved = false,
                                                                        certificateSubject = certificate?.issuedTo?.cName
                                                                            ?: certificate?.issuedTo?.dName,
                                                                        certificateIssuer = certificate?.issuedBy?.cName
                                                                            ?: certificate?.issuedBy?.dName
                                                                    )
                                                                }
                                                                finishedTab.title = view.title ?: "Page"
                                                                finishedTab.isPageLoading = false
                                                                finishedTab.hasCommittedContent = true
                                                                finishedTab.loadProgress = 100
                                                                if (finishedTab === currentTab) {
                                                                    pageLoadTime = System.currentTimeMillis() - lastPageStart
                                                                    if (isRefreshing) isRefreshing = false
                                                                }
                                                                if (pendingKeyboardDismissTabId == finishedTab.id) {
                                                                    pendingKeyboardDismissTabId = null
                                                                    if (finishedTab === currentTab) {
                                                                        WindowInsetsControllerCompat(
                                                                            this@MainActivity.window,
                                                                            this@MainActivity.window.decorView
                                                                        ).hide(WindowInsetsCompat.Type.ime())
                                                                    }
                                                                }

                                                                // Password manager: inspect only secure pages; authenticate
                                                                // before displaying or sending a saved login to the page.
                                                                view?.let { wv ->
                                                                    val pageUrl = wv.url.orEmpty()
                                                                    val pageOrigin = com.click.browser.engine.PasswordOriginPolicy.securePageOrigin(pageUrl)
                                                                    val callbackMatchesPage =
                                                                        com.click.browser.engine.PasswordOriginPolicy.isSameSecureOrigin(
                                                                            url.orEmpty(), pageUrl
                                                                        )
                                                                    if (pageOrigin != null && callbackMatchesPage && !finishedTab.isIncognito) {
                                                                        wv.evaluateJavascript(
                                                                            com.click.browser.engine.PasswordDetector.PASSWORD_DETECT_JS,
                                                                            null
                                                                        )
                                                                        scope.launch {
                                                                            val managerEnabled = try {
                                                                                com.click.browser.engine.PasswordManager.isEnabled(this@MainActivity)
                                                                            } catch (_: Exception) { false }
                                                                            if (!managerEnabled) return@launch
                                                                            val hasCredential = com.click.browser.engine.PasswordManager.hasForOrigin(
                                                                                this@MainActivity, pageOrigin
                                                                            )
                                                                            if (!hasCredential) return@launch
                                                                            val isActiveTarget =
                                                                                finishedTab.webView === wv &&
                                                                                    finishedTab === currentTab &&
                                                                                    tabs.indexOf(finishedTab) == activeTabIndex &&
                                                                                    hasWindowFocus()
                                                                            val requestContext =
                                                                                com.click.browser.engine.PasswordAccessPolicy.AutofillContext(
                                                                                    passwordManagerEnabled = managerEnabled,
                                                                                    hasSavedCredential = hasCredential,
                                                                                    isPrivateTab = finishedTab.isIncognito,
                                                                                    isActiveTab = isActiveTarget,
                                                                                    isSecurePage = pageOrigin != null,
                                                                                    isSameOrigin = com.click.browser.engine.PasswordOriginPolicy.isSameSecureOrigin(
                                                                                        pageUrl, wv.url.orEmpty()
                                                                                    ),
                                                                                    deviceAuthenticationAvailable =
                                                                                        com.click.browser.engine.PrivateTabLock.canLock(this@MainActivity),
                                                                                    deviceAuthenticated = false
                                                                                )
                                                                            if (!com.click.browser.engine.PasswordAccessPolicy
                                                                                    .shouldRequestAutofillAuthentication(requestContext)
                                                                            ) return@launch
                                                                            if (!finishedTab.passwordAutofillPromptGuard.markIfNewOrigin(pageOrigin)) {
                                                                                return@launch
                                                                            }

                                                                            fun launchAutofillPrompt(manualRetry: Boolean = false) {
                                                                            val promptStarted = promptPasswordAction(
                                                                                title = "Use saved password?",
                                                                                subtitle = "Authenticate to fill the login for ${pageOrigin.removePrefix("https://")}",
                                                                                onCancelled = {
                                                                                    scope.launch {
                                                                                        val result = snackbarHostState.showSnackbar(
                                                                                            message = "Authentication canceled; no password was filled.",
                                                                                            actionLabel = "Retry",
                                                                                            duration = SnackbarDuration.Long
                                                                                        )
                                                                                        if (result != SnackbarResult.ActionPerformed) return@launch
                                                                                        if (finishedTab.webView !== wv || finishedTab !== currentTab ||
                                                                                            tabs.indexOf(finishedTab) != activeTabIndex || !hasWindowFocus()
                                                                                        ) {
                                                                                            Toast.makeText(
                                                                                                this@MainActivity,
                                                                                                "Switch back to the login tab to retry.",
                                                                                                Toast.LENGTH_SHORT
                                                                                            ).show()
                                                                                            return@launch
                                                                                        }
                                                                                        val retryManagerEnabled = try {
                                                                                            com.click.browser.engine.PasswordManager.isEnabled(this@MainActivity)
                                                                                        } catch (_: Exception) { false }
                                                                                        val retryHasCredential = if (retryManagerEnabled) {
                                                                                            try {
                                                                                                com.click.browser.engine.PasswordManager.hasForOrigin(
                                                                                                    this@MainActivity, pageOrigin
                                                                                                )
                                                                                            } catch (_: Exception) { false }
                                                                                        } else false
                                                                                        if (finishedTab.webView !== wv || finishedTab !== currentTab ||
                                                                                            tabs.indexOf(finishedTab) != activeTabIndex || !hasWindowFocus()
                                                                                        ) return@launch
                                                                                        val retryUrl = wv.url.orEmpty()
                                                                                        val retryContext = requestContext.copy(
                                                                                            passwordManagerEnabled = retryManagerEnabled,
                                                                                            hasSavedCredential = retryHasCredential,
                                                                                            isPrivateTab = finishedTab.isIncognito,
                                                                                            isActiveTab =
                                                                                                finishedTab.webView === wv &&
                                                                                                    finishedTab === currentTab &&
                                                                                                    tabs.indexOf(finishedTab) == activeTabIndex &&
                                                                                                    hasWindowFocus(),
                                                                                            isSecurePage =
                                                                                                com.click.browser.engine.PasswordOriginPolicy.securePageOrigin(retryUrl) != null,
                                                                                            isSameOrigin =
                                                                                                com.click.browser.engine.PasswordOriginPolicy.isSameSecureOrigin(
                                                                                                    pageUrl, retryUrl
                                                                                                ),
                                                                                            deviceAuthenticationAvailable =
                                                                                                com.click.browser.engine.PrivateTabLock.canLock(this@MainActivity)
                                                                                        )
                                                                                        if (com.click.browser.engine.PasswordAccessPolicy
                                                                                                .shouldRequestAutofillAuthentication(retryContext)
                                                                                        ) {
                                                                                            launchAutofillPrompt(manualRetry = true)
                                                                                        } else {
                                                                                            Toast.makeText(
                                                                                                this@MainActivity,
                                                                                                "Page changed or saved login is unavailable.",
                                                                                                Toast.LENGTH_SHORT
                                                                                            ).show()
                                                                                        }
                                                                                    }
                                                                                }
                                                                            ) {
                                                                                scope.launch {
                                                                                    val enabledAfterAuth = try {
                                                                                        com.click.browser.engine.PasswordManager.isEnabled(this@MainActivity)
                                                                                    } catch (_: Exception) { false }
                                                                                    if (!enabledAfterAuth) return@launch
                                                                                    if (finishedTab.webView !== wv) return@launch
                                                                                    val saved = try {
                                                                                        com.click.browser.engine.PasswordManager.getForOrigin(
                                                                                            this@MainActivity, pageOrigin
                                                                                        )
                                                                                    } catch (_: Exception) { null } ?: return@launch
                                                                                    if (finishedTab.webView !== wv) return@launch
                                                                                    val authContext =
                                                                                        com.click.browser.engine.PasswordAccessPolicy.AutofillContext(
                                                                                            passwordManagerEnabled = enabledAfterAuth,
                                                                                            hasSavedCredential = true,
                                                                                            isPrivateTab = finishedTab.isIncognito,
                                                                                            isActiveTab =
                                                                                                finishedTab.webView === wv &&
                                                                                                    finishedTab === currentTab &&
                                                                                                    tabs.indexOf(finishedTab) == activeTabIndex &&
                                                                                                    hasWindowFocus(),
                                                                                            isSecurePage =
                                                                                                com.click.browser.engine.PasswordOriginPolicy.securePageOrigin(
                                                                                                    wv.url.orEmpty()
                                                                                                ) != null,
                                                                                            isSameOrigin =
                                                                                                com.click.browser.engine.PasswordOriginPolicy.isSameSecureOrigin(
                                                                                                    pageUrl, wv.url.orEmpty()
                                                                                                ),
                                                                                            deviceAuthenticationAvailable =
                                                                                                com.click.browser.engine.PrivateTabLock.canLock(this@MainActivity),
                                                                                            deviceAuthenticated = true
                                                                                        )
                                                                                        if (!com.click.browser.engine.PasswordAccessPolicy.mayAutofill(authContext)) {
                                                                                            return@launch
                                                                                        }
                                                                                    val js = "(function(expectedOrigin, username, password) {" +
                                                                                        "if (window.location.origin !== expectedOrigin || window.top !== window) return;" +
                                                                                        "if (typeof window.__clickFillLogin !== 'function') return;" +
                                                                                        "window.__clickFillLogin(username, password);" +
                                                                                        "})(${org.json.JSONObject.quote(pageOrigin)}, " +
                                                                                        "${org.json.JSONObject.quote(saved.username)}, " +
                                                                                        "${org.json.JSONObject.quote(saved.password)});"
                                                                                    fun fillIfStillEligible(managerIsEnabled: Boolean) {
                                                                                        if (finishedTab.webView !== wv) return
                                                                                        val latestUrl = wv.url.orEmpty()
                                                                                        val latestContext =
                                                                                            com.click.browser.engine.PasswordAccessPolicy.AutofillContext(
                                                                                                passwordManagerEnabled = managerIsEnabled,
                                                                                                hasSavedCredential = true,
                                                                                                isPrivateTab = finishedTab.isIncognito,
                                                                                                isActiveTab =
                                                                                                    finishedTab.webView === wv &&
                                                                                                        finishedTab === currentTab &&
                                                                                                        tabs.indexOf(finishedTab) == activeTabIndex &&
                                                                                                        hasWindowFocus(),
                                                                                                isSecurePage =
                                                                                                    com.click.browser.engine.PasswordOriginPolicy.securePageOrigin(latestUrl) != null,
                                                                                                isSameOrigin =
                                                                                                    com.click.browser.engine.PasswordOriginPolicy.isSameSecureOrigin(
                                                                                                        pageUrl, latestUrl
                                                                                                    ),
                                                                                                deviceAuthenticationAvailable =
                                                                                                    com.click.browser.engine.PrivateTabLock.canLock(this@MainActivity),
                                                                                                deviceAuthenticated = true
                                                                                            )
                                                                                        if (finishedTab.webView === wv &&
                                                                                            com.click.browser.engine.PasswordAccessPolicy.mayAutofill(latestContext)
                                                                                        ) {
                                                                                            wv.evaluateJavascript(js, null)
                                                                                        }
                                                                                    }
                                                                                    // Fill now, then retry after a delay for SPA login forms.
                                                                                    wv.post { fillIfStillEligible(enabledAfterAuth) }
                                                                                    wv.postDelayed({
                                                                                        scope.launch {
                                                                                            val enabledLater = try {
                                                                                                com.click.browser.engine.PasswordManager.isEnabled(this@MainActivity)
                                                                                            } catch (_: Exception) { false }
                                                                                            if (enabledLater) {
                                                                                                wv.post { fillIfStillEligible(enabledLater) }
                                                                                            }
                                                                                        }
                                                                                    }, 1500)
                                                                                }
                                                                            }
                                                                            if (!promptStarted) {
                                                                                if (manualRetry) {
                                                                                    Toast.makeText(
                                                                                        this@MainActivity,
                                                                                        "Could not start authentication; no password was filled.",
                                                                                        Toast.LENGTH_SHORT
                                                                                    ).show()
                                                                                } else {
                                                                                    finishedTab.passwordAutofillPromptGuard.clearIfNotStarted(pageOrigin)
                                                                                }
                                                                            }
                                                                            }
                                                                            launchAutofillPrompt()
                                                                        }
                                                                    }
                                                                }

                                                                // History persistence enforces the captured private-mode flag.
                                                                if (url != null && url != "about:blank") {
                                                                    val isIncognitoTab = finishedTab.isIncognito
                                                                    scope.launch {
                                                                        repository.addHistoryItem(
                                                                            HistoryItem(finishedTab.title, url, System.currentTimeMillis()),
                                                                            isIncognitoTab = isIncognitoTab
                                                                        )
                                                                    }
                                                                }

                                                                // Text scaling (accessibility): apply the per-host
                                                                // override for this host, else the global default.
                                                                view?.let { wv ->
                                                                    val host = try {
                                                                        android.net.Uri.parse(url.orEmpty()).host.orEmpty().lowercase()
                                                                    } catch (_: Exception) { "" }
                                                                    wv.settings.textZoom =
                                                                        liveHostTextScales[host] ?: liveGlobalTextScale
                                                                    // Accessibility injections: force zoom,
                                                                    // reduced motion, reader mode (opt-in toggles).
                                                                    try {
                                                                        if (liveForceZoom) {
                                                                            wv.evaluateJavascript(
                                                                                com.click.browser.engine.AccessibilityJs.FORCE_ZOOM_JS,
                                                                                null
                                                                            )
                                                                        }
                                                                        if (liveReducedMotion) {
                                                                            wv.evaluateJavascript(
                                                                                com.click.browser.engine.AccessibilityJs.REDUCED_MOTION_JS,
                                                                                null
                                                                            )
                                                                        }
                                                                        if (liveReaderMode) {
                                                                            wv.evaluateJavascript(
                                                                                com.click.browser.engine.AccessibilityJs.READER_MODE_JS,
                                                                                null
                                                                            )
                                                                        }
                                                                    } catch (_: Exception) { /* non-fatal */ }
                                                                }

                                                                // Visual tab switcher thumbnails: capture the visible
                                                                // viewport ONCE per page finish, scaled down (360px).
                                                                // Never continuous, never for incognito tabs (privacy).
                                                                view?.let { wv ->
                                                                    val finishedTab = tabs.firstOrNull { it.webView === wv }
                                                                    if (finishedTab != null && !finishedTab.isIncognito
                                                                        && (url == null || !url.startsWith("about:"))
                                                                    ) {
                                                                        wv.post {
                                                                            TabThumbnailStore.capture(wv)?.let { bmp ->
                                                                                // Keep only if this WebView still
                                                                                // belongs to the same tab.
                                                                                if (finishedTab.webView === wv) {
                                                                                    TabThumbnailStore.put(finishedTab.id, bmp)
                                                                                    thumbnailVersion++
                                                                                } else {
                                                                                    bmp.recycle()
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }

                                                            }

                                                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                                                // Passive DevTools observation only; normal requests still
                                                                // continue through WebView unless Click's existing ad filter blocks them.
                                                                val shouldBlock = request != null &&
                                                                    this@MainActivity.isAdBlockEnabledForHost(
                                                                        try { android.net.Uri.parse(view?.url ?: "").host } catch (_: Exception) { null }
                                                                    ) && AdBlocker.shouldBlock(request.url.toString())
                                                                recordDevToolsRequest(view, request, blockedByClick = shouldBlock)
                                                                if (shouldBlock) {
                                                                    return WebResourceResponse("text/plain", "UTF-8", null)
                                                                }
                                                                return super.shouldInterceptRequest(view, request)
                                                            }

                                                            override fun onReceivedError(
                                                                view: WebView?,
                                                                request: WebResourceRequest?,
                                                                error: WebResourceError?
                                                            ) {
                                                                super.onReceivedError(view, request, error)
                                                                recordDevToolsLoadError(view, request, error)
                                                                if (request?.isForMainFrame != true) return
                                                                val failedTab = view?.let { target -> tabs.firstOrNull { it.webView === target } } ?: return
                                                                failedTab.connectionSecurity = ConnectionSecurityPolicy.afterWebViewLoadError(
                                                                    failedTab.url, failedTab.connectionSecurity
                                                                )
                                                                failedTab.isPageLoading = false
                                                                failedTab.hasCommittedContent = true
                                                                failedTab.loadProgress = 0
                                                                val retryUrl = request?.let { browserRetryUrl(it.method, it.url.toString()) }
                                                                this@MainActivity.showBrowserErrorPage(view, error?.description?.toString(), retryUrl)
                                                            }

                                                            override fun onReceivedSslError(
                                                                view: WebView?,
                                                                handler: android.webkit.SslErrorHandler?,
                                                                error: android.net.http.SslError?
                                                            ) {
                                                                val affectedTab = view?.let { target ->
                                                                    tabs.firstOrNull { it.webView === target }
                                                                }
                                                                if (affectedTab != null) {
                                                                    affectedTab.connectionSecurity = ConnectionSecurityPolicy.fromWebView(
                                                                        url = affectedTab.url.takeIf { it.isNotBlank() }
                                                                            ?: view?.url ?: error?.url ?: "",
                                                                        pageFinished = false,
                                                                        certificatePresent = false,
                                                                        sslErrorObserved = true
                                                                    )
                                                                }
                                                                // Never bypass an invalid certificate. The default WebView behavior
                                                                // is to cancel; keep that explicit while surfacing the warning.
                                                                handler?.cancel()
                                                            }

                                                            override fun onReceivedHttpError(
                                                                view: WebView?,
                                                                request: WebResourceRequest?,
                                                                errorResponse: WebResourceResponse?
                                                            ) {
                                                                super.onReceivedHttpError(view, request, errorResponse)
                                                                recordDevToolsHttpError(view, request, errorResponse)
                                                            }

                                                        }

                                                        val devToolsOwnerWebView = this
                                                        webChromeClient = object : WebChromeClient() {

                                                            // Read page console output from the native WebView callback only.
                                                            // No page-side console patch or DevTools JavaScript bridge is used.
                                                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                                                val msg = consoleMessage ?: return true
                                                                if (!devToolsCaptureEnabled || devToolsTargetWebView !== devToolsOwnerWebView) return false
                                                                val type = when (msg.messageLevel()) {
                                                                    ConsoleMessage.MessageLevel.ERROR -> "error"
                                                                    ConsoleMessage.MessageLevel.WARNING -> "warning"
                                                                    ConsoleMessage.MessageLevel.LOG -> "success"
                                                                    // TIP = developer tips from the page, not a
                                                                    // success signal — show as neutral info.
                                                                    ConsoleMessage.MessageLevel.TIP -> "info"
                                                                    ConsoleMessage.MessageLevel.DEBUG -> "info"
                                                                    else -> "info"
                                                                }
                                                                // sourceId can be a URL, a filename, or a pseudo-
                                                                // source like "console-api". Only show it when it
                                                                // looks like a real file (has an extension).
                                                                // Strip query params (?a=b) and hash fragments
                                                                // (#section) — they're noise in a log line.
                                                                val rawSrc = msg.sourceId()
                                                                    ?.substringAfterLast('/')
                                                                    ?.substringBefore('?')
                                                                    ?.substringBefore('#')
                                                                val src = rawSrc?.takeIf { it.isNotBlank() && it.contains('.') }
                                                                val loc = if (src != null) " ($src:${msg.lineNumber()})" else ""
                                                                // Cap message length so a spammy page can't OOM the log list.
                                                                val text = msg.message().take(500)
                                                                android.util.Log.d(
                                                                    "ClickDevTools",
                                                                    "console [$type] $text$loc"
                                                                )
                                                                addDevToolsLog(LogEntry(type, "$text$loc"))
                                                                return true
                                                            }

                                                            // Fullscreen video support (YouTube, Facebook, etc.):
                                                            // when a page requests fullscreen video, show the
                                                            // custom view full-screen; hide it when done.
                                                            private var customView: android.view.View? = null
                                                            private var customViewCallback: CustomViewCallback? = null

                                                            override fun onShowCustomView(
                                                                view: android.view.View?,
                                                                callback: CustomViewCallback?
                                                            ) {
                                                                if (view == null) {
                                                                    callback?.onCustomViewHidden()
                                                                    return
                                                                }
                                                                if (customView != null) {
                                                                    callback?.onCustomViewHidden()
                                                                    return
                                                                }
                                                                val decor = this@MainActivity.window.decorView as? android.view.ViewGroup
                                                                if (decor == null) {
                                                                    callback?.onCustomViewHidden()
                                                                    return
                                                                }
                                                                val params = android.widget.FrameLayout.LayoutParams(
                                                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                                                )
                                                                try {
                                                                    decor.addView(view, params)
                                                                } catch (_: Exception) {
                                                                    callback?.onCustomViewHidden()
                                                                    return
                                                                }
                                                                customView = view
                                                                customViewCallback = callback
                                                                videoFullscreenActive = true
                                                                // Register the back-press exit hook.
                                                                this@MainActivity.exitFullscreenVideo = {
                                                                    onHideCustomView()
                                                                }
                                                            }

                                                            override fun onHideCustomView() {
                                                                customView?.let { v ->
                                                                    (this@MainActivity.window.decorView as? android.view.ViewGroup)?.removeView(v)
                                                                }
                                                                customView = null
                                                                videoFullscreenActive = false
                                                                this@MainActivity.exitFullscreenVideo = null
                                                                customViewCallback?.onCustomViewHidden()
                                                                customViewCallback = null

                                                            }

                                                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                                                super.onReceivedTitle(view, title)
                                                                view?.let { target -> tabs.firstOrNull { it.webView === target }?.title = title ?: "Page" }
                                                            }

                                                            // Page-load progress → thin progress bar under the
                                                            // top bar (battery-safe: only fires while loading).
                                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                                super.onProgressChanged(view, newProgress)
                                                                tabs.firstOrNull { it.webView === view }?.loadProgress = newProgress
                                                            }

                                                            // Click does not request Android location permission.
                                                            // Fail closed for every origin; no fake coordinates or Allow UI.
                                                            override fun onGeolocationPermissionsShowPrompt(
                                                                origin: String?,
                                                                callback: GeolocationPermissions.Callback?
                                                            ) {
                                                                callback?.invoke(origin, false, false)
                                                            }

                                                            override fun onGeolocationPermissionsHidePrompt() {
                                                                super.onGeolocationPermissionsHidePrompt()
                                                            }

                                                            // Only audio capture can enter the per-site microphone
                                                            // consent flow. Every camera or mixed request remains denied.
                                                            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                                                                val permissionRequest = request ?: return
                                                                val ownerTab = tabs.firstOrNull {
                                                                    it.webView === devToolsOwnerWebView
                                                                }
                                                                val resources = permissionRequest.resources?.toSet().orEmpty()
                                                                if (ownerTab == null ||
                                                                    resources != setOf(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                                                                ) {
                                                                    permissionRequest.deny()
                                                                    return
                                                                }
                                                                val requestId = java.util.UUID.randomUUID().toString()
                                                                pendingWebViewMicrophoneRequestIds[permissionRequest] = requestId
                                                                handleWebsiteMicrophoneRequest(
                                                                    tab = ownerTab,
                                                                    requestedOrigin = permissionRequest.origin.toString(),
                                                                    grant = {
                                                                        permissionRequest.grant(
                                                                            arrayOf(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                                                                        )
                                                                    },
                                                                    reject = { permissionRequest.deny() },
                                                                    requestId = requestId
                                                                )
                                                            }

                                                            override fun onPermissionRequestCanceled(
                                                                request: android.webkit.PermissionRequest?
                                                            ) {
                                                                if (request != null) {
                                                                    pendingWebViewMicrophoneRequestIds
                                                                        .remove(request)
                                                                        ?.let(::cancelWebsiteMicrophoneRequest)
                                                                }
                                                            }
                                                        }

                                                        // The DevTools panel does not expose a JavaScript bridge to page content.
                                                        // This separate legacy bridge is limited to the built-in WebRTC check.
                                                        addJavascriptInterface(
                                                            WebRtcTestBridge(
                                                                onResultCallback = { runId, result ->
                                                                    scope.launch {
                                                                        if (webrtcRunId == runId) {
                                                                            webrtcResult = result
                                                                            webrtcRunId = null
                                                                            webrtcTestRunning = false
                                                                        }
                                                                    }
                                                                }
                                                            ),
                                                            "WebRtcTestBridge"
                                                        )


                                                        // Password manager: accept messages only from HTTPS origins.
                                                        // Unlike addJavascriptInterface, WebMessageListener supplies
                                                        // the browser-verified source origin and frame status.
                                                        try {
                                                            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                                                                val passwordWebView = this
                                                                val passwordBridge = com.click.browser.engine.PasswordBridge { sourceOrigin, credentialOrigin, username, password ->
                                                                    runOnUiThread {
                                                                        val offerTab = tabs.firstOrNull { it.webView === passwordWebView }
                                                                            ?: return@runOnUiThread
                                                                        if (offerTab.isIncognito || tabs.indexOf(offerTab) != activeTabIndex) return@runOnUiThread
                                                                        if (!com.click.browser.engine.PasswordOriginPolicy.isSameSecureOrigin(
                                                                                sourceOrigin, passwordWebView.url.orEmpty()
                                                                            )
                                                                        ) return@runOnUiThread
                                                                        if (showPasswordSaveDialog || pendingPasswordSave != null || pendingGeckoPasswordSave != null) return@runOnUiThread
                                                                        val credentialHost = com.click.browser.engine.PasswordOriginPolicy.hostFromSecureOrigin(
                                                                            credentialOrigin
                                                                        ) ?: return@runOnUiThread
                                                                        scope.launch {
                                                                            val managerEnabled = try {
                                                                                com.click.browser.engine.PasswordManager.isEnabled(this@MainActivity)
                                                                            } catch (_: Exception) { false }
                                                                            if (!managerEnabled) return@launch
                                                                            val currentOfferTab = tabs.firstOrNull { it.id == offerTab.id }
                                                                                ?: return@launch
                                                                            if (currentOfferTab.isIncognito ||
                                                                                tabs.indexOf(currentOfferTab) != activeTabIndex ||
                                                                                !com.click.browser.engine.PasswordOriginPolicy.isSameSecureOrigin(
                                                                                    sourceOrigin, passwordWebView.url.orEmpty()
                                                                                ) || showPasswordSaveDialog || pendingPasswordSave != null || pendingGeckoPasswordSave != null
                                                                            ) return@launch
                                                                            pendingPasswordSave = PendingPasswordSaveOffer(
                                                                                tabId = currentOfferTab.id,
                                                                                sourceOrigin = sourceOrigin,
                                                                                credential = com.click.browser.engine.SavedPassword(
                                                                                    host = credentialHost,
                                                                                    username = username,
                                                                                    password = password,
                                                                                    origin = credentialOrigin
                                                                                )
                                                                            )
                                                                            showPasswordSaveDialog = true
                                                                        }
                                                                    }
                                                                }
                                                                WebViewCompat.addWebMessageListener(
                                                                    this,
                                                                    "PasswordBridge",
                                                                    setOf("https://*"),
                                                                    WebViewCompat.WebMessageListener { messageView, message, sourceOrigin, isMainFrame, _ ->
                                                                        val origin = sourceOrigin.toString()
                                                                        if (com.click.browser.engine.PasswordOriginPolicy.isSameSecureOrigin(
                                                                                origin, messageView.url.orEmpty()
                                                                            )
                                                                        ) {
                                                                            passwordBridge.onMessage(origin, isMainFrame, message)
                                                                        }
                                                                    }
                                                                )
                                                            }
                                                        } catch (e: Exception) {
                                                            android.util.Log.w("PasswordManager", "Secure password bridge unavailable", e)
                                                        }

                                                        // Config settings
                                                        settings.javaScriptEnabled = javaScriptEnabledGlobal
                                                        settings.domStorageEnabled = true
                                                        // Privacy toggles (HTTPS-Only / night mode / data saver);
                                                        // MIXED_CONTENT_ALWAYS_ALLOW was removed — it contradicted HTTPS-Only.
                                                        this@MainActivity.applyPrivacyToggles(this)
                                                        settings.supportZoom()
                                                        settings.builtInZoomControls = true
                                                        settings.displayZoomControls = false

                                                                modeManager.applySettings(this, activeMode, forceDesktopMode || liveFlags.desktopDefault)
                                                                // Sharp View: full-density rendering
                                                                // (no artificial scale-down) so text
                                                                // & images stay crisp on high-DPI
                                                                // screens. Vector icons are already
                                                                // used throughout the UI.
                                                                if (liveSharpViewEnabled) setInitialScale(0)
                                                                // Experimental flags (click://flags) — AFTER mode settings
                                                                // so user-selected layout/zoom options override mode defaults.
                                                                // Consistent with the desktopDefault OR above: the flag
                                                                // participates in the mode decision, then fine-tunes it.
                                                                this@MainActivity.applyExperimentalFlags(this)
                                                                // Per-site desktop override for the initial URL.
                                                                this@MainActivity.applyPerSiteDesktop(this, currentTab.url)
                                                                this@MainActivity.applyPerSiteSettings(this, currentTab.url)
                                                                currentTab.webView = this
                                                                currentTab.browserSession?.let { session ->
                                                                    if (!this@MainActivity.liveBrowserSessions.contains(session)) {
                                                                        this@MainActivity.liveBrowserSessions.add(session)
                                                                    }
                                                                }
                                                                if (!this@MainActivity.liveWebViews.contains(this)) {
                                                                    this@MainActivity.liveWebViews.add(this)
                                                                }

                                                                setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                                                                    val scheme = try {
                                                                        android.net.Uri.parse(url).scheme?.lowercase()
                                                                    } catch (_: Exception) { null }
                                                                    if (scheme != "http" && scheme != "https") {
                                                                        Toast.makeText(this@MainActivity, "This download link is not supported.", Toast.LENGTH_SHORT).show()
                                                                    } else {
                                                                        val fileName = android.webkit.URLUtil.guessFileName(
                                                                            url, contentDisposition, mimeType
                                                                        )
                                                                        val pending = PendingBrowserDownload(
                                                                            url = url,
                                                                            fileName = fileName,
                                                                            mimeType = mimeType,
                                                                            userAgent = userAgent,
                                                                            mode = liveMode
                                                                        )
                                                                        scope.launch {
                                                                            val savedLocation = DownloadSettings.location(this@MainActivity)
                                                                            customDownloadFolderAvailable = DownloadSettings.customUri(this@MainActivity) != null
                                                                            downloadDestination = if (
                                                                                savedLocation == DownloadSettings.LOCATION_CUSTOM && !customDownloadFolderAvailable
                                                                            ) DownloadSettings.LOCATION_PHONE else savedLocation
                                                                            pendingBrowserDownload = pending
                                                                        }
                                                                    }
                                                                }

                                                                // WebView has no public native selection-action callback, so it
                                                                // deliberately offers no selected-text AI action.

                                                                // Long-press context menu (links + images).
                                                                // Attached once at WebView creation, so it works in
                                                                // every mode (Simple / Developer / Power / Advance).
                                                                // requestFocusNodeHref gives both the anchor URL
                                                                // ("url") and the image source ("src") for the
                                                                // long-pressed node; HitTestResult is the fallback.
                                                                // Returning false for plain text keeps the default
                                                                // text-selection behavior intact.
                                                                setOnLongClickListener { v ->
                                                                    val wv = v as WebView
                                                                    try {
                                                                        val handler = android.os.Handler(
                                                                            android.os.Looper.getMainLooper()
                                                                        )
                                                                        val msg = handler.obtainMessage()
                                                                        wv.requestFocusNodeHref(msg)
                                                                        val data = msg.data
                                                                        val hrefUrl = data.getString("url")
                                                                            ?.takeIf { it.isNotBlank() }
                                                                        val hrefSrc = data.getString("src")
                                                                            ?.takeIf { it.isNotBlank() }
                                                                        if (hrefUrl != null || hrefSrc != null) {
                                                                            longPressLinkUrl = hrefUrl
                                                                            longPressImageUrl = hrefSrc
                                                                            showLongPressMenu = true
                                                                            true
                                                                        } else {
                                                                            val result = wv.hitTestResult
                                                                            val extra = result.extra
                                                                                ?.takeIf { it.isNotBlank() }
                                                                            when (result.type) {
                                                                                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                                                                                    longPressLinkUrl = extra
                                                                                    longPressImageUrl = null
                                                                                    showLongPressMenu = true
                                                                                    true
                                                                                }
                                                                                WebView.HitTestResult.IMAGE_TYPE,
                                                                                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                                                                    longPressImageUrl = extra
                                                                                    longPressLinkUrl = null
                                                                                    showLongPressMenu = true
                                                                                    true
                                                                                }
                                                                                else -> false
                                                                            }
                                                                        }
                                                                    } catch (_: Exception) {
                                                                        false
                                                                    }
                                                                }

                                                                if (currentTab.url != "about:blank") {
                                                                    loadUrl(currentTab.url)
                                                                }
                                                            }
                                                        }
                                                    },
                                                    update = { browserView ->
                                                        (browserView as? WebView)?.let { webView ->
                                                            webView.settings.javaScriptEnabled = javaScriptEnabledGlobal
                                                            // Re-apply privacy toggles with the freshest state on every recomposition
                                                            this@MainActivity.applyPrivacyToggles(
                                                                webView,
                                                                httpsMode,
                                                                forceNightModeWebsites,
                                                                dataSaverEnabled
                                                            )
                                                            // Re-apply experimental flags too.
                                                            this@MainActivity.applyExperimentalFlags(webView)
                                                        }
                                                    }
                                                )
                                                if (currentTab.url != "about:blank" && currentTab.isPageLoading && !currentTab.hasCommittedContent) {
                                                    WebsiteProcessingOverlay(
                                                        theme = theme,
                                                        site = hostOfUrl(currentTab.url),
                                                        progress = currentTab.loadProgress,
                                                        animationsEnabled = flagsUi.tabAnimations && !liveReducedMotion,
                                                        onCancel = {
                                                            currentTab.browserSession?.stopLoading()
                                                            currentTab.isPageLoading = false
                                                            if (currentTab.engine == BrowserTabEngine.GECKOVIEW) {
                                                                if (!currentTab.hasCommittedContent) {
                                                                    currentTab.geckoNavigationFailure = geckoNavigationStopOutcome(
                                                                        success = false,
                                                                        failedUrl = currentTab.url,
                                                                        safeRetryUrl = null,
                                                                        wasCancelled = true
                                                                    ).failure
                                                                }
                                                            } else {
                                                                currentTab.hasCommittedContent = true
                                                            }
                                                            currentTab.loadProgress = 0
                                                        }
                                                    )
                                                }
                                                if (currentTab.engine == BrowserTabEngine.GECKOVIEW &&
                                                    currentTab.rendererRecoveryRequired
                                                ) {
                                                    Surface(
                                                        modifier = Modifier
                                                            .align(Alignment.Center)
                                                            .padding(horizontal = 20.dp)
                                                            .fillMaxWidth(),
                                                        shape = RoundedCornerShape(20.dp),
                                                        color = theme.surface,
                                                        tonalElevation = 8.dp
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(20.dp),
                                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            Text(
                                                                "This page stopped rendering",
                                                                color = theme.onSurface,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                            val isExtensionOptionsPage = currentTab.url.startsWith("moz-extension://", ignoreCase = true)
                                                            Text(
                                                                if (isExtensionOptionsPage) {
                                                                    "This add-on settings page stopped rendering. Your previous webpage stays open in its own tab; close this tab or reload the settings page."
                                                                } else {
                                                                    "The page address and browser history are still available. Reload the page or return to the previous page."
                                                                },
                                                                color = theme.onSurface.copy(alpha = 0.8f)
                                                            )
                                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                if (currentTab.canGoBack) {
                                                                TextButton(onClick = {
                                                                        currentTab.browserSession.goBackIfPossible()
                                                                    }) {
                                                                        Text("Back")
                                                                    }
                                                                }
                                                                if (isExtensionOptionsPage && tabs.size > 1) {
                                                                    TextButton(onClick = { closeTabAt(activeTabIndex) }) {
                                                                        Text("Close tab")
                                                                    }
                                                                }
                                                                Button(onClick = {
                                                                    currentTab.browserSession?.reload()
                                                                }) {
                                                                    Text("Reload")
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                val navigationFailure = currentTab.geckoNavigationFailure
                                                if (currentTab.engine == BrowserTabEngine.GECKOVIEW &&
                                                    !currentTab.rendererRecoveryRequired && navigationFailure != null
                                                ) {
                                                    Surface(
                                                        modifier = Modifier
                                                            .align(Alignment.Center)
                                                            .padding(horizontal = 20.dp)
                                                            .fillMaxWidth(),
                                                        shape = RoundedCornerShape(20.dp),
                                                        color = theme.surface,
                                                        tonalElevation = 8.dp
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(20.dp),
                                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            Text(
                                                                if (navigationFailure.wasCancelled) "Loading stopped" else "Page couldn't load",
                                                                color = theme.onSurface,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                            Text(
                                                                if (navigationFailure.wasCancelled) {
                                                                    "This page did not finish loading, so Click hasn't marked it as loaded. You can go back, open Home, or enter another address."
                                                                } else {
                                                                    "The page did not finish loading. Check your connection or return to the previous page."
                                                                },
                                                                color = theme.onSurface.copy(alpha = 0.8f)
                                                            )
                                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                if (currentTab.canGoBack) {
                                                                    TextButton(onClick = {
                                                                        currentTab.browserSession.goBackIfPossible()
                                                                    }) {
                                                                        Text("Back")
                                                                    }
                                                                }
                                                                navigationFailure.retryUrl?.let { retryUrl ->
                                                                    Button(onClick = {
                                                                        currentTab.geckoNavigationFailure = null
                                                                        currentTab.browserSession?.loadUrl(retryUrl)
                                                                    }) {
                                                                        Text("Retry")
                                                                    }
                                                                }
                                                                TextButton(onClick = {
                                                                    currentTab.geckoNavigationFailure = null
                                                                    currentTab.browserSession?.loadUrl(homeUrl())
                                                                }) {
                                                                    Text("Home")
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        }
                                        } // PageTransitionWrapper

                                    // 5. FLOATING DEV DEBUG STATUS OVERLAY + DevTools toggle
                                    // DevTools icon sits at the TOP-RIGHT corner (Prince's
                                    // request); the diagnostics overlay hangs below it.
                                    if (activeMode == BrowserMode.DEVELOPER &&
                                        currentTab.url.isNotBlank() && currentTab.url != "about:blank" &&
                                        ModeCapabilityRegistry.availability(
                                            feature = FeatureId.DEVTOOLS,
                                            mode = activeMode,
                                            engine = currentTab.engine,
                                            hasLoadedPage = currentTab.url.isNotBlank() && currentTab.url != "about:blank"
                                        ).enabled
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            // DevTools panel toggle — top-right corner.
                                            IconButton(
                                                onClick = {
                                                    val opening = !showDevToolsSheet
                                                    if (opening) {
                                                        when (devToolsTab) {
                                                            0, 3 -> refreshDevToolsPageSnapshot()
                                                            4 -> refreshDevToolsDeviceInfo()
                                                        }
                                                    }
                                                    showDevToolsSheet = opening
                                                },
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .background(
                                                        if (showDevToolsSheet) Color(0xFF7B1FA2)
                                                        else Color(0xCC1A1A2E),
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                            ) {
                                                Icon(
                                                    Icons.Default.Code,
                                                    contentDescription = "DevTools",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            if (showDebugOverlay) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                FloatingDebugOverlay(
                                                    pageLoadTime = pageLoadTime
                                                )
                                            }
                                        }
                                    }

                                    // Browsing surface: floating glowing AI button (bottom-right)
                                    // with quick popup (Ask Click AI · Summarize · Translate).
                                    // Hidden in immersive fullscreen and on the home surface.
                                    if (!immersiveMode && !showOverlays) {
                                        AiQuickFab(
                                            theme = theme,
                                            onAskAi = { if (tamperBlocked) showTamperDialog = true else showAiChat = true },
                                            onSummarize = { if (tamperBlocked) showTamperDialog = true else showAiChat = true },
                                            onTranslate = { handleFeature(FeatureId.TRANSLATE) },
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(end = 16.dp, bottom = 16.dp)
                                        )
                                    }

                                    // Periodic verified protection-status popup (top-center). One-shot
                                    // fade in/out every ~18s; browsing surface only.
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = showPrivacyPopup && !immersiveMode && !showOverlays,
                                        enter = androidx.compose.animation.fadeIn() +
                                            androidx.compose.animation.slideInVertically { -it / 2 },
                                        exit = androidx.compose.animation.fadeOut(),
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .windowInsetsPadding(WindowInsets.statusBars)
                                            .padding(top = 12.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = theme.surface.copy(alpha = 0.88f),
                                            border = BorderStroke(
                                                1.dp,
                                                theme.primary.copy(alpha = 0.35f)
                                            ),
                                            shadowElevation = 8.dp
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 14.dp,
                                                    vertical = 8.dp
                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Shield,
                                                    contentDescription = null,
                                                    tint = if (adBlockStatus.active) {
                                                        Color(0xFF22C55E)
                                                    } else {
                                                        theme.onSurface.copy(alpha = 0.45f)
                                                    },
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    adBlockStatus.label,
                                                    color = theme.onSurface,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                }

                                // 7. DevTools lives in a dismissible bottom sheet now
                                // (see overlay screens below) — the website is never
                                // covered by a fixed panel.
                            }
                            }

                            // Premium bottom nav (Phase 1): Home · Tabs (count badge) · big glowing
                            // AI center · Bookmarks · Menu. Theme follows the selected palette
                            // and per-mode light/dark choice.
                            if (showOverlays && !immersiveMode && !videoFullscreenActive) {
                                ClickBottomNav(
                                    theme = theme,
                                    tabCount = tabs.size,
                                    onHome = {
                                        currentTab.url = "about:blank"
                                        currentTab.browserSession?.loadUrl("about:blank")
                                    },
                                    onTabs = { showTabsManager = true },
                                    onAi = { if (tamperBlocked) showTamperDialog = true else showAiChat = true },
                                    onBookmarks = { showBookmarks = true },
                                    onMenu = { showBrowserMenu = true },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }

                            // SURFACE 2 — Browsing bottom nav: Menu · Home · Tabs · More.
                            // Back/forward stay in the address bar to avoid duplicate controls.
                            // (No center AI tab here — AI lives in the floating pill.)
                            // Polish: one-shot theme crossfade (battery-safe).
                            // Bottom-address-bar option: bar renders below the page,
                            // padded above the overlaid bottom nav so they never overlap.
                            if (flagsUi.bottomAddressBar && !showOverlays && !videoFullscreenActive) {
                                Box(modifier = Modifier.padding(bottom = 76.dp)) {
                                    BrowseTopBarBlock()
                                }
                            }
                            if (!showOverlays && !immersiveMode && !videoFullscreenActive &&
                                (currentTab.url == "about:blank" || currentTab.isPageLoading)
                            ) {
                                Crossfade(
                                    targetState = theme,
                                    label = "browseNavTheme",
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                ) { themed ->
                                    BrowseBottomNav(
                                        theme = themed,
                                        activeMode = activeMode,
                                        tabCount = tabs.size,
                                        onDrawerClick = { scope.launch { drawerState.open() } },
                                        onHome = {
                                            currentTab.url = "about:blank"
                                            currentTab.browserSession?.loadUrl("about:blank")
                                        },
                                        onTabs = { showTabsManager = true },
                                        onMenu = { showBrowserMenu = true }
                                    )
                                }
                            }

                            val featureMenuPageHost = try {
                                android.net.Uri.parse(currentTab.url).host?.lowercase().orEmpty()
                            } catch (_: Exception) { "" }
                            val featureMenuWideLayout = DesktopLayoutPolicy.enabledForHost(
                                featureMenuPageHost, desktopOverrides, activeMode,
                                forceDesktopMode, liveFlags.desktopDefault
                            )
                            // Keep the feature FAB on the home surface; hide it on loaded sites
                            // so it does not cover page content. Home remains in the 3-dot menu.
                            if (!immersiveMode && !videoFullscreenActive &&
                                (showOverlays || currentTab.url == "about:blank" || currentTab.isPageLoading)
                            ) {
                                FeatureMenuFabOverlay(
                                    theme = theme,
                                    expanded = showFeatureMenu,
                                    activeMode = activeMode,
                                    activeEngine = currentTab.engine,
                                    hasLoadedPage = currentTab.url.isNotBlank() && currentTab.url != "about:blank",
                                    desktopLayoutEnabled = featureMenuWideLayout,
                                    onToggle = { showFeatureMenu = !showFeatureMenu },
                                    onDismiss = { showFeatureMenu = false },
                                    onModeChange = { mode ->
                                        showFeatureMenu = false
                                        // Mode switch needs restart — ask first via dialog.
                                        if (activeMode != mode) {
                                            pendingModeSwitch = mode
                                        } else {
                                            Toast.makeText(this@MainActivity, "Already in ${mode.display().title}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onFeature = { id -> handleFeature(id) },
                                )
                            }

                            // Tab-close UNDO snackbar (Premium UI v2) — sits above
                            // the bottom nav / FAB so it never overlaps them.
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
                            )

                            // --- Visual tab switcher (Mises/Chrome-style card grid) ---
                            // Replaces the old PremiumTabsManager: thumbnails,
                            // search, per-card close, + button. The top TabStrip
                            // stays as the quick switcher.
                            if (showTabsManager) {
                                key(thumbnailVersion) {
                                    TabSwitcherScreen(
                                        tabs = tabs,
                                        activeTabIndex = activeTabIndex,
                                        theme = theme,
                                        thumbnails = TabThumbnailStore.snapshot(),
                                        animationsEnabled = flagsUi.tabAnimations,
                                        onSelectTab = { idx ->
                                            activeTabIndex = idx
                                            showTabsManager = false
                                        },
                                        onCloseTab = { idx -> closeTabAt(idx) },
                                        onNewTab = {
                                            tabs.add(TabItem(url = homeUrl(), title = "New Tab"))
                                            activeTabIndex = tabs.size - 1
                                        },
                                        onClose = { showTabsManager = false }
                                    )
                                }
                            }

                            // Overlays screens
                            if (showBookmarks) {
                                BookmarksScreen(
                                    repository = repository,
                                    onNavigate = { url ->
                                        val clean = cleanTrackingUrl(url)
                                        currentTab.url = clean
                                        currentTab.browserSession?.loadUrl(clean)
                                    },
                                    onClose = { showBookmarks = false }
                                )
                            }

                            if (showHistory) {
                                HistoryScreen(
                                    repository = repository,
                                    theme = theme,
                                    onNavigate = { url ->
                                        val clean = cleanTrackingUrl(url)
                                        currentTab.url = clean
                                        currentTab.browserSession?.loadUrl(clean)
                                    },
                                    onClearHistory = {
                                        val modeToClear = activeMode
                                        discardPendingTabUndoForMode(modeToClear)
                                        scope.launch {
                                            RecentlyClosedTabs.clearHistoryAndRecentlyClosedForMode(modeToClear) { historyMode ->
                                                repository.clearHistoryFor(historyMode)
                                            }
                                        }
                                    },
                                    onClose = { showHistory = false }
                                )
                            }

                            if (showDownloads) {
                                DownloadsScreen(
                                    repository = repository,
                                    mode = activeMode,
                                    resumeSignal = appResumeCount,
                                    onClose = { showDownloads = false }
                                )
                            }

                            if (showPlaylist) {
                                PlaylistScreen(
                                    playlistManager = playlistManager,
                                    onClose = { showPlaylist = false; playlistSeedUrl = null },
                                    seedUrl = playlistSeedUrl
                                )
                            }

                            // Live Stream player (ExoPlayer for HLS/DASH live streams).
                            if (showStreamPlayer) {
                                StreamPlayerScreen(
                                    onClose = { showStreamPlayer = false }
                                )
                            }

                            if (showInstalledAddonsPanel) {
                                val currentTabActions = if (
                                    currentTab.engine == BrowserTabEngine.GECKOVIEW && !currentTab.isIncognito
                                ) geckoTabActionsState.value[currentTab.id].orEmpty().associateBy { it.extensionId }
                                else emptyMap()
                                val panelItems = installedAddonPanelItems.map { addOn ->
                                    val action = currentTabActions[addOn.id]
                                    addOn.copy(
                                        toolbarAction = action?.let {
                                            InstalledAddonPanelAction(it.label, it.pageAction)
                                        }
                                    )
                                }
                                InstalledAddonsPanel(
                                    addOns = panelItems,
                                    isLoading = installedAddonPanelLoading,
                                    errorMessage = installedAddonPanelError,
                                    onRefresh = { refreshInstalledAddonPanel() },
                                    onOpenAction = { extensionId ->
                                        val tab = currentTab
                                        val action = geckoTabActionsState.value[tab.id]
                                            .orEmpty()
                                            .firstOrNull { it.extensionId == extensionId }
                                        if (tab.engine != BrowserTabEngine.GECKOVIEW || tab.isIncognito ||
                                            action == null || normalTabExtensionRouter?.clickAction(tab.id, action, tab.url) !=
                                            NormalTabActionDispatchResult.REQUESTED
                                        ) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "This add-on action is unavailable on the active regular tab.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onOpenOptions = { extensionId ->
                                        val tab = currentTab
                                        ExtensionBrowserRuntime.get(applicationContext).webExtensionController.list().accept(
                                            { extensions ->
                                                runOnUiThread {
                                                    val extension = extensions.orEmpty().firstOrNull {
                                                        it.id == extensionId && !it.isBuiltIn
                                                    }
                                                    val metadata = extension?.metaData
                                                    val optionsUrl = metadata?.let {
                                                        ExtensionInstallPolicy.safeExtensionOptionsPageUrl(
                                                            it.baseUrl,
                                                            it.optionsPageUrl
                                                        )
                                                    }
                                                    val baseUrl = metadata?.baseUrl?.takeIf { optionsUrl != null }
                                                    val policyBlocked = extension?.let {
                                                        extensionPolicyBlockers(it).isNotEmpty()
                                                    } ?: true
                                                    if (tabs.getOrNull(activeTabIndex)?.id != tab.id ||
                                                        tab.engine != BrowserTabEngine.GECKOVIEW || tab.isIncognito ||
                                                        extension == null || metadata?.enabled != true || policyBlocked ||
                                                        baseUrl == null || optionsUrl == null
                                                    ) {
                                                        Toast.makeText(
                                                            this@MainActivity,
                                                            "This options page is unavailable or blocked by Click's safety policy.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        val session = tab.browserSession
                                                        if (session == null) {
                                                            Toast.makeText(
                                                                this@MainActivity,
                                                                "Open options from an active regular Gecko tab.",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        } else {
                                                            showInstalledAddonsPanel = false
                                                            val optionsTab = TabItem(
                                                                url = optionsUrl,
                                                                title = "${metadata?.name.orEmpty().ifBlank { "Add-on" }} Options",
                                                                initialEngine = BrowserTabEngine.GECKOVIEW
                                                            )
                                                            approvedExtensionOptionsBasesByTab[optionsTab.id] = baseUrl
                                                            tabs.add(optionsTab)
                                                            activeTabIndex = tabs.lastIndex
                                                        }
                                                    }
                                                }
                                            },
                                            {
                                                runOnUiThread {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "Couldn't verify this add-on's current options page.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        )
                                    },
                                    onOpenStore = {
                                        val tab = currentTab
                                        showInstalledAddonsPanel = false
                                        openExtensionStore(tab)
                                    },
                                    currentTabUsesGecko = currentTab.engine == BrowserTabEngine.GECKOVIEW,
                                    currentPageSupportsActions = com.click.browser.extensions.supportsNormalTabExtensionActions(currentTab.url),
                                    onDismiss = { showInstalledAddonsPanel = false }
                                )
                            }

                            val extensionPopup = geckoTabPopupState.value
                            if (currentTab.engine == BrowserTabEngine.GECKOVIEW &&
                                !currentTab.isIncognito && extensionPopup?.ownerTabId == currentTab.id
                            ) {
                                androidx.compose.ui.window.Dialog(
                                    onDismissRequest = {
                                        normalTabExtensionRouter?.closePopup(extensionPopup.session)
                                    }
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        color = theme.surface,
                                        tonalElevation = 8.dp
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    extensionPopup.title,
                                                    modifier = Modifier.weight(1f),
                                                    color = theme.onSurface,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                IconButton(onClick = {
                                                    normalTabExtensionRouter?.closePopup(extensionPopup.session)
                                                }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Close add-on popup")
                                                }
                                            }
                                            when (extensionPopup.renderState) {
                                                com.click.browser.extensions.NormalTabExtensionPopupRenderState.LOADING -> {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().height(220.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center
                                                    ) {
                                                        CircularProgressIndicator()
                                                        Text("Loading add-on action…", modifier = Modifier.padding(start = 12.dp), color = theme.onSurface)
                                                    }
                                                }
                                                com.click.browser.extensions.NormalTabExtensionPopupRenderState.READY -> key(extensionPopup.session) {
                                                    AndroidView(
                                                        modifier = Modifier.fillMaxWidth().height(420.dp),
                                                        factory = { popupContext ->
                                                            org.mozilla.geckoview.GeckoView(popupContext).apply {
                                                                setSession(extensionPopup.session)
                                                            }
                                                        }
                                                    )
                                                }
                                                com.click.browser.extensions.NormalTabExtensionPopupRenderState.FAILED -> Column(
                                                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).padding(20.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    val retryAction = geckoTabActionsState.value[currentTab.id].orEmpty()
                                                        .firstOrNull { it.extensionId == extensionPopup.extensionId }
                                                    val retryRouter = normalTabExtensionRouter
                                                    Text("This add-on popup stopped loading or did not render. Your webpage is still open.", color = theme.onSurface)
                                                    androidx.compose.material3.TextButton(
                                                        enabled = com.click.browser.extensions.canRetryNormalTabExtensionPopup(
                                                            isOwnerSelected = extensionPopup.ownerTabId == currentTab.id,
                                                            hasAvailableAction = retryAction != null && retryRouter != null,
                                                            currentUrl = currentTab.url
                                                        ),
                                                        onClick = {
                                                            val action = geckoTabActionsState.value[currentTab.id].orEmpty()
                                                                .firstOrNull { it.extensionId == extensionPopup.extensionId }
                                                            val router = normalTabExtensionRouter
                                                            if (action == null || router == null) {
                                                                Toast.makeText(
                                                                    this@MainActivity,
                                                                    "This add-on action is no longer available. Close this popup and try the toolbar action again.",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            } else if (
                                                                router.clickAction(currentTab.id, action, currentTab.url) ==
                                                                com.click.browser.extensions.NormalTabActionDispatchResult.REQUESTED
                                                            ) {
                                                                router.closePopup(extensionPopup.session)
                                                            } else {
                                                                Toast.makeText(
                                                                    this@MainActivity,
                                                                    "This add-on action could not be retried. Your webpage is still open.",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        }
                                                    ) { Text("Try again") }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // DevTools bottom sheet (Developer mode): dismissible,
                            // partially-expanded by default so the website stays
                            // visible above it. Opened via the top-right corner
                            // icon, the drawer DevTools entries, or the FAB menu.
                            if (showDevToolsSheet && activeMode == BrowserMode.DEVELOPER) {
                                val devToolsSheetState = rememberModalBottomSheetState(
                                    skipPartiallyExpanded = false
                                )
                                ModalBottomSheet(
                                    onDismissRequest = { showDevToolsSheet = false },
                                    sheetState = devToolsSheetState,
                                    dragHandle = { BottomSheetDefaults.DragHandle() },
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    DevToolsPanel(
                                        logs = logs,
                                        networkRequests = networkRequests,
                                        domHtml = domHtml,
                                        sourcesList = sourcesList,
                                        selectedTab = devToolsTab,
                                        onTabSelected = {
                                            devToolsTab = it
                                            if (it == 0 || it == 3) refreshDevToolsPageSnapshot()
                                            if (it == 4) refreshDevToolsDeviceInfo()
                                        },
                                        onClearLogs = { logs.clear() },
                                        onClearNetwork = { networkRequests.clear() },
                                        onRefreshPageSnapshot = { refreshDevToolsPageSnapshot() },
                                        deviceInfo = devToolsDeviceInfo,
                                        onRefreshDeviceInfo = { refreshDevToolsDeviceInfo() }
                                    )
                                }
                            }

                            // click:// internal pages (chrome://-style) full-screen overlay.
                            if (showClickPage != null) {
                                ClickPageHost(
                                    pageKey = showClickPage!!,
                                    theme = theme,
                                    flags = flagsUi,
                                    versionName = BuildConfig.VERSION_NAME,
                                    versionCode = BuildConfig.VERSION_CODE,
                                    onFlagToggle = { meta, value ->
                                        scope.launch {
                                            dataStore.edit { prefs -> prefs[meta.key] = value }
                                            liveFlags = meta.set(liveFlags, value)
                                            flagsUi = liveFlags
                                            if (meta == ExperimentalFlags.Meta.BLOCK_SCREENSHOTS) {
                                                applyScreenshotFlag()
                                            }
                                            // Re-apply to all live WebViews right away.
                                            liveWebViews.forEach { wv ->
                                                try { applyExperimentalFlags(wv) } catch (_: Exception) { }
                                            }
                                            if (meta == ExperimentalFlags.Meta.DESKTOP_DEFAULT) {
                                                liveBrowserSessions.forEach { session ->
                                                    val tab = tabs.firstOrNull { it.browserSession === session }
                                                        ?: return@forEach
                                                    session.setWideLayout(wideLayoutEnabledForUrl(tab.url))
                                                }
                                                currentTab.browserSession?.reload()
                                            }
                                        }
                                    },
                                    onStringFlag = { key, value ->
                                        scope.launch {
                                            dataStore.edit { prefs -> prefs[key] = value }
                                            liveFlags = liveFlags.copy(customHomepage = value)
                                            flagsUi = liveFlags
                                            liveWebViews.forEach { wv ->
                                                try { applyExperimentalFlags(wv) } catch (_: Exception) { }
                                            }
                                        }
                                    },
                                    onOpenPage = { key ->
                                        when (ClickInternalPages.securityPageDestination(key)) {
                                            ClickInternalPages.SecurityPageDestination.CLICK_VPN -> {
                                                showClickPage = null
                                                showVpn = true
                                            }
                                            ClickInternalPages.SecurityPageDestination.V9_SHIELD -> {
                                                showClickPage = null
                                                showV9Shield = true
                                            }
                                            null -> when (key) {
                                                "settings" -> { showClickPage = null; showSettings = true }
                                                "history" -> { showClickPage = null; showHistory = true }
                                                "downloads" -> { showClickPage = null; showDownloads = true }
                                                "bookmarks" -> { showClickPage = null; showBookmarks = true }
                                                "newtab" -> {
                                                    showClickPage = null
                                                    tabs.add(TabItem(url = homeUrl(), title = "New Tab"))
                                                    activeTabIndex = tabs.size - 1
                                                }
                                                "cookies" -> { showClickPage = null; showCookieManager = true }
                                                else -> showClickPage = key
                                            }
                                        }
                                    },
                                    onClose = { showClickPage = null },
                                    onPlayGame = { gameId ->
                                        showClickPage = null
                                        showGamePlayer = gameId
                                    }
                                )
                            }

                            // Games: full-screen offline mini-game player.
                            if (showGamePlayer != null) {
                                GamePlayerScreen(
                                    gameId = showGamePlayer!!,
                                    theme = theme,
                                    onClose = { showGamePlayer = null }
                                )
                            }

                            // Confirm-exit dialog (flag).
                            if (showExitConfirm) {
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = { showExitConfirm = false },
                                    title = { androidx.compose.material3.Text("Exit Click Browser?") },
                                    confirmButton = {
                                        androidx.compose.material3.TextButton(onClick = { finish() }) {
                                            androidx.compose.material3.Text("Exit")
                                        }
                                    },
                                    dismissButton = {
                                        androidx.compose.material3.TextButton(onClick = { showExitConfirm = false }) {
                                            androidx.compose.material3.Text("Stay")
                                        }
                                    }
                                )
                            }

                            if (showLocationSettings) {
                                LocationSettingsSheet(
                                    theme = theme,
                                    onClose = { showLocationSettings = false }
                                )
                            }

                            // Crash-restore waits until the first-run tour is complete.
                            if (showRestoreDialog && onboardingReady && !showOnboarding) {
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = {
                                        showRestoreDialog = false
                                        scope.launch {
                                            SessionRestore.clearSession(
                                                this@MainActivity, V9Engine.bootMode
                                            )
                                            sessionStartupReady = true
                                        }
                                    },
                                    title = {
                                        androidx.compose.material3.Text("Restore previous session?")
                                    },
                                    text = {
                                        androidx.compose.material3.Text(
                                            "Click didn't close properly last time. " +
                                                "Restore your ${crashedTabs.size} open " +
                                                (if (crashedTabs.size == 1) "tab?" else "tabs?")
                                        )
                                    },
                                    confirmButton = {
                                        androidx.compose.material3.TextButton(onClick = {
                                            showRestoreDialog = false
                                            tabs.clear()
                                            tabs.addAll(crashedTabs.map { saved ->
                                                TabItem(
                                                    url = saved.url,
                                                    title = saved.title,
                                                    initialEngine = saved.engine,
                                                    initialGeckoSessionState = saved.geckoSessionState
                                                )
                                            })
                                            activeTabIndex = crashedActiveIndex.coerceIn(
                                                0, (tabs.size - 1).coerceAtLeast(0)
                                            )
                                            sessionStartupReady = true
                                        }) {
                                            androidx.compose.material3.Text("Restore")
                                        }
                                    },
                                    dismissButton = {
                                        androidx.compose.material3.TextButton(onClick = {
                                            showRestoreDialog = false
                                            scope.launch {
                                                SessionRestore.clearSession(
                                                    this@MainActivity, V9Engine.bootMode
                                                )
                                                sessionStartupReady = true
                                            }
                                        }) {
                                            androidx.compose.material3.Text("Start fresh")
                                        }
                                    }
                                )
                            }

                            // ---- Default browser: one-time first-launch prompt ----
                            // State + actions live here (after the restore dialog)
                            // so both this dialog and Settings can use them.
                            val isDefaultBrowser by isDefaultBrowserState.collectAsState()
                            var showDefaultBrowserPrompt by remember { mutableStateOf(false) }

                            /**
                             * Marks the first-launch prompt as shown so it
                             * never nags again (every exit path does this).
                             */
                            fun dismissDefaultBrowserPrompt() {
                                showDefaultBrowserPrompt = false
                                scope.launch {
                                    dataStore.edit { prefs ->
                                        prefs[AppSettings.DEFAULT_BROWSER_PROMPT_SHOWN] = true
                                    }
                                }
                            }

                            /**
                             * Launches the system "set Click as default
                             * browser" flow: the RoleManager role request on
                             * API 29+, else the system's Default-Apps page.
                             * Both paths are real — no dead button.
                             */
                            fun requestDefaultBrowser() {
                                if (DefaultBrowserHelper.canRequestRole(this@MainActivity)) {
                                    val roleIntent =
                                        DefaultBrowserHelper.requestRoleIntent(this@MainActivity)
                                    if (roleIntent != null) {
                                        defaultBrowserLauncher.launch(roleIntent)
                                        return
                                    }
                                }
                                DefaultBrowserHelper.openDefaultAppsSettings(this@MainActivity)
                            }

                            // External links arriving while the app is already
                            // running (onNewIntent): open each in a new
                            // foreground tab.
                            LaunchedEffect(Unit) {
                                pendingExternalUrl.collect { url ->
                                    if (url != null) {
                                        tabs.add(TabItem(url = url, title = "New Tab"))
                                        activeTabIndex = tabs.size - 1
                                        pendingExternalUrl.value = null
                                    }
                                }
                            }

                            // One-time default-browser prompt follows the first-run tour.
                            LaunchedEffect(onboardingReady, showOnboarding) {
                                if (onboardingReady && !showOnboarding) {
                                    isDefaultBrowserState.value =
                                        DefaultBrowserHelper.isDefaultBrowser(this@MainActivity)
                                    val shown =
                                        dataStore.data.first()[AppSettings.DEFAULT_BROWSER_PROMPT_SHOWN] == true
                                    if (!shown && !isDefaultBrowserState.value) {
                                        showDefaultBrowserPrompt = true
                                    }
                                }
                            }

                            if (showDefaultBrowserPrompt) {
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = { dismissDefaultBrowserPrompt() },
                                    title = {
                                        androidx.compose.material3.Text("Make Click your default browser?")
                                    },
                                    text = {
                                        androidx.compose.material3.Text(
                                            "Links you tap in other apps will open right here in Click."
                                        )
                                    },
                                    confirmButton = {
                                        androidx.compose.material3.TextButton(onClick = {
                                            dismissDefaultBrowserPrompt()
                                            requestDefaultBrowser()
                                        }) {
                                            androidx.compose.material3.Text("Set as default")
                                        }
                                    },
                                    dismissButton = {
                                        androidx.compose.material3.TextButton(onClick = {
                                            dismissDefaultBrowserPrompt()
                                        }) {
                                            androidx.compose.material3.Text("Not now")
                                        }
                                    }
                                )
                            }

                            if (showSettings) {
                                SettingsScreen(
                                    theme = theme,
                                    lightThemeVariant = lightThemeVariant,
                                    onLightThemeVariantChange = { variant ->
                                        lightThemeVariant = variant
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[AppSettings.LIGHT_THEME_VARIANT] = variant.id
                                            }
                                        }
                                    },
                                    autoRotateThemes = autoRotateThemes,
                                    onToggleAutoRotateThemes = { enabled ->
                                        autoRotateThemes = enabled
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[AppSettings.AUTO_ROTATE_THEMES] = enabled
                                            }
                                        }
                                    },
                                    wallpaperUri = wallpaperUri,
                                    onWallpaperChange = { uri ->
                                        wallpaperUri = uri
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                if (uri == null) prefs.remove(AppSettings.WALLPAPER_URI)
                                                else prefs[AppSettings.WALLPAPER_URI] = uri
                                            }
                                        }
                                    },
                                    activeMode = activeMode,
                                    onModeChange = { mode ->
                                        // Mode switch needs restart — ask first via dialog.
                                        if (activeMode != mode) {
                                            pendingModeSwitch = mode
                                        } else {
                                            Toast.makeText(this@MainActivity, "Already in ${mode.display().title}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    currentSearchEngineSetting = currentSearchEngineSetting,
                                    onSearchEngineChange = { engine ->
                                        val offered = ModePersonalization.searchEngines(activeMode)
                                        if (engine in offered) {
                                            currentSearchEngineSetting = engine
                                            scope.launch {
                                                ModePersonalization.setSearchEngine(this@MainActivity, activeMode, engine)
                                            }
                                        }
                                    },
                                    addressBarPosition = if (flagsUi.bottomAddressBar) "bottom" else "top",
                                    onAddressBarPositionChange = { pos ->
                                        val bottom = pos == "bottom"
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[ExperimentalFlags.K_BOTTOM_ADDRESS_BAR] = bottom
                                            }
                                            liveFlags = liveFlags.copy(bottomAddressBar = bottom)
                                            flagsUi = liveFlags
                                        }
                                    },
                                    onCustomizeMenu = { showSettings = false; showMenuCustomize = true },
                                    // Default browser: live status + the same
                                    // system flow as the first-launch prompt.
                                    isDefaultBrowser = isDefaultBrowser,
                                    onSetDefaultBrowser = { requestDefaultBrowser() },
                                    adBlockerEnabled = adBlockerEnabled,
                                    onToggleAdBlocker = { adBlockerEnabled = it },
                                    httpsMode = httpsMode,
                                    onHttpsModeChange = { mode ->
                                        httpsMode = AppSettings.normalizeMode(mode)
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.HTTPS_MODE] = httpsMode } }
                                    },
                                    httpsStrictExceptions = httpsStrictExceptions,
                                    onAddHttpsException = { host ->
                                        val clean = host.trim().lowercase().removePrefix("http://").removePrefix("https://").substringBefore("/")
                                        if (clean.isNotEmpty() && !httpsStrictExceptions.contains(clean)) {
                                            httpsStrictExceptions = httpsStrictExceptions + clean
                                            scope.launch {
                                                dataStore.edit { prefs ->
                                                    prefs[AppSettings.HTTPS_STRICT_EXCEPTIONS] =
                                                        AppSettings.hostsToJson(httpsStrictExceptions)
                                                }
                                            }
                                        }
                                    },
                                    onRemoveHttpsException = { host ->
                                        httpsStrictExceptions = httpsStrictExceptions - host
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[AppSettings.HTTPS_STRICT_EXCEPTIONS] =
                                                    AppSettings.hostsToJson(httpsStrictExceptions)
                                            }
                                        }
                                    },
                                    jsEnabled = javaScriptEnabledGlobal,
                                    onToggleJs = { javaScriptEnabledGlobal = it },
                                    dataSaver = dataSaverEnabled,
                                    onToggleDataSaver = { dataSaverEnabled = it },
                                    currentTabEngine = currentTab.engine,
                                    backgroundAudioEnabled = backgroundAudioEnabled,
                                    onToggleBackgroundAudio = { v ->
                                        backgroundAudioEnabled = v
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[AppSettings.BACKGROUND_AUDIO_ENABLED] = v
                                            }
                                        }
                                    },
                                    // Background news notifications (opt-in).
                                    newsNotificationsEnabled = newsNotificationsEnabled,
                                    onToggleNewsNotifications = { setNewsNotificationsEnabled(it) },
                                    newsTopics = newsTopics,
                                    onNewsTopicsChange = { topics ->
                                        newsTopics = topics
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[AppSettings.NEWS_NOTIFICATION_TOPICS] = topics
                                            }
                                        }
                                    },
                                    biometricLockEnabled = biometricLockEnabled,
                                    onToggleBiometricLock = { v ->
                                        if (v && !com.click.browser.engine.PrivateTabLock.canLock(this@MainActivity)) {
                                            // Graceful fallback: no biometric/PIN enrolled —
                                            // explain instead of enabling a dead toggle.
                                            Toast.makeText(
                                                this@MainActivity,
                                                com.click.browser.engine.PrivateTabLock.unavailableReason(this@MainActivity),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            biometricLockEnabled = v
                                            liveBiometricLockEnabled = v
                                            scope.launch {
                                                dataStore.edit { prefs ->
                                                    prefs[AppSettings.BIOMETRIC_TAB_LOCK] = v
                                                }
                                            }
                                            if (v) Toast.makeText(
                                                this@MainActivity,
                                                if (hasStrongBiometric) "Private tabs will lock behind biometrics"
                                                else "Private tabs will lock behind your device PIN",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    biometricStatusText = if (com.click.browser.engine.PrivateTabLock.canLock(this@MainActivity))
                                        if (hasStrongBiometric) "Re-lock private tabs behind fingerprint/face when the app backgrounds"
                                        else "Re-lock private tabs behind your device PIN when the app backgrounds"
                                    else com.click.browser.engine.PrivateTabLock.unavailableReason(this@MainActivity),
                                    globalTextScale = globalTextScale,
                                    onGlobalTextScaleChange = { v ->
                                        val clamped = com.click.browser.engine.TextScaleStore.clamp(v)
                                        globalTextScale = clamped
                                        liveGlobalTextScale = clamped
                                        scope.launch {
                                            com.click.browser.engine.TextScaleStore.setGlobal(this@MainActivity, clamped)
                                        }
                                    },
                                    // Brave-inspired privacy quick wins (persisted to DataStore).
                                    stripTrackingParams = stripTrackingParams,
                                    onToggleStripTrackingParams = { v ->
                                        stripTrackingParams = v
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.STRIP_TRACKING_PARAMS] = v } }
                                    },
                                    forgetfulBrowsing = forgetfulBrowsing,
                                    onToggleForgetfulBrowsing = { v ->
                                        forgetfulBrowsing = v
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.FORGETFUL_BROWSING] = v } }
                                    },
                                    forgetfulExceptions = forgetfulExceptions,
                                    onForgetfulExceptionsChange = { v ->
                                        forgetfulExceptions = v
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.FORGETFUL_BROWSING_EXCEPTIONS] = v } }
                                    },
                                    animationsEnabled = flagsUi.tabAnimations,
                                    onToggleAnimations = { enabled ->
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[ExperimentalFlags.K_TAB_ANIMATIONS] = enabled
                                            }
                                            liveFlags = liveFlags.copy(tabAnimations = enabled)
                                            flagsUi = liveFlags
                                        }
                                    },
                                    advanceIntroEnabled = advanceIntroEnabled,
                                    onToggleAdvanceIntro = { v ->
                                        advanceIntroEnabled = v
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.ADVANCE_INTRO_ENABLED] = v } }
                                    },
                                    sharpViewEnabled = sharpViewEnabled,
                                    onToggleSharpView = { v ->
                                        sharpViewEnabled = v
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.SHARP_VIEW_ENABLED] = v } }
                                        // Applies on next page load; reload now so the
                                        // user sees the change immediately.
                                        currentTab.browserSession?.reload()
                                    },
                                    onClearHistoryForMode = { mode ->
                                        discardPendingTabUndoForMode(mode)
                                        scope.launch {
                                            RecentlyClosedTabs.clearHistoryAndRecentlyClosedForMode(mode) { historyMode ->
                                                repository.clearHistoryFor(historyMode)
                                            }
                                            val historyScope = if (mode == BrowserMode.ADVANCED) {
                                                "Advance"
                                            } else {
                                                "Simple/Developer/Power shared"
                                            }
                                            Toast.makeText(
                                                this@MainActivity,
                                                "$historyScope history and recently closed tabs cleared",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onClearCookies = {
                                        // CookieManager is pinned to the current engine's data
                                        // directory — this clears the CURRENT mode's cookies only.
                                        if (currentTab.engine == BrowserTabEngine.GECKOVIEW) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Gecko per-category cookie clearing is unavailable; use Clear all current mode data.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            CookieStore.clearAll {
                                                Toast.makeText(this@MainActivity, "Cookies cleared (current profile)", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onClearCache = {
                                        if (currentTab.engine == BrowserTabEngine.GECKOVIEW) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Gecko per-category cache clearing is unavailable; use Clear all current mode data.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            currentTab.webView?.clearCache(true)
                                            Toast.makeText(this@MainActivity, "Cache cleared (current profile)", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onClearAllCurrentMode = {
                                        val modeToClear = activeMode
                                        discardPendingTabUndoForMode(modeToClear)
                                        CookieStore.clearAll()
                                        currentTab.webView?.clearCache(true)
                                        requestGeckoModeDataClear(modeToClear)
                                        val historyScope = if (modeToClear == BrowserMode.ADVANCED) {
                                            "Advance"
                                        } else {
                                            "Simple/Developer/Power shared"
                                        }
                                        scope.launch {
                                            RecentlyClosedTabs.clearHistoryAndRecentlyClosedForMode(modeToClear) { historyMode ->
                                                repository.clearHistoryFor(historyMode)
                                            }
                                            Toast.makeText(
                                                this@MainActivity,
                                                "WebView cookies/cache and Gecko profile data clear requested for ${modeToClear.display().title}; $historyScope history and recently closed tabs cleared",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onOpenCookieManager = { showSettings = false; showCookieManager = true },
                                    onOpenSiteSettings = {
                                        siteSettingsReturnToAdvancedSecurity = false
                                        showSettings = false
                                        showSiteSettingsList = true
                                    },
                                    onOpenAdvancedSecurity = { showSettings = false; showAdvancedSecurity = true },
                                    onOpenV9Shield = { showSettings = false; showV9Shield = true },
                                    onOpenTabGroups = { showSettings = false; showTabGroups = true },
                                    onOpenAccessibility = { showSettings = false; showAccessibility = true },
                                    onOpenLanguage = { showSettings = false; showLanguage = true },
                                    onOpenDownloadSettings = { showSettings = false; showDownloadSettings = true },
                                    onOpenReports = { showSettings = false; showReports = true },
                                    onOpenCatLog = { showSettings = false; showClickDiagnostics = true },
                                    onOpenPrivacySecurity = { showSettings = false; showPrivacySecurity = true },
                                    locationSummary = LocationGuard.websitesWillSee(),
                                    onOpenLocationSettings = { showLocationSettings = true },
                                    aiApiKey = aiApiKey,
                                    builtInKeyActive = BuildConfig.GROQ_API_KEY_OBF.isNotBlank(),
                                    onAiApiKeyChange = { v ->
                                        aiApiKey = v
                                        // Auto-detect provider from the key prefix.
                                        if (v.isNotBlank()) aiProvider = AppSettings.detectProvider(v)
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[AppSettings.AI_API_KEY] = v
                                                prefs[AppSettings.AI_PROVIDER] = aiProvider
                                            }
                                        }
                                    },
                                    aiProvider = aiProvider,
                                    onAiProviderChange = { v ->
                                        aiProvider = v
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.AI_PROVIDER] = v } }
                                    },
                                    aiModel = aiModel,
                                    onAiModelChange = { v ->
                                        aiModel = v
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.AI_MODEL] = v } }
                                    },
                                    onOpenPrivacyPolicy = { showSettings = false; showPrivacyPolicy = true },
                                    onOpenLicenses = { showSettings = false; showLicenses = true },
                                    onOpenHelpFeedback = { showSettings = false; showHelp = true },
                                    onClose = { showSettings = false }
                                )
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
                            if (showQuickTogglesDialog) {
                                val quickToggleAdBlock = com.click.browser.engine.QuickTogglesAvailabilityPolicy.adBlockState(
                                    engine = currentTab.engine,
                                    globalEnabled = adBlockerEnabled,
                                    siteOverride = siteSettings.forHost(hostOfUrl(currentTab.url))?.adBlock
                                )
                                QuickTogglesDialog(
                                    engine = currentTab.engine,
                                    isPrivate = currentTab.isIncognito,
                                    adblock = quickToggleAdBlock.checked,
                                    adblockToggleEnabled = quickToggleAdBlock.toggleEnabled,
                                    adblockSiteOverrideActive = quickToggleAdBlock.siteOverrideActive,
                                    onToggleAdblock = { enabled ->
                                        adBlockerEnabled = enabled
                                        liveAdBlockerEnabled = enabled
                                        if (currentTab.url.isNotBlank() && currentTab.url != "about:blank") {
                                            currentTab.browserSession?.reload()
                                        }
                                    },
                                    javaScriptEnabled = javaScriptEnabledGlobal,
                                    onToggleJavaScript = { enabled ->
                                        javaScriptEnabledGlobal = enabled
                                        (liveWebViews + listOfNotNull(currentTab.webView)).distinct().forEach { webView ->
                                            runCatching { webView.settings.javaScriptEnabled = enabled }
                                        }
                                        if (currentTab.url.isNotBlank() && currentTab.url != "about:blank") {
                                            currentTab.browserSession?.reload()
                                        }
                                    },
                                    dataSaverEnabled = dataSaverEnabled,
                                    onToggleDataSaver = { enabled ->
                                        dataSaverEnabled = enabled
                                        liveDataSaver = enabled
                                        (liveWebViews + listOfNotNull(currentTab.webView)).distinct().forEach { webView ->
                                            runCatching {
                                                applyPrivacyToggles(webView, liveHttpsMode, liveNightMode, enabled)
                                            }
                                        }
                                        if (currentTab.url.isNotBlank() && currentTab.url != "about:blank") {
                                            currentTab.browserSession?.reload()
                                        }
                                    },
                                    httpsMode = httpsMode,
                                    onCycleHttpsMode = {
                                        val nextMode = when (httpsMode) {
                                            "off" -> "standard"
                                            "standard" -> "strict"
                                            else -> "off"
                                        }
                                        httpsMode = nextMode
                                        liveHttpsMode = nextMode
                                        scope.launch {
                                            dataStore.edit { prefs -> prefs[AppSettings.HTTPS_MODE] = nextMode }
                                        }
                                    },
                                    onOpenExtensionBrowser = {
                                        showQuickTogglesDialog = false
                                        requestExtensionsForTab(currentTab)
                                    },
                                    onOpenSiteSettings = {
                                        if (!currentTab.isIncognito && currentTab.url.startsWith("http")) {
                                            showQuickTogglesDialog = false
                                            showSiteSettings = true
                                        }
                                    },
                                    onClose = { showQuickTogglesDialog = false }
                                )
                            }
                            if (showPostInstallGeckoSwitchConfirmation) {
                                AlertDialog(
                                    onDismissRequest = {
                                        showPostInstallGeckoSwitchConfirmation = false
                                        pendingGeckoSwitchTabId = null
                                        pendingInstalledExtensionName = null
                                    },
                                    title = { Text("Use ${pendingInstalledExtensionName ?: "the installed add-on"} in this tab?") },
                                    text = {
                                        Text(
                                            "The add-on is installed. To use it on this page, Click must reload this same tab in GeckoView. WebView cookies, sign-ins, page history and in-memory page state do not transfer; you may need to sign in again. You can keep this page in WebView instead."
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            val tabId = pendingGeckoSwitchTabId
                                            pendingGeckoSwitchTabId = null
                                            pendingInstalledExtensionName = null
                                            showPostInstallGeckoSwitchConfirmation = false
                                            val tab = tabs.firstOrNull { it.id == tabId }
                                            if (tab == null || tab.id != currentTab.id || tab.isIncognito || tab.engine != BrowserTabEngine.WEBVIEW) {
                                                Toast.makeText(this@MainActivity, "The original tab is no longer selected. The add-on remains installed; no page was switched.", Toast.LENGTH_LONG).show()
                                                return@TextButton
                                            }
                                            val url = (tab.browserSession as? WebViewBrowserTabSession)?.webView?.url
                                                ?.takeIf { it.isNotBlank() } ?: tab.url
                                            if (!isSupportedGeckoTabUrl(url)) {
                                                Toast.makeText(this@MainActivity, "This page cannot be reopened in GeckoView. The add-on remains installed.", Toast.LENGTH_LONG).show()
                                            } else if (replaceTabEngine(tab, BrowserTabEngine.GECKOVIEW)) {
                                                Toast.makeText(this@MainActivity, "Reloading this same tab in GeckoView. WebView state was not transferred.", Toast.LENGTH_LONG).show()
                                            }
                                        }) { Text("Reload this tab in GeckoView") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = {
                                            showPostInstallGeckoSwitchConfirmation = false
                                            pendingGeckoSwitchTabId = null
                                            pendingInstalledExtensionName = null
                                        }) { Text("Keep WebView") }
                                    }
                                )
                            }
                            if (showWebViewSwitchConfirmation) {
                                AlertDialog(
                                    onDismissRequest = {
                                        showWebViewSwitchConfirmation = false
                                        pendingWebViewSwitchTabId = null
                                    },
                                    title = { Text("Switch this tab to WebView?") },
                                    text = {
                                        Text(
                                            "The same tab will reload in WebView. Gecko cookies, extension data and back history are not copied, and add-ons will not run in this tab. Private mode is not enabled automatically."
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            val tabId = pendingWebViewSwitchTabId
                                            pendingWebViewSwitchTabId = null
                                            showWebViewSwitchConfirmation = false
                                            val tab = tabs.firstOrNull { it.id == tabId }
                                            if (tab != null && !tab.isIncognito && tab.engine == BrowserTabEngine.GECKOVIEW) {
                                                replaceTabEngine(tab, BrowserTabEngine.WEBVIEW)
                                            }
                                        }) { Text("Switch to WebView") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = {
                                            showWebViewSwitchConfirmation = false
                                            pendingWebViewSwitchTabId = null
                                        }) { Text("Cancel") }
                                    }
                                )
                            }
                            if (showPrivacyPolicy) {
                                PrivacyPolicyDialog(onClose = { showPrivacyPolicy = false })
                            }
                            if (showLicenses) {
                                com.click.browser.ui.screens.OpenSourceLicensesScreen(
                                    theme = theme,
                                    onClose = { showLicenses = false }
                                )
                            }
                            if (showAboutApp) {
                                AboutAppDialog(onClose = { showAboutApp = false })
                            }
                            // V9 Shield screen (DNS protection + resolver + profile identities).
                            if (showV9Shield) {
                                V9ShieldScreen(onClose = { showV9Shield = false })
                            }
                            // Click VPN screen (full IP tunnel via sing-box).
                            if (showVpn) {
                                com.click.browser.ui.screens.VpnScreen(
                                    onClose = { showVpn = false })
                            }
                            // Help & Feedback (FAQ + email to Prince).
                            if (showHelp) {
                                com.click.browser.ui.screens.HelpFeedbackScreen(
                                    theme = theme,
                                    onClose = { showHelp = false }
                                )
                            }
                            // Password manager: saved-logins list.
                            if (showPasswordManager && com.click.browser.engine.PasswordAccessPolicy.canOpenVault(
                                    deviceAuthenticationAvailable =
                                        com.click.browser.engine.PrivateTabLock.canLock(this@MainActivity),
                                    deviceAuthenticated = passwordManagerAuthenticated
                                )
                            ) {
                                com.click.browser.ui.screens.PasswordManagerScreen(
                                    theme = theme,
                                    onClose = {
                                        showPasswordManager = false
                                        passwordManagerAuthenticatedFlow.value = false
                                    }
                                )
                            }
                            // Privacy & Security suite: account, passwords, Safety Check, autofill, cards.
                            if (showPrivacySecurity) {
                                com.click.browser.ui.screens.PrivacySecurityScreen(
                                    theme = theme,
                                    onOpenPaymentMethods = { showPaymentMethods = true },
                                    onOpenPasswords = { requestPasswordManagerAccess() },
                                    onClose = { showPrivacySecurity = false }
                                )
                            }
                            if (showAdvancedSecurity) {
                                com.click.browser.ui.screens.AdvancedSecurityScreen(
                                    theme = theme,
                                    onOpenSiteSettings = {
                                        siteSettingsReturnToAdvancedSecurity = true
                                        showAdvancedSecurity = false
                                        showSiteSettingsList = true
                                    },
                                    onClose = { showAdvancedSecurity = false },
                                    onFixApplied = { checkId ->
                                        when (checkId) {
                                            "https" -> httpsMode = "standard"
                                            "strip_params" -> stripTrackingParams = true
                                        }
                                    }
                                )
                            }
                            if (showPaymentMethods) {
                                com.click.browser.ui.screens.PaymentMethodsScreen(
                                    theme = theme,
                                    onClose = { showPaymentMethods = false }
                                )
                            }
                            // Cookie manager: per-site cookie list.
                            if (showCookieManager) {
                                com.click.browser.ui.screens.CookieManagerScreen(
                                    theme = theme,
                                    repository = repository,
                                    onClose = { showCookieManager = false }
                                )
                            }
                            // All-sites settings: every saved per-site choice in one list.
                            if (showSiteSettingsList) {
                                com.click.browser.ui.screens.AllSiteSettingsScreen(
                                    theme = theme,
                                    repository = repository,
                                    onClose = {
                                        showSiteSettingsList = false
                                        if (siteSettingsReturnToAdvancedSecurity) {
                                            siteSettingsReturnToAdvancedSecurity = false
                                            showAdvancedSecurity = true
                                        }
                                    }
                                )
                            }
                            // Settings suite: tab groups, accessibility, language, downloads, reports.
                            if (showTabGroups) {
                                com.click.browser.ui.screens.TabGroupsScreen(
                                    theme = theme,
                                    liveTabs = tabs.map { it.id to it.title },
                                    onClose = { showTabGroups = false }
                                )
                            }
                            if (showAccessibility) {
                                com.click.browser.ui.screens.AccessibilityScreen(
                                    theme = theme,
                                    onClose = { showAccessibility = false }
                                )
                            }
                            if (showLanguage) {
                                com.click.browser.ui.screens.LanguageScreen(
                                    theme = theme,
                                    onClose = { showLanguage = false }
                                )
                            }
                            if (showDownloadSettings) {
                                com.click.browser.ui.screens.DownloadSettingsScreen(
                                    theme = theme,
                                    onClose = { showDownloadSettings = false }
                                )
                            }
                            if (showReports) {
                                com.click.browser.ui.screens.ReportsScreen(
                                    theme = theme,
                                    onClose = { showReports = false }
                                )
                            }
                            if (showClickDiagnostics) {
                                com.click.browser.ui.screens.ClickDiagnosticsScreen(
                                    theme = theme,
                                    onClose = { showClickDiagnostics = false }
                                )
                            }
                            // Per-site settings sheet (lock icon in the address bar).
                            // Mode-switch confirmation: mode changes need a process
                            // restart (WebView data dir pinned at boot). The user
                            // confirms, then we save + restart. Cancel = stay.
                            pendingModeSwitch?.let { targetMode ->
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = {
                                        com.click.browser.engine.ClickDiagnosticsLog.record(
                                            this@MainActivity,
                                            com.click.browser.engine.ClickCatLogCode.MODE_SWITCH_CANCELLED,
                                            V9Engine.bootMode,
                                            targetMode
                                        )
                                        pendingModeSwitch = null
                                    },
                                    title = {
                                        androidx.compose.material3.Text("Switch to ${targetMode.display().title}?")
                                    },
                                    text = {
                                        androidx.compose.material3.Text(
                                            "Switching profiles closes and reopens Click; Android may take a little time to relaunch it. Eligible regular web tabs are saved in this profile and restored when you return. Private and internal tabs are not included. Continue?"
                                        )
                                    },
                                    confirmButton = {
                                        androidx.compose.material3.TextButton(onClick = {
                                            val mode = targetMode
                                            pendingModeSwitch = null
                                            scope.launch {
                                                v9SwitchMode(mode, currentTab.webView, forceDesktopMode)
                                            }
                                        }) {
                                            androidx.compose.material3.Text("Restart")
                                        }
                                    },
                                    dismissButton = {
                                        androidx.compose.material3.TextButton(onClick = {
                                            com.click.browser.engine.ClickDiagnosticsLog.record(
                                                this@MainActivity,
                                                com.click.browser.engine.ClickCatLogCode.MODE_SWITCH_CANCELLED,
                                                V9Engine.bootMode,
                                                targetMode
                                            )
                                            pendingModeSwitch = null
                                        }) {
                                            androidx.compose.material3.Text("Cancel")
                                        }
                                    }
                                )
                            }
                            if (showSiteSettings) {
                                val siteHost = try {
                                    android.net.Uri.parse(currentTab.url).host?.lowercase().orEmpty()
                                } catch (_: Exception) { "" }
                                val siteMicrophoneOrigin = canonicalWebsiteMicrophoneOrigin(currentTab.url)
                                val siteMicrophoneDecision = siteMicrophoneOrigin?.let { origin ->
                                    if (currentTab.isIncognito) {
                                        currentTab.privateWebsiteMicrophonePermissions[origin]
                                    } else {
                                        websiteMicrophonePermissions[origin]
                                    }
                                }
                                val androidMicrophoneGranted = ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                val siteMicrophoneStatusLabel = when {
                                    siteMicrophoneOrigin == null -> "Blocked on insecure pages"
                                    else -> when (websiteMicrophoneStatus(
                                        siteMicrophoneDecision,
                                        androidMicrophoneGranted
                                    )) {
                                        WebsiteMicrophoneStatus.ASK -> "Ask on request"
                                        WebsiteMicrophoneStatus.ALLOWED -> "Allowed"
                                        WebsiteMicrophoneStatus.BLOCKED_FOR_SITE -> "Blocked for this site"
                                        WebsiteMicrophoneStatus.ANDROID_PERMISSION_REQUIRED ->
                                            "Android permission required"
                                    }
                                }
                                if (currentTab.engine == BrowserTabEngine.GECKOVIEW) {
                                    AlertDialog(
                                        onDismissRequest = { showSiteSettings = false },
                                        title = { Text(siteHost.ifBlank { "Site information" }) },
                                        text = {
                                            Column(
                                                modifier = Modifier
                                                    .heightIn(max = 360.dp)
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                Text("Website permissions")
                                                Spacer(Modifier.height(6.dp))
                                                Text("Camera: blocked by Click")
                                                Text("Microphone: $siteMicrophoneStatusLabel")
                                                Text("Location: blocked by Click")
                                                Text("Website notifications: blocked / unsupported")
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    if (currentTab.isIncognito) {
                                                        "A secure site must request the microphone. Click asks first, then Android may ask for RECORD_AUDIO. This choice lasts only in this private tab."
                                                    } else {
                                                        "A secure site must request the microphone. Click asks first, then Android may ask for RECORD_AUDIO. The site choice is saved only in this browser profile."
                                                    }
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    "This screen cannot read individual cookies. Clear site data removes data owned by this exact host across Gecko profiles; data from third-party hosts may remain. Normal Gecko tabs are closed and reopened before clearing so they cannot repopulate data during the operation."
                                                )
                                                if (siteMicrophoneOrigin != null && siteMicrophoneDecision != null) {
                                                    TextButton(onClick = {
                                                        if (currentTab.isIncognito) {
                                                            currentTab.privateWebsiteMicrophonePermissions.remove(siteMicrophoneOrigin)
                                                        } else {
                                                            scope.launch {
                                                                repository.clearWebsiteMicrophonePermission(siteMicrophoneOrigin)
                                                            }
                                                        }
                                                    }) { Text("Reset microphone choice") }
                                                }
                                                if (siteMicrophoneDecision == true && !androidMicrophoneGranted) {
                                                    TextButton(onClick = { openWebsiteMicrophoneAndroidSettings() }) {
                                                        Text("Open Click's Android settings")
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(
                                                enabled = siteHost.isNotBlank() && !clearSiteDataInProgress,
                                                onClick = { showClearSiteDataConfirm = true }
                                            ) { Text("Clear site data") }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showSiteSettings = false }) { Text("Done") }
                                        }
                                    )
                                } else if (siteHost.isNotEmpty()) {
                                    // Shields: live cookie count for this host.
                                    var siteCookieCount by remember(siteHost) { mutableStateOf(0) }
                                    LaunchedEffect(siteHost) {
                                        siteCookieCount = try {
                                            com.click.browser.engine.CookieStore
                                                .cookiesForHost(siteHost).size
                                        } catch (_: Exception) { 0 }
                                    }
                                    val siteCurrent =
                                        siteSettings.forHost(siteHost)
                                            ?: com.click.browser.engine.SiteSettings()
                                    val siteDesktop = DesktopLayoutPolicy.enabledForHost(
                                        siteHost, desktopOverrides, activeMode,
                                        forceDesktopMode, liveFlags.desktopDefault
                                    )
                                    val siteAdBlockCapability = ModeCapabilityRegistry.availability(
                                        feature = FeatureId.ADBLOCK,
                                        mode = activeMode,
                                        engine = currentTab.engine
                                    )
                                    com.click.browser.ui.screens.SiteSettingsSheet(
                                        theme = theme,
                                        host = siteHost,
                                        connectionSecurity = ConnectionSecurityPolicy.forPage(
                                            currentTab.url, currentTab.connectionSecurity
                                        ),
                                        current = siteCurrent,
                                        desktopMode = siteDesktop,
                                        globalJavaScript = javaScriptEnabledGlobal,
                                        globalAdBlock = adBlockerEnabled,
                                        adBlockStatusLabel = adBlockStatus.label,
                                        siteAdBlockOverrideVisible = siteAdBlockCapability.visible,
                                        siteAdBlockOverrideEnabled = siteAdBlockCapability.enabled,
                                        siteAdBlockUnavailableExplanation = siteAdBlockCapability.explanation,
                                        globalThirdPartyCookies = !liveFlags.blockThirdPartyCookies,
                                        onSettingsChange = { next ->
                                            scope.launch {
                                                repository.setSiteSettings(siteHost, next)
                                            }
                                        },
                                        onDesktopModeChange = { desktop ->
                                            scope.launch {
                                                persistDesktopOverride(siteHost, desktop)
                                                val nextWideLayout = DesktopLayoutPolicy.enabledForHost(
                                                    siteHost, liveDesktopOverrides, activeMode,
                                                    forceDesktopMode, liveFlags.desktopDefault
                                                )
                                                applyWideLayoutToTab(currentTab, nextWideLayout)
                                            }
                                        },
                                        onApplyToWebView = { next ->
                                            currentTab.webView?.let { wv ->
                                                applyPerSiteSettings(wv, currentTab.url, next)
                                            }
                                        },
                                        onReload = { currentTab.browserSession?.reload() },
                                        onDismiss = { showSiteSettings = false },
                                        // Protection status and site data for the active host.
                                        cookieCount = siteCookieCount,
                                        historyScopeDescription = if (V9Engine.bootMode == BrowserMode.ADVANCED) {
                                            "Advanced history"
                                        } else {
                                            "shared Simple, Developer, and Power history"
                                        },
                                        otherHistoryScopeDescription = if (V9Engine.bootMode == BrowserMode.ADVANCED) {
                                            "shared Simple, Developer, and Power history"
                                        } else {
                                            "Advanced history"
                                        },
                                        onClearHistoryForHost = { targetHost ->
                                            repository.clearHistoryForHost(targetHost)
                                        },
                                        onManageCookies = {
                                            showSiteSettings = false
                                            showCookieManager = true
                                        },
                                        onOpenHistory = {
                                            showSiteSettings = false
                                            showHistory = true
                                        },
                                        microphonePermissionStatusLabel = siteMicrophoneStatusLabel,
                                        microphonePermissionExplanation = if (currentTab.isIncognito) {
                                            "This choice lasts only in this private tab. Android permission is still required after you allow the site."
                                        } else {
                                            "Click asks before a secure site can use the microphone; Android permission is also required."
                                        },
                                        canResetMicrophonePermission =
                                            siteMicrophoneOrigin != null && siteMicrophoneDecision != null,
                                        showMicrophoneAndroidSettings =
                                            siteMicrophoneDecision == true && !androidMicrophoneGranted,
                                        onResetMicrophonePermission = {
                                            if (siteMicrophoneOrigin != null) {
                                                if (currentTab.isIncognito) {
                                                    currentTab.privateWebsiteMicrophonePermissions.remove(siteMicrophoneOrigin)
                                                } else {
                                                    scope.launch {
                                                        repository.clearWebsiteMicrophonePermission(siteMicrophoneOrigin)
                                                    }
                                                }
                                            }
                                        },
                                        onOpenMicrophoneAndroidSettings = {
                                            openWebsiteMicrophoneAndroidSettings()
                                        }
                                    )
                                } else {
                                    showSiteSettings = false
                                }
                            }
                            websiteMicrophoneConsent?.let { consent ->
                                AlertDialog(
                                    onDismissRequest = consent.onDismiss,
                                    title = { Text("Allow microphone for ${consent.host}?") },
                                    text = {
                                        Text(
                                            "Only this secure site can receive microphone audio after you allow it here and Android grants Click's RECORD_AUDIO permission. " +
                                                if (consent.privateTab) {
                                                    "This choice lasts only in this private tab. Camera, location, and website notifications stay blocked."
                                                } else {
                                                    "This choice is saved only in the current browser profile. Camera, location, and website notifications stay blocked."
                                                }
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = consent.onAllow) { Text("Allow microphone") }
                                    },
                                    dismissButton = {
                                        Row {
                                            TextButton(onClick = consent.onBlock) { Text("Block site") }
                                            TextButton(onClick = consent.onDismiss) { Text("Not now") }
                                        }
                                    }
                                )
                            }
                            websiteMicrophoneRecovery?.let { recovery ->
                                AlertDialog(
                                    onDismissRequest = { websiteMicrophoneRecovery = null },
                                    title = { Text("Android microphone permission is off") },
                                    text = {
                                        Text(
                                            "The microphone choice for ${recovery.origin} is saved in " +
                                                if (recovery.privateTab) {
                                                    "this private tab, but Android denied Click's RECORD_AUDIO permission. Enable microphone access in Android settings, then return to the site and try again."
                                                } else {
                                                    "this browser profile, but Android denied Click's RECORD_AUDIO permission. Enable microphone access in Android settings, then return to the site and try again."
                                                }
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            websiteMicrophoneRecovery = null
                                            openWebsiteMicrophoneAndroidSettings()
                                        }) { Text("Open Android settings") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { websiteMicrophoneRecovery = null }) {
                                            Text("Not now")
                                        }
                                    }
                                )
                            }
                            if (showClearSiteDataConfirm) {
                                AlertDialog(
                                    onDismissRequest = { if (!clearSiteDataInProgress) showClearSiteDataConfirm = false },
                                    title = { Text("Clear site data?") },
                                    text = {
                                        Text(
                                            "This clears cookies, DOM storage, caches, and site permissions owned by the exact host across Gecko profiles. It closes and reopens normal Gecko tabs. It does not clear history, private WebView tab data, or data owned by third-party hosts."
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(
                                            enabled = !clearSiteDataInProgress,
                                            onClick = {
                                                val host = runCatching { android.net.Uri.parse(currentTab.url).host.orEmpty() }.getOrDefault("")
                                                clearGeckoSiteData(host)
                                            }
                                        ) { Text(if (clearSiteDataInProgress) "Clearing…" else "Clear") }
                                    },
                                    dismissButton = {
                                        TextButton(
                                            enabled = !clearSiteDataInProgress,
                                            onClick = { showClearSiteDataConfirm = false }
                                        ) { Text("Cancel") }
                                    }
                                )
                            }

                            // V9: Power Mode signature moment — full-screen 5s
                            // Markhor intro after a Power profile boot. Tap to skip.
                            if (showHackIntro) {
                                HackIntroOverlay(onDone = { showHackIntro = false })
                            }
                            // V9: Advance Mode signature moment — full-screen 5s
                            // teal intro after an Advance engine boot. Tap to skip.
                            if (showAdvanceIntro) {
                                AdvanceIntroOverlay(onDone = { showAdvanceIntro = false })
                            }
                            // "About Advance Mode" specifications sheet.
                            if (showAdvanceSpecs) {
                                AdvanceSpecsSheet(
                                    onClose = { showAdvanceSpecs = false },
                                    onEnterAdvance = {
                                        showAdvanceSpecs = false
                                        scope.launch { drawerState.close() }
                                        // Mode switch needs restart — ask first via dialog.
                                        if (activeMode != BrowserMode.ADVANCED) {
                                            pendingModeSwitch = BrowserMode.ADVANCED
                                        } else {
                                            Toast.makeText(this@MainActivity, "Already in Advance Mode", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                            // ---- Built-in engines ----
                            // Safe Browsing interstitial (premium-styled, theme-aware).
                            val sbHit = safeBrowsingHit
                            if (sbHit != null) {
                                com.click.browser.ui.screens.SafeBrowsingWarningScreen(
                                    hit = sbHit,
                                    theme = theme,
                                    onBackToSafety = {
                                        com.click.browser.engine.SafeBrowsingManager.backToSafety()
                                    },
                                    onProceedAnyway = {
                                        com.click.browser.engine.SafeBrowsingManager.proceedAnyway()
                                    }
                                )
                            }
                            // On-device ML Kit translation sheet.
                            if (showTranslateSheet) {
                                val selectedTranslation = pendingSelectedTextTranslation
                                com.click.browser.ui.screens.TranslateSheet(
                                    theme = theme,
                                    webView = if (selectedTranslation == null) currentTab.webView else null,
                                    pageUrl = if (selectedTranslation == null) currentTab.url else "",
                                    onDismiss = {
                                        showTranslateSheet = false
                                        pendingSelectedTextTranslation = null
                                    },
                                    initialText = selectedTranslation?.selection?.text.orEmpty(),
                                    selectedTextOnly = selectedTranslation != null,
                                    selectionSessionIdentity = selectedTranslation?.selectionId.orEmpty(),
                                    selectionWasTruncated = selectedTranslation?.selection?.wasTruncated == true
                                )
                            }
                            // Long-press context menu (links + images). Every action
                            // is real: new tab / copy / share / DownloadManager
                            // save to Downloads/ClickBrowser. Works in all modes.
                            if (showLongPressMenu && (longPressLinkUrl != null || longPressImageUrl != null)) {
                                com.click.browser.ui.screens.LongPressMenuSheet(
                                    theme = theme,
                                    linkUrl = longPressLinkUrl,
                                    imageUrl = longPressImageUrl,
                                    onOpenInNewTab = { url ->
                                        tabs.add(TabItem(url = url, title = "New Tab"))
                                        activeTabIndex = tabs.size - 1
                                        showLongPressMenu = false
                                        longPressLinkUrl = null
                                        longPressImageUrl = null
                                    },
                                    onCopyLink = { url ->
                                        copyLongPressText(url, "Link copied")
                                        showLongPressMenu = false
                                        longPressLinkUrl = null
                                        longPressImageUrl = null
                                    },
                                    onShareLink = { url ->
                                        shareLongPressText(url, "Share link via")
                                        showLongPressMenu = false
                                        longPressLinkUrl = null
                                        longPressImageUrl = null
                                    },
                                    onSaveImage = { url ->
                                        saveLongPressImage(url)
                                        showLongPressMenu = false
                                        longPressLinkUrl = null
                                        longPressImageUrl = null
                                    },
                                    onCopyImageUrl = { url ->
                                        copyLongPressText(url, "Image URL copied")
                                        showLongPressMenu = false
                                        longPressLinkUrl = null
                                        longPressImageUrl = null
                                    },
                                    onShareImage = { url ->
                                        shareLongPressText(url, "Share image via")
                                        showLongPressMenu = false
                                        longPressLinkUrl = null
                                        longPressImageUrl = null
                                    },
                                    // Only offer for direct media file links — never for
                                    // streaming-service pages (Playlist refuses those).
                                    onAddToPlaylist = longPressLinkUrl
                                        ?.takeIf { PlaylistManager.looksLikeDirectMedia(it) }
                                        ?.let { _ ->
                                            { url: String ->
                                                showLongPressMenu = false
                                                longPressLinkUrl = null
                                                longPressImageUrl = null
                                                playlistSeedUrl = url
                                                showPlaylist = true
                                            }
                                        },
                                    onDismiss = {
                                        showLongPressMenu = false
                                        longPressLinkUrl = null
                                        longPressImageUrl = null
                                    }
                                )
                            }
                            // PDF: offer in-app viewing when a PDF link is tapped.
                            val offerUrl = pdfOfferUrl
                            if (offerUrl != null && viewingPdfFile == null) {
                                com.click.browser.ui.screens.PdfOfferSheet(
                                    theme = theme,
                                    pdfUrl = offerUrl,
                                    downloading = pdfDownloading,
                                    onViewInClick = {
                                        pdfDownloading = true
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val file = downloadPdfToCache(offerUrl)
                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                pdfDownloading = false
                                                if (file != null) {
                                                    viewingPdfFile = file
                                                } else {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "Couldn't download the PDF.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    pdfOfferUrl = null
                                                }
                                            }
                                        }
                                    },
                                    onDownload = {
                                        // Real download via DownloadHelper — honors the user's
                                        // download-location setting (phone / app-private / custom).
                                        val fileName = offerUrl.substringAfterLast("/").take(64).ifBlank { "document.pdf" }
                                        scope.launch {
                                            enqueueAndRecordDownload(offerUrl, fileName)
                                        }
                                        pdfOfferUrl = null
                                    },
                                    onOpenExternal = {
                                        try {
                                            startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(offerUrl)
                                                )
                                            )
                                        } catch (e: Exception) {
                                            Toast.makeText(this@MainActivity, "No app can open this PDF.", Toast.LENGTH_SHORT).show()
                                        }
                                        pdfOfferUrl = null
                                    },
                                    onDismiss = { pdfOfferUrl = null }
                                )
                            }
                            // Full-screen in-app PDF viewer.
                            val pdfFile = viewingPdfFile
                            if (pdfFile != null) {
                                com.click.browser.ui.screens.PdfViewerScreen(
                                    pdfFile = pdfFile,
                                    theme = theme,
                                    onClose = {
                                        viewingPdfFile = null
                                        pdfOfferUrl = null
                                        try { pdfFile.delete() } catch (_: Exception) { }
                                    }
                                )
                            }
                            val downloadPrompt = pendingBrowserDownload
                            if (downloadPrompt != null) {
                                val destinations = listOf(
                                    Triple(DownloadSettings.LOCATION_PHONE, "Phone storage", "Public Downloads folder"),
                                    Triple(DownloadSettings.LOCATION_APP, "App private storage", "Only available inside Click"),
                                    Triple(DownloadSettings.LOCATION_CUSTOM, "Custom folder", "Use the folder selected in Downloads settings")
                                )
                                AlertDialog(
                                    onDismissRequest = {
                                        pendingBrowserDownload?.responseBody?.let { runCatching { it.close() } }
                                        pendingBrowserDownload = null
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = null,
                                            tint = theme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    title = { Text("Save download?") },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                downloadPrompt.fileName,
                                                color = theme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            downloadPrompt.mimeType?.takeIf { it.isNotBlank() }?.let {
                                                Text(it, color = theme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text("Choose where to save it:", color = theme.onSurface, fontSize = 13.sp)
                                            destinations.forEach { (value, label, description) ->
                                                val enabled = value != DownloadSettings.LOCATION_CUSTOM || customDownloadFolderAvailable
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable(enabled = enabled) { downloadDestination = value }
                                                        .padding(vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    RadioButton(
                                                        selected = downloadDestination == value,
                                                        onClick = { downloadDestination = value },
                                                        enabled = enabled
                                                    )
                                                    Column(Modifier.padding(start = 8.dp)) {
                                                        Text(label, color = if (enabled) theme.onSurface else theme.onSurface.copy(alpha = 0.4f), fontSize = 13.sp)
                                                        Text(description, color = theme.onSurface.copy(alpha = 0.58f), fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                            if (!customDownloadFolderAvailable) {
                                                Text(
                                                    "Choose a custom folder in Settings → Downloads first.",
                                                    color = theme.onSurface.copy(alpha = 0.58f),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            val accepted = downloadPrompt
                                            pendingBrowserDownload = null
                                            val acceptedMode = accepted.mode
                                            val acceptedDestination = downloadDestination
                                            scope.launch {
                                                val responseBody = accepted.responseBody
                                                if (responseBody != null) {
                                                    val saved = DownloadHelper.enqueueResponse(
                                                        context = this@MainActivity,
                                                        responseBody = responseBody,
                                                        url = accepted.url,
                                                        fileName = accepted.fileName,
                                                        mimeType = accepted.mimeType,
                                                        locationOverride = acceptedDestination,
                                                        mode = acceptedMode
                                                    )
                                                    if (saved != null) {
                                                        repository.addDownloadItemFor(
                                                            acceptedMode,
                                                            DownloadItem(
                                                                saved.fileName,
                                                                accepted.url,
                                                                saved.location,
                                                                System.currentTimeMillis()
                                                            )
                                                        )
                                                    }
                                                } else {
                                                    enqueueAndRecordDownload(
                                                        accepted.url,
                                                        accepted.fileName,
                                                        locationOverride = acceptedDestination,
                                                        userAgent = accepted.userAgent,
                                                        mode = acceptedMode
                                                    )
                                                }
                                            }
                                        }) { Text("Save") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = {
                                            pendingBrowserDownload?.responseBody?.let { runCatching { it.close() } }
                                            pendingBrowserDownload = null
                                        }) { Text("Cancel") }
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    containerColor = theme.surface,
                                    tonalElevation = 6.dp
                                )
                            }
                            // Password manager: "Save password?" offer dialog.
                            val pwOffer = pendingPasswordSave
                            val geckoPwOffer = pendingGeckoPasswordSave
                            val pwSave = pwOffer?.credential ?: geckoPwOffer?.credential
                            if (showPasswordSaveDialog && pwSave != null) {
                                fun dismissGeckoOffer() {
                                    geckoPwOffer?.let { offer ->
                                        runCatching { offer.completion.complete(offer.request.dismiss()) }
                                    }
                                }
                                AlertDialog(
                                    onDismissRequest = {
                                        showPasswordSaveDialog = false
                                        pendingPasswordSave = null
                                        pendingGeckoPasswordSave = null
                                        dismissGeckoOffer()
                                    },
                                    title = { Text("Save password?") },
                                    text = {
                                        Text(
                                            "Save the login for ${pwSave.origin.removePrefix("https://")} " +
                                                (if (pwSave.username.isNotBlank()) "(${pwSave.username}) " else "") +
                                                "in Click's encrypted password manager? Authenticate to confirm; device authentication is also required to view or fill saved logins."
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showPasswordSaveDialog = false
                                            pendingPasswordSave = null
                                            pendingGeckoPasswordSave = null
                                            scope.launch {
                                                val managerEnabled = try {
                                                    com.click.browser.engine.PasswordManager.isEnabled(this@MainActivity)
                                                } catch (_: Exception) { false }
                                                if (!managerEnabled) {
                                                    dismissGeckoOffer()
                                                    Toast.makeText(this@MainActivity, "Password manager is off; password not saved.", Toast.LENGTH_SHORT).show()
                                                    return@launch
                                                }
                                                if (!com.click.browser.engine.PrivateTabLock.canLock(this@MainActivity)) {
                                                    dismissGeckoOffer()
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "Set up a device lock before saving passwords in Click's vault.",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    return@launch
                                                }
                                                val promptStarted = promptPasswordAction(
                                                    title = "Confirm password save",
                                                    subtitle = "Authenticate before updating Click's saved-password vault",
                                                    onCancelled = { dismissGeckoOffer() }
                                                ) {
                                                    scope.launch {
                                                        val stillEnabled = try {
                                                            com.click.browser.engine.PasswordManager.isEnabled(this@MainActivity)
                                                        } catch (_: Exception) { false }
                                                        if (!stillEnabled) {
                                                            dismissGeckoOffer()
                                                            Toast.makeText(this@MainActivity, "Password manager is off; password not saved.", Toast.LENGTH_SHORT).show()
                                                            return@launch
                                                        }
                                                        if (pwOffer != null) {
                                                            val saveTab = tabs.firstOrNull { it.id == pwOffer.tabId }
                                                            val offerStillValid = saveTab != null &&
                                                                currentTab.id == pwOffer.tabId &&
                                                                tabs.indexOf(saveTab) == activeTabIndex &&
                                                                !saveTab.isIncognito &&
                                                                com.click.browser.engine.PasswordOriginPolicy.isSameSecureOrigin(
                                                                    pwOffer.sourceOrigin,
                                                                    saveTab.webView?.url.orEmpty()
                                                                )
                                                            val saved = if (offerStillValid) {
                                                                try {
                                                                    com.click.browser.engine.PasswordManager.saveForOrigin(
                                                                        this@MainActivity,
                                                                        pwSave.origin,
                                                                        pwSave.username,
                                                                        pwSave.password
                                                                    )
                                                                    true
                                                                } catch (_: Exception) { false }
                                                            } else false
                                                            val notice = when {
                                                                !offerStillValid -> "Page changed; password not saved."
                                                                saved -> "Password saved (encrypted)."
                                                                else -> "Could not save password."
                                                            }
                                                            Toast.makeText(this@MainActivity, notice, Toast.LENGTH_SHORT).show()
                                                        } else if (geckoPwOffer != null) {
                                                            val saveTab = tabs.firstOrNull { it.id == geckoPwOffer.tabId }
                                                            val liveSession = saveTab?.browserSession as? GeckoBrowserTabSession
                                                            val originValid = com.click.browser.engine.PasswordOriginPolicy.secureOrigin(
                                                                pwSave.origin, isMainFrame = true
                                                            ) == pwSave.origin
                                                            val pageStillMatches = saveTab != null &&
                                                                com.click.browser.engine.PasswordOriginPolicy.isSameSecureOrigin(
                                                                    pwSave.origin, saveTab.url
                                                                )
                                                            val offerStillValid = saveTab != null && originValid && pageStillMatches &&
                                                                currentTab.id == geckoPwOffer.tabId &&
                                                                tabs.indexOf(saveTab) == activeTabIndex &&
                                                                saveTab.engine == BrowserTabEngine.GECKOVIEW &&
                                                                !saveTab.isIncognito && liveSession?.session === geckoPwOffer.session
                                                            if (!offerStillValid) {
                                                                dismissGeckoOffer()
                                                                Toast.makeText(this@MainActivity, "Page changed; password not saved.", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                try {
                                                                    com.click.browser.engine.PasswordManager.saveForOrigin(
                                                                        this@MainActivity,
                                                                        pwSave.origin,
                                                                        pwSave.username,
                                                                        pwSave.password
                                                                    )
                                                                    geckoPwOffer.completion.complete(
                                                                        geckoPwOffer.request.confirm(geckoPwOffer.option)
                                                                    )
                                                                    Toast.makeText(this@MainActivity, "Password saved (encrypted).", Toast.LENGTH_SHORT).show()
                                                                } catch (_: Exception) {
                                                                    dismissGeckoOffer()
                                                                    Toast.makeText(this@MainActivity, "Could not save password.", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                if (!promptStarted) {
                                                    dismissGeckoOffer()
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "Authentication could not be started; password not saved.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }) {
                                            Text("Save")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = {
                                            showPasswordSaveDialog = false
                                            pendingPasswordSave = null
                                            pendingGeckoPasswordSave = null
                                            dismissGeckoOffer()
                                        }) {
                                            Text("Not now")
                                        }
                                    }
                                )
                            }
                            if (showTamperDialog) {
                                // Warning for repackaged/modified copies.
                                // Dismissible (back press / outside tap / Dismiss), but AI chat
                                // stays disabled while tamperBlocked is true (Kilo review).
                                AlertDialog(
                                    onDismissRequest = { showTamperDialog = false },
                                    title = { Text("Warning") },
                                    text = {
                                        Text("Warning: this copy of Click Browser appears modified or repackaged. AI features are disabled for your safety.")
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { showTamperDialog = false }) {
                                            Text("Understood")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showTamperDialog = false }) {
                                            Text("Dismiss")
                                        }
                                    }
                                )
                            }
                            var aiChatExpanded by remember { mutableStateOf(false) }
                            val aiChatPanelShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                            // AI chat panel — one-shot slide/spring entrance (battery-safe:
                            // no continuous animation).
                            androidx.compose.animation.AnimatedVisibility(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .fillMaxHeight(AiChatPanelLayoutPolicy.heightFraction(aiChatExpanded))
                                    .shadow(12.dp, aiChatPanelShape)
                                    .clip(aiChatPanelShape),
                                visible = showAiChat && !tamperBlocked,
                                enter = androidx.compose.animation.slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                    )
                                ) + androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = androidx.compose.animation.core.tween(220)
                                ) + androidx.compose.animation.fadeOut(
                                    animationSpec = androidx.compose.animation.core.tween(180)
                                )
                            ) {
                            if (showAiChat && !tamperBlocked) {
                                // Key resolution: the user's own Settings key wins; otherwise fall
                                // back to the built-in Groq key (XOR-obfuscated in BuildConfig,
                                // decoded at runtime — see KeyObfuscator).
                                val effectiveAiKey = aiApiKey.ifBlank {
                                    KeyObfuscator.decode(BuildConfig.GROQ_API_KEY_OBF)
                                }
                                val usingBuiltInKey = aiApiKey.isBlank() &&
                                    BuildConfig.GROQ_API_KEY_OBF.isNotBlank()
                                val aiComparisonTabOptions = tabs.mapNotNull { tab ->
                                    val webView = tab.webView ?: return@mapNotNull null
                                    val tabUrl = webView.url?.takeIf { it.isNotBlank() } ?: tab.url
                                    if (
                                        AiTabComparisonPolicy.isEligible(
                                            isPrivateTab = tab.isIncognito,
                                            engine = tab.engine,
                                            hasLoadedContent = tab.hasCommittedContent && !tab.isPageLoading && webView.progress >= 100,
                                            pageUrl = tabUrl
                                        )
                                    ) {
                                        AiTabComparisonOption(id = tab.id, title = tab.title, url = tabUrl)
                                    } else {
                                        null
                                    }
                                }
                                AiChatScreen(
                                    apiKey = effectiveAiKey,
                                    providerId = if (usingBuiltInKey) "groq" else aiProvider,
                                    model = aiModel,
                                    secureDns = secureDnsEnabled,
                                    usingBuiltInKey = usingBuiltInKey,
                                    isExpanded = aiChatExpanded,
                                    onToggleExpanded = { aiChatExpanded = !aiChatExpanded },
                                    pageTitle = currentTab.title,
                                    pageUrl = currentTab.url,
                                    pageContextIdentity = currentTab.id,
                                    selectedText = pendingAiSelectedText?.selection,
                                    selectedTextSessionIdentity = pendingAiSelectedText?.selectionId.orEmpty(),
                                    canSharePageContext = pendingAiSelectedText == null &&
                                        com.click.browser.engine.AiPageContextPolicy.canOfferPageContext(
                                        isPrivateTab = currentTab.isIncognito,
                                        isWebViewSupported = currentTab.engine == BrowserTabEngine.WEBVIEW && currentTab.webView != null,
                                        pageUrl = currentTab.url
                                    ),
                                    onCancelSelectedText = {
                                        pendingAiSelectedText = null
                                        showAiChat = false
                                    },
                                    onRequestPageContext = { onCaptured ->
                                        val requestedTabId = currentTab.id
                                        val requestedUrl = currentTab.url
                                        val requestedWebView = currentTab.webView
                                        val mayCapture = pendingAiSelectedText == null &&
                                            com.click.browser.engine.AiPageContextPolicy.canOfferPageContext(
                                            isPrivateTab = currentTab.isIncognito,
                                            isWebViewSupported = currentTab.engine == BrowserTabEngine.WEBVIEW && requestedWebView != null,
                                            pageUrl = requestedUrl
                                        )
                                        if (!mayCapture || requestedWebView == null) {
                                            onCaptured("")
                                        } else {
                                            requestedWebView.evaluateJavascript(
                                                "(function(){try{return (document.body&&document.body.innerText||'').replace(/\\s+/g,' ').trim().slice(0,3000);}catch(e){return '';}})()"
                                            ) { raw ->
                                                val samePageStillActive = currentTab.id == requestedTabId &&
                                                    currentTab.url == requestedUrl &&
                                                    pendingAiSelectedText == null &&
                                                    !currentTab.isIncognito &&
                                                    currentTab.engine == BrowserTabEngine.WEBVIEW
                                                if (!samePageStillActive) {
                                                    onCaptured("")
                                                } else {
                                                    val excerpt = try {
                                                        if (raw != null && raw != "null") {
                                                            org.json.JSONTokener(raw).nextValue().toString()
                                                        } else {
                                                            ""
                                                        }
                                                    } catch (_: Exception) {
                                                        ""
                                                    }
                                                    onCaptured(excerpt.take(com.click.browser.engine.AiPageContextPolicy.MAX_EXCERPT_CHARS))
                                                }
                                            }
                                        }
                                    },
                                    comparisonTabs = aiComparisonTabOptions,
                                    onRequestTabComparisonContext = { requestedTabs, onCaptured ->
                                        if (pendingAiSelectedText != null) {
                                            onCaptured(null)
                                            null
                                        } else {
                                            val captureTargets = requestedTabs.mapNotNull { option ->
                                                val tab = tabs.firstOrNull { it.id == option.id }
                                                val webView = tab?.webView
                                                val actualUrl = webView?.url?.takeIf { it.isNotBlank() } ?: tab?.url.orEmpty()
                                                if (
                                                    tab != null && webView != null &&
                                                    tab.title == option.title && actualUrl == option.url &&
                                                    AiTabComparisonPolicy.isEligible(
                                                        isPrivateTab = tab.isIncognito,
                                                        engine = tab.engine,
                                                        hasLoadedContent = tab.hasCommittedContent && !tab.isPageLoading && webView.progress >= 100,
                                                        pageUrl = actualUrl
                                                    )
                                                ) {
                                                    Triple(option, tab, webView)
                                                } else {
                                                    null
                                                }
                                            }
                                            if (captureTargets.size != requestedTabs.size) {
                                                onCaptured(null)
                                                null
                                            } else {
                                                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                                                val capturedTextById = mutableMapOf<String, String>()
                                                var finished = false
                                                val timeout = Runnable {
                                                    if (!finished) {
                                                        finished = true
                                                        onCaptured(null)
                                                    }
                                                }
                                                fun finishCapture(result: Map<String, String>?) {
                                                    if (finished) return
                                                    finished = true
                                                    handler.removeCallbacks(timeout)
                                                    onCaptured(result)
                                                }
                                                fun captureAt(index: Int) {
                                                    if (finished) return
                                                    if (index >= captureTargets.size) {
                                                        finishCapture(capturedTextById.toMap())
                                                        return
                                                    }
                                                    val (option, expectedTab, expectedWebView) = captureTargets[index]
                                                    runCatching {
                                                        expectedWebView.evaluateJavascript(
                                                            AiTabComparisonPolicy.VISIBLE_TEXT_SCRIPT
                                                        ) response@{ raw ->
                                                            if (finished) return@response
                                                            val liveTab = tabs.firstOrNull { it.id == option.id }
                                                            val liveWebView = liveTab?.webView
                                                            val liveUrl = liveWebView?.url?.takeIf { it.isNotBlank() }
                                                                ?: liveTab?.url.orEmpty()
                                                            val stillMatches = liveTab === expectedTab &&
                                                                liveWebView === expectedWebView &&
                                                                pendingAiSelectedText == null &&
                                                                liveTab?.title == option.title && liveUrl == option.url &&
                                                                liveTab?.let {
                                                                    AiTabComparisonPolicy.isEligible(
                                                                        isPrivateTab = it.isIncognito,
                                                                        engine = it.engine,
                                                                        hasLoadedContent = it.hasCommittedContent && !it.isPageLoading && expectedWebView.progress >= 100,
                                                                        pageUrl = liveUrl
                                                                    )
                                                                } == true
                                                            if (!stillMatches) {
                                                                finishCapture(null)
                                                                return@response
                                                            }
                                                            val visibleText = try {
                                                                if (raw != null && raw != "null") {
                                                                    org.json.JSONTokener(raw).nextValue().toString()
                                                                } else {
                                                                    ""
                                                                }
                                                            } catch (_: Exception) {
                                                                ""
                                                            }
                                                            if (visibleText.isBlank()) {
                                                                finishCapture(null)
                                                            } else {
                                                                capturedTextById[option.id] = visibleText.take(
                                                                    AiTabComparisonPolicy.MAX_EXCERPT_CHARS_PER_TAB
                                                                )
                                                                captureAt(index + 1)
                                                            }
                                                        }
                                                    }.onFailure {
                                                        finishCapture(null)
                                                    }
                                                }
                                                handler.postDelayed(timeout, 12_000L)
                                                captureAt(0)
                                                val cancelCapture: () -> Unit = {
                                                    if (!finished) {
                                                        finished = true
                                                        handler.removeCallbacks(timeout)
                                                    }
                                                }
                                                cancelCapture
                                            }
                                        }
                                    },
                                    onOpenUrl = { targetUri ->
                                        pendingAiSelectedText = null
                                        showAiChat = false
                                        val destination = cleanTrackingUrl(formatUrl(targetUri, currentSearchEngineSetting))
                                        currentTab.url = destination
                                        currentTab.browserSession?.loadUrl(destination)
                                    },
                                    onReportMessage = { text ->
                                        // In-app reporting (Play AI-Generated Content policy):
                                        // persist the flagged response on-device, then confirm.
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[AppSettings.AI_REPORTS_JSON] =
                                                    AppSettings.appendReport(prefs[AppSettings.AI_REPORTS_JSON], text)
                                            }
                                        }
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Thanks — report recorded.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onOpenSettings = {
                                        pendingAiSelectedText = null
                                        showAiChat = false
                                        showSettings = true
                                    },
                                    onClose = {
                                        pendingAiSelectedText = null
                                        showAiChat = false
                                    }
                                )
                            }
                            } // AnimatedVisibility (AI chat slide/spring)
                            if (showPrivacyGuards) {
                                PrivacyGuardsScreen(
                                    dntEnabled = dntEnabled,
                                    onToggleDnt = { value ->
                                        dntEnabled = value
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.DNT_ENABLED] = value } }
                                    },
                                    gpcEnabled = gpcEnabled,
                                    onToggleGpc = { value ->
                                        gpcEnabled = value
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.GPC_ENABLED] = value } }
                                    },
                                    secureDns = secureDnsEnabled,
                                    onToggleSecureDns = { value ->
                                        secureDnsEnabled = value
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.SECURE_DNS_ENABLED] = value } }
                                    },
                                    webrtcRunning = webrtcTestRunning,
                                    webrtcResult = webrtcResult,
                                    onRunWebrtcTest = { runWebrtcLeakTest() },
                                    onClose = { showPrivacyGuards = false }
                                )
                            }

                            // Find in Page overlay
                            if (showFindInPageDialog) {
                                val clearFindMatches: () -> Unit = {
                                    val geckoSession = currentTab.browserSession as? GeckoBrowserTabSession
                                    if (geckoSession != null) geckoSession.session.finder.clear()
                                    else currentTab.webView?.clearMatches()
                                }
                                val dismissFindInPage: () -> Unit = {
                                    FindInPagePolicy.dismiss(
                                        clearMatches = clearFindMatches,
                                        hideDialog = { showFindInPageDialog = false }
                                    )
                                }
                                AlertDialog(
                                    onDismissRequest = dismissFindInPage,
                                    title = { Text("Find in Page") },
                                    text = {
                                        OutlinedTextField(
                                            value = findQuery,
                                            onValueChange = { query ->
                                                findQuery = query
                                                when (val action = FindInPagePolicy.actionForQuery(query)) {
                                                    FindInPageAction.Clear -> clearFindMatches()
                                                    is FindInPageAction.Search -> {
                                                        val geckoSession = currentTab.browserSession as? GeckoBrowserTabSession
                                                        if (geckoSession != null) {
                                                            geckoSession.session.finder.setDisplayFlags(
                                                                org.mozilla.geckoview.GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL
                                                            )
                                                            geckoSession.session.finder.find(
                                                                action.query,
                                                                org.mozilla.geckoview.GeckoSession.FINDER_FIND_FORWARD
                                                            )
                                                        } else currentTab.webView?.findAllAsync(action.query)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("Find text...") }
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            val geckoSession = currentTab.browserSession as? GeckoBrowserTabSession
                                            if (geckoSession != null) geckoSession.session.finder.find(
                                                null, org.mozilla.geckoview.GeckoSession.FINDER_FIND_BACKWARDS
                                            ) else currentTab.webView?.findNext(false)
                                        }) {
                                            Text("Prev")
                                        }
                                    },
                                    dismissButton = {
                                        Row {
                                            TextButton(onClick = {
                                                val geckoSession = currentTab.browserSession as? GeckoBrowserTabSession
                                                if (geckoSession != null) geckoSession.session.finder.find(
                                                    null, org.mozilla.geckoview.GeckoSession.FINDER_FIND_FORWARD
                                                ) else currentTab.webView?.findNext(true)
                                            }) {
                                                Text("Next")
                                            }
                                            TextButton(onClick = dismissFindInPage) {
                                                Text("Close")
                                            }
                                        }
                                    }
                                )
                            }

                            // Chrome/Mises-style browser menu (bottom sheet). Every item
                            // works — New tab, New private tab, Tabs switcher,
                            // History, Delete browsing data, Downloads, Bookmarks,
                            // Recent tabs, Share,
                            // Find in page, Translate, Wide layout, Settings.
                            if (showBrowserMenu) {
                                val menuPageHost = try {
                                    android.net.Uri.parse(currentTab.url).host?.lowercase().orEmpty()
                                } catch (_: Exception) { "" }
                                val menuDesktopForSite = DesktopLayoutPolicy.enabledForHost(
                                    menuPageHost, desktopOverrides, activeMode,
                                    forceDesktopMode, liveFlags.desktopDefault
                                )
                                BrowserMenuSheet(
                                    theme = theme,
                                    mode = activeMode,
                                    isDesktopForSite = menuDesktopForSite,
                                    onHome = {
                                        showBrowserMenu = false
                                        currentTab.url = "about:blank"
                                        currentTab.browserSession?.loadUrl("about:blank")
                                    },
                                    onNewTab = {
                                        showBrowserMenu = false
                                        tabs.add(TabItem(url = homeUrl(), title = "New Tab"))
                                        activeTabIndex = tabs.size - 1
                                    },
                                    onNewPrivateTab = {
                                        showBrowserMenu = false
                                        tabs.add(TabItem(url = "about:blank", title = "Private Tab", isIncognito = true))
                                        activeTabIndex = tabs.size - 1
                                        Toast.makeText(this@MainActivity, "Private tab opened — history is not recorded.", Toast.LENGTH_SHORT).show()
                                    },
                                    onOpenTabSwitcher = {
                                        showBrowserMenu = false
                                        showTabsManager = true
                                    },
                                    onHistory = { showBrowserMenu = false; showHistory = true },
                                    onDeleteBrowsingData = {
                                        showBrowserMenu = false
                                        showDeleteBrowsingConfirm = true
                                    },
                                    onDownloads = {
                                        showBrowserMenu = false
                                        showDownloads = true
                                    },
                                    onPlaylist = {
                                        showBrowserMenu = false
                                        playlistSeedUrl = null
                                        showPlaylist = true
                                    },
                                    onStreamPlayer = {
                                        showBrowserMenu = false
                                        showStreamPlayer = true
                                    },
                                    onBookmarks = { showBrowserMenu = false; showBookmarks = true },
                                    onAddBookmark = {
                                        showBrowserMenu = false
                                        val bookmark = com.click.browser.data.BookmarkAddPolicy.prepare(
                                            currentTab.title,
                                            currentTab.url
                                        )
                                        if (bookmark == null) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Open a valid web page before adding a bookmark.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            scope.launch {
                                                val added = repository.addBookmark(bookmark)
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    if (added) "Bookmark added." else "Already bookmarked.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    onGames = { showBrowserMenu = false; showClickPage = "games" },
                                    onRecentTabs = { showBrowserMenu = false; showRecentTabs = true },
                                    onShare = {
                                        showBrowserMenu = false
                                        val url = currentTab.url
                                        if (url == "about:blank") {
                                            Toast.makeText(this@MainActivity, "Nothing to share yet.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val share = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, currentTab.title)
                                                putExtra(Intent.EXTRA_TEXT, "${currentTab.title}\n$url")
                                            }
                                            startActivity(Intent.createChooser(share, "Share page via"))
                                        }
                                    },
                                    onFindInPage = {
                                        showBrowserMenu = false
                                        findQuery = ""
                                        showFindInPageDialog = true
                                    },
                                    onTranslate = {
                                        showBrowserMenu = false
                                        val url = currentTab.url
                                        if (url == "about:blank" || !url.startsWith("http")) {
                                            Toast.makeText(this@MainActivity, "Open a page first to translate it.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            // On-device ML Kit translation sheet.
                                            showTranslateSheet = true
                                        }
                                    },
                                    onToggleDesktopSite = {
                                        showBrowserMenu = false
                                        scope.launch {
                                            if (menuPageHost.isEmpty() || currentTab.url == "about:blank") {
                                                Toast.makeText(this@MainActivity, "Open a page first.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                persistDesktopOverride(menuPageHost, !menuDesktopForSite)
                                                val nextWideLayout = DesktopLayoutPolicy.enabledForHost(
                                                    menuPageHost, liveDesktopOverrides, activeMode,
                                                    forceDesktopMode, liveFlags.desktopDefault
                                                )
                                                applyWideLayoutToTab(currentTab, nextWideLayout)
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    if (nextWideLayout) "Wide layout enabled for $menuPageHost"
                                                    else "Mobile layout restored for $menuPageHost",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    onSiteSettings = {
                                        showBrowserMenu = false
                                        if (currentTab.url == "about:blank" || !currentTab.url.startsWith("http")) {
                                            Toast.makeText(this@MainActivity, "Open a page first to change its site settings.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            showSiteSettings = true
                                        }
                                    },
                                    onSettings = { showBrowserMenu = false; showSettings = true },
                                    onVpn = { showBrowserMenu = false; showVpn = true },
                                    visibleItems = MenuCustomization.effectiveVisibleItems(menuOrder, menuHidden),
                                    onCustomizeMenu = { showBrowserMenu = false; showMenuCustomize = true },
                                    onDismiss = { showBrowserMenu = false }
                                )
                            }

                            // "Customize menu" sheet (reorder + hide menu items).
                            if (showMenuCustomize) {
                                MenuCustomizeSheet(
                                    theme = theme,
                                    initialOrder = menuOrder,
                                    initialHidden = menuHidden,
                                    onSave = { order, hidden ->
                                        menuOrder = order
                                        menuHidden = hidden
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[MenuCustomization.MENU_ORDER_JSON] =
                                                    MenuCustomization.orderToJson(order)
                                                prefs[MenuCustomization.MENU_HIDDEN_JSON] =
                                                    MenuCustomization.hiddenToJson(hidden)
                                            }
                                        }
                                    },
                                    onDismiss = { showMenuCustomize = false }
                                )
                            }

                            // Recent tabs (recently closed, re-openable)
                            if (showRecentTabs) {
                                RecentTabsScreen(
                                    closedTabs = RecentlyClosedTabs.list(activeMode),
                                    theme = theme,
                                    onReopen = { ct ->
                                        reopenClosedTab(ct)
                                        showRecentTabs = false
                                    },
                                    onRemove = { ct -> RecentlyClosedTabs.remove(ct) },
                                    onClearAll = { RecentlyClosedTabs.clear(activeMode) },
                                    onClose = { showRecentTabs = false }
                                )
                            }

                            // Delete browsing data — confirmation first, then close all sessions before clearing.
                            if (showDeleteBrowsingConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteBrowsingConfirm = false },
                                    title = { Text("Delete browsing data?") },
                                    text = {
                                        Text(
                                            "This closes all open tabs (including private tabs), clears Click history and recently closed tabs for the current history profile (shared by Simple/Developer/Power, separate in Advance), and clears the current mode's session snapshot. It waits for WebView cookie removal and requests WebView cache/DOM-storage deletion. GeckoView's mode-specific purge is requested after its sessions close; GeckoView 153 exposes no completion result for this context-scoped API, so Click cannot verify that backend purge finished. A fresh blank tab will open."
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            val modeToClear = activeMode
                                            discardPendingTabUndoForMode(modeToClear)
                                            scope.launch {
                                                normalTabExtensionRouter?.closePopup()
                                                tabs.forEach { tab ->
                                                    val webView = tab.webView
                                                    if (webView != null) {
                                                        runCatching { webView.stopLoading() }
                                                        runCatching { webView.clearHistory() }
                                                        runCatching { webView.clearCache(true) }
                                                        liveWebViews.remove(webView)
                                                    }
                                                    val session = tab.browserSession
                                                    if (session != null) {
                                                        runCatching { session.stopLoading() }
                                                        runCatching { session.detachFromParent() }
                                                        liveBrowserSessions.remove(session)
                                                        runCatching { session.destroy() }
                                                        tab.attachBrowserSession(null)
                                                    } else {
                                                        runCatching { webView?.destroy() }
                                                    }
                                                    tab.takePendingGeckoSession()?.let { pending -> runCatching { pending.close() } }
                                                }
                                                tabs.clear()
                                                requestGeckoModeDataClear(modeToClear)
                                                RecentlyClosedTabs.clearHistoryAndRecentlyClosedForMode(modeToClear) { historyMode ->
                                                    repository.clearHistoryFor(historyMode)
                                                }
                                                com.click.browser.engine.SessionRestore.clearSession(this@MainActivity, modeToClear)
                                                kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
                                                    android.webkit.CookieManager.getInstance().removeAllCookies { removed ->
                                                        if (continuation.isActive) {
                                                            continuation.resumeWith(Result.success(removed))
                                                        }
                                                    }
                                                }
                                                android.webkit.CookieManager.getInstance().flush()
                                                android.webkit.WebStorage.getInstance().deleteAllData()
                                                geckoTabActionsState.value = emptyMap()
                                                geckoTabPopupState.value = null
                                                tabs.add(TabItem(url = "about:blank", title = "New Tab"))
                                                activeTabIndex = 0
                                                showDeleteBrowsingConfirm = false
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "History and recently closed tabs cleared; WebView cookies cleared; DOM storage/cache deletion requested. Gecko profile purge requested; GeckoView 153 exposes no completion result.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }) {
                                            Text("Close tabs and clear")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteBrowsingConfirm = false }) { Text("Cancel") }
                                    }
                                )
                            }

                            // Text scaling (accessibility): live slider + optional
                            // per-site persistence. Applies instantly to the
                            // current tab via WebView textZoom.
                            if (showTextScaleSheet) {
                                val sheetHost = try {
                                    android.net.Uri.parse(currentTab.url).host?.lowercase().orEmpty()
                                } catch (_: Exception) { "" }
                                val hostOverride = sheetHost.isNotEmpty() && hostTextScales.containsKey(sheetHost)
                                com.click.browser.ui.screens.TextScaleSheet(
                                    theme = theme,
                                    host = sheetHost,
                                    currentScale = sheetScale,
                                    globalScale = globalTextScale,
                                    hostHasOverride = hostOverride,
                                    onScaleChange = { v ->
                                        val clamped = com.click.browser.engine.TextScaleStore.clamp(v)
                                        sheetScale = clamped
                                        currentTab.webView?.settings?.textZoom = clamped
                                    },
                                    onSaveForSite = { save ->
                                        scope.launch {
                                            if (sheetHost.isNotEmpty()) {
                                                if (save) {
                                                    com.click.browser.engine.TextScaleStore.setHostScale(
                                                        this@MainActivity, sheetHost, sheetScale
                                                    )
                                                    hostTextScales = hostTextScales + (sheetHost to sheetScale)
                                                } else {
                                                    com.click.browser.engine.TextScaleStore.clearHostScale(
                                                        this@MainActivity, sheetHost
                                                    )
                                                    hostTextScales = hostTextScales - sheetHost
                                                }
                                                liveHostTextScales = hostTextScales
                                            }
                                        }
                                    },
                                    onResetGlobal = {
                                        scope.launch {
                                            if (sheetHost.isNotEmpty()) {
                                                com.click.browser.engine.TextScaleStore.clearHostScale(
                                                    this@MainActivity, sheetHost
                                                )
                                                hostTextScales = hostTextScales - sheetHost
                                                liveHostTextScales = hostTextScales
                                            }
                                            sheetScale = globalTextScale
                                            currentTab.webView?.settings?.textZoom = globalTextScale
                                        }
                                    },
                                    onDismiss = { showTextScaleSheet = false }
                                )
                            }

                            // Biometric private-tab lock overlay — above
                            // everything except the cold-start splash. Auto-
                            // prompts for authentication when it appears.
                            if (privateLocked) {
                                LaunchedEffect(Unit) { promptUnlockPrivateTabs() }
                                com.click.browser.ui.screens.PrivateTabLockOverlay(
                                    theme = theme,
                                    useBiometricLabel = hasStrongBiometric,
                                    onUnlock = { promptUnlockPrivateTabs() }
                                )
                            }

                            // First launch: feature cards replace the every-launch intro.
                            if (onboardingReady && showOnboarding && !privateLocked) {
                                FirstRunOnboarding(onFinished = { finishFirstRunOnboarding() })
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Extracts an http(s) URL from an incoming ACTION_VIEW intent (e.g. a
     * link tapped in another app when Click is the default browser).
     * Returns null for anything else — only web links are ever opened.
     */
    private fun externalViewUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val url = intent.dataString?.trim().orEmpty()
        return url.takeIf {
            it.startsWith("http://", ignoreCase = true) ||
                it.startsWith("https://", ignoreCase = true)
        }
    }

    // onNewIntent is defined once near the top of this class (handles both
    // news-notification deep links and external ACTION_VIEW links).

    /** New-tab URL: custom homepage flag or the premium home (about:blank). */
    private fun homeUrl(): String {
        val custom = liveFlags.customHomepage.trim()
        return if (custom.isEmpty()) "about:blank" else custom
    }

    /**
     * Power-mode desktop host preference: rewrites known mobile hosts to their
     * desktop equivalents so sites remain on the selected desktop host.
     * e.g. m.youtube.com -> www.youtube.com, m.facebook.com -> www.facebook.com
     */
    private fun forceDesktopHost(url: String): String {
        var result = url
        val mobileToDesktop = mapOf(
            "m.youtube.com" to "www.youtube.com",
            "youtu.be" to "www.youtube.com", // shorts links open full site
            "m.facebook.com" to "www.facebook.com",
            "m.twitter.com" to "x.com",
            "mobile.twitter.com" to "x.com",
            "m.instagram.com" to "www.instagram.com",
            "m.reddit.com" to "www.reddit.com",
            "old.reddit.com" to "www.reddit.com",
            "m.wikipedia.org" to "en.wikipedia.org",
            "m.amazon.com" to "www.amazon.com",
            "m.ebay.com" to "www.ebay.com"
        )
        for ((mobile, desktop) in mobileToDesktop) {
            if (result.contains("://$mobile/") || result.contains("://$mobile?") ||
                result.endsWith("://$mobile")
            ) {
                result = result.replace("://$mobile", "://$desktop")
                break
            }
        }
        return result
    }

    private fun formatUrl(input: String, searchEngine: String): String =
        formatBrowserInputUrl(input, searchEngine) { engine, query ->
            ModePersonalization.searchUrl(engine, query)
        }

    private fun dismissBrowserKeyboard() {
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        currentFocus?.clearFocus()
    }

    /**
     * Switching modes selects browser-data profiles. Each profile (Simple /
     * Developer / Power / Advance) owns an isolated WebView data directory, so when the
     * selected mode differs from the engine this process booted with, the mode
     * is persisted and the process restarts into the new engine. Advance
     * additionally owns an isolated app-data profile that starts empty —
     * nothing carries over from the other modes.
     *
     * @return true if a restart was triggered (the process is dying).
     */
    private suspend fun v9SwitchMode(
        mode: BrowserMode,
        webView: android.webkit.WebView?,
        forceDesktop: Boolean,
    ): Boolean {
        if (!V9Engine.needsRestart(mode)) {
            val savedMode = modeManager.modeFlow.first()
            if (shouldPersistNoRestartModeSelection(savedMode, mode)) {
                modeManager.setMode(mode)
            }
            com.click.browser.engine.ClickDiagnosticsLog.record(
                this, com.click.browser.engine.ClickCatLogCode.MODE_CHANGED_WITHOUT_RESTART,
                savedMode, mode
            )
            webView?.let { modeManager.applySettings(it, mode, forceDesktop) }
            android.widget.Toast.makeText(
                this, "${mode.display().title} activated", android.widget.Toast.LENGTH_SHORT
            ).show()
            return false
        }
        val toastMsg = if (mode == BrowserMode.ADVANCED) {
            // Honest copy: a fresh isolated space, not a new engine.
            "Entering Click Advance — fresh isolated space, restarting…"
        } else {
            "Switching browser profile — restarting…"
        }
        android.widget.Toast.makeText(
            this, toastMsg, android.widget.Toast.LENGTH_LONG
        ).show()
        // An intentional profile switch saves the source profile's own tabs,
        // arms its one-shot restore marker, and marks the exit clean. Returning
        // to that profile restores those tabs without a crash prompt. The
        // mirror is refreshed after composition; only its DataStore write is
        // debounced. Clear-on-exit still removes the snapshot and marker.
        val sourceMode = V9Engine.bootMode
        com.click.browser.engine.ClickDiagnosticsLog.beginModeSwitch(this, sourceMode, mode)
        modeSwitchInProgress = true
        if (liveFlags.clearOnExit) {
            SessionRestore.clearSession(this, sourceMode)
        } else {
            SessionRestore.saveSessionForModeSwitch(
                this, sourceMode, liveRestoreTabs, liveRestoreActiveIndex
            )
        }
        com.click.browser.engine.ClickDiagnosticsLog.record(
            this, com.click.browser.engine.ClickCatLogCode.MODE_SWITCH_SESSION_SAVED,
            sourceMode, mode
        )
        SessionRestore.markCleanExit(this, sourceMode, true)
        val restarted = V9Engine.restartForEngineSwitch(
            this, modeManager, mode,
            hackIntro = (mode == BrowserMode.HACK),
            advanceIntro = (mode == BrowserMode.ADVANCED) && liveAdvanceIntroEnabled
        )
        if (!restarted) {
            val selectionRestored = runCatching { modeManager.setMode(sourceMode) }.isSuccess
            runCatching { SessionRestore.clearModeSwitchRestore(this, sourceMode) }
            runCatching { SessionRestore.markCleanExit(this, sourceMode, false) }
            modeSwitchInProgress = false
            com.click.browser.engine.ClickDiagnosticsLog.record(
                this, com.click.browser.engine.ClickCatLogCode.MODE_SWITCH_ROLLED_BACK,
                sourceMode, mode
            )
            if (!selectionRestored) {
                android.util.Log.e("V9Engine", "Could not restore the saved browser mode after a failed profile restart")
            }
            android.widget.Toast.makeText(
                this,
                if (selectionRestored) {
                    "Couldn't restart. Staying in ${sourceMode.display().title}. Try switching again."
                } else {
                    "Couldn't restart the browser profile. Close and reopen Click before switching modes."
                },
                android.widget.Toast.LENGTH_LONG
            ).show()
            return false
        }
        return true // unreachable — the process is killed above
    }

    private fun requestStoragePermissions() {
        // Granular media permissions (used by the real music player / image gallery).
        // NOTE: MANAGE_EXTERNAL_STORAGE was removed — downloads use DownloadManager
        // and no longer need any file permission.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: request granular media permissions
            val perms = arrayOf(
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_AUDIO
            )
            val needed = perms.filter {
                androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                androidx.core.app.ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            // Android 6-12: request READ_EXTERNAL_STORAGE for media access
            val perms = arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            val needed = perms.filter {
                androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                androidx.core.app.ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
            }
        }
    }

    private fun importFromPowerCut() {
        // Permission gate: shared-media reads need a runtime grant. Bail out with
        // guidance if it isn't granted yet (the drawer item also requests it first).
        val videoPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
            android.Manifest.permission.READ_MEDIA_VIDEO
        else
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(this, videoPermission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestStoragePermissions()
            Toast.makeText(this, "Grant media access, then tap Import again.", Toast.LENGTH_LONG).show()
            return
        }
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        scope.launch {
            try {
                // Scoped-storage-friendly scan: find PowerCut exports via MediaStore.
                // Raw File access to shared-storage dirs is blocked on API 30+ without
                // MANAGE_EXTERNAL_STORAGE (deliberately removed), so query by path.
                val foundUris = mutableListOf<Pair<android.net.Uri, String>>()
                val projection = arrayOf(
                    android.provider.MediaStore.Video.Media._ID,
                    android.provider.MediaStore.Video.Media.DISPLAY_NAME
                )
                // RELATIVE_PATH exists on API 29+; DATA is the legacy column below that.
                val pathColumn = if (android.os.Build.VERSION.SDK_INT >= 29)
                    android.provider.MediaStore.Video.Media.RELATIVE_PATH
                else
                    android.provider.MediaStore.Video.Media.DATA
                try {
                    contentResolver.query(
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        "$pathColumn LIKE ?",
                        arrayOf("%PowerCut%"),
                        null
                    )?.use { c ->
                        val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
                        val nameCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
                        while (c.moveToNext()) {
                            val id = c.getLong(idCol)
                            val uri = android.content.ContentUris.withAppendedId(
                                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                            )
                            foundUris.add(uri to (c.getString(nameCol) ?: "powercut_$id.mp4"))
                        }
                    }
                } catch (_: Exception) { }
                if (foundUris.isNotEmpty()) {
                    val targetDir = java.io.File(getExternalFilesDir(null), "Movies/ClickBrowser/Imported")
                    targetDir.mkdirs()
                    var imported = 0
                    for ((uri, name) in foundUris) {
                        try {
                            val target = java.io.File(targetDir, name)
                            contentResolver.openInputStream(uri)?.use { input ->
                                target.outputStream().use { output -> input.copyTo(output) }
                            }
                            repository.addDownloadItemFor(
                                liveMode,
                                DownloadItem(
                                    fileName = name,
                                    url = "file://${target.absolutePath}",
                                    path = target.absolutePath,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                            imported++
                        } catch (_: Exception) {}
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Imported $imported video(s) from PowerCut Editor!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No PowerCut videos found. Export videos from PowerCut Editor first.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
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
    subtitle: String? = null,
    onInfoClick: (() -> Unit)? = null,
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
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.12f)), shape = RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // Optional trailing info button (used by the Advance mode entry
            // to open the "About Advance Mode" specifications sheet).
            if (onInfoClick != null) {
                IconButton(onClick = onInfoClick) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "About $label",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Unified top bar — fixed at the very top in every mode (Prince's requirement).
 * Styled per the four current browser-profile designs:
 *   Row 1: [hamburger] "Click" wordmark + SMALL stylish mode pill ..... [settings]
 *   Row 2: [back][forward] [ full-width rounded address/search field ] [refresh][bookmark]
 *   Row 3 (Developer mode only): element inspector + device emulator toggles.
 * The large mode button is gone — the profile shows only as the small pill,
 * like the "SIMPLE MODE" pill in the approved Simple design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserTopBar(
    activeMode: BrowserMode,
    theme: ModeTheme,
    currentUrl: String,
    connectionSecurity: ConnectionSecurityInfo = ConnectionSecurityPolicy.unknown(currentUrl),
    canGoBack: Boolean,
    canGoForward: Boolean,
    isBookmarked: Boolean,
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBrowserMenuClick: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onToggleBookmark: () -> Unit,
    onNavigate: (String) -> Unit,
    elementInspectorEnabled: Boolean,
    onToggleInspector: () -> Unit,
    deviceEmulatorMode: String,
    onToggleEmulator: () -> Unit,
    onShieldClick: () -> Unit = {}
) {
    var textInput by remember(currentUrl) { mutableStateOf(currentUrl) }
    val accent = theme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.topBarBg)
            .padding(top = 4.dp, bottom = 6.dp)
    ) {
        // Row 1: hamburger + wordmark + small mode pill + settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = theme.onTopBar)
            }
            Text(
                text = "Click",
                color = theme.onTopBar,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 19.sp,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = theme.modePillText,
                    color = accent,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // Chrome-style browser menu (⋮) — hamburger stays the drawer.
            IconButton(onClick = onBrowserMenuClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Browser menu", tint = theme.onTopBar)
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = theme.onTopBar)
            }
        }

        // Row 2: nav buttons + full-width rounded address/search field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = theme.onTopBar.copy(alpha = if (canGoBack) 1f else 0.3f)
                )
            }
            IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = theme.onTopBar.copy(alpha = if (canGoForward) 1f else 0.3f)
                )
            }
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(fontSize = 13.sp, color = theme.onSurface),
                singleLine = true,
                placeholder = {
                    Text(
                        "Search or enter address",
                        fontSize = 13.sp,
                        color = theme.onSurface.copy(alpha = 0.45f)
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent.copy(alpha = 0.7f),
                    unfocusedBorderColor = accent.copy(alpha = 0.35f),
                    focusedContainerColor = theme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = theme.surfaceVariant.copy(alpha = 0.5f),
                    cursorColor = accent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onNavigate(textInput) }),
                leadingIcon = {
                    // The indicator reflects matching engine evidence, never HTTPS text alone.
                    val securityStatus = ConnectionSecurityPolicy
                        .forPage(currentUrl, connectionSecurity).status
                    val shieldTint = when (securityStatus) {
                        ConnectionSecurityStatus.SECURE_HTTPS -> Color(0xFF22C55E)
                        ConnectionSecurityStatus.SECURE_WITH_MIXED_CONTENT,
                        ConnectionSecurityStatus.UNENCRYPTED_HTTP -> Color(0xFFF59E0B)
                        ConnectionSecurityStatus.CERTIFICATE_WARNING -> MaterialTheme.colorScheme.error
                        ConnectionSecurityStatus.CHECKING,
                        ConnectionSecurityStatus.UNKNOWN -> theme.onSurface.copy(alpha = 0.5f)
                    }
                    androidx.compose.material3.IconButton(
                        onClick = onShieldClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "${ConnectionSecurityPolicy.title(securityStatus)} — tap for site settings",
                            tint = shieldTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
            IconButton(onClick = onRefresh, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = theme.onTopBar)
            }
            IconButton(onClick = onToggleBookmark, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Bookmark",
                    tint = if (isBookmarked) Color(0xFFFBBF24) else theme.onTopBar
                )
            }
        }

        // Row 3: developer tools (Developer mode only)
        if (activeMode == BrowserMode.DEVELOPER) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onToggleInspector) {
                    Text(
                        if (elementInspectorEnabled) "Inspect ON" else "Inspect OFF",
                        fontSize = 10.sp,
                        color = if (elementInspectorEnabled) Color.Magenta else theme.onTopBar.copy(alpha = 0.7f)
                    )
                }
                TextButton(onClick = onToggleEmulator) {
                    Text(
                        "Emulator: $deviceEmulatorMode",
                        fontSize = 10.sp,
                        color = theme.onTopBar.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Desktop-style top tab strip: a horizontal row of chips for every open tab.
 * Tap a chip to switch (no data loss), X closes that tab, + opens a new
 * blank tab and switches to it. Scrolls horizontally when many tabs are
 * open. Follows the per-mode theme. The full Tabs Manager screen stays
 * available too — this strip is the quick switcher.
 */
@Composable
fun TabStrip(
    tabs: List<TabItem>,
    activeTabIndex: Int,
    theme: ModeTheme,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onNewTab: () -> Unit,
    onOpenTabSwitcher: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.topBarBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tab count badge (Mises-style) — tap opens the visual tab switcher.
        Surface(
            modifier = Modifier
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Open tab switcher",
                    onClick = onOpenTabSwitcher
                )
                .semantics { contentDescription = "${tabs.size} open tabs. Open tab switcher" },
            shape = RoundedCornerShape(7.dp),
            color = theme.primary.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.4f))
        ) {
            Text(
                text = tabs.size.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = theme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(tabs, key = { _, tab -> tab.id }) { idx, tab ->
                val selected = idx == activeTabIndex
                val tabTitle = tab.title.ifBlank { "New Tab" }
                Surface(
                    modifier = Modifier
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelectTab(idx) }
                        )
                        .semantics {
                            contentDescription = "Tab ${idx + 1} of ${tabs.size}: $tabTitle"
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) theme.primary.copy(alpha = 0.18f)
                    else theme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(
                        1.dp,
                        if (selected) theme.primary.copy(alpha = 0.6f)
                        else theme.onSurface.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tabTitle.take(18),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (selected) theme.primary
                            else theme.onSurface.copy(alpha = 0.75f),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.widthIn(max = 110.dp)
                        )
                        IconButton(
                            onClick = { onCloseTab(idx) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close $tabTitle tab",
                                tint = theme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        IconButton(
            onClick = onNewTab,
            modifier = Modifier
                .size(30.dp)
                .background(theme.primary.copy(alpha = 0.15f), CircleShape)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "New tab",
                tint = theme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PremiumHomeScreen(
    activeMode: BrowserMode,
    theme: ModeTheme,
    wallpaperUri: String?,
    @Suppress("UNUSED_PARAMETER") isIncognito: Boolean,
    onNavigate: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onModeChange: (BrowserMode) -> Unit,
    tabs: List<TabItem>,
    onSelectTab: (Int) -> Unit,
    // Dialog callback clicks
    onMusicClick: () -> Unit,
    onVideoClick: () -> Unit,
    onPdfClick: () -> Unit,
    onImagesClick: () -> Unit,
    // Power mode layout control
    forceDesktopMode: Boolean,
    onToggleForceDesktop: () -> Unit,
    // Settings controllers
    adBlockerEnabled: Boolean,
    onToggleAdBlocker: (Boolean) -> Unit,
    forceNightMode: Boolean,
    onToggleNightMode: (Boolean) -> Unit,
    httpsMode: String,
    onHttpsModeChange: (String) -> Unit,
    jsEnabled: Boolean,
    onToggleJs: (Boolean) -> Unit,
    dataSaver: Boolean,
    onToggleDataSaver: (Boolean) -> Unit
) {
    // Settings module expandable state
    var settingsExpanded by remember { mutableStateOf(false) }

    // Custom wallpaper (decoded off the main thread). When set, it replaces
    // the theme background with a dimmed photo.
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
                    .background(theme.background.copy(alpha = 0.55f))
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
                    .border(BorderStroke(1.dp, Brush.horizontalGradient(listOf(theme.primary, theme.secondary))), shape = RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = theme.surface.copy(alpha = 0.85f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI greeting",
                            color = theme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Brush.linearGradient(listOf(theme.primary, theme.secondary)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Welcome to the Click Browser ecosystem. Select high performance modes or surf secure.",
                        color = theme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 2-Column Premium Widget Grid: Music, Video, Download, Image, PDF, Browser (wide)
            Text(
                text = "Premium Widget Grid",
                color = theme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )

            // Since grid scroll is nested inside Column verticalScroll, we can build custom layout flow or Row layouts
            // Let's lay them out in clean modular Rows.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Widget 1: Music player (real on-device audio library)
                WidgetCard(
                    theme = theme,
                    title = "MUSIC PLAYER",
                    value = "Device Music",
                    sub = "Play your audio",
                    icon = Icons.Default.MusicNote,
                    color = Color(0xFFFF5A9E),
                    modifier = Modifier.weight(1f),
                    onClick = onMusicClick
                )
                // Widget 2: Video
                WidgetCard(
                    theme = theme,
                    title = "VIDEO PLAYER",
                    value = "Click Cinema",
                    sub = "Play video files",
                    icon = Icons.Default.PlayCircle,
                    color = Color(0xFF3EE7B0),
                    modifier = Modifier.weight(1f),
                    onClick = onVideoClick
                )
                // Widget 3: Downloads
                WidgetCard(
                    theme = theme,
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
                    theme = theme,
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
                    theme = theme,
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
                    .border(1.dp, theme.onBackground.copy(0.1f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = theme.surfaceVariant)
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
                        Text("Explore Web Ecosystem", color = theme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Adblock active", color = theme.onSurface.copy(alpha = 0.7f), fontSize = 11.sp)
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

            // Recent Tabs Section (real open tabs)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Recent Tabs",
                    color = theme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
                tabs.forEachIndexed { index, tab ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { onSelectTab(index) }
                            .shadow(4.dp, shape = RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0x11FFFFFF))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    tab.title.ifBlank { "New Tab" },
                                    color = theme.onBackground, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1
                                )
                                Text(
                                    tab.url.take(48),
                                    color = Color.Gray, fontSize = 10.sp, maxLines = 1
                                )
                            }
                            if (tab.isIncognito) {
                                Text("Private", color = Color(0xFFFF9800), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Pinned Websites Section with premium icons only (NO TEXT)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Pinned Websites",
                    color = theme.onBackground,
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

            // App info card (real installed APK size)
            val context = LocalContext.current
            val apkSizeMb = remember {
                try {
                    val src = context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir
                    java.io.File(src).length() / (1024 * 1024)
                } catch (_: Exception) { -1L }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, shape = RoundedCornerShape(16.dp))
                    .border(1.dp, theme.onBackground.copy(0.1f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = theme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("App Info", fontWeight = FontWeight.Bold, color = theme.onBackground, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (apkSizeMb >= 0) "Installed APK: ~$apkSizeMb MB (${BuildConfig.BUILD_TYPE} build)"
                        else "Click Browser (${BuildConfig.BUILD_TYPE} build)",
                        color = Color.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onSettingsClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Text("Open Settings", fontSize = 11.sp)
                    }
                }
            }

            // Consolidated Settings Module Card (Display, Extensions, Security, Permissions, Advanced, Team Info - expandable)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, shape = RoundedCornerShape(16.dp))
                    .border(1.dp, theme.onBackground.copy(0.1f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = theme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { settingsExpanded = !settingsExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Consolidated Settings Module", fontWeight = FontWeight.Bold, color = theme.onBackground, fontSize = 13.sp)
                        Icon(
                            imageVector = if (settingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = theme.onBackground
                        )
                    }

                    AnimatedVisibility(visible = settingsExpanded) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            // adblock
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("🧩 Adblocker Extension Active", color = theme.onBackground, fontSize = 11.sp)
                                Switch(checked = adBlockerEnabled, onCheckedChange = onToggleAdBlocker, modifier = Modifier.scale(0.8f))
                            }
                            // HTTPS mode (tap to cycle Off -> Standard -> Strict)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onHttpsModeChange(
                                            when (httpsMode) {
                                                "standard" -> "strict"
                                                "strict" -> "off"
                                                else -> "standard"
                                            }
                                        )
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔒 HTTPS Mode", color = theme.onBackground, fontSize = 11.sp)
                                Text(
                                    httpsMode.replaceFirstChar { it.uppercase() },
                                    color = theme.onBackground, fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            // JS
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("⚡ JavaScript Execution Support", color = theme.onBackground, fontSize = 11.sp)
                                Switch(checked = jsEnabled, onCheckedChange = onToggleJs, modifier = Modifier.scale(0.8f))
                            }
                            // data saver
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("📦 Data Saver Mode", color = theme.onBackground, fontSize = 11.sp)
                                Switch(checked = dataSaver, onCheckedChange = onToggleDataSaver, modifier = Modifier.scale(0.8f))
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = theme.onBackground.copy(0.1f))

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
                colors = CardDefaults.cardColors(containerColor = theme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Consolidated System & Download Module", fontWeight = FontWeight.Bold, color = theme.onBackground, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))

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
    theme: ModeTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .height(130.dp)
            .clickable { onClick() }
            .shadow(4.dp, shape = RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = theme.surfaceVariant)
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
                Text(title, color = theme.onSurface.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(value, color = theme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(sub, color = theme.onSurface.copy(alpha = 0.7f), fontSize = 10.sp)
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
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.08f))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title.uppercase(),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}
