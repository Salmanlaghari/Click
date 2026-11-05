package com.click.browser.extensions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.click.browser.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.mozilla.geckoview.WebResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * AMO store and add-on manager surface. It shares Click's GeckoView runtime with normal Gecko tabs,
 * while remaining separate from WebView storage and browser bridges.
 * GeckoView validates Mozilla signatures before installation; this activity never registers native messaging.
 * Do not add WebExtensionController.update(): GeckoView may apply same-permission updates without a prompt,
 * and the pinned API cannot preflight the updated add-on's current Android compatibility or AMO package record.
 */
class ExtensionBrowserActivity : ComponentActivity() {
    private var runtime: GeckoRuntime? = null
    private var session: GeckoSession? = null
    private var originTabId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val geckoRuntime = ExtensionBrowserRuntime.get(applicationContext)
        runtime = geckoRuntime
        session = GeckoSession().also { geckoSession ->
            geckoSession.open(geckoRuntime)
            geckoSession.setPermissionDelegate(DenyWebsiteDevicePermissions)
        }

        originTabId = intent.getStringExtra(EXTRA_RETURN_TAB_ID)
        val requestedStartUrl = intent.getStringExtra(EXTRA_START_URL).orEmpty()
        val openManagerOnStart = intent.getBooleanExtra(EXTRA_SHOW_MANAGER, false)
        setContent {
            MaterialTheme {
                ExtensionBrowserScreen(
                    runtime = geckoRuntime,
                    session = requireNotNull(session),
                    requestedStartUrl = requestedStartUrl,
                    openManagerOnStart = openManagerOnStart,
                    onCatalogInstallSucceeded = ::reportCatalogInstallSuccess,
                    onExit = { finish() }
                )
            }
        }
    }

    private fun reportCatalogInstallSuccess(extensionId: String, extensionName: String, enabled: Boolean) {
        val sourceTabId = originTabId ?: return
        if (!enabled) return
        runOnUiThread {
            val result = Intent()
                .putExtra(EXTRA_RETURN_TAB_ID, sourceTabId)
                .putStringArrayListExtra(EXTRA_INSTALLED_EXTENSION_IDS, arrayListOf(extensionId))
                .putExtra(EXTRA_INSTALLED_EXTENSION_NAME, extensionName)
            setResult(android.app.Activity.RESULT_OK, result)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        setExtensionTabActive(true)
    }

    override fun onPause() {
        setExtensionTabActive(false)
        super.onPause()
    }

    private fun setExtensionTabActive(active: Boolean) {
        val currentRuntime = runtime ?: return
        val currentSession = session ?: return
        // Action.click() resolves against GeckoView's active tab; the public click API carries no session ID.
        currentRuntime.webExtensionController.setTabActive(currentSession, active)
    }

    override fun onDestroy() {
        // The manager is only attached while this UI is alive; null means any background prompt
        // is denied by GeckoView rather than routed to a stale Activity.
        runtime?.webExtensionController?.setPromptDelegate(null)
        session?.close()
        session = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_START_URL = "com.click.browser.extensions.START_URL"
        const val EXTRA_SHOW_MANAGER = "com.click.browser.extensions.SHOW_MANAGER"
        const val EXTRA_RETURN_TAB_ID = "com.click.browser.extensions.RETURN_TAB_ID"
        const val EXTRA_INSTALLED_EXTENSION_IDS = "com.click.browser.extensions.INSTALLED_IDS"
        const val EXTRA_INSTALLED_EXTENSION_NAME = "com.click.browser.extensions.INSTALLED_NAME"
    }
}

/** No page or add-on may request Android device permissions in this extension browser. */
private object DenyWebsiteDevicePermissions : GeckoSession.PermissionDelegate {
    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        callback.reject()
    }

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int> = GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback
    ) {
        callback.reject()
    }
}

private data class PendingExtensionPrompt(
    val kind: String,
    val extensionName: String,
    val extensionId: String,
    val publisher: String,
    val source: String,
    val permissions: List<String>,
    val origins: List<String>,
    val dataCollectionPermissions: List<String>,
    val blockedReasons: List<String>,
    val decide: (allow: Boolean, allowDataCollection: Boolean) -> Unit
)

private data class ExtensionActionItem(
    val extension: WebExtension,
    val action: WebExtension.Action
)

internal fun isEligibleMozillaCatalogExtension(
    isSigned: Boolean,
    isNotBlocked: Boolean,
    amoListingUrl: String?,
    extensionId: String?
): Boolean = isSigned && isNotBlocked &&
    FirefoxAmoCatalog.isApprovedAmoListing(amoListingUrl) &&
    FirefoxAmoCatalog.isApprovedExtensionId(extensionId)

private val amoApiClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
}

