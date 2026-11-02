package com.click.browser.extensions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Separate, optional browser surface. The main Click browser and its WebView bridges are not used here.
 * GeckoView validates Mozilla signatures before installation; this activity never registers native messaging.
 * Do not add WebExtensionController.update(): GeckoView may apply same-permission updates without a prompt,
 * and the pinned API cannot preflight the updated add-on's recommendation metadata.
 */
class ExtensionBrowserActivity : ComponentActivity() {
    private var runtime: GeckoRuntime? = null
    private var session: GeckoSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val geckoRuntime = ExtensionBrowserRuntime.get(applicationContext)
        runtime = geckoRuntime
        session = GeckoSession().also { geckoSession ->
            geckoSession.open(geckoRuntime)
            geckoSession.setPermissionDelegate(DenyWebsiteDevicePermissions)
        }

        val requestedStartUrl = intent.getStringExtra(EXTRA_START_URL).orEmpty()
        val openManagerOnStart = intent.getBooleanExtra(EXTRA_SHOW_MANAGER, false)
        setContent {
            MaterialTheme {
                ExtensionBrowserScreen(
                    runtime = geckoRuntime,
                    session = requireNotNull(session),
                    requestedStartUrl = requestedStartUrl,
                    openManagerOnStart = openManagerOnStart,
                    onExit = { finish() }
                )
            }
        }
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

private fun isMozillaRecommended(extension: WebExtension): Boolean {
    val metadata = extension.metaData
    return metadata.isRecommended &&
        metadata.signedState == WebExtension.SignedStateFlags.SIGNED &&
        metadata.blocklistState == WebExtension.BlocklistStateFlags.NOT_BLOCKED &&
        ExtensionInstallPolicy.isOfficialAmoListing(metadata.amoListingUrl)
}

internal fun extensionPolicyBlockers(extension: WebExtension): List<String> {
    val metadata = extension.metaData
    val currentPermissions = metadata.requiredPermissions.orEmpty().asList() +
        metadata.grantedOptionalPermissions.orEmpty().asList()
    val currentOrigins = metadata.requiredOrigins.orEmpty().asList() +
        metadata.grantedOptionalOrigins.orEmpty().asList()
    return buildList {
        if (!isMozillaRecommended(extension)) {
            add("Not fully reviewed and Mozilla-recommended in the official AMO catalog")
        }
        addAll(ExtensionInstallPolicy.blockedPermissionNames(currentPermissions).map { "Sensitive API: $it" })
        addAll(ExtensionInstallPolicy.blockedOriginPatterns(currentOrigins).map { "Broad or insecure site access: $it" })
    }.distinct()
}

@Composable
private fun ExtensionBrowserScreen(
    runtime: GeckoRuntime,
    session: GeckoSession,
    requestedStartUrl: String,
    openManagerOnStart: Boolean,
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
    var title by remember { mutableStateOf("Firefox/AMO compatible add-ons") }
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
    var selectedFileName by remember { mutableStateOf<String?>(null) }

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
            "User-selected XPI; no listing or download URL was declared in the package."
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
            selectedFileName?.let { add("Selected local file: $it") }
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
                if (extension.id in installedExtensionIds) {
                    add("This add-on ID is already installed; remove it before importing a replacement")
                }
                addAll(extensionPolicyBlockers(extension))
                addAll(ExtensionInstallPolicy.blockedPermissionNames(permissions).map { "Sensitive API: $it" })
                addAll(ExtensionInstallPolicy.blockedOriginPatterns(origins).map { "Broad or insecure site access: $it" })
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
                    kind = "Additional permission request",
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
                statusMessage = "Updates are not automatic. Remove the extension and import a fresh AMO XPI to re-run Click's full safety checks."
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
                title = newTitle.orEmpty().ifBlank { "Firefox/AMO compatible add-ons" }
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

    val pickXpi = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                var copied: File? = null
                try {
                    val displayName = withContext(Dispatchers.IO) { queryDisplayName(context, uri) }
                    if (!ExtensionInstallPolicy.isSupportedPackageName(displayName)) {
                        statusMessage = "Choose an XPI package from an official AMO listing."
                        return@launch
                    }
                    val cleanName = displayName.orEmpty().replace('\n', ' ').replace('\r', ' ').take(160)
                    val packageFile = withContext(Dispatchers.IO) { copyPickedXpi(context, uri, cleanName) }
                    copied = packageFile
                    selectedFileName = cleanName
                    statusMessage = "Checking the selected XPI signature…"
                    controller.install(
                        packageFile.toURI().toString(),
                        WebExtensionController.INSTALLATION_METHOD_FROM_FILE
                    ).accept(
                        { extension ->
                            if (extension == null) {
                                selectedFileName = null
                                packageFile.delete()
                                statusMessage = "Install rejected: GeckoView returned no extension."
                            } else {
                                sourcePrefs.edit().putString(extension.id, "Local file selected by you: $cleanName").apply()
                                selectedFileName = null
                                packageFile.delete()
                                statusMessage = "Installed ${extension.metaData.name.orEmpty()}. Review its site access in Extensions."
                                refreshExtensions()
                            }
                        },
                        { error ->
                            selectedFileName = null
                            packageFile.delete()
                            statusMessage = "Install rejected: ${error?.message?.take(180) ?: "invalid, unsigned, or unsupported XPI"}"
                        }
                    )
                } catch (error: Exception) {
                    copied?.delete()
                    selectedFileName = null
                    statusMessage = error.message?.take(180) ?: "Could not read the selected extension file."
                }
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

    BackHandler(enabled = popupSession != null || pendingPrompt != null || showManager || removeTarget != null) {
        when {
            popupSession != null -> closeActionPopup()
            pendingPrompt != null -> decidePrompt(false)
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
                    Text("Separate GeckoView profile", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showManager = true }) {
                    Icon(Icons.Default.Extension, contentDescription = "Manage Firefox/AMO compatible add-ons")
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            catalogEntries = FirefoxAmoCatalog.entries,
            isImportEnabled = extensionListLoaded,
            sourceFor = ::sourceDescription,
            onInstall = { pickXpi.launch(arrayOf("*/*")) },
            onOpenListing = { entry ->
                if (!ExtensionInstallPolicy.isOfficialAmoListing(entry.listingUrl)) {
                    statusMessage = "Blocked: this is not an official HTTPS Mozilla Add-ons listing."
                } else {
                    showManager = false
                    address = entry.listingUrl
                    session.loadUri(entry.listingUrl)
                    statusMessage = "Opened the official AMO listing in Click's separate GeckoView."
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
            title = { Text(prompt.kind, fontWeight = FontWeight.Bold) },
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
                        "Only Mozilla Recommended add-ons that are fully signed, not blocklisted, and listed on addons.mozilla.org can proceed. GeckoView validates the package signature; Click rejects broad or insecure site access and sensitive browser APIs.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PermissionSection("Browser permissions", prompt.permissions)
                    PermissionSection("Website access", prompt.origins)
                    PermissionSection("Declared data collection", prompt.dataCollectionPermissions)
                    if (prompt.dataCollectionPermissions.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = grantDataCollection, onCheckedChange = { grantDataCollection = it })
                            Text("Allow technical and interaction data collection", fontSize = 12.sp)
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
                    Button(onClick = { prompt.decide(true, grantDataCollection) }) { Text("Allow and continue") }
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
    catalogEntries: List<FirefoxAmoCatalogEntry>,
    isImportEnabled: Boolean,
    sourceFor: (WebExtension) -> String,
    onInstall: () -> Unit,
    onOpenListing: (FirefoxAmoCatalogEntry) -> Unit,
    onOpenExternalListing: (FirefoxAmoCatalogEntry) -> Unit,
    onOpenOptions: (WebExtension) -> Unit,
    onToggle: (WebExtension, Boolean) -> Unit,
    onRemove: (WebExtension) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Firefox/AMO compatible add-ons", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Limited AMO catalog", fontWeight = FontWeight.SemiBold)
                Text(
                    "These are official Firefox for Android listings selected for Click's isolated GeckoView policy. Any in-page install still passes Click's signature, recommendation, blocklist, and permission checks.",
                    fontSize = 12.sp
                )
                catalogEntries.forEach { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Image(
                                painter = painterResource(entry.iconResource),
                                contentDescription = "${entry.name} official AMO logo",
                                modifier = Modifier.size(52.dp)
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.name, fontWeight = FontWeight.SemiBold)
                                Text("AMO Recommended · Firefox for Android", fontSize = 11.sp)
                                Text("Version ${entry.version} · Android ${entry.minimumAndroidVersion}+", fontSize = 10.sp)
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
                                OutlinedButton(onClick = { onOpenListing(entry) }) {
                                    Text("Open official AMO listing in Click")
                                }
                                TextButton(onClick = { onOpenExternalListing(entry) }) {
                                    Text("Download XPI in default browser")
                                }
                            }
                        }
                    }
                }
                Text(
                    context.getString(R.string.amo_catalog_install_instructions),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Add-ons run only in this separate GeckoView profile. They do not share Click's regular tabs, cookies, history, or saved passwords. Approved add-ons can still read or change the exact websites you allow.",
                    fontSize = 12.sp
                )
                Text(
                    "Only Firefox/AMO compatible add-ons that are Mozilla Recommended, fully reviewed, non-blocklisted, and Mozilla-signed are accepted. Wildcard, HTTP, local-network, and over-10-site access plus native messaging and sensitive browser APIs are blocked.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    context.getString(R.string.amo_catalog_update_note),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isImportEnabled) {
                    Text("Checking installed add-ons; import stays disabled until the check succeeds.", fontSize = 11.sp)
                }
                if (extensions.isEmpty() && isImportEnabled) {
                    Text("No add-ons installed.", fontSize = 13.sp)
                }
                extensions.forEach { extension ->
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
                                    metadata.optionsPageUrl.isNullOrBlank() -> "This add-on does not provide an options page."
                                    policyBlockers.isNotEmpty() -> "Options unavailable because this add-on is blocked by Click."
                                    !metadata.enabled -> "Enable this approved add-on to open its options page."
                                    else -> "Options blocked: page is not on this add-on's own GeckoView origin."
                                }
                                Text(optionsMessage, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = onInstall, enabled = isImportEnabled, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Install a downloaded AMO XPI")
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } }
    )
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

@Throws(IOException::class)
private fun copyPickedXpi(context: Context, uri: Uri, displayName: String): File {
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IOException("The selected file could not be opened.")
    val destination = File(context.cacheDir, "extension-${UUID.randomUUID()}.xpi")
    try {
        input.use { source ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > ExtensionInstallPolicy.MAX_XPI_BYTES) {
                        throw IOException("The XPI file exceeds the 30 MB limit.")
                    }
                    output.write(buffer, 0, count)
                }
            }
        }
        if (destination.length() == 0L) throw IOException("The selected XPI file is empty.")
        return destination
    } catch (error: Exception) {
        destination.delete()
        throw error
    }
}
