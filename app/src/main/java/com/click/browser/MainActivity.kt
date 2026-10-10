package com.click.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.webkit.WebChromeClient
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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.webkit.WebSettingsCompat
import androidx.annotation.RequiresApi
import androidx.webkit.WebViewFeature
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

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
    /** 0..100 page-load progress (drives the thin progress bar under the top bar). */
    var loadProgress by mutableStateOf(0)
}

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

    private lateinit var modeManager: ModeManager
    private lateinit var repository: BrowserRepository

    // Live copies of composable state for use inside WebViewClient callbacks,
    // which are created once and would otherwise capture stale values.
    private var liveMode: BrowserMode = BrowserMode.SIMPLE
    private var liveAntiDetection = true
    private var liveNightMode = false
    private var liveDataSaver = false
    // Live copies for the privacy-guard features (see PrivacyGuards).
    private var liveHeaderSpoof = false
    // Fingerprint mode: "off" | "standard" | "strict" (Brave-hardening).
    private var liveFingerprintMode = "standard"
    private var liveSecureDns = false
    private var liveCustomHeaders: Map<String, String> = emptyMap()
    // Brave-inspired privacy quick wins (live copies for WebViewClient callbacks).
    private var liveStripTrackingParams = true
    private var liveForgetfulBrowsing = false
    private var liveForgetfulExceptions: Set<String> = emptySet()
    private var liveBlockConsentBanners = true
    // HTTPS mode: "off" | "standard" | "strict" + per-site Strict exceptions.
    private var liveHttpsMode = "standard"
    private var liveHttpsStrictExceptions: Set<String> = emptySet()
    // DNT + GPC privacy signals (opt-in, default off).
    private var liveDntEnabled = false
    private var liveGpcEnabled = false
    // Live copy of per-site desktop hosts (persisted per host via BrowserRepository).
    private var liveDesktopHosts: Set<String> = emptySet()
    // LocationGuard live copies (WebViewClient/WebChromeClient run off the UI
    // thread and can't read composable state — same pattern as above).
    private var liveLocationMode: LocationGuard.LocationMode = LocationGuard.LocationMode.ASK
    private var liveLocationSpoofLat: Double = LocationGuard.DEFAULT_PRESET.lat
    private var liveLocationSpoofLng: Double = LocationGuard.DEFAULT_PRESET.lng
    private var liveLocationSpoofLabel: String = LocationGuard.DEFAULT_PRESET.label
    private var liveLocationSiteModes: Map<String, String> = emptyMap()
    // Live copy of experimental flags (click://flags) for WebViewClient callbacks.
    private var liveFlags = ExperimentalFlags()
    // Biometric private-tab lock: live copies for onPause(), which runs
    // outside compose scope.
    private var liveBiometricLockEnabled = false
    private var liveHasIncognitoTabs = false
    private val privateLockedFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    // Text scaling live copies for WebViewClient callbacks (per-host map).
    private var liveGlobalTextScale = com.click.browser.engine.TextScaleStore.DEFAULT
    private var liveHostTextScales: Map<String, Int> = emptyMap()
    // Crash-restore: latest restorable-tab snapshot for this boot mode, kept
    // outside composable scope so onDestroy() and v9SwitchMode() can persist
    // it and mark clean exits without touching UI state. The mirror is
    // refreshed synchronously on every tab change (see the snapshot saver) —
    // only the DataStore write is debounced — so it never lags behind.
    private var liveRestoreTabs: List<SavedTab> = emptyList()
    private var liveRestoreActiveIndex: Int = 0

    /**
     * Builds the restorable-tab snapshot from live tab state: drops
     * incognito / non-http(s) tabs, caps at [SessionRestore.MAX_TABS], and
     * translates the active index through the filter. If the active tab was
     * filtered out, the index falls back to 0.
     */
    private fun buildRestorableSnapshot(
        tabStates: List<Triple<String, String, Boolean>>,
        activeIdx: Int
    ): Pair<List<SavedTab>, Int> {
        val kept = tabStates.mapIndexed { i, t -> i to t }
            .filter { (_, t) -> !t.third && SessionRestore.isRestorableUrl(t.first) }
            .take(SessionRestore.MAX_TABS)
        val active = kept.indexOfFirst { (i, _) -> i == activeIdx }.takeIf { it >= 0 } ?: 0
        val tabs = kept.map { (_, t) -> SavedTab(t.first, t.second.ifBlank { t.first }) }
        return tabs to active
    }
    // Registry of live WebViews for flag-driven cleanup (clear-on-exit).
    private val liveWebViews = mutableListOf<android.webkit.WebView>()

    // Session salt for per-session fingerprint-noise randomization.
    // Generated once per app launch: noise is stable within a session but
    // differs on every launch, so hashes can't be correlated across sessions.
    private val sessionSalt: String = java.util.UUID.randomUUID().toString()
    /** Fingerprint-protection script for the current mode (strict adds extra hooks). */
    private fun fingerprintScript(strict: Boolean): String =
        PrivacyGuards.buildFingerprintScript(sessionSalt, strict)

    // Userscript extensions (HACK mode). Live caches are refreshed whenever
    // the script list changes so WebViewClient always sees current data.
    private val userscriptManager by lazy { UserscriptManager(this) }
    private var liveUserscripts: List<UserscriptInfo> = emptyList()
    private var liveUserscriptCode: Map<String, String> = emptyMap()

    /** Downloads a text file (used for installing userscripts from URL). Null on any failure. */
    private fun downloadText(url: String): String? {
        return try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "ClickBrowser/1.0").build()
            getHeaderFetchClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    // OkHttp client used to re-fetch page resources with spoofed headers.
    // Rebuilt when the Secure-DNS mode changes.
    private var headerFetchClient: OkHttpClient? = null
    private var headerFetchClientSecureDns = false

    private fun getHeaderFetchClient(): OkHttpClient {
        val wantSecure = liveSecureDns
        val cached = headerFetchClient
        if (cached != null && headerFetchClientSecureDns == wantSecure) return cached
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
        if (wantSecure) {
            // Secure DNS covers the app's own requests (this re-fetch is one
            // of them). WebView page loads still use the system DNS resolver.
            try { builder.dns(PrivacyGuards.buildSecureDns()) } catch (_: Exception) { }
        }
        return builder.build().also {
            headerFetchClient = it
            headerFetchClientSecureDns = wantSecure
        }
    }

    /**
     * Re-fetches [url] with the user's spoofed custom headers.
     * Returns null when the fetch fails — the caller then lets WebView load
     * the resource normally (honest degradation, never a fake response).
     */
    private fun fetchWithSpoofedHeaders(url: String): WebResourceResponse? {
        return try {
            val req = Request.Builder().url(url).apply {
                liveCustomHeaders.forEach { (name, value) -> header(name, value) }
                // Privacy signals ride on re-fetched subresources too.
                if (liveFlags.dntHeader || liveDntEnabled) header("DNT", "1")
                if (liveGpcEnabled) header("Sec-GPC", "1")
            }.build()
            val resp = getHeaderFetchClient().newCall(req).execute()
            val body = resp.body ?: return null
            val contentType = resp.header("Content-Type") ?: "text/html"
            val mime = contentType.substringBefore(";").trim().ifEmpty { "text/html" }
            val charset = contentType.substringAfter("charset=", "").substringBefore(";").trim()
            WebResourceResponse(
                mime,
                charset.ifEmpty { "UTF-8" },
                resp.code,
                "OK",
                mapOf("Content-Type" to contentType),
                body.byteStream()
            )
        } catch (_: Exception) {
            null
        }
    }

    private var ttsEngine: TextToSpeech? = null

    override fun onDestroy() {
        try { ttsEngine?.shutdown() } catch (_: Exception) { }
        // Experimental flags: clear data / history on exit.
        if (liveFlags.clearOnExit) {
            try {
                liveWebViews.forEach { it.clearCache(true) }
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.WebStorage.getInstance().deleteAllData()
            } catch (_: Exception) { }
        }
        if (liveFlags.clearHistoryOnExit) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try { repository.clearHistory() } catch (_: Exception) { }
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
     * (same established pattern as the video downloader — no storage permission
     * needed on API 29+). blob:/data: URLs can't be fetched by DownloadManager,
     * so those get an honest toast instead of a dead button.
     */
    private fun saveLongPressImage(url: String) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            Toast.makeText(this, "This image can't be saved directly.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
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
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                .setTitle("Click Browser Download")
                .setDescription(fileName)
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    "ClickBrowser/$fileName"
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            dm.enqueue(request)
            Toast.makeText(this, "Downloading image…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed.", Toast.LENGTH_SHORT).show()
        }
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
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
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
        // Custom UA overrides the mode default (applied after modeManager.applySettings).
        if (f.customUserAgent.isNotBlank()) s.userAgentString = f.customUserAgent
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
    /**
     * Applies the per-site desktop/mobile override for the given URL.
     * If the URL's host is in the persisted desktop-hosts set, the WebView gets
     * a desktop UA + wide viewport; otherwise the current mode's settings apply.
     */
    private fun applyPerSiteDesktop(webView: WebView, url: String?) {
        val host = try {
            android.net.Uri.parse(url ?: "").host?.lowercase().orEmpty()
        } catch (e: Exception) {
            ""
        }
        val desktop = host.isNotEmpty() && (host in liveDesktopHosts
            || liveDesktopHosts.any { h -> host == h || host.endsWith(".$h") })
        modeManager.applyDesktopOverride(webView, liveMode, desktop)
    }

    /**
     * LocationGuard: resolves the effective location mode for a URL —
     * per-site override wins, else the global mode. Matches subdomains too
     * (same rule as per-site desktop).
     */
    private fun effectiveLocationMode(url: String?): LocationGuard.LocationMode {
        val host = LocationGuard.hostFromUrl(url).orEmpty()
        if (host.isNotEmpty()) {
            val override = liveLocationSiteModes[host]
                ?: liveLocationSiteModes.entries.firstOrNull { (h, _) ->
                    host == h || host.endsWith(".$h")
                }?.value
            if (!override.isNullOrBlank()) return LocationGuard.LocationMode.fromKey(override)
        }
        return liveLocationMode
    }

    /**
     * LocationGuard: handles a WebView geolocation permission request.
     * Must be called on the UI thread (WebChromeClient callbacks are).
     *
     * - BLOCK → deny immediately (website gets PERMISSION_DENIED).
     * - SPOOF → inject the JS override, then grant (the page's
     *   navigator.geolocation now returns spoofed coordinates; the native
     *   prompt path is bypassed). No Android location permission is used.
     * - ASK → stash the callback and show the in-app dialog (handled by the
     *   composable via showLocationPrompt state).
     */
    private fun handleGeolocationPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?,
        onAsk: (origin: String, host: String?, callback: GeolocationPermissions.Callback) -> Unit
    ) {
        if (callback == null) return
        // The requesting page's URL gives us the host for per-site lookup.
        // WebChromeClient doesn't hand us the WebView here, so callers pass
        // the current tab URL via onAsk; for BLOCK/SPOOF we resolve from origin.
        val host = try {
            android.net.Uri.parse(origin ?: "").host?.lowercase()
        } catch (_: Exception) {
            null
        }
        val mode = if (!host.isNullOrEmpty()) {
            val override = liveLocationSiteModes[host]
                ?: liveLocationSiteModes.entries.firstOrNull { (h, _) ->
                    host == h || host.endsWith(".$h")
                }?.value
            if (!override.isNullOrBlank()) LocationGuard.LocationMode.fromKey(override)
            else liveLocationMode
        } else {
            liveLocationMode
        }
        when (mode) {
            LocationGuard.LocationMode.BLOCK -> callback.invoke(origin, false, false)
            LocationGuard.LocationMode.SPOOF -> {
                // Best-effort: the page-start injection usually already
                // replaced navigator.geolocation; granting here keeps the
                // WebView contract satisfied for any late requests.
                callback.invoke(origin, true, false)
            }
            LocationGuard.LocationMode.ASK -> onAsk(origin ?: "", host, callback)
        }
    }

    /** Shared pretty error page used by both onReceivedError variants. */
    /** Extracts the lowercase host from a URL ("" on failure). */
    private fun hostOfUrl(url: String): String = try {
        android.net.Uri.parse(url).host?.lowercase().orEmpty()
    } catch (_: Exception) {
        ""
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

    private fun showBrowserErrorPage(view: WebView?, description: String?) {
        val safeDesc = description?.take(200) ?: "Unknown error"
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
                <p><i>Details: $safeDesc</i></p>
                <button class="btn" onclick="location.reload()">Retry Connection</button>
            </body>
            </html>
        """.trimIndent()
        view?.loadDataWithBaseURL(null, customHtml, "text/html", "UTF-8", null)
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

    /**
     * Biometric private-tab lock: whenever the app goes to background while
     * the lock is enabled and at least one incognito tab exists, the private
     * tabs re-lock. The overlay + BiometricPrompt handle the unlock on return.
     */
    override fun onPause() {
        super.onPause()
        if (liveBiometricLockEnabled && liveHasIncognitoTabs) {
            privateLockedFlow.value = true
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

        modeManager = ModeManager(this)
        repository = BrowserRepository(this)
        // Bundled ad/tracker filter lists (assets) + weekly remote updates.
        com.click.browser.engine.FilterListManager.init(this)
        // Translate language list (asset-overridable, built-in fallback).
        com.click.browser.engine.MlKitTranslator.loadLanguages(this)

        setContent {
            // App-start intro: 5s Markhor "TEAM PK AI ERA" animation on EVERY
            // cold start (Prince's request). rememberSaveable: not replayed
            // on rotation. Tap skips immediately.
            var showSplash by rememberSaveable { mutableStateOf(true) }
            val scope = rememberCoroutineScope()
            val activeMode by modeManager.modeFlow.collectAsState(initial = BrowserMode.SIMPLE)

            // App Layout configuration: global Day/Night toggle.
            // Each browsing mode has its own premium light/dark theme
            // (see ModeThemes — adapted from the approved design references).
            var currentThemeSetting by remember { mutableStateOf("Dark") }
            // Per-mode Day/Night overrides (null = follow the global toggle).
            var perModeDark by remember { mutableStateOf<Map<BrowserMode, Boolean?>>(emptyMap()) }
            var wallpaperUri by remember { mutableStateOf<String?>(null) }
            val theme = remember(activeMode, currentThemeSetting, perModeDark) {
                val dark = perModeDark[activeMode] ?: (currentThemeSetting == "Dark")
                ModeThemes.forMode(activeMode, dark)
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
            val tabs = remember { mutableStateListOf<TabItem>(TabItem(url = homeUrl(), title = "New Tab")) }
            var activeTabIndex by remember { mutableStateOf(0) }
            val currentTab = tabs.getOrNull(activeTabIndex) ?: TabItem(url = "about:blank")

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
            var lastClosedTab by remember { mutableStateOf<Triple<String, String, Int>?>(null) }
            var showRecentTabs by remember { mutableStateOf(false) }
            var showDeleteBrowsingConfirm by remember { mutableStateOf(false) }
            // Bumps every time a tab thumbnail is captured → recomposes the
            // visual tab switcher if it happens to be open.
            var thumbnailVersion by remember { mutableStateOf(0) }

            /**
             * Single close-tab path used by the top tab strip AND the visual
             * switcher: remembers the tab for "Recent tabs" (never private
             * ones), drops its thumbnail, and keeps one blank tab minimum.
             * Premium UI v2: also offers UNDO via a bottom snackbar.
             */
            fun closeTabAt(idx: Int) {
                if (idx !in tabs.indices) return
                val closed = tabs[idx]
                RecentlyClosedTabs.push(closed.title, closed.url, closed.isIncognito)
                TabThumbnailStore.remove(closed.id)
                // Drop the closed tab's WebView from the flags registry so
                // clear-on-exit doesn't iterate stale, detached WebViews.
                closed.webView?.let { liveWebViews.remove(it) }
                // Remember for UNDO (not for private tabs — privacy first).
                val undoInfo: Triple<String, String, Int>? =
                    if (closed.isIncognito) null
                    else Triple(closed.title, closed.url, idx.coerceAtMost(tabs.size - 1))
                if (tabs.size > 1) {
                    tabs.removeAt(idx)
                    if (activeTabIndex >= tabs.size) {
                        activeTabIndex = tabs.size - 1
                    }
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
                    lastClosedTab = undoInfo
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Tab closed",
                            actionLabel = "UNDO",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            lastClosedTab?.let { (title, url, atIdx) ->
                                val restored = TabItem(url = url, title = title.ifBlank { "New Tab" })
                                val insertAt = atIdx.coerceIn(0, tabs.size)
                                tabs.add(insertAt, restored)
                                activeTabIndex = insertAt
                            }
                            lastClosedTab = null
                        } else if (forgetCandidate && closedHost != null) {
                            // Snackbar dismissed / timed out without UNDO:
                            // the site is truly gone — forget it now.
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
            // Gated by the flags-loading effect below: the session saver and
            // the restore check must not read liveFlags.clearOnExit before
            // DataStore has delivered the real value (LaunchedEffect order
            // is not a reliable signal).
            var flagsLoaded by remember { mutableStateOf(false) }

            // Crash-restore startup check: if the previous run for THIS boot
            // mode did not exit cleanly and a tab snapshot exists, offer to
            // restore it. Never crosses modes — each mode keeps its own
            // snapshot in its own profile store (isolation is a feature).
            // Waits for flagsLoaded: liveFlags.clearOnExit must come from
            // DataStore, not its default, before we decide.
            LaunchedEffect(Unit) {
                snapshotFlow { flagsLoaded }.first { it }
                val bootMode = V9Engine.bootMode
                val clean = SessionRestore.wasCleanExit(this@MainActivity, bootMode)
                val (saved, savedActive) = SessionRestore.loadSession(this@MainActivity, bootMode)
                // From here on, assume this run may die uncleanly.
                SessionRestore.markCleanExit(this@MainActivity, bootMode, false)
                if (!clean && saved.isNotEmpty() && !liveFlags.clearOnExit) {
                    crashedTabs = saved
                    crashedActiveIndex = savedActive
                    showRestoreDialog = true
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
                snapshotFlow { tabs.map { Triple(it.url, it.title, it.isIncognito) } to activeTabIndex }
                    .collect { (tabStates, activeIdx) ->
                        val (snapshot, active) = buildRestorableSnapshot(tabStates, activeIdx)
                        liveRestoreTabs = snapshot
                        liveRestoreActiveIndex = active
                        // Biometric lock bookkeeping: does an incognito tab exist right now?
                        liveHasIncognitoTabs = tabStates.any { it.third }
                        delay(1000) // trailing-edge debounce: rapid edits collapse into one write
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
            // Real session count of blocked tracker/ad requests (home privacy pill).
            val blockedCount by AdBlocker.blockedCountFlow.collectAsState()
            // V9 Shield VPN running state (for the home shield card).
            val shieldActive by com.click.browser.engine.V9VpnController.isRunning.collectAsState()
            var forceNightModeWebsites by remember { mutableStateOf(false) }
            // HTTPS mode: "off" | "standard" | "strict" (Brave-hardening).
            var httpsMode by remember { mutableStateOf("standard") }
            var httpsStrictExceptions by remember { mutableStateOf(emptyList<String>()) }
            var javaScriptEnabledGlobal by remember { mutableStateOf(true) }
            var dataSaverEnabled by remember { mutableStateOf(false) }
            // Brave-inspired privacy quick wins (DataStore-persisted below).
            var stripTrackingParams by remember { mutableStateOf(true) }
            var forgetfulBrowsing by remember { mutableStateOf(false) }
            var forgetfulExceptions by remember { mutableStateOf(setOf<String>()) }
            var blockConsentBanners by remember { mutableStateOf(true) }

            // LocationGuard: hide/spoof browser geolocation (Prince request).
            var locationMode by remember { mutableStateOf(LocationGuard.LocationMode.ASK) }
            var locationSpoofLat by remember { mutableStateOf(LocationGuard.DEFAULT_PRESET.lat) }
            var locationSpoofLng by remember { mutableStateOf(LocationGuard.DEFAULT_PRESET.lng) }
            var locationSpoofLabel by remember { mutableStateOf(LocationGuard.DEFAULT_PRESET.label) }
            val locationSiteModes by repository.locationModeHostsFlow.collectAsState(initial = emptyMap())
            // Pending geolocation permission request (ASK mode dialog).
            var locationPromptOrigin by remember { mutableStateOf<String?>(null) }
            var locationPromptCallback by remember { mutableStateOf<GeolocationPermissions.Callback?>(null) }
            var locationPromptHost by remember { mutableStateOf<String?>(null) }
            var showLocationPrompt by remember { mutableStateOf(false) }
            var showLocationSettings by remember { mutableStateOf(false) }

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
            var showPrivacyPolicy by remember { mutableStateOf(false) }
            var showAboutApp by remember { mutableStateOf(false) }

            // AI chat + privacy guards
            var showAiChat by remember { mutableStateOf(false) }
            var showPrivacyGuards by remember { mutableStateOf(false) }
            // click:// internal pages (chrome://-style). Holds the page key or null.
            var showClickPage by remember { mutableStateOf<String?>(null) }
            // Games: full-screen player for a bundled offline mini-game (game id or null).
            var showGamePlayer by remember { mutableStateOf<String?>(null) }
            // Experimental flags (click://flags) — UI mirror of liveFlags.
            var flagsUi by remember { mutableStateOf(ExperimentalFlags()) }
            // Confirm-exit dialog (flag).
            var showExitConfirm by remember { mutableStateOf(false) }
            // V9: Shield screen (VPN + DNS + engines) and the Hack Mode
            // Markhor intro animation (shown once after a Hack engine boot).
            var showV9Shield by remember { mutableStateOf(false) }
            // Help & Feedback screen.
            var showHelp by remember { mutableStateOf(false) }
            var showHackIntro by remember {
                mutableStateOf(intent.getBooleanExtra(V9Engine.EXTRA_HACK_INTRO, false))
            }
            // Advance Mode signature moment: full-screen 5s blue-light intro
            // after an Advance engine boot. Tap to skip.
            var showAdvanceIntro by remember {
                mutableStateOf(intent.getBooleanExtra(V9Engine.EXTRA_ADVANCE_INTRO, false))
            }
            // "About Advance Mode" specifications sheet.
            var showAdvanceSpecs by remember { mutableStateOf(false) }
            // Password manager: save-offer dialog state.
            var showPasswordSaveDialog by remember { mutableStateOf(false) }
            var pendingPasswordSave by remember {
                mutableStateOf<com.click.browser.engine.SavedPassword?>(null)
            }
            // Password manager: saved-logins management screen.
            var showPasswordManager by remember { mutableStateOf(false) }
            // Cookie manager: per-site cookie viewer.
            var showCookieManager by remember { mutableStateOf(false) }
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
            var aiApiKey by remember { mutableStateOf("") }
            var aiProvider by remember { mutableStateOf("groq") }
            var aiModel by remember { mutableStateOf("") }
            var headerSpoofEnabled by remember { mutableStateOf(false) }
            // Fingerprint mode: "off" | "standard" | "strict" (Brave-hardening).
            var fingerprintMode by remember { mutableStateOf("standard") }
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
            var customHeaders by remember { mutableStateOf(listOf<AppSettings.CustomHeader>()) }
            var webrtcTestRunning by remember { mutableStateOf(false) }
            var webrtcTested by remember { mutableStateOf(false) }
            var webrtcIps by remember { mutableStateOf<List<String>?>(null) }

            // Userscript extensions (HACK mode)
            var showUserscripts by remember { mutableStateOf(false) }
            var userscripts by remember { mutableStateOf(listOf<UserscriptInfo>()) }
            var userscriptNotice by remember { mutableStateOf<String?>(null) }

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
            // UA consistency: HACK mode is desktop UA by mode design, SIMPLE/DEVELOPER
            // are mobile UA. The global force-desktop defaults OFF — desktop UA
            // comes from the mode or the per-site Desktop toggle only, and the
            // UA is never switched mid-page-load (see onToggleForceDesktop reload).
            var forceDesktopMode by remember { mutableStateOf(false) }
            var spoofedUAIndex by remember { mutableStateOf(0) }
            val detectedVideos = remember { mutableStateListOf<String>() }
            var showDownloaderDialog by remember { mutableStateOf(false) }

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

            // Full-view / immersive browsing: MANUAL fullscreen toggle only.
            // (Prince: no auto-hide on scroll — user control via the drawer toggle.)
            var immersiveMode by remember { mutableStateOf(false) }

            // Pull-to-refresh state for web pages.
            var isRefreshing by remember { mutableStateOf(false) }
            // Per-site desktop preference (persisted per host).
            val desktopHosts by repository.desktopHostsFlow.collectAsState(initial = emptySet())

            /**
             * Premium UI v2: wires the 32 bottom-left FAB menu features to the
             * existing screens/actions. Reuses current handlers — no feature is
             * reimplemented. Closes the FAB panel first.
             */
            fun handleFeature(id: FeatureId) {
                showFeatureMenu = false
                when (id) {
                    FeatureId.HISTORY -> showHistory = true
                    FeatureId.SETTINGS -> showSettings = true
                    FeatureId.STORAGE, FeatureId.CLEAR_DATA -> showDeleteBrowsingConfirm = true
                    FeatureId.PASSWORDS -> showPasswordManager = true
                    FeatureId.COOKIES -> showCookieManager = true
                    FeatureId.TOOLS -> showExtensionsManager = true
                    FeatureId.DEVTOOLS -> {
                        // Opens the DevTools bottom sheet (website stays visible).
                        // The FPS diagnostics overlay has its own drawer toggle.
                        if (activeMode == BrowserMode.DEVELOPER && currentTab.url != "about:blank") {
                            showDevToolsSheet = true
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "DevTools needs Developer mode with a page loaded.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    FeatureId.DOWNLOADS -> {
                        this@MainActivity.requestStoragePermissions()
                        showDownloads = true
                    }
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
                                val isDesktop = host in desktopHosts ||
                                    desktopHosts.any { h -> host == h || host.endsWith(".$h") }
                                repository.setDesktopHost(host, !isDesktop)
                                currentTab.webView?.let { wv ->
                                    modeManager.applyDesktopOverride(wv, activeMode, !isDesktop)
                                }
                                currentTab.webView?.reload()
                                Toast.makeText(
                                    this@MainActivity,
                                    if (!isDesktop) "Desktop site ON" else "Desktop site OFF",
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
                    FeatureId.EXTENSIONS -> showUserscripts = true
                    FeatureId.ADBLOCK -> {
                        adBlockerEnabled = !adBlockerEnabled
                        Toast.makeText(
                            this@MainActivity,
                            if (adBlockerEnabled) "AdBlock ON" else "AdBlock OFF",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    FeatureId.READER -> Toast.makeText(
                        this@MainActivity, "Reader mode coming soon.", Toast.LENGTH_SHORT
                    ).show()
                    FeatureId.SCREENSHOT -> Toast.makeText(
                        this@MainActivity, "Screenshot coming soon.", Toast.LENGTH_SHORT
                    ).show()
                    FeatureId.SAVE_PDF -> {
                        val printManager = getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
                        val adapter = currentTab.webView?.createPrintDocumentAdapter("Click Browser Print Job")
                        if (printManager != null && adapter != null) {
                            printManager.print("Click Browser Document", adapter, android.print.PrintAttributes.Builder().build())
                        } else {
                            Toast.makeText(this@MainActivity, "Printing not available.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    FeatureId.ADD_HOME -> Toast.makeText(
                        this@MainActivity, "Add to Home coming soon.", Toast.LENGTH_SHORT
                    ).show()
                    FeatureId.SITE_INFO -> {
                        val host = try { android.net.Uri.parse(currentTab.url).host ?: currentTab.url } catch (_: Exception) { currentTab.url }
                        Toast.makeText(this@MainActivity, host, Toast.LENGTH_LONG).show()
                    }
                    FeatureId.PRIVACY_GUARDS -> showPrivacyGuards = true
                    // V9: Shield screen (built-in VPN + DNS + 3-engine identities).
                    FeatureId.V9_SHIELD -> showV9Shield = true
                    FeatureId.HELP_FEEDBACK -> showHelp = true
                    FeatureId.UA_SPOOFER, FeatureId.UA_SWITCHER -> {
                        spoofedUAIndex = (spoofedUAIndex + 1) % 4
                        val uaStr = when (spoofedUAIndex) {
                            0 -> ModeManager.UA_HACK
                            1 -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
                            2 -> "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/125.0"
                            else -> ModeManager.UA_SIMPLE
                        }
                        currentTab.webView?.settings?.userAgentString = uaStr
                        Toast.makeText(this@MainActivity, "User-Agent switched ($spoofedUAIndex)", Toast.LENGTH_SHORT).show()
                    }
                    FeatureId.FULLSCREEN -> immersiveMode = !immersiveMode
                    FeatureId.TEXT_SIZE -> {
                        // Open the text-size sheet: live slider + optional
                        // per-site persistence (accessibility).
                        sheetScale = com.click.browser.engine.TextScaleStore.clamp(
                            currentTab.webView?.settings?.textZoom ?: liveGlobalTextScale
                        )
                        showTextScaleSheet = true
                    }
                    FeatureId.NIGHT_MODE -> {
                        forceNightModeWebsites = !forceNightModeWebsites
                        currentTab.webView?.let { modeManager.applySettings(it, activeMode, forceDesktopMode) }
                        Toast.makeText(
                            this@MainActivity,
                            if (forceNightModeWebsites) "Night mode ON" else "Night mode OFF",
                            Toast.LENGTH_SHORT
                        ).show()
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
            LaunchedEffect(antiDetectionEnabled) { liveAntiDetection = antiDetectionEnabled }
            LaunchedEffect(httpsMode) { liveHttpsMode = httpsMode }
            LaunchedEffect(httpsStrictExceptions) { liveHttpsStrictExceptions = httpsStrictExceptions.toSet() }
            LaunchedEffect(dntEnabled) { liveDntEnabled = dntEnabled }
            LaunchedEffect(gpcEnabled) { liveGpcEnabled = gpcEnabled }
            LaunchedEffect(forceNightModeWebsites) { liveNightMode = forceNightModeWebsites }
            LaunchedEffect(dataSaverEnabled) { liveDataSaver = dataSaverEnabled }
            LaunchedEffect(headerSpoofEnabled) { liveHeaderSpoof = headerSpoofEnabled }
            LaunchedEffect(fingerprintMode) { liveFingerprintMode = fingerprintMode }
            // Privacy quick wins: keep WebViewClient-safe live copies in sync.
            LaunchedEffect(stripTrackingParams) { liveStripTrackingParams = stripTrackingParams }
            LaunchedEffect(forgetfulBrowsing) { liveForgetfulBrowsing = forgetfulBrowsing }
            LaunchedEffect(forgetfulExceptions) { liveForgetfulExceptions = forgetfulExceptions }
            LaunchedEffect(blockConsentBanners) { liveBlockConsentBanners = blockConsentBanners }
            LaunchedEffect(secureDnsEnabled) { liveSecureDns = secureDnsEnabled }
            LaunchedEffect(customHeaders) { liveCustomHeaders = customHeaders.associate { it.name to it.value } }
            LaunchedEffect(desktopHosts) { liveDesktopHosts = desktopHosts }
            LaunchedEffect(locationMode) { liveLocationMode = locationMode }
            LaunchedEffect(locationSpoofLat) { liveLocationSpoofLat = locationSpoofLat }
            LaunchedEffect(locationSpoofLng) { liveLocationSpoofLng = locationSpoofLng }
            LaunchedEffect(locationSpoofLabel) { liveLocationSpoofLabel = locationSpoofLabel }
            LaunchedEffect(locationSiteModes) { liveLocationSiteModes = locationSiteModes }

            // Reloads the userscript list + code cache (DataStore + files).
            fun refreshUserscripts() {
                scope.launch(Dispatchers.IO) {
                    val scripts = userscriptManager.listScripts()
                    val codes = scripts
                        .filter { it.enabled }
                        .associate { it.id to (userscriptManager.getCode(it.id).orEmpty()) }
                    withContext(Dispatchers.Main) {
                        userscripts = scripts
                        liveUserscripts = scripts
                        liveUserscriptCode = codes
                    }
                }
            }

            // Installs a userscript from raw .user.js source text.
            fun installUserscriptFromSource(source: String) {
                scope.launch(Dispatchers.IO) {
                    val result = userscriptManager.install(source)
                    withContext(Dispatchers.Main) {
                        userscriptNotice = result.fold(
                            onSuccess = { "Installed \"${it.meta.name}\"." },
                            onFailure = { "Install failed: ${it.message}" }
                        )
                    }
                    refreshUserscripts()
                }
            }

            // Installs a userscript by downloading it from a URL.
            fun installUserscriptFromUrl(url: String) {
                scope.launch(Dispatchers.IO) {
                    val text = downloadText(url)
                    withContext(Dispatchers.Main) {
                        if (text.isNullOrBlank()) {
                            userscriptNotice = "Download failed — check the URL."
                        } else {
                            installUserscriptFromSource(text)
                        }
                    }
                }
            }

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
                webrtcTestRunning = true
                webrtcTested = false
                webrtcIps = null
                wv.evaluateJavascript(PrivacyGuards.WEBRTC_LEAK_TEST_JS, null)
                scope.launch {
                    delay(9000)
                    if (webrtcTestRunning) {
                        webrtcTestRunning = false
                        webrtcTested = true
                    }
                }
            }

            // Load AI + privacy-guard + appearance settings from DataStore once at startup.
            LaunchedEffect(Unit) {
                dataStore.data.first().let { prefs ->
                    aiApiKey = prefs[AppSettings.AI_API_KEY].orEmpty()
                    aiProvider = prefs[AppSettings.AI_PROVIDER] ?: "groq"
                    aiModel = prefs[AppSettings.AI_MODEL].orEmpty()
                    headerSpoofEnabled = prefs[AppSettings.HEADER_SPOOF_ENABLED] == true
                    // Fingerprint mode: migrate the legacy boolean (true->standard, false->off).
                    fingerprintMode = AppSettings.normalizeMode(
                        prefs[AppSettings.FINGERPRINT_MODE]
                            ?: if (prefs[AppSettings.FINGERPRINT_PROTECTION] == false) "off" else "standard"
                    )
                    // HTTPS mode (default "standard" = historical HTTPS-Only upgrade behavior).
                    httpsMode = AppSettings.normalizeMode(prefs[AppSettings.HTTPS_MODE])
                    httpsStrictExceptions = AppSettings.parseHostList(prefs[AppSettings.HTTPS_STRICT_EXCEPTIONS])
                    dntEnabled = prefs[AppSettings.DNT_ENABLED] == true || prefs[ExperimentalFlags.K_DNT_HEADER] == true
                    gpcEnabled = prefs[AppSettings.GPC_ENABLED] == true
                    secureDnsEnabled = prefs[AppSettings.SECURE_DNS_ENABLED] == true
                    // LocationGuard: hide/spoof browser geolocation.
                    locationMode = LocationGuard.LocationMode.fromKey(prefs[AppSettings.LOCATION_MODE])
                    locationSpoofLat = prefs[AppSettings.LOCATION_SPOOF_LAT]?.toDoubleOrNull()
                        ?: LocationGuard.DEFAULT_PRESET.lat
                    locationSpoofLng = prefs[AppSettings.LOCATION_SPOOF_LNG]?.toDoubleOrNull()
                        ?: LocationGuard.DEFAULT_PRESET.lng
                    locationSpoofLabel = prefs[AppSettings.LOCATION_SPOOF_LABEL]
                        ?: LocationGuard.DEFAULT_PRESET.label
                    customHeaders = AppSettings.parseHeaders(prefs[AppSettings.CUSTOM_HEADERS_JSON])
                    // Brave-inspired privacy quick wins.
                    stripTrackingParams = prefs[AppSettings.STRIP_TRACKING_PARAMS] ?: true
                    forgetfulBrowsing = prefs[AppSettings.FORGETFUL_BROWSING] == true
                    forgetfulExceptions = prefs[AppSettings.FORGETFUL_BROWSING_EXCEPTIONS] ?: emptySet()
                    blockConsentBanners = prefs[AppSettings.BLOCK_CONSENT_BANNERS] ?: true
                    currentThemeSetting = if (prefs[AppSettings.UI_DARK_MODE] == false) "Light" else "Dark"
                    // Per-mode Day/Night overrides (missing key = follow global).
                    perModeDark = BrowserMode.values().associateWith { mode ->
                        prefs[AppSettings.darkModeKey(mode)]
                    }
                    wallpaperUri = prefs[AppSettings.WALLPAPER_URI]
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
                // First run: install the bundled pre-installed userscript
                // extensions (most enabled by default; the WebRTC Leak Guard
                // seeds disabled/opt-in because it breaks video calls). The
                // user can enable, disable, or delete any of them, or add
                // their own, in the Extensions screen.
                userscriptManager.seedBundledScripts()
                refreshUserscripts()
            }

            // Settings Configurations
            var currentSearchEngineSetting by remember { mutableStateOf("Google") }

            LaunchedEffect(activeMode) {
                val engines = when (activeMode) {
                    BrowserMode.SIMPLE -> listOf("Google", "Yahoo", "Bing")
                    BrowserMode.DEVELOPER -> listOf("Yandex", "DuckDuckGo", "Baidu")
                    BrowserMode.HACK -> listOf("Ahmia Search", "Deep Search", "AI Search")
                    BrowserMode.ADVANCED -> listOf("Google", "Brave Search", "DuckDuckGo")
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

            // Opens the real DevTools bottom sheet on the requested tab (0=Elements,
            // 1=Console, 2=Network, 3=Sources, 4=Device), switching to Developer
            // mode if needed. Declared after drawerState so the drawer can be
            // closed from it.
            fun openDevToolsTab(tab: Int) {
                scope.launch {
                    drawerState.close()
                    if (currentTab.url == "about:blank") {
                        Toast.makeText(this@MainActivity, "Load a web page first.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    if (activeMode != BrowserMode.DEVELOPER) {
                        // V9: DevTools runs in the Developer engine.
                        val restarting = v9SwitchMode(BrowserMode.DEVELOPER, currentTab.webView, forceDesktopMode)
                        if (!restarting) currentTab.webView?.reload()
                    }
                    devToolsTab = tab
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
                                            // V9: engine switch (restarts when the engine changes).
                                            val restarting = v9SwitchMode(BrowserMode.SIMPLE, currentTab.webView, forceDesktopMode)
                                            if (!restarting) Toast.makeText(this@MainActivity, "Simple Mode Activated", Toast.LENGTH_SHORT).show()                                        }
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
                                            // V9: engine switch (restarts when the engine changes).
                                            val restarting = v9SwitchMode(BrowserMode.DEVELOPER, currentTab.webView, forceDesktopMode)
                                            if (!restarting) Toast.makeText(this@MainActivity, "Developer Mode Activated", Toast.LENGTH_SHORT).show()                                        }
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
                                            // V9: engine switch (restarts when the engine changes).
                                            val restarting = v9SwitchMode(BrowserMode.HACK, currentTab.webView, forceDesktopMode)
                                            if (!restarting) Toast.makeText(this@MainActivity, "Hack Mode Activated", Toast.LENGTH_SHORT).show()                                        }
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
                                            // V9: engine switch (restarts when the engine changes).
                                            // Advance enters a fresh isolated space — nothing
                                            // carries over from the other modes.
                                            val restarting = v9SwitchMode(BrowserMode.ADVANCED, currentTab.webView, forceDesktopMode)
                                            if (!restarting) Toast.makeText(this@MainActivity, "Advance Mode Activated — fresh isolated space", Toast.LENGTH_SHORT).show()                                        }
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
                                            requestStoragePermissions()
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
                                    DrawerItem(label = "Quick Toggles", icon = Icons.Default.Extension, color = Color(0xFF00FF00)) {
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
                                    DrawerItem(label = "Video Grabber Detection", icon = Icons.Default.SlowMotionVideo, color = Color(0xFFF472B6)) {
                                        scope.launch {
                                            drawerState.close()
                                            showDownloaderDialog = true
                                            Toast.makeText(this@MainActivity, "${detectedVideos.size} video stream(s) ready for download", Toast.LENGTH_SHORT).show()
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
                                    // Per-site desktop/mobile toggle (persisted per host).
                                    val pageHost = try {
                                        android.net.Uri.parse(currentTab.url).host?.lowercase().orEmpty()
                                    } catch (e: Exception) { "" }
                                    val isDesktopForSite = pageHost.isNotEmpty() && (pageHost in desktopHosts
                                        || desktopHosts.any { h -> pageHost == h || pageHost.endsWith(".$h") })
                                    DrawerItem(
                                        label = if (isDesktopForSite) "Desktop site: ON (this site)" else "Desktop site (this site)",
                                        icon = Icons.Default.DesktopWindows,
                                        color = Color(0xFF60A5FA)
                                    ) {
                                        scope.launch {
                                            drawerState.close()
                                            if (pageHost.isEmpty() || currentTab.url == "about:blank") {
                                                Toast.makeText(this@MainActivity, "Open a page first.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                repository.setDesktopHost(pageHost, !isDesktopForSite)
                                                currentTab.webView?.let { wv ->
                                                    modeManager.applyDesktopOverride(wv, activeMode, !isDesktopForSite)
                                                    wv.reload()
                                                }
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    if (!isDesktopForSite) "Desktop site enabled for $pageHost"
                                                    else "Mobile site restored for $pageHost",
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
                                    DrawerItem(label = "Toggle Dark Mode Websites", icon = Icons.Default.Brightness4, color = Color(0xFF818CF8)) {
                                        scope.launch {
                                            drawerState.close()
                                            forceNightModeWebsites = !forceNightModeWebsites
                                            currentTab.webView?.let {
                                                applyPrivacyToggles(it, httpsMode, forceNightModeWebsites, dataSaverEnabled)
                                            }
                                            Toast.makeText(this@MainActivity, "Dark Mode Force is " + (if(forceNightModeWebsites) "ENABLED" else "DISABLED"), Toast.LENGTH_SHORT).show()
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
                                    DrawerItem(label = "Inspect HTML Elements", icon = Icons.Default.ManageSearch, color = Color(0xFFF472B6)) {
                                        scope.launch {
                                            drawerState.close()
                                            elementInspectorEnabled = !elementInspectorEnabled
                                            val wv = currentTab.webView
                                            if (elementInspectorEnabled) {
                                                wv?.evaluateJavascript(
                                                    DevToolsInjections.ELEMENT_INSPECTOR_ENABLE, null
                                                )
                                                Toast.makeText(this@MainActivity, "Page Inspector: Click elements to view tag details", Toast.LENGTH_SHORT).show()
                                            } else {
                                                wv?.evaluateJavascript(
                                                    DevToolsInjections.ELEMENT_INSPECTOR_DISABLE, null
                                                )
                                                Toast.makeText(this@MainActivity, "Page Inspector OFF", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                item {
                                    DrawerItem(label = "Active DOM Explorer", icon = Icons.Default.AccountTree, color = Color(0xFF34D399)) {
                                        openDevToolsTab(0)
                                    }
                                }
                                item {
                                    DrawerItem(label = "Live Network Traffic Monitor", icon = Icons.Default.NetworkCheck, color = Color(0xFF60A5FA)) {
                                        openDevToolsTab(2)
                                    }
                                }
                                item {
                                    DrawerItem(label = "Embedded Resource Sniffer", icon = Icons.Default.OfflineShare, color = Color(0xFFFBBF24)) {
                                        openDevToolsTab(3)
                                    }
                                }
                                item {
                                    DrawerItem(label = "JavaScript Interactive Console", icon = Icons.Default.Terminal, color = Color(0xFF34D399)) {
                                        openDevToolsTab(1)
                                    }
                                }

                                item { DrawerCategoryHeader(title = "7. Hack / Power Mode Shield") }
                                item {
                                    DrawerItem(label = "Anti-Detection Guard", icon = Icons.Default.BugReport, color = Color(0xFFDC2626)) {
                                        scope.launch {
                                            drawerState.close()
                                            antiDetectionEnabled = !antiDetectionEnabled
                                            Toast.makeText(this@MainActivity, "Anti-Detection Guard " + (if(antiDetectionEnabled) "ENABLED (UA, canvas + audio fingerprint spoofing)" else "DISABLED"), Toast.LENGTH_LONG).show()
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
                                // Userscript extensions — HACK, DEVELOPER and ADVANCE
                                // modes (Advance keeps its own script folder).
                                if (activeMode == BrowserMode.HACK || activeMode == BrowserMode.DEVELOPER ||
                                    activeMode == BrowserMode.ADVANCED) {
                                    item {
                                        DrawerItem(label = "Userscript Extensions", icon = Icons.Default.Extension, color = Color(0xFF39FF14)) {
                                            scope.launch { drawerState.close(); showUserscripts = true }
                                        }
                                    }
                                }
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

                            // Manual immersive mode also hides the Android system bars.
                            LaunchedEffect(immersiveMode) {
                                val controller = WindowInsetsControllerCompat(window, window.decorView)
                                if (immersiveMode) {
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
                            // New page → toolbar back.
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
                            fun BrowseTopBarBlock() {
                                AnimatedVisibility(
                                    visible = !immersiveMode && !showOverlays && toolbarVisible,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Crossfade(targetState = theme, label = "topbarTheme") { themed ->
                                        Column {
                                            CompactBrowseBar(
                                                theme = themed,
                                                currentUrl = currentTab.url,
                                                onNavigate = { input ->
                                                    val destination = cleanTrackingUrl(formatUrl(input, currentSearchEngineSetting, activeMode))
                                                    currentTab.url = destination
                                                    currentTab.webView?.loadUrl(destination)
                                                },
                                                onReload = { currentTab.webView?.reload() },
                                                onMenuClick = { showBrowserMenu = true },
                                                // LocationGuard indicator: show when this site's
                                                // location is blocked or spoofed.
                                                locationMode = run {
                                                    val m = effectiveLocationMode(currentTab.url)
                                                    if (m == LocationGuard.LocationMode.ASK) null else m
                                                },
                                                locationSpoofLabel = locationSpoofLabel,
                                                onLocationClick = { showLocationSettings = true }
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

                            Column(modifier = Modifier.fillMaxSize()) {

                                // PREMIUM UI v2 — Chrome-like single-row top bar (Prince-approved
                                // design): back, forward, rounded address bar, tab-count badge,
                                // ⋮ browser menu. The old 2-row bar + separate tab strip are
                                // gone; the counter opens the visual tab switcher, and the
                                // bottom-left FAB menu replaces the top hamburger.
                                // SURFACE 2 — Browsing: compact address bar + privacy strip.
                                // (Home surface has no browser top bar — it has its own
                                // big search bar; tab switching lives in the bottom nav.)
                                if (!flagsUi.bottomAddressBar) BrowseTopBarBlock()

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
                                            theme = ModeThemes.forMode(activeMode, dark = activeMode != BrowserMode.SIMPLE),
                                            activeMode = activeMode,
                                            adBlockerEnabled = adBlockerEnabled,
                                            blockedCount = blockedCount,
                                            wallpaperUri = wallpaperUri,
                                            shieldActive = shieldActive,
                                            onV9ShieldClick = { showV9Shield = true },
                                            onGamesClick = { showClickPage = "games" },
                                            onNavigate = { input ->
                                                val destination = cleanTrackingUrl(formatUrl(input, currentSearchEngineSetting, activeMode))
                                                currentTab.url = destination
                                                currentTab.webView?.loadUrl(destination)
                                            },
                                            onOpenAiChat = {
                                                if (tamperBlocked) showTamperDialog = true else showAiChat = true
                                            },
                                            onTranslate = { handleFeature(FeatureId.TRANSLATE) },
                                            onReaderMode = { handleFeature(FeatureId.READER) },
                                            onQrClick = {
                                                Toast.makeText(this@MainActivity, "QR scanner coming soon.", Toast.LENGTH_SHORT).show()
                                            },
                                            onProfileClick = { showSettings = true }
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
                                                        currentTab.webView?.reload()
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
                                                                        if (request != null && request.isRedirect) return false
                                                                        // HTTPS mode handling FIRST (so http://click://
                                                                        // can't bypass it). Standard upgrades http->https;
                                                                        // Strict BLOCKS plain-http unless the host is
                                                                        // in the per-site exception list; Off allows it.
                                                                        if (urlStr.startsWith("http://")) {
                                                                            when (liveHttpsMode) {
                                                                                "strict" -> {
                                                                                    val host = hostOfUrl(urlStr)
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
                                                                        // HACK mode desktop persistence: sites like YouTube
                                                                        // redirect to their mobile domain (m.youtube.com)
                                                                        // even with a desktop UA. Force the desktop host
                                                                        // so Hack mode STAYS in desktop view.
                                                                        if (liveMode == BrowserMode.HACK) {
                                                                            urlStr = forceDesktopHost(urlStr)
                                                                        }
                                                                        // click:// internal pages (chrome://-style): open natively.
                                                                        ClickInternalPages.interceptNavigation(urlStr)?.let { pageKey ->
                                                                            showClickPage = pageKey
                                                                            return true
                                                                        }
                                                                        if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                                                                            // Per-site desktop override BEFORE loading (UA must be set first).
                                                                            if (view != null) this@MainActivity.applyPerSiteDesktop(view, urlStr)
                                                                            // Privacy signals + header spoofing: navigations
                                                                            // carry DNT/GPC whenever enabled (not gated
                                                                            // on the header-spoof toggle — the toggles
                                                                            // must measurably change behavior).
                                                                            val navHeaders = liveCustomHeaders.toMutableMap()
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
                                                                        // HTTPS mode handling FIRST (see above).
                                                                        // PDF: offer in-app viewing instead of navigating.
                                                                        if (isPdfUrl(urlStr)) {
                                                                            pdfOfferUrl = urlStr
                                                                            return true
                                                                        }
                                                                        if (urlStr.startsWith("http://")) {
                                                                            when (liveHttpsMode) {
                                                                                "strict" -> {
                                                                                    val host = hostOfUrl(urlStr)
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
                                                                        // HACK mode desktop persistence (see above).
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
                                                                            // Privacy signals: DNT/GPC ride on navigations
                                                                            // whenever enabled (not gated on header-spoof).
                                                                            val navHeaders = liveCustomHeaders.toMutableMap()
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
                                                                currentTab.url = url ?: ""
                                                                lastPageStart = System.currentTimeMillis()
                                                                // Page transition animation trigger (main-frame
                                                                // navigations only — subframes don't call this).
                                                                pageTransitionTick++

                                                                // Clear stats
                                                                networkRequests.clear()
                                                                sourcesList.clear()
                                                                detectedVideos.clear()

                                                                // EARLY desktop-environment spoof (Hack mode + Anti-Detection
                                                                // Guard): applied at page START, not page finish, so sites
                                                                // like YouTube see a consistent desktop UA / 1920x1080
                                                                // screen / no-touch environment from the first script
                                                                // they run — no desktop→mobile mid-load flip.
                                                                // (The onPageFinished injection below stays as a safety net.)
                                                                if (liveMode == BrowserMode.HACK && liveAntiDetection) {
                                                                    view?.evaluateJavascript(
                                                                        AntiDetectionInjections.INJECT_SPOOF_LAYERS,
                                                                        null
                                                                    )
                                                                }

                                                                // LocationGuard: when the effective mode for this
                                                                // page is SPOOF, replace navigator.geolocation
                                                                // BEFORE page scripts run, so getCurrentPosition /
                                                                // watchPosition return the spoofed coordinates and
                                                                // the native permission prompt never fires.
                                                                if (effectiveLocationMode(url) == LocationGuard.LocationMode.SPOOF) {
                                                                    view?.evaluateJavascript(
                                                                        LocationGuard.geolocationSpoofJs(
                                                                            liveLocationSpoofLat,
                                                                            liveLocationSpoofLng
                                                                        ),
                                                                        null
                                                                    )
                                                                }

                                                                // V9: per-engine fingerprint for ALL modes — each engine
                                                                // presents a distinct device/browser identity (navigator,
                                                                // screen, WebGL vendor/renderer, seeded canvas noise)
                                                                // so websites see Simple / Developer / Hack as three
                                                                // different browsers.
                                                                try {
                                                                    view?.evaluateJavascript(
                                                                        V9Engine.fingerprintJs(liveMode),
                                                                        null
                                                                    )
                                                                } catch (_: Exception) { /* non-fatal */ }
                                                            }

                                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                                super.onPageFinished(view, url)
                                                                currentTab.title = view?.title ?: "Page"
                                                                pageLoadTime = System.currentTimeMillis() - lastPageStart
                                                                // Pull-to-refresh completes when the page finishes loading.
                                                                if (isRefreshing) isRefreshing = false

                                                                // Cookie-consent banner blocking (Brave-style): hide
                                                                // GDPR/consent banners via cosmetic selectors.
                                                                if (liveBlockConsentBanners) {
                                                                    try {
                                                                        ConsentBannerBlocker.buildScript(
                                                                            FilterListManager.currentConsentSelectors()
                                                                        )?.let { script ->
                                                                            view?.evaluateJavascript(script, null)
                                                                        }
                                                                    } catch (_: Exception) { /* non-fatal */ }
                                                                }

                                                                // Password manager: inject form detection + auto-fill
                                                                // saved credentials for this host (if any).
                                                                view?.let { wv ->
                                                                    wv.evaluateJavascript(
                                                                        com.click.browser.engine.PasswordDetector.PASSWORD_DETECT_JS,
                                                                        null
                                                                    )
                                                                    val host = try {
                                                                        android.net.Uri.parse(url.orEmpty()).host.orEmpty()
                                                                    } catch (_: Exception) { "" }
                                                                    if (host.isNotBlank() && !currentTab.isIncognito) {
                                                                        scope.launch {
                                                                            val saved = com.click.browser.engine.PasswordManager.getForHost(
                                                                                this@MainActivity, host
                                                                            )
                                                                            if (saved != null) {
                                                                                val js = "window.__clickFillLogin(" +
                                                                                    "'${saved.username.replace("'", "\\'")}', " +
                                                                                    "'${saved.password.replace("'", "\\'")}')"
                                                                                wv.post { wv.evaluateJavascript(js, null) }
                                                                            }
                                                                        }
                                                                    }
                                                                }

                                                                // History tracking (skip if Incognito Tab)
                                                                if (!currentTab.isIncognito && url != null && url != "about:blank") {
                                                                    scope.launch {
                                                                        repository.addHistoryItem(HistoryItem(currentTab.title, url, System.currentTimeMillis()))
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

                                                                // JS tools injections based on the CURRENT browser mode
                                                                // (liveMode is used — the factory-time capture would go stale
                                                                // after a mode switch)
                                                                if (liveMode == BrowserMode.DEVELOPER) {
                                                                    view?.evaluateJavascript(DevToolsInjections.CONSOLE_HIJACK, null)
                                                                    view?.evaluateJavascript(DevToolsInjections.NETWORK_INTERCEPT, null)
                                                                    view?.evaluateJavascript(DevToolsInjections.GET_DOM, null)
                                                                    view?.evaluateJavascript(DevToolsInjections.GET_SOURCES, null)
                                                                } else if (liveMode == BrowserMode.HACK) {
                                                                    if (liveAntiDetection) {
                                                                        view?.evaluateJavascript(AntiDetectionInjections.INJECT_SPOOF_LAYERS, null)
                                                                    }
                                                                    view?.evaluateJavascript(AntiDetectionInjections.VIDEO_GRABBER_JS, null)
                                                                }

                                                                // Fingerprint Protection: session-randomized canvas/audio
                                                                // noise in EVERY mode (see PrivacyGuards). Strict mode
                                                                // adds font/toBlob/audio-data hooks + stronger noise.
                                                                if (liveFingerprintMode != "off") {
                                                                    view?.evaluateJavascript(
                                                                        this@MainActivity.fingerprintScript(
                                                                            liveFingerprintMode == "strict"
                                                                        ),
                                                                        null
                                                                    )
                                                                }

                                                                // Userscript extensions (HACK + DEVELOPER + ADVANCE modes):
                                                                // inject every enabled script whose @match/@include
                                                                // fits this URL. Advance uses its own script folder.
                                                                // NOTE: WebView has no true document-start hook, so
                                                                // @run-at document-start scripts also run here at
                                                                // page finish — the earliest reliable point. The UI
                                                                // states this plainly.
                                                                if (liveMode == BrowserMode.HACK || liveMode == BrowserMode.DEVELOPER ||
                                                                    liveMode == BrowserMode.ADVANCED) {
                                                                    val pageUrl = url.orEmpty()
                                                                    liveUserscripts.forEach { script ->
                                                                        if (script.enabled && UserscriptEngine.matchesUrl(script.meta, pageUrl)) {
                                                                            val code = liveUserscriptCode[script.id]
                                                                            if (!code.isNullOrBlank()) {
                                                                                view?.evaluateJavascript(
                                                                                    UserscriptEngine.buildInjection(script.id, script.meta.name, code),
                                                                                    null
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                                                // Basic Ad Blocker check
                                                                if (adBlockerEnabled && AdBlocker.shouldBlock(request?.url?.toString())) {
                                                                    return WebResourceResponse("text/plain", "UTF-8", null)
                                                                }
                                                                // Header spoofing: re-fetch subresources with the
                                                                // user's custom headers. Main-frame navigations
                                                                // already carry them via loadUrl(url, extraHeaders).
                                                                if (liveHeaderSpoof && liveCustomHeaders.isNotEmpty()
                                                                    && request?.isForMainFrame == false
                                                                ) {
                                                                    val resUrl = request.url?.toString().orEmpty()
                                                                    if (resUrl.startsWith("http://") || resUrl.startsWith("https://")) {
                                                                        this@MainActivity.fetchWithSpoofedHeaders(resUrl)?.let { return it }
                                                                    }
                                                                }
                                                                return super.shouldInterceptRequest(view, request)
                                                            }

                                                            @Suppress("Deprecated")
                                                            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                                                // Framework routes main-frame errors to the deprecated overload
                                                                this@MainActivity.showBrowserErrorPage(view, description)
                                                            }

                                                        }

                                                        webChromeClient = object : WebChromeClient() {
                                                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                                                super.onReceivedTitle(view, title)
                                                                currentTab.title = title ?: "Page"
                                                            }

                                                            // Page-load progress → thin progress bar under the
                                                            // top bar (battery-safe: only fires while loading).
                                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                                super.onProgressChanged(view, newProgress)
                                                                tabs.firstOrNull { it.webView === view }?.loadProgress = newProgress
                                                            }

                                                            // LocationGuard: intercept browser geolocation
                                                            // requests. BLOCK denies, SPOOF grants (the JS
                                                            // override injected at page start returns spoofed
                                                            // coordinates), ASK shows the in-app dialog.
                                                            override fun onGeolocationPermissionsShowPrompt(
                                                                origin: String?,
                                                                callback: GeolocationPermissions.Callback?
                                                            ) {
                                                                if (callback == null) {
                                                                    super.onGeolocationPermissionsShowPrompt(origin, callback)
                                                                    return
                                                                }
                                                                handleGeolocationPrompt(origin, callback) { o, h, cb ->
                                                                    locationPromptOrigin = o
                                                                    locationPromptHost = h
                                                                    locationPromptCallback = cb
                                                                    showLocationPrompt = true
                                                                }
                                                            }

                                                            override fun onGeolocationPermissionsHidePrompt() {
                                                                super.onGeolocationPermissionsHidePrompt()
                                                                showLocationPrompt = false
                                                                locationPromptCallback = null
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

                                                        // Receives the result of the real WebRTC leak test.
                                                        addJavascriptInterface(
                                                            WebRtcTestBridge(
                                                                onResult = { ips ->
                                                                    scope.launch {
                                                                        webrtcIps = ips
                                                                        webrtcTestRunning = false
                                                                        webrtcTested = true
                                                                    }
                                                                }
                                                            ),
                                                            "WebRtcTestBridge"
                                                        )

                                                        // GM_* storage bridge for userscript extensions.
                                                        addJavascriptInterface(
                                                            UserscriptBridge(ctx),
                                                            "UserscriptBridge"
                                                        )

                                                        // Password manager: detects login forms, offers to save.
                                                        addJavascriptInterface(
                                                            com.click.browser.engine.PasswordBridge { host, username, password ->
                                                                scope.launch {
                                                                    pendingPasswordSave = com.click.browser.engine.SavedPassword(
                                                                        host = host,
                                                                        username = username,
                                                                        password = password
                                                                    )
                                                                    showPasswordSaveDialog = true
                                                                }
                                                            },
                                                            "PasswordBridge"
                                                        )

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
                                                                // Experimental flags (click://flags) — AFTER mode settings
                                                                // so custom UA / zoom / etc. override the mode defaults.
                                                                // Consistent with the desktopDefault OR above: the flag
                                                                // participates in the mode decision, then fine-tunes it.
                                                                this@MainActivity.applyExperimentalFlags(this)
                                                                // Per-site desktop override for the initial URL.
                                                                this@MainActivity.applyPerSiteDesktop(this, currentTab.url)
                                                                currentTab.webView = this
                                                                if (!this@MainActivity.liveWebViews.contains(this)) {
                                                                    this@MainActivity.liveWebViews.add(this)
                                                                }

                                                                // Long-press context menu (links + images).
                                                                // Attached once at WebView creation, so it works in
                                                                // every mode (Simple / Developer / Hack / Advance).
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
                                                    update = { webView ->
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
                                                )
                                            }
                                        }
                                        }
                                        } // PageTransitionWrapper

                                    // 5. FLOATING DEV DEBUG STATUS OVERLAY + DevTools toggle
                                    // DevTools icon sits at the TOP-RIGHT corner (Prince's
                                    // request); the diagnostics overlay hangs below it.
                                    if (activeMode == BrowserMode.DEVELOPER && currentTab.url != "about:blank") {
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            // DevTools panel toggle — top-right corner.
                                            IconButton(
                                                onClick = {
                                                    if (!showDevToolsSheet && devToolsTab == 4) {
                                                        refreshDevToolsDeviceInfo()
                                                    }
                                                    showDevToolsSheet = !showDevToolsSheet
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

                                    // Periodic privacy-status glass popup (top-center):
                                    // "🛡️ Protected · N trackers blocked". One-shot
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
                                                    tint = Color(0xFF22C55E),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    if (adBlockerEnabled) "Protected · $blockedCount trackers blocked"
                                                    else "AdBlock off",
                                                    color = theme.onSurface,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                    // 6. FLOATING HACK VIDEO GRABBER TRIGGER
                                    // (stacked above the AI button when both are visible)
                                    if (activeMode == BrowserMode.HACK && detectedVideos.isNotEmpty()) {
                                        FloatingActionButton(
                                            onClick = { showDownloaderDialog = true },
                                            containerColor = Color(0xFFFF5722),
                                            contentColor = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(end = 16.dp, bottom = 88.dp)
                                                .scale(1.1f)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = "Grab Video")
                                        }
                                    }
                                }

                                // 7. DevTools lives in a dismissible bottom sheet now
                                // (see overlay screens below) — the website is never
                                // covered by a fixed panel.
                            }
                            }

                            // Premium bottom nav (Phase 1): Home · Tabs (count badge) · big glowing
                            // AI center · Bookmarks · Menu. Mode-wired theme: Simple=light,
                            // Developer=dark glass, Hack=OLED black neon.
                            if (showOverlays && !immersiveMode) {
                                ClickBottomNav(
                                    theme = ModeThemes.forMode(activeMode, dark = activeMode != BrowserMode.SIMPLE),
                                    tabCount = tabs.size,
                                    onHome = { currentTab.url = "about:blank" },
                                    onTabs = { showTabsManager = true },
                                    onAi = { if (tamperBlocked) showTamperDialog = true else showAiChat = true },
                                    onBookmarks = { showBookmarks = true },
                                    onMenu = { showBrowserMenu = true },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }

                            // SURFACE 2 — Browsing bottom nav: ☰ Menu(drawer) · ‹ Back ·
                            // › Forward · ⌂ Home · ▭ Tabs (count badge) · ⋮ More.
                            // (No center AI tab here — AI lives in the floating pill.)
                            // Polish: one-shot theme crossfade (battery-safe).
                            // Bottom-address-bar option: bar renders below the page,
                            // padded above the overlaid bottom nav so they never overlap.
                            if (flagsUi.bottomAddressBar) {
                                Box(modifier = Modifier.padding(bottom = 76.dp)) {
                                    BrowseTopBarBlock()
                                }
                            }
                            if (!showOverlays && !immersiveMode) {
                                Crossfade(
                                    targetState = theme,
                                    label = "browseNavTheme",
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                ) { themed ->
                                    BrowseBottomNav(
                                        theme = themed,
                                        tabCount = tabs.size,
                                        canGoBack = currentTab.webView?.canGoBack() == true,
                                        canGoForward = currentTab.webView?.canGoForward() == true,
                                        onDrawerClick = { scope.launch { drawerState.open() } },
                                        onBack = { currentTab.webView?.goBack() },
                                        onForward = { currentTab.webView?.goForward() },
                                        onHome = { currentTab.url = "about:blank" },
                                        onTabs = { showTabsManager = true },
                                        onMenu = { showBrowserMenu = true }
                                    )
                                }
                            }

                            // Premium UI v2: always-visible bottom-left feature menu FAB
                            // (replaces the old floating home button; the 32-feature
                            // panel covers navigation, and Home stays in the bottom
                            // nav). Hidden in immersive fullscreen for chrome-free view.
                            if (!immersiveMode) {
                                FeatureMenuFabOverlay(
                                    theme = theme,
                                    expanded = showFeatureMenu,
                                    activeMode = activeMode,
                                    onToggle = { showFeatureMenu = !showFeatureMenu },
                                    onDismiss = { showFeatureMenu = false },
                                    onModeChange = { mode ->
                                        showFeatureMenu = false
                                        scope.launch {
                                            // V9: engine switch (restarts when the engine changes).
                                            val restarting = v9SwitchMode(mode, currentTab.webView, forceDesktopMode)
                                            if (!restarting) {
                                                val label = when (mode) {
                                                    BrowserMode.SIMPLE -> "Simple"
                                                    BrowserMode.DEVELOPER -> "Developer"
                                                    BrowserMode.HACK -> "Hack"
                                                    BrowserMode.ADVANCED -> "Advance"
                                                }
                                                Toast.makeText(
                                                    this@MainActivity, "$label Mode Activated",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
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
                                            showTabsManager = false
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
                                        currentTab.webView?.loadUrl(clean)
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
                                        currentTab.webView?.loadUrl(clean)
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
                                            if (it == 4) refreshDevToolsDeviceInfo()
                                        },
                                        onClearLogs = { logs.clear() },
                                        onClearNetwork = { networkRequests.clear() },
                                        onEvalJs = { code ->
                                            currentTab.webView?.evaluateJavascript(code, null)
                                        },
                                        inspectorEnabled = elementInspectorEnabled,
                                        onToggleInspector = {
                                            elementInspectorEnabled = !elementInspectorEnabled
                                            val wv = currentTab.webView
                                            if (elementInspectorEnabled) {
                                                wv?.evaluateJavascript(
                                                    DevToolsInjections.ELEMENT_INSPECTOR_ENABLE, null
                                                )
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Inspector ON — tap any page element.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                wv?.evaluateJavascript(
                                                    DevToolsInjections.ELEMENT_INSPECTOR_DISABLE, null
                                                )
                                            }
                                        },
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
                                        }
                                    },
                                    onStringFlag = { key, value ->
                                        scope.launch {
                                            dataStore.edit { prefs -> prefs[key] = value }
                                            liveFlags = liveFlags.copy(
                                                customUserAgent = if (key == ExperimentalFlags.K_CUSTOM_UA) value else liveFlags.customUserAgent,
                                                customHomepage = if (key == ExperimentalFlags.K_CUSTOM_HOMEPAGE) value else liveFlags.customHomepage
                                            )
                                            flagsUi = liveFlags
                                            liveWebViews.forEach { wv ->
                                                try { applyExperimentalFlags(wv) } catch (_: Exception) { }
                                            }
                                        }
                                    },
                                    onOpenPage = { key ->
                                        when (key) {
                                            "settings" -> { showClickPage = null; showSettings = true }
                                            "history" -> { showClickPage = null; showHistory = true }
                                            "downloads" -> { showClickPage = null; showDownloads = true }
                                            "bookmarks" -> { showClickPage = null; showBookmarks = true }
                                            "newtab" -> {
                                                showClickPage = null
                                                tabs.add(TabItem(url = homeUrl(), title = "New Tab"))
                                                activeTabIndex = tabs.size - 1
                                            }
                                            "vpn" -> { showClickPage = null; showV9Shield = true }
                                            "dns" -> { showClickPage = null; showV9Shield = true }
                                            "cookies" -> { showClickPage = null; showCookieManager = true }
                                            else -> showClickPage = key
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

                            // LocationGuard: ASK-mode dialog — a website wants geolocation.
                            if (showLocationPrompt && locationPromptCallback != null) {
                                LocationPromptDialog(
                                    origin = locationPromptOrigin.orEmpty(),
                                    host = locationPromptHost,
                                    spoofLabel = locationSpoofLabel,
                                    onBlock = { rememberForSite ->
                                        locationPromptCallback?.invoke(locationPromptOrigin, false, false)
                                        if (rememberForSite && locationPromptHost != null) {
                                            scope.launch {
                                                repository.setLocationModeHost(
                                                    locationPromptHost!!,
                                                    LocationGuard.LocationMode.BLOCK.key
                                                )
                                            }
                                        }
                                        showLocationPrompt = false
                                        locationPromptCallback = null
                                    },
                                    onSpoof = { rememberForSite ->
                                        // Inject the spoof JS now (page-start injection may
                                        // have missed if the prompt fired first), then grant.
                                        currentTab.webView?.evaluateJavascript(
                                            LocationGuard.geolocationSpoofJs(
                                                locationSpoofLat, locationSpoofLng
                                            ),
                                            null
                                        )
                                        locationPromptCallback?.invoke(locationPromptOrigin, true, false)
                                        if (rememberForSite && locationPromptHost != null) {
                                            scope.launch {
                                                repository.setLocationModeHost(
                                                    locationPromptHost!!,
                                                    LocationGuard.LocationMode.SPOOF.key
                                                )
                                            }
                                        }
                                        showLocationPrompt = false
                                        locationPromptCallback = null
                                    },
                                    onDismiss = {
                                        // "Not now" = deny this request without remembering.
                                        locationPromptCallback?.invoke(locationPromptOrigin, false, false)
                                        showLocationPrompt = false
                                        locationPromptCallback = null
                                    }
                                )
                            }

                            // LocationGuard: full settings sheet.
                            if (showLocationSettings) {
                                LocationSettingsSheet(
                                    theme = theme,
                                    mode = locationMode,
                                    onModeChange = { m ->
                                        locationMode = m
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[AppSettings.LOCATION_MODE] = m.key
                                            }
                                        }
                                    },
                                    spoofLat = locationSpoofLat,
                                    spoofLng = locationSpoofLng,
                                    spoofLabel = locationSpoofLabel,
                                    onSpoofPreset = { preset ->
                                        locationSpoofLat = preset.lat
                                        locationSpoofLng = preset.lng
                                        locationSpoofLabel = preset.label
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[AppSettings.LOCATION_SPOOF_LAT] = preset.lat.toString()
                                                prefs[AppSettings.LOCATION_SPOOF_LNG] = preset.lng.toString()
                                                prefs[AppSettings.LOCATION_SPOOF_LABEL] = preset.label
                                            }
                                        }
                                    },
                                    onCustomSpoof = { lat, lng, label ->
                                        locationSpoofLat = lat
                                        locationSpoofLng = lng
                                        locationSpoofLabel = label
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[AppSettings.LOCATION_SPOOF_LAT] = lat.toString()
                                                prefs[AppSettings.LOCATION_SPOOF_LNG] = lng.toString()
                                                prefs[AppSettings.LOCATION_SPOOF_LABEL] = label
                                            }
                                        }
                                    },
                                    siteModes = locationSiteModes,
                                    onClearSiteMode = { host ->
                                        scope.launch {
                                            repository.setLocationModeHost(host, null)
                                        }
                                    },
                                    onClose = { showLocationSettings = false }
                                )
                            }

                            // Crash-restore offer: the previous run for this mode died
                            // uncleanly (crash / force-close / system kill).
                            // Gated on !showSplash so it pops right after the
                            // 5s Markhor intro instead of hiding beneath it.
                            if (showRestoreDialog && !showSplash) {
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = {
                                        showRestoreDialog = false
                                        scope.launch {
                                            SessionRestore.clearSession(
                                                this@MainActivity, V9Engine.bootMode
                                            )
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
                                                TabItem(url = saved.url, title = saved.title)
                                            })
                                            activeTabIndex = crashedActiveIndex.coerceIn(
                                                0, (tabs.size - 1).coerceAtLeast(0)
                                            )
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
                                            }
                                        }) {
                                            androidx.compose.material3.Text("Start fresh")
                                        }
                                    }
                                )
                            }

                            if (showSettings) {
                                SettingsScreen(
                                    theme = theme,
                                    currentThemeSetting = currentThemeSetting,
                                    onThemeChange = {
                                        currentThemeSetting = it
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[AppSettings.UI_DARK_MODE] = it == "Dark"
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
                                        scope.launch {
                                            // V9: engine switch (restarts when the engine changes).
                                            val restarting = v9SwitchMode(mode, currentTab.webView, forceDesktopMode)
                                            if (!restarting) currentTab.webView?.reload()
                                        }
                                    },
                                    currentSearchEngineSetting = currentSearchEngineSetting,
                                    onSearchEngineChange = { currentSearchEngineSetting = it },
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
                                    adBlockerEnabled = adBlockerEnabled,
                                    onToggleAdBlocker = { adBlockerEnabled = it },
                                    forceNightMode = forceNightModeWebsites,
                                    onToggleNightMode = { forceNightModeWebsites = it },
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
                                    blockConsentBanners = blockConsentBanners,
                                    onToggleBlockConsentBanners = { v ->
                                        blockConsentBanners = v
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.BLOCK_CONSENT_BANNERS] = v } }
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
                                    onClearHistoryForMode = { mode ->
                                        scope.launch {
                                            repository.clearHistoryFor(mode)
                                            Toast.makeText(
                                                this@MainActivity,
                                                "${mode.name.lowercase().replaceFirstChar { it.uppercase() }} history cleared",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onClearCookies = {
                                        // CookieManager is pinned to the current engine's data
                                        // directory — this clears the CURRENT mode's cookies only.
                                        CookieStore.clearAll {
                                            Toast.makeText(this@MainActivity, "Cookies cleared (current mode)", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onClearCache = {
                                        currentTab.webView?.clearCache(true)
                                        Toast.makeText(this@MainActivity, "Cache cleared (current mode)", Toast.LENGTH_SHORT).show()
                                    },
                                    onClearAllCurrentMode = {
                                        scope.launch { repository.clearHistory() }
                                        CookieStore.clearAll()
                                        currentTab.webView?.clearCache(true)
                                        Toast.makeText(this@MainActivity, "All browsing data cleared (current mode)", Toast.LENGTH_SHORT).show()
                                    },
                                    onOpenCookieManager = { showSettings = false; showCookieManager = true },
                                    locationSummary = LocationGuard.websitesWillSee(locationMode, locationSpoofLabel),
                                    onOpenLocationSettings = { showLocationSettings = true },
                                    perModeDark = perModeDark,
                                    onPerModeThemeChange = { mode, dark ->
                                        val updated = perModeDark.toMutableMap()
                                        if (dark == null) updated.remove(mode) else updated[mode] = dark
                                        perModeDark = updated
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                if (dark == null) prefs.remove(AppSettings.darkModeKey(mode))
                                                else prefs[AppSettings.darkModeKey(mode)] = dark
                                            }
                                        }
                                    },
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
                            // V9 Shield screen (VPN + DNS + 3-engine identities).
                            if (showV9Shield) {
                                V9ShieldScreen(onClose = { showV9Shield = false })
                            }
                            // Help & Feedback (FAQ + email to Prince).
                            if (showHelp) {
                                com.click.browser.ui.screens.HelpFeedbackScreen(
                                    theme = theme,
                                    onClose = { showHelp = false }
                                )
                            }
                            // Password manager: saved-logins list.
                            if (showPasswordManager) {
                                com.click.browser.ui.screens.PasswordManagerScreen(
                                    theme = theme,
                                    onClose = { showPasswordManager = false }
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
                            // V9: Hack Mode signature moment — full-screen 5s
                            // Markhor intro after a Hack engine boot. Tap to skip.
                            if (showHackIntro) {
                                HackIntroOverlay(onDone = { showHackIntro = false })
                            }
                            // V9: Advance Mode signature moment — full-screen 5s
                            // blue-light intro after an Advance engine boot. Tap to skip.
                            if (showAdvanceIntro) {
                                AdvanceIntroOverlay(onDone = { showAdvanceIntro = false })
                            }
                            // "About Advance Mode" specifications sheet.
                            if (showAdvanceSpecs) {
                                AdvanceSpecsSheet(
                                    onClose = { showAdvanceSpecs = false },
                                    onEnterAdvance = {
                                        showAdvanceSpecs = false
                                        scope.launch {
                                            drawerState.close()
                                            // Same feedback contract as the drawer's Advance entry:
                                            // if the engine restart wasn't possible, say so.
                                            val restarting = v9SwitchMode(BrowserMode.ADVANCED, currentTab.webView, forceDesktopMode)
                                            if (!restarting) Toast.makeText(this@MainActivity, "Advance Mode Activated — fresh isolated space", Toast.LENGTH_SHORT).show()
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
                                com.click.browser.ui.screens.TranslateSheet(
                                    theme = theme,
                                    webView = currentTab.webView,
                                    pageUrl = currentTab.url,
                                    onDismiss = { showTranslateSheet = false }
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
                                        // Real download via DownloadManager (existing pattern).
                                        try {
                                            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                            val fileName = offerUrl.substringAfterLast("/").take(64).ifBlank { "document.pdf" }
                                            val request = android.app.DownloadManager.Request(android.net.Uri.parse(offerUrl))
                                                .setTitle("Click Browser Download")
                                                .setDescription(fileName)
                                                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                .setDestinationInExternalPublicDir(
                                                    android.os.Environment.DIRECTORY_DOWNLOADS,
                                                    "ClickBrowser/$fileName"
                                                )
                                                .setAllowedOverMetered(true)
                                                .setAllowedOverRoaming(false)
                                            dm.enqueue(request)
                                            Toast.makeText(this@MainActivity, "Downloading PDF…", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(this@MainActivity, "Download failed.", Toast.LENGTH_SHORT).show()
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
                            // Password manager: "Save password?" offer dialog.
                            val pwSave = pendingPasswordSave
                            if (showPasswordSaveDialog && pwSave != null) {
                                AlertDialog(
                                    onDismissRequest = {
                                        showPasswordSaveDialog = false
                                        pendingPasswordSave = null
                                    },
                                    title = { Text("Save password?") },
                                    text = {
                                        Text(
                                            "Save the login for ${pwSave.host} " +
                                                (if (pwSave.username.isNotBlank()) "(${pwSave.username}) " else "") +
                                                "in Click's encrypted password manager?"
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            scope.launch {
                                                com.click.browser.engine.PasswordManager.save(
                                                    this@MainActivity,
                                                    pwSave.host, pwSave.username, pwSave.password
                                                )
                                            }
                                            showPasswordSaveDialog = false
                                            pendingPasswordSave = null
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Password saved (encrypted).",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }) {
                                            Text("Save")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = {
                                            showPasswordSaveDialog = false
                                            pendingPasswordSave = null
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
                            // AI chat panel — one-shot slide/spring entrance (battery-safe:
                            // no continuous animation).
                            androidx.compose.animation.AnimatedVisibility(
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
                                AiChatScreen(
                                    apiKey = effectiveAiKey,
                                    providerId = aiProvider,
                                    model = aiModel,
                                    secureDns = secureDnsEnabled,
                                    usingBuiltInKey = usingBuiltInKey,
                                    pageTitle = currentTab.title,
                                    pageUrl = currentTab.url,
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
                                    onOpenSettings = { showAiChat = false; showSettings = true },
                                    onClose = { showAiChat = false }
                                )
                            }
                            } // AnimatedVisibility (AI chat slide/spring)
                            if (showPrivacyGuards) {
                                PrivacyGuardsScreen(
                                    headerSpoofEnabled = headerSpoofEnabled,
                                    onToggleHeaderSpoof = { v ->
                                        headerSpoofEnabled = v
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.HEADER_SPOOF_ENABLED] = v } }
                                    },
                                    customHeaders = customHeaders,
                                    onAddHeader = { name, value ->
                                        customHeaders = customHeaders + AppSettings.CustomHeader(name, value)
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[AppSettings.CUSTOM_HEADERS_JSON] = AppSettings.headersToJson(customHeaders)
                                            }
                                        }
                                    },
                                    onRemoveHeader = { index ->
                                        customHeaders = customHeaders.filterIndexed { i, _ -> i != index }
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[AppSettings.CUSTOM_HEADERS_JSON] = AppSettings.headersToJson(customHeaders)
                                            }
                                        }
                                    },
                                    fingerprintMode = fingerprintMode,
                                    onFingerprintModeChange = { mode ->
                                        fingerprintMode = AppSettings.normalizeMode(mode)
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.FINGERPRINT_MODE] = fingerprintMode } }
                                    },
                                    dntEnabled = dntEnabled,
                                    onToggleDnt = { v ->
                                        dntEnabled = v
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.DNT_ENABLED] = v } }
                                    },
                                    gpcEnabled = gpcEnabled,
                                    onToggleGpc = { v ->
                                        gpcEnabled = v
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.GPC_ENABLED] = v } }
                                    },
                                    secureDns = secureDnsEnabled,
                                    onToggleSecureDns = { v ->
                                        secureDnsEnabled = v
                                        scope.launch { dataStore.edit { prefs -> prefs[AppSettings.SECURE_DNS_ENABLED] = v } }
                                    },
                                    webrtcRunning = webrtcTestRunning,
                                    webrtcIps = webrtcIps,
                                    webrtcTested = webrtcTested,
                                    onRunWebrtcTest = { runWebrtcLeakTest() },
                                    webrtcGuardEnabled = userscripts.any {
                                        it.meta.name == UserscriptManager.WEBRTC_GUARD_NAME && it.enabled
                                    },
                                    // Userscripts inject in Developer/Hack modes only —
                                    // never imply protection in Simple mode.
                                    webrtcGuardApplies = activeMode == BrowserMode.DEVELOPER ||
                                        activeMode == BrowserMode.HACK ||
                                        activeMode == BrowserMode.ADVANCED,
                                    onToggleWebrtcGuard = { v ->
                                        scope.launch(Dispatchers.IO) {
                                            userscriptManager.setWebrtcGuardEnabled(v)
                                            withContext(Dispatchers.Main) { refreshUserscripts() }
                                        }
                                    },
                                    onClose = { showPrivacyGuards = false }
                                )
                            }
                            if (showUserscripts) {
                                UserscriptsScreen(
                                    scripts = userscripts,
                                    notice = userscriptNotice,
                                    onToggleScript = { id, enabled ->
                                        scope.launch(Dispatchers.IO) {
                                            userscriptManager.setEnabled(id, enabled)
                                            withContext(Dispatchers.Main) { refreshUserscripts() }
                                        }
                                    },
                                    onDeleteScript = { id ->
                                        scope.launch(Dispatchers.IO) {
                                            userscriptManager.delete(id)
                                            withContext(Dispatchers.Main) {
                                                userscriptNotice = "Script deleted."
                                                refreshUserscripts()
                                            }
                                        }
                                    },
                                    onInstallSource = { source -> installUserscriptFromSource(source) },
                                    onInstallUrl = { url -> installUserscriptFromUrl(url) },
                                    onClose = {
                                        showUserscripts = false
                                        userscriptNotice = null
                                    }
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
                                            currentTab.webView?.findNext(false)
                                        }) {
                                            Text("Prev")
                                        }
                                    },
                                    dismissButton = {
                                        Row {
                                            TextButton(onClick = {
                                                currentTab.webView?.findNext(true)
                                            }) {
                                                Text("Next")
                                            }
                                            TextButton(onClick = {
                                                currentTab.webView?.clearMatches()
                                                showFindInPageDialog = false
                                            }) {
                                                Text("Close")
                                            }
                                        }
                                    }
                                )
                            }

                            // Chrome/Mises-style browser menu (bottom sheet). Every item
                            // works — New tab, New private tab, Tabs switcher,
                            // History, Delete browsing data, Downloads, Bookmarks,
                            // Recent tabs, Extensions (userscripts), Share,
                            // Find in page, Translate, Desktop site, Settings.
                            if (showBrowserMenu) {
                                val menuPageHost = try {
                                    android.net.Uri.parse(currentTab.url).host?.lowercase().orEmpty()
                                } catch (_: Exception) { "" }
                                val menuDesktopForSite = menuPageHost.isNotEmpty() && (menuPageHost in desktopHosts
                                    || desktopHosts.any { h -> menuPageHost == h || menuPageHost.endsWith(".$h") })
                                BrowserMenuSheet(
                                    theme = theme,
                                    isDesktopForSite = menuDesktopForSite,
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
                                        this@MainActivity.requestStoragePermissions()
                                        showDownloads = true
                                    },
                                    onBookmarks = { showBrowserMenu = false; showBookmarks = true },
                                    onGames = { showBrowserMenu = false; showClickPage = "games" },
                                    onRecentTabs = { showBrowserMenu = false; showRecentTabs = true },
                                    onExtensions = { showBrowserMenu = false; showUserscripts = true },
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
                                                repository.setDesktopHost(menuPageHost, !menuDesktopForSite)
                                                currentTab.webView?.let { wv ->
                                                    modeManager.applyDesktopOverride(wv, activeMode, !menuDesktopForSite)
                                                    wv.reload()
                                                }
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    if (!menuDesktopForSite) "Desktop site enabled for $menuPageHost"
                                                    else "Mobile site restored for $menuPageHost",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    onSettings = { showBrowserMenu = false; showSettings = true },
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
                                    closedTabs = RecentlyClosedTabs.list(),
                                    theme = theme,
                                    onReopen = { ct ->
                                        tabs.add(TabItem(url = ct.url, title = ct.title))
                                        activeTabIndex = tabs.size - 1
                                        RecentlyClosedTabs.remove(ct)
                                        showRecentTabs = false
                                    },
                                    onRemove = { ct -> RecentlyClosedTabs.remove(ct) },
                                    onClearAll = { RecentlyClosedTabs.clear() },
                                    onClose = { showRecentTabs = false }
                                )
                            }

                            // Delete browsing data — confirmation first.
                            if (showDeleteBrowsingConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteBrowsingConfirm = false },
                                    title = { Text("Delete browsing data?") },
                                    text = { Text("This clears history, cache, cookies and site data for all tabs.") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            scope.launch {
                                                repository.clearHistory()
                                                currentTab.webView?.clearHistory()
                                                currentTab.webView?.clearCache(true)
                                                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                                                android.webkit.CookieManager.getInstance().flush()
                                                android.webkit.WebStorage.getInstance().deleteAllData()
                                                showDeleteBrowsingConfirm = false
                                                Toast.makeText(this@MainActivity, "Browsing data deleted.", Toast.LENGTH_SHORT).show()
                                            }
                                        }) { Text("Delete") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteBrowsingConfirm = false }) { Text("Cancel") }
                                    }
                                )
                            }

                            // Downloader selection overlay — REAL downloads via Android DownloadManager.
                            // Live (blob:) streams are listed separately and NEVER auto-toast:
                            // the "can't be downloaded" notice appears ONLY when the user
                            // explicitly taps a live-stream row (Prince's bug report).
                            if (showDownloaderDialog) {
                                val downloadable = detectedVideos.filter { !it.startsWith("blob:") }
                                val liveStreams = detectedVideos.filter { it.startsWith("blob:") }
                                AlertDialog(
                                    onDismissRequest = { showDownloaderDialog = false },
                                    title = { Text("Video Downloader", color = Color(0xFFFF5722)) },
                                    text = {
                                        Column {
                                            if (detectedVideos.isEmpty()) {
                                                Text("No videos detected on this page yet. Videos are detected automatically in Hack mode.")
                                            } else {
                                                if (downloadable.isNotEmpty()) Text("Detected Videos on page:")
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            downloadable.forEachIndexed { idx, url ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            try {
                                                                val dm = this@MainActivity.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                                                val fileName = "click_video_${System.currentTimeMillis()}.mp4"
                                                                val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                                                                    .setTitle("Click Browser Download")
                                                                    .setDescription(url.take(80))
                                                                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                                    .setDestinationInExternalPublicDir(
                                                                        android.os.Environment.DIRECTORY_DOWNLOADS,
                                                                        "ClickBrowser/$fileName"
                                                                    )
                                                                    .setAllowedOverMetered(true)
                                                                    .setAllowedOverRoaming(false)
                                                                dm.enqueue(request)
                                                                scope.launch {
                                                                    repository.addDownloadItem(
                                                                        DownloadItem(
                                                                            fileName = fileName,
                                                                            url = url,
                                                                            path = "Downloads/ClickBrowser/$fileName",
                                                                            timestamp = System.currentTimeMillis()
                                                                        )
                                                                    )
                                                                }
                                                                Toast.makeText(
                                                                    this@MainActivity,
                                                                    "Download started — see notification / Downloads Center",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                            } catch (e: Exception) {
                                                                Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                            }
                                                            showDownloaderDialog = false
                                                        }
                                                        .padding(vertical = 4.dp),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text("Video ${idx + 1} — tap to download", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                        Text(url.take(60) + "...", fontSize = 11.sp, color = Color.Gray)
                                                    }
                                                }
                                            }
                                            // Live streams: shown, never auto-downloaded, never auto-toast.
                                            // Tapping one is the ONLY way the notice appears.
                                            if (liveStreams.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    "Live streams (${liveStreams.size}) — can't be downloaded:",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = Color.Gray
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                liveStreams.forEachIndexed { idx, url ->
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                Toast.makeText(
                                                                    this@MainActivity,
                                                                    "This video is a live stream (blob) and can't be downloaded directly.",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                            }
                                                            .padding(vertical = 4.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                        )
                                                    ) {
                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                            Text(
                                                                "Live stream ${idx + 1} — tap for info",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp,
                                                                color = Color.Gray
                                                            )
                                                            Text(url.take(48) + "...", fontSize = 11.sp, color = Color.Gray)
                                                        }
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

                            // App-start intro: 5s Markhor animation on EVERY cold
                            // start (Prince's request) — last child of the root
                            // composition, so it draws above everything.
                            // Tap anywhere skips it immediately.
                            AnimatedVisibility(
                                visible = showSplash,
                                enter = EnterTransition.None,
                                exit = fadeOut(animationSpec = tween(450)),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                HackIntroOverlay(
                                    onDone = { showSplash = false },
                                    durationMs = 5000L,
                                    title = "CLICK BROWSER",
                                    subtitle = "TEAM PK AI ERA"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** New-tab URL: custom homepage flag or the premium home (about:blank). */
    private fun homeUrl(): String {
        val custom = liveFlags.customHomepage.trim()
        return if (custom.isEmpty()) "about:blank" else custom
    }

    /**
     * HACK mode desktop persistence: rewrites known mobile hosts to their
     * desktop equivalents so sites can't flip Hack mode back to mobile view.
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

    private fun formatUrl(input: String, searchEngine: String, mode: BrowserMode): String {
        val trimmed = input.trim()
        // click:// internal pages (chrome://-style): pass through (normalized
        // to lowercase) so shouldOverrideUrlLoading can intercept them into
        // native screens.
        if (trimmed.startsWith("click://", ignoreCase = true)) {
            return trimmed.lowercase()
        }
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
                    "AI Search", "integrated AI search" -> "https://perplexity.ai/search?q=$query"
                    else -> "https://ahmia.fi/search/?q=$query"
                }
            }
            BrowserMode.ADVANCED -> {
                when (searchEngine) {
                    "Google" -> "https://www.google.com/search?q=$query"
                    "Brave Search" -> "https://search.brave.com/search?q=$query"
                    "DuckDuckGo" -> "https://duckduckgo.com/?q=$query"
                    "Startpage" -> "https://www.startpage.com/sp/search?query=$query"
                    else -> "https://www.google.com/search?q=$query"
                }
            }
        }
    }

    /**
     * V9: switching modes = switching engines. Each engine (Simple / Developer
     * / Hack / Advance) owns an isolated WebView data directory, so when the
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
            webView?.let { modeManager.applySettings(it, mode, forceDesktop) }
            return false
        }
        val toastMsg = if (mode == BrowserMode.ADVANCED) {
            // Honest copy: a fresh isolated space, not a new engine.
            "Entering Click Advance — fresh isolated space, restarting…"
        } else {
            "Switching V9 engine — restarting…"
        }
        android.widget.Toast.makeText(
            this, toastMsg, android.widget.Toast.LENGTH_LONG
        ).show()
        // Crash-restore: an intentional engine switch is a CLEAN exit of the
        // source mode — persist its final tab snapshot and mark it clean so
        // switching Simple→Hack→Simple restores Simple's tabs instead of
        // offering a phantom "crash" restore. Per-mode keys keep isolation.
        // liveRestoreTabs is refreshed synchronously on every tab change
        // (only the DataStore write is debounced), so this is never stale.
        val sourceMode = V9Engine.bootMode
        SessionRestore.saveSession(this, sourceMode, liveRestoreTabs, liveRestoreActiveIndex)
        SessionRestore.markCleanExit(this, sourceMode, true)
        val restarted = V9Engine.restartForEngineSwitch(
            this, modeManager, mode,
            hackIntro = (mode == BrowserMode.HACK),
            advanceIntro = (mode == BrowserMode.ADVANCED)
        )
        if (!restarted) {
            // Restart wasn't possible (alarm unavailable etc.) — apply the
            // mode in-place WITHOUT engine isolation rather than killing
            // the app. Cookies stay shared, but the app stays alive.
            webView?.let { modeManager.applySettings(it, mode, forceDesktop) }
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
                            repository.addDownloadItem(
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
 * Styled per the three approved mode designs:
 *   Row 1: [hamburger] "Click" wordmark + SMALL stylish mode pill ..... [settings]
 *   Row 2: [back][forward] [ full-width rounded address/search field ] [refresh][bookmark]
 *   Row 3 (Developer mode only): element inspector + device emulator toggles.
 * The big HACK pill button is gone — the mode shows only as the small pill,
 * like the "SIMPLE MODE" pill in the approved Simple design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserTopBar(
    activeMode: BrowserMode,
    theme: ModeTheme,
    currentUrl: String,
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
    onToggleEmulator: () -> Unit
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
                    if (currentUrl.startsWith("https")) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Secure",
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = theme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
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
            modifier = Modifier.clickable(onClick = onOpenTabSwitcher),
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
            itemsIndexed(tabs) { idx, tab ->
                val selected = idx == activeTabIndex
                Surface(
                    modifier = Modifier.clickable { onSelectTab(idx) },
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
                            text = tab.title.ifBlank { "New Tab" }.take(18),
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
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close tab",
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
                            // night mode
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("🌙 Force Night Mode Website", color = theme.onBackground, fontSize = 11.sp)
                                Switch(checked = forceNightMode, onCheckedChange = onToggleNightMode, modifier = Modifier.scale(0.8f))
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