private suspend fun fetchCurrentAmoPackage(slug: String): VerifiedAmoCatalogRecord? =
    withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://addons.mozilla.org/api/v5/addons/addon/${Uri.encode(slug)}/?lang=en-US")
            .header("Accept", "application/json")
            .header("User-Agent", "ClickBrowser/${com.click.browser.BuildConfig.VERSION_NAME} (Android add-on catalog)")
            .get()
            .build()
        runCatching {
            amoApiClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrBlank()) {
                    FirefoxAmoCatalog.verifyCurrentAndroidCompatiblePackage(slug, body)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

private fun isTrustedMozillaExtension(extension: WebExtension): Boolean {
    val metadata = extension.metaData
    return isEligibleMozillaCatalogExtension(
        isSigned = metadata.signedState == WebExtension.SignedStateFlags.SIGNED,
        isNotBlocked = metadata.blocklistState == WebExtension.BlocklistStateFlags.NOT_BLOCKED,
        amoListingUrl = metadata.amoListingUrl,
        extensionId = extension.id
    )
}

internal fun extensionPolicyBlockers(extension: WebExtension): List<String> {
    val metadata = extension.metaData
    val currentPermissions = metadata.requiredPermissions.orEmpty().asList() +
        metadata.grantedOptionalPermissions.orEmpty().asList()
    val currentOrigins = metadata.requiredOrigins.orEmpty().asList() +
        metadata.grantedOptionalOrigins.orEmpty().asList()
    return buildList {
        if (!isTrustedMozillaExtension(extension)) {
            add("Not a Mozilla-signed, non-blocklisted official AMO add-on")
        }
        addAll(ExtensionInstallPolicy.unsupportedPermissionNames(currentPermissions).map { "Unsupported capability: $it" })
        addAll(ExtensionInstallPolicy.blockedOriginPatterns(currentOrigins).map { "Unreviewable site access pattern: $it" })
    }.distinct()
}

@Composable
private fun ExtensionBrowserScreen(
    runtime: GeckoRuntime,
    session: GeckoSession,
    requestedStartUrl: String,
    openManagerOnStart: Boolean,
    onCatalogInstallSucceeded: (extensionId: String, extensionName: String, enabled: Boolean) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val controller = remember(runtime) { runtime.webExtensionController }
    val sourcePrefs = remember(context) {
        context.getSharedPreferences("extension_browser_sources", Context.MODE_PRIVATE)
    }

    var address by remember {
        mutableStateOf(
            ExtensionBrowserInput.resolve(requestedStartUrl)
                ?: "https://duckduckgo.com/"
        )
    }
    var currentAmoEntry by remember { mutableStateOf<FirefoxAmoCatalogEntry?>(null) }
    var title by remember { mutableStateOf("Firefox for Android Add-ons") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var installedExtensions by remember { mutableStateOf<List<WebExtension>>(emptyList()) }
    var installedExtensionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var extensionActions by remember { mutableStateOf<Map<String, ExtensionActionItem>>(emptyMap()) }
    var popupSession by remember { mutableStateOf<GeckoSession?>(null) }
    var popupTitle by remember { mutableStateOf("") }
    var extensionListLoaded by remember { mutableStateOf(false) }
    var showManager by remember { mutableStateOf(openManagerOnStart) }
    var removeTarget by remember { mutableStateOf<WebExtension?>(null) }
    var pendingPrompt by remember { mutableStateOf<PendingExtensionPrompt?>(null) }
    var pageInstallConfirmation by remember { mutableStateOf<FirefoxAmoCatalogEntry?>(null) }
    var installingCatalogSlug by remember { mutableStateOf<String?>(null) }
    var verifiedCatalogPackage by remember { mutableStateOf<VerifiedAmoCatalogRecord?>(null) }

    val latestInstalledExtensions = rememberUpdatedState(installedExtensions)
    val latestPopupSession = rememberUpdatedState(popupSession)

    fun canExposeAction(extension: WebExtension): Boolean =
        extension.metaData.enabled && extensionPolicyBlockers(extension).isEmpty()

    fun openActionPopup(extension: WebExtension): GeckoResult<GeckoSession>? {
        if (!canExposeAction(extension)) return null
        popupSession?.close()
        val nextPopup = GeckoSession().also {
            it.open(runtime)
            it.setPermissionDelegate(DenyWebsiteDevicePermissions)
        }
        popupTitle = extension.metaData.name.orEmpty().ifBlank { "Add-on" }
        popupSession = nextPopup
        return GeckoResult.fromValue(nextPopup)
    }

    fun updateAction(
        extension: WebExtension,
        actionSession: GeckoSession?,
        action: WebExtension.Action,
        isPageAction: Boolean
    ) {
        if (actionSession != null && actionSession !== session) return
        val visible = canExposeAction(extension) && action.enabled != false &&
            (!isPageAction || action.enabled == true)
        extensionActions = if (visible) {
            extensionActions + (extension.id to ExtensionActionItem(extension, action))
        } else {
            extensionActions - extension.id
        }
    }

    val actionDelegate = remember(runtime, session) {
        object : WebExtension.ActionDelegate {
            override fun onBrowserAction(
                extension: WebExtension,
                session: GeckoSession?,
                action: WebExtension.Action
            ) {
                updateAction(extension, session, action, isPageAction = false)
            }

            override fun onPageAction(
                extension: WebExtension,
                session: GeckoSession?,
                action: WebExtension.Action
            ) {
                updateAction(extension, session, action, isPageAction = true)
            }

            override fun onOpenPopup(
                extension: WebExtension,
                action: WebExtension.Action
            ): GeckoResult<GeckoSession>? = openActionPopup(extension)

            override fun onTogglePopup(
                extension: WebExtension,
                action: WebExtension.Action
            ): GeckoResult<GeckoSession>? = openActionPopup(extension)
        }
    }

    fun refreshExtensions() {
        controller.list().accept(
            { extensions ->
                val appExtensions = extensions.orEmpty().filterNot { it.isBuiltIn }
                installedExtensionIds = appExtensions.map { it.id }.toSet()
                extensionListLoaded = true
                ExtensionToolbarState.updateFromExtensions(context, appExtensions)
                val disallowed = appExtensions.filter { extensionPolicyBlockers(it).isNotEmpty() }
                disallowed.filter { it.metaData.enabled }.forEach { extension ->
                    controller.disable(extension, WebExtensionController.EnableSource.APP)
                }
                // Keep disallowed add-ons visible so the user can remove them; never let the manager enable them.
                installedExtensions = appExtensions
                if (disallowed.isNotEmpty()) {
                    statusMessage = "Disabled ${disallowed.size} extension(s) that do not meet Click's safe add-on policy."
                }
                val permittedActionExtensions = appExtensions.filter(::canExposeAction)
                permittedActionExtensions.forEach { extension ->
                    extension.setActionDelegate(actionDelegate)
                    session.webExtensionController.setActionDelegate(extension, actionDelegate)
                }
                val permittedIds = permittedActionExtensions.map { it.id }.toSet()
                extensionActions = extensionActions.filterKeys { it in permittedIds }
            },
            { error ->
                extensionListLoaded = false
                ExtensionToolbarState.invalidate(context)
                statusMessage = "Couldn't load extensions: ${error?.message ?: "unknown error"}"
            }
        )
    }

    fun beginCatalogInstall(entry: FirefoxAmoCatalogEntry) {
        val listingSlug = FirefoxAmoCatalog.slugFromOfficialListing(entry.listingUrl)
        if (listingSlug != entry.slug.lowercase()) {
            statusMessage = "Blocked: this is not a matching official Mozilla AMO Android listing."
            return
        }
        if (installingCatalogSlug != null || pendingPrompt != null) return
        if (!extensionListLoaded) {
            statusMessage = "Click is still checking the Gecko add-on list. Try again in a moment."
            refreshExtensions()
            return
        }
        if (entry.extensionId in installedExtensionIds) {
            statusMessage = "${entry.name} is already installed. Remove it before installing a replacement."
            return
        }

        showManager = false
        installingCatalogSlug = entry.slug
        statusMessage = "Checking the official AMO record for current Android compatibility…"
        scope.launch {
            try {
                val record = fetchCurrentAmoPackage(entry.slug)
                if (record == null || (entry.extensionId.isNotBlank() && record.entry.extensionId != entry.extensionId)) {
                    installingCatalogSlug = null
                    statusMessage = "Install stopped: Click could not verify a public, Android-compatible AMO package and official XPI. Check your connection and try again."
                    return@launch
                }
                val verifiedEntry = record.entry
                val packageInfo = record.packageInfo
                val unsupportedPermissions = ExtensionInstallPolicy.unsupportedPermissionNames(verifiedEntry.requiredPermissions)
                val unreviewableOrigins = ExtensionInstallPolicy.blockedOriginPatterns(verifiedEntry.requiredOrigins)
                if (unsupportedPermissions.isNotEmpty() || unreviewableOrigins.isNotEmpty()) {
                    installingCatalogSlug = null
                    statusMessage = ExtensionUiGuidance.blockedCatalogInstallStatus(unsupportedPermissions, unreviewableOrigins)
                    return@launch
                }
                if (packageInfo.extensionId in installedExtensionIds) {
                    installingCatalogSlug = null
                    statusMessage = "${verifiedEntry.name} is already installed. Remove it before installing a replacement."
                    return@launch
                }

                verifiedCatalogPackage = record
                statusMessage = "AMO confirms ${verifiedEntry.name} v${packageInfo.version} supports Android ${packageInfo.minimumAndroidVersion}–${FirefoxAmoCatalog.GECKOVIEW_ANDROID_COMPAT_VERSION}; GeckoView will verify its Mozilla signature and permissions."
                controller.install(
                    packageInfo.downloadUrl,
                    WebExtensionController.INSTALLATION_METHOD_MANAGER
                ).accept(
                    { extension ->
                        installingCatalogSlug = null
                        verifiedCatalogPackage = null
                        if (extension == null) {
                            statusMessage = "Install rejected: GeckoView returned no extension."
                        } else if (extension.id != packageInfo.extensionId ||
                            FirefoxAmoCatalog.slugFromOfficialListing(extension.metaData.amoListingUrl) != packageInfo.slug
                        ) {
                            controller.disable(extension, WebExtensionController.EnableSource.APP)
                            sourcePrefs.edit().remove(extension.id).apply()
                            statusMessage = "Install rejected: the installed add-on identity did not match the live AMO record."
                            refreshExtensions()
                        } else {
                            val name = extension.metaData.name.orEmpty().ifBlank { verifiedEntry.name }
                            sourcePrefs.edit().putString(
                                extension.id,
                                "Mozilla AMO API-verified Android-compatible package v${packageInfo.version} (${packageInfo.slug})"
                            ).apply()
                            statusMessage = "Installed $name from official AMO."
                            refreshExtensions()
                            onCatalogInstallSucceeded(extension.id, name, extension.metaData.enabled)
                        }
                    },
                    { error ->
                        installingCatalogSlug = null
                        verifiedCatalogPackage = null
                        statusMessage = "Install rejected by GeckoView: ${error?.message?.take(180) ?: "invalid, unsigned, blocked, or incompatible XPI"}"
                    }
                )
            } catch (error: Exception) {
                installingCatalogSlug = null
                verifiedCatalogPackage = null
                statusMessage = "Install stopped: ${error.message?.take(180) ?: "AMO verification failed"}"
            }
        }
    }

    fun sourceDescription(extension: WebExtension): String {
        val metadata = extension.metaData
        val saved = sourcePrefs.getString(extension.id, null)
        val downloadUrl = metadata.downloadUrl
        return buildList {
            if (!saved.isNullOrBlank()) add(saved)
            if (!metadata.amoListingUrl.isNullOrBlank()) add("Mozilla Add-ons listing: ${metadata.amoListingUrl}")
            if (!downloadUrl.isNullOrBlank() && downloadUrl.startsWith("https://")) {
                add("Package URL: $downloadUrl")
            }
        }.distinct().joinToString("\n").ifBlank {
            "No verified AMO source was recorded for this package."
        }
    }

    fun buildPrompt(
        kind: String,
        extension: WebExtension,
        permissions: List<String>,
        origins: List<String>,
        dataCollectionPermissions: List<String>,
        decide: (Boolean, Boolean) -> Unit
    ): PendingExtensionPrompt {
        val metadata = extension.metaData
        val downloadUrl = metadata.downloadUrl
        val publisher = metadata.creatorName.orEmpty().takeIf { it.isNotBlank() } ?: "Not declared"
        val source = buildList {
            verifiedCatalogPackage?.takeIf { it.packageInfo.extensionId == extension.id }?.let { record ->
                add("Live AMO check: ${record.entry.slug} v${record.packageInfo.version}, Android ${record.packageInfo.minimumAndroidVersion}–${FirefoxAmoCatalog.GECKOVIEW_ANDROID_COMPAT_VERSION}, ${record.packageInfo.fileSize} bytes")
            }
            if (!metadata.amoListingUrl.isNullOrBlank()) add("Mozilla Add-ons listing: ${metadata.amoListingUrl}")
            if (!downloadUrl.isNullOrBlank() && downloadUrl.startsWith("https://")) {
                add("Package URL: $downloadUrl")
            }
        }.distinct().joinToString("\n").ifBlank {
            sourceDescription(extension)
        }
        return PendingExtensionPrompt(
            kind = kind,
            extensionName = metadata.name.orEmpty().ifBlank { "Unnamed extension" },
            extensionId = extension.id,
            publisher = publisher,
            source = source,
            permissions = permissions.distinct().sorted(),
            origins = origins.distinct().sorted(),
            dataCollectionPermissions = dataCollectionPermissions.distinct().sorted(),
            blockedReasons = buildList {
                if (!extensionListLoaded) add("Click could not verify the installed add-on list")
                if (kind == "Install extension") {
                    val verified = verifiedCatalogPackage
                    val listingSlug = FirefoxAmoCatalog.slugFromOfficialListing(metadata.amoListingUrl)
                    if (verified == null || verified.packageInfo.extensionId != extension.id || listingSlug != verified.entry.slug) {
                        add("The package was not verified against its current public Android AMO record")
                    }
                }
                if (extension.id in installedExtensionIds) {
                    add("This add-on ID is already installed; remove it before importing a replacement")
                }
                addAll(extensionPolicyBlockers(extension))
                addAll(ExtensionInstallPolicy.unsupportedPermissionNames(permissions).map { "Unsupported capability: $it" })
                addAll(ExtensionInstallPolicy.blockedOriginPatterns(origins).map { "Unreviewable site access pattern: $it" })
            }.distinct(),
            decide = decide
        )
    }

    val promptDelegate = remember(controller) {
        object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                if (pendingPrompt != null) {
                    return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(false, false, false))
                }
                val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                pendingPrompt = buildPrompt(
                    kind = "Install extension",
                    extension = extension,
                    permissions = permissions.toList(),
                    origins = origins.toList(),
                    dataCollectionPermissions = dataCollectionPermissions.toList()
                ) { allow, allowData ->
                    pendingPrompt = null
                    result.complete(WebExtension.PermissionPromptResponse(allow, false, allow && allowData))
                }
                return result
            }

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<AllowOrDeny> {
                if (pendingPrompt != null) {
                    return GeckoResult.deny()
                }
                val result = GeckoResult<AllowOrDeny>()
                pendingPrompt = buildPrompt(
                    kind = "Optional permission request",
                    extension = extension,
                    permissions = permissions.toList(),
                    origins = origins.toList(),
                    dataCollectionPermissions = dataCollectionPermissions.toList()
                ) { allow, _ ->
                    pendingPrompt = null
                    result.complete(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                }
                return result
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                newPermissions: Array<out String>,
                newOrigins: Array<out String>,
                newDataCollectionPermissions: Array<out String>
            ): GeckoResult<AllowOrDeny> {
                statusMessage = "Updates are not automatic. Remove the extension, then reinstall it from its official AMO listing to re-run Click's safety checks."
                return GeckoResult.deny()
            }
        }
    }

    DisposableEffect(controller, promptDelegate) {
        controller.setPromptDelegate(promptDelegate)
        onDispose {
            pendingPrompt?.decide(false, false)
            controller.setPromptDelegate(null)
        }
    }

    DisposableEffect(session) {
        session.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                permissions: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (url != null) address = url
            }
        })
        session.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, newTitle: String?) {
                title = newTitle.orEmpty().ifBlank { "Firefox for Android Add-ons" }
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val packageUri = response.uri
                runCatching { response.body?.close() }
                if (!ExtensionInstallPolicy.isOfficialAmoXpiUrl(packageUri)) return
                val slug = FirefoxAmoCatalog.slugFromOfficialListing(address)
                if (slug == null) {
                    statusMessage = "Blocked: the official AMO package was not requested from an Android/Firefox add-on listing."
                    return
                }
                statusMessage = "Checking the selected add-on against the current AMO Android record…"
                scope.launch {
                    val record = fetchCurrentAmoPackage(slug)
                    if (FirefoxAmoCatalog.slugFromOfficialListing(address) != slug) return@launch
                    if (record == null) {
                        statusMessage = "Install stopped: this listing is not public and compatible with the pinned GeckoView Android version, or its package is too large."
                    } else {
                        pageInstallConfirmation = record.entry
                        statusMessage = "AMO verified the current Android-compatible package. Review Click's safety checks before installation."
                    }
                }
            }
        })
        onDispose {
            session.setNavigationDelegate(null)
            session.setContentDelegate(null)
        }
    }

    DisposableEffect(controller, session, actionDelegate) {
        onDispose {
            latestInstalledExtensions.value.forEach { extension ->
                extension.setActionDelegate(null)
                session.webExtensionController.setActionDelegate(extension, null)
            }
            latestPopupSession.value?.close()
            extensionActions = emptyMap()
        }
    }

    LaunchedEffect(session, openManagerOnStart) {
        val start = ExtensionBrowserInput.resolve(requestedStartUrl) ?: address
        session.loadUri(start)
        if (!openManagerOnStart) refreshExtensions()
    }
    LaunchedEffect(showManager) {
        if (showManager) refreshExtensions()
    }
    LaunchedEffect(address) {
        val slug = FirefoxAmoCatalog.slugFromOfficialListing(address)
        currentAmoEntry = null
        if (slug != null) {
            val record = fetchCurrentAmoPackage(slug)
            if (FirefoxAmoCatalog.slugFromOfficialListing(address) == slug) {
                currentAmoEntry = record?.entry
            }
        }
    }

    fun decidePrompt(allow: Boolean, allowDataCollection: Boolean = false) {
        pendingPrompt?.decide(allow, allowDataCollection)
    }

    fun closeActionPopup() {
        popupSession?.close()
        popupSession = null
        popupTitle = ""
    }

    BackHandler(enabled = popupSession != null || pendingPrompt != null || pageInstallConfirmation != null || showManager || removeTarget != null) {
        when {
            popupSession != null -> closeActionPopup()
            pendingPrompt != null -> decidePrompt(false)
            pageInstallConfirmation != null -> pageInstallConfirmation = null
            removeTarget != null -> removeTarget = null
            else -> showManager = false
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onExit) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Return to Click")
                }
                Column(Modifier.weight(1f)) {
                    Text(title, maxLines = 1, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("GeckoView runtime · separate from WebView data", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                currentAmoEntry?.let { entry ->
                    val alreadyInstalled = entry.extensionId in installedExtensionIds
                    TextButton(
                        enabled = extensionListLoaded && installingCatalogSlug == null && pendingPrompt == null && !alreadyInstalled,
                        onClick = { beginCatalogInstall(entry) }
                    ) {
                        Text(
                            when {
                                alreadyInstalled -> "Installed"
                                installingCatalogSlug == entry.slug -> "Checking…"
                                else -> "Install"
                            },
                            maxLines = 1
                        )
                    }
                }
                IconButton(onClick = { showManager = true }) {
                    Icon(Icons.Default.Extension, contentDescription = "Manage installed add-ons")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Web address or search") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        val target = ExtensionBrowserInput.resolve(address)
                        if (target == null) {
                            statusMessage = "Only HTTP and HTTPS web addresses can be opened."
                        } else {
                            address = target
                            session.loadUri(target)
                            keyboard?.hide()
                        }
                    })
                )
            }

            if (extensionActions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    extensionActions.values.forEach { item ->
                        OutlinedButton(onClick = {
                            if (canExposeAction(item.extension)) item.action.click()
                        }) {
                            Text(
                                item.action.title?.takeIf { it.isNotBlank() }
                                    ?: item.extension.metaData.name.orEmpty().ifBlank { "Add-on" },
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            statusMessage?.let { message ->
                Text(
                    message,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    color = if (message.startsWith("Install stopped by Click's safety policy")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 12.sp
                )
            }

            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { viewContext -> GeckoView(viewContext).apply { setSession(session) } }
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { session.goBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = { session.goForward() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = { session.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload")
                }
                IconButton(onClick = {
                    address = "https://duckduckgo.com/"
                    session.loadUri(address)
                }) {
                    Icon(Icons.Default.Home, contentDescription = "Home")
                }
                Button(onClick = { showManager = true }) { Text("Add-ons") }
            }
        }
    }

    if (showManager) {
        ExtensionManagerDialog(
            extensions = installedExtensions,
            installedExtensionIds = installedExtensionIds,
            catalogEntries = FirefoxAmoCatalog.entries,
            isImportEnabled = extensionListLoaded,
            installingCatalogSlug = installingCatalogSlug,
            sourceFor = ::sourceDescription,
            onBrowseCatalog = {
                showManager = false
                address = FirefoxAmoCatalog.ANDROID_STORE_URL
                session.loadUri(address)
                statusMessage = "Opened Mozilla's full Firefox for Android add-ons catalog."
            },
            onInstallCatalogEntry = ::beginCatalogInstall,
            onOpenListing = { entry ->
                if (!ExtensionInstallPolicy.isOfficialAmoListing(entry.listingUrl)) {
                    statusMessage = "Blocked: this is not an official HTTPS Mozilla Add-ons listing."
                } else {
                    showManager = false
                    address = entry.listingUrl
                    session.loadUri(entry.listingUrl)
                    statusMessage = "Opened the official AMO listing in Click's GeckoView add-on store."
                }
            },
            onOpenExternalListing = { entry ->
                if (!ExtensionInstallPolicy.isOfficialAmoListing(entry.listingUrl)) {
                    statusMessage = "Blocked: this is not an official HTTPS Mozilla Add-ons listing."
                } else {
                    showManager = false
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.listingUrl)))
                    }.onFailure {
                        statusMessage = "Couldn't open the official AMO listing in your default browser."
                    }
                }
            },
            onOpenOptions = { extension ->
                val metadata = extension.metaData
                val optionsPageUrl = ExtensionInstallPolicy.safeExtensionOptionsPageUrl(
                    metadata.baseUrl,
                    metadata.optionsPageUrl
                )
                when {
                    metadata.optionsPageUrl.isNullOrBlank() -> statusMessage = "This add-on does not declare an options page."
                    extensionPolicyBlockers(extension).isNotEmpty() ->
                        statusMessage = "Options are unavailable because this add-on is blocked by Click's safety policy."
                    !metadata.enabled ->
                        statusMessage = "Enable this approved add-on to open its options page."
                    optionsPageUrl == null ->
                        statusMessage = "Blocked: the options page is not on this add-on's own GeckoView origin."
                    else -> {
                        showManager = false
                        address = optionsPageUrl
                        session.loadUri(optionsPageUrl)
                        statusMessage = "Opened ${metadata.name.orEmpty().ifBlank { "add-on" }} options in the isolated GeckoView profile."
                    }
                }
            },
            onToggle = { extension, enabled ->
                val policyBlockers = extensionPolicyBlockers(extension)
                if (enabled && policyBlockers.isNotEmpty()) {
                    statusMessage = "This extension is blocked by Click's safe add-on policy; remove it instead."
                } else {
                    val result = if (enabled) {
                        controller.enable(extension, WebExtensionController.EnableSource.USER)
                    } else {
                        controller.disable(extension, WebExtensionController.EnableSource.USER)
                    }
                    result.accept(
                        { refreshExtensions() },
                        { error -> statusMessage = "Couldn't update extension: ${error?.message ?: "unknown error"}" }
                    )
                }
            },
            onRemove = { extension -> removeTarget = extension },
            onClose = { showManager = false }
        )
    }

    pageInstallConfirmation?.let { entry ->
        AlertDialog(
            onDismissRequest = { pageInstallConfirmation = null },
            title = { Text("Install ${entry.name} from AMO?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "AMO currently lists ${entry.name} v${entry.version} as public and compatible with Android ${entry.minimumAndroidVersion}–${FirefoxAmoCatalog.GECKOVIEW_ANDROID_COMPAT_VERSION}. Click rechecks the official package; GeckoView verifies its Mozilla signature, and Click reviews permissions and site access. Nothing installs unless you continue.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(onClick = {
                    pageInstallConfirmation = null
                    beginCatalogInstall(entry)
                }) { Text("Verify and continue") }
            },
            dismissButton = { TextButton(onClick = { pageInstallConfirmation = null }) { Text("Cancel") } }
        )
    }

    popupSession?.let { addOnPopupSession ->
        AlertDialog(
            onDismissRequest = ::closeActionPopup,
            title = { Text(popupTitle, fontWeight = FontWeight.Bold) },
            text = {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(340.dp),
                    factory = { viewContext -> GeckoView(viewContext).apply { setSession(addOnPopupSession) } }
                )
            },
            confirmButton = { TextButton(onClick = ::closeActionPopup) { Text("Close") } }
        )
    }

    removeTarget?.let { extension ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove extension?") },
            text = { Text("Remove ${extension.metaData.name.orEmpty()} and its stored add-on data from the extension profile?") },
            confirmButton = {
                Button(onClick = {
                    controller.uninstall(extension).accept(
                        {
                            sourcePrefs.edit().remove(extension.id).apply()
                            removeTarget = null
                            refreshExtensions()
                            statusMessage = "Extension removed."
                        },
                        { error ->
                            removeTarget = null
                            statusMessage = "Couldn't remove extension: ${error?.message ?: "unknown error"}"
                        }
                    )
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } }
        )
    }

    pendingPrompt?.let { prompt ->
        var grantDataCollection by remember(prompt.extensionId, prompt.kind) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { prompt.decide(false, false) },
            title = {
                Text(
                    if (prompt.kind == "Install extension") "Review permissions before installing"
                    else prompt.kind,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(prompt.extensionName, fontWeight = FontWeight.SemiBold)
                    Text("Publisher field in package: ${prompt.publisher}", fontSize = 12.sp)
                    Text("Extension ID: ${prompt.extensionId}", fontSize = 11.sp)
                    Text("Source information:\n${prompt.source}", fontSize = 12.sp)
                    Text(
                        if (prompt.kind == "Install extension") {
                            ExtensionUiGuidance.installPermissionConsent
                        } else {
                            ExtensionUiGuidance.optionalPermissionConsent
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ExtensionUiGuidance.permissionRiskWarning(prompt.permissions)?.let { warning ->
                        Text(
                            warning,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    ExtensionUiGuidance.siteAccessRiskWarning(prompt.origins)?.let { warning ->
                        Text(
                            warning,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    PermissionSection("Browser permissions requested", prompt.permissions.map(ExtensionUiGuidance::permissionDisclosure))
                    PermissionSection("Website access requested", prompt.origins.map(ExtensionUiGuidance::originDisclosure))
                    PermissionSection("Declared data collection", prompt.dataCollectionPermissions)
                    if (prompt.dataCollectionPermissions.isNotEmpty()) {
                        if (prompt.kind == "Install extension") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = grantDataCollection, onCheckedChange = { grantDataCollection = it })
                                Text("Allow technical and interaction data collection", fontSize = 12.sp)
                            }
                        } else {
                            Text(
                                "GeckoView handles this optional data-collection request with the same Allow or Cancel choice shown above.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (prompt.blockedReasons.isNotEmpty()) {
                        Text(
                            "Not allowed by Click's safe add-on policy: ${prompt.blockedReasons.joinToString()}. This add-on cannot be installed or updated here.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                if (prompt.blockedReasons.isEmpty()) {
                    Button(onClick = { prompt.decide(true, grantDataCollection) }) {
                        Text(if (prompt.kind == "Install extension") "Add" else "Allow this request")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { prompt.decide(false, false) }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PermissionSection(label: String, values: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        if (values.isEmpty()) {
            Text("None", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            values.forEach { value ->
                Text("• $value", fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun ExtensionManagerDialog(
    extensions: List<WebExtension>,
    installedExtensionIds: Set<String>,
    catalogEntries: List<FirefoxAmoCatalogEntry>,
    isImportEnabled: Boolean,
    installingCatalogSlug: String?,
    sourceFor: (WebExtension) -> String,
    onBrowseCatalog: () -> Unit,
    onInstallCatalogEntry: (FirefoxAmoCatalogEntry) -> Unit,
    onOpenListing: (FirefoxAmoCatalogEntry) -> Unit,
    onOpenExternalListing: (FirefoxAmoCatalogEntry) -> Unit,
    onOpenOptions: (WebExtension) -> Unit,
    onToggle: (WebExtension, Boolean) -> Unit,
    onRemove: (WebExtension) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    val filteredEntries = remember(catalogEntries, selectedCategory, searchQuery) {
        val q = searchQuery.trim().lowercase()
        catalogEntries.filter { entry ->
            val catMatch = selectedCategory == "All" || entry.category == selectedCategory
            val queryMatch = q.isEmpty() ||
                entry.name.lowercase().contains(q) ||
                entry.slug.lowercase().contains(q) ||
                entry.summary.lowercase().contains(q) ||
                entry.category.lowercase().contains(q)
            catMatch && queryMatch
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Installed Add-ons & AMO Quick Picks", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${filteredEntries.size} safe quick picks. Browse Mozilla's full Android catalog from the store page.",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            context.getString(R.string.amo_catalog_play_policy_note),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "An enabled add-on may not have a popup or settings page. Some work only on supported websites; page actions appear only where available. In a regular GeckoView tab, tap the browser's Extensions button to open an available action.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Search quick picks…") }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FirefoxAmoCatalog.categories.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat, fontSize = 11.sp) }
                                )
                            }
                        }
                        OutlinedButton(onClick = onBrowseCatalog, enabled = isImportEnabled, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("Browse the full Firefox for Android catalog")
                        }
                    }
                }

                if (extensions.isNotEmpty()) {
                    item(key = "installed-title") {
                        Text("Installed in Click's GeckoView runtime (${extensions.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    item(key = "installed-guidance") {
                        Text(
                            ExtensionUiGuidance.enabledDoesNotMeanPopup,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(extensions, key = { "inst-${it.id}" }) { extension ->
                        val metadata = extension.metaData
                        val policyBlockers = extensionPolicyBlockers(extension)
                        val optionsPageUrl = ExtensionInstallPolicy.safeExtensionOptionsPageUrl(
                            metadata.baseUrl,
                            metadata.optionsPageUrl
                        )
                        val canOpenOptionsPage = policyBlockers.isEmpty() && metadata.enabled &&
                            optionsPageUrl != null
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(metadata.name.orEmpty().ifBlank { "Unnamed extension" }, fontWeight = FontWeight.SemiBold)
                                Text("Version ${metadata.version.orEmpty()} · ${metadata.creatorName.orEmpty().ifBlank { "Publisher not declared" }}", fontSize = 11.sp)
                                if (policyBlockers.isNotEmpty()) {
                                    Text("Disabled by Click safety policy: ${policyBlockers.joinToString()}", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                }
                                Text("Source:\n${sourceFor(extension)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Site access: ${(metadata.requiredOrigins.orEmpty().asList() + metadata.grantedOptionalOrigins.orEmpty().asList()).distinct().ifEmpty { listOf("None declared") }.joinToString()}", fontSize = 10.sp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(checked = metadata.enabled, enabled = policyBlockers.isEmpty(), onCheckedChange = { onToggle(extension, it) })
                                        Text(if (policyBlockers.isNotEmpty()) "Blocked" else if (metadata.enabled) "Enabled" else "Disabled", fontSize = 12.sp)
                                    }
                                    TextButton(onClick = { onRemove(extension) }) { Text("Remove") }
                                }
                                if (canOpenOptionsPage) {
                                    OutlinedButton(
                                        onClick = { onOpenOptions(extension) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Options") }
                                } else {
                                    val optionsMessage = when {
                                        metadata.optionsPageUrl.isNullOrBlank() -> ExtensionUiGuidance.noOptionsPage
                                        policyBlockers.isNotEmpty() -> "Options unavailable because this add-on is blocked by Click."
                                        !metadata.enabled -> "Enable this approved add-on to open its options page."
                                        else -> "Options blocked: page is not on this add-on's own GeckoView origin."
                                    }
                                    Text(optionsMessage, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                items(filteredEntries, key = { it.slug }) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Image(
                                painter = painterResource(entry.iconResource),
                                contentDescription = "${entry.name} AMO icon",
                                modifier = Modifier.size(48.dp)
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (entry.isRecommended) "AMO Recommended for Firefox Android · ${entry.category}" else "Firefox for Android · ${entry.category}",
                                    fontSize = 11.sp
                                )
                                Text("Catalog version ${entry.version} · Firefox for Android ${entry.minimumAndroidVersion}+", fontSize = 10.sp)
                                Text(entry.summary, fontSize = 11.sp)
                                Text(
                                    context.getString(
                                        R.string.amo_catalog_permission_summary,
                                        entry.requiredPermissions.joinToString().ifBlank { "none declared" },
                                        entry.requiredOrigins.ifEmpty { listOf("none declared") }.joinToString()
                                    ),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val alreadyInstalled = entry.extensionId in installedExtensionIds
                                OutlinedButton(
                                    onClick = { onInstallCatalogEntry(entry) },
                                    enabled = isImportEnabled && installingCatalogSlug == null && !alreadyInstalled,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        when {
                                            alreadyInstalled -> "Installed · remove before reinstalling"
                                            installingCatalogSlug == entry.slug -> "Checking AMO…"
                                            else -> "Install in Click"
                                        }
                                    )
                                }
                                OutlinedButton(onClick = { onOpenListing(entry) }) {
                                    Text("Open official AMO listing in Click")
                                }
                                TextButton(onClick = { onOpenExternalListing(entry) }) {
                                    Text("Open official listing in default browser")
                                }
                            }
                        }
                    }
                }

                item(key = "footer-notes") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            context.getString(R.string.amo_catalog_install_instructions),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "The store is Mozilla's full Firefox for Android catalog. Installed add-ons can run in Click tabs that use GeckoView; WebView tabs are unchanged until you explicitly approve a same-tab reload. WebView cookies, sign-ins, history and in-memory page state do not transfer.",
                            fontSize = 12.sp
                        )
                        Text(
                            context.getString(R.string.amo_catalog_update_note),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } }
    )
}
