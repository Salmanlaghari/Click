package com.click.browser.ui.screens

import android.webkit.WebStorage
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WebAsset
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.data.BrowserRepository
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.SiteSettings
import com.click.browser.engine.SiteSettingsCatalog
import com.click.browser.engine.SiteSettingsHubRow
import com.click.browser.engine.SiteSettingsRowKind
import com.click.browser.engine.TextScaleStore
import com.click.browser.engine.WebsitePermissionGlobalDefault
import kotlinx.coroutines.launch

/** Navigation destinations inside the Site settings hub. */
private sealed interface SiteSettingsDestination {
    data object Hub : SiteSettingsDestination
    data object AllSites : SiteSettingsDestination
    data object DataStored : SiteSettingsDestination
    data class PermissionCategory(val kind: DevicePermissionKind) : SiteSettingsDestination
    data class ContentCategory(val rowId: String) : SiteSettingsDestination
    data class UnsupportedInfo(val rowId: String) : SiteSettingsDestination
}

/** The three device permissions Click can really grant per site. */
private enum class DevicePermissionKind { CAMERA, MICROPHONE, LOCATION }

/**
 * Chrome-style Site settings hub (Settings → Site settings): every row from
 * Chrome's page, in Chrome's order (see [SiteSettingsCatalog]). Rows the
 * engine can enforce open real controls backed by BrowserRepository /
 * AppSettings state; rows for capabilities the engine does not provide open
 * a truthful info screen — never a fake toggle.
 */
@Composable
fun SiteSettingsHubScreen(
    theme: ModeTheme,
    repository: BrowserRepository,
    javaScriptEnabled: Boolean,
    onJavaScriptChange: (Boolean) -> Unit,
    desktopSiteDefault: Boolean,
    onDesktopSiteDefaultChange: (Boolean) -> Unit,
    siteDataEnabled: Boolean,
    onSiteDataEnabledChange: (Boolean) -> Unit,
    autoRemovePermissions: Boolean,
    onAutoRemovePermissionsChange: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    var destination by remember { mutableStateOf<SiteSettingsDestination>(SiteSettingsDestination.Hub) }
    BackHandler(enabled = destination !is SiteSettingsDestination.Hub) {
        destination = SiteSettingsDestination.Hub
    }
    when (val dest = destination) {
        SiteSettingsDestination.Hub -> SiteSettingsHubList(
            theme = theme,
            repository = repository,
            javaScriptEnabled = javaScriptEnabled,
            desktopSiteDefault = desktopSiteDefault,
            siteDataEnabled = siteDataEnabled,
            autoRemovePermissions = autoRemovePermissions,
            onAutoRemovePermissionsChange = onAutoRemovePermissionsChange,
            onOpenAllSites = { destination = SiteSettingsDestination.AllSites },
            onOpenRow = { row ->
                destination = when (row.kind) {
                    SiteSettingsRowKind.PERMISSION -> SiteSettingsDestination.PermissionCategory(
                        when (row.id) {
                            "camera" -> DevicePermissionKind.CAMERA
                            "location" -> DevicePermissionKind.LOCATION
                            else -> DevicePermissionKind.MICROPHONE
                        }
                    )
                    SiteSettingsRowKind.CONTENT_OVERRIDES,
                    SiteSettingsRowKind.CONTENT_GLOBAL,
                    SiteSettingsRowKind.SAVED_ZOOM -> SiteSettingsDestination.ContentCategory(row.id)
                    SiteSettingsRowKind.DATA_STORED -> SiteSettingsDestination.DataStored
                    SiteSettingsRowKind.UNSUPPORTED -> SiteSettingsDestination.UnsupportedInfo(row.id)
                }
            },
            onClose = onClose
        )
        SiteSettingsDestination.AllSites -> AllSiteSettingsScreen(
            theme = theme,
            repository = repository,
            onClose = { destination = SiteSettingsDestination.Hub }
        )
        SiteSettingsDestination.DataStored -> DataStoredScreen(
            theme = theme,
            onClose = { destination = SiteSettingsDestination.Hub }
        )
        is SiteSettingsDestination.PermissionCategory -> PermissionCategoryScreen(
            theme = theme,
            repository = repository,
            kind = dest.kind,
            onBack = { destination = SiteSettingsDestination.Hub }
        )
        is SiteSettingsDestination.ContentCategory -> ContentCategoryScreen(
            theme = theme,
            repository = repository,
            rowId = dest.rowId,
            javaScriptEnabled = javaScriptEnabled,
            onJavaScriptChange = onJavaScriptChange,
            desktopSiteDefault = desktopSiteDefault,
            onDesktopSiteDefaultChange = onDesktopSiteDefaultChange,
            siteDataEnabled = siteDataEnabled,
            onSiteDataEnabledChange = onSiteDataEnabledChange,
            onBack = { destination = SiteSettingsDestination.Hub }
        )
        is SiteSettingsDestination.UnsupportedInfo -> {
            SiteSettingsCatalog.row(dest.rowId)?.let { row ->
                UnsupportedInfoScreen(
                    theme = theme,
                    row = row,
                    onBack = { destination = SiteSettingsDestination.Hub }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteSettingsHubList(
    theme: ModeTheme,
    repository: BrowserRepository,
    javaScriptEnabled: Boolean,
    desktopSiteDefault: Boolean,
    siteDataEnabled: Boolean,
    autoRemovePermissions: Boolean,
    onAutoRemovePermissionsChange: (Boolean) -> Unit,
    onOpenAllSites: () -> Unit,
    onOpenRow: (SiteSettingsHubRow) -> Unit,
    onClose: () -> Unit
) {
    val cameraDefault by repository.websiteCameraDefaultFlow
        .collectAsState(initial = WebsitePermissionGlobalDefault.ASK)
    val microphoneDefault by repository.websiteMicrophoneDefaultFlow
        .collectAsState(initial = WebsitePermissionGlobalDefault.ASK)
    val locationDefault by repository.websiteLocationDefaultFlow
        .collectAsState(initial = WebsitePermissionGlobalDefault.ASK)
    val siteSettings by repository.siteSettingsFlow.collectAsState(initial = emptyMap())
    val context = LocalContext.current
    var zoomHostCount by remember { mutableStateOf<Int?>(null) }
    var storedTotalBytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        zoomHostCount = try {
            TextScaleStore.snapshot(context).second.size
        } catch (_: Exception) {
            null
        }
        WebStorage.getInstance().getOrigins { map ->
            storedTotalBytes = map?.values.orEmpty()
                .sumOf { (it as? WebStorage.Origin)?.usage ?: 0L }
        }
    }

    fun permissionDefaultLabel(value: WebsitePermissionGlobalDefault): String = when (value) {
        WebsitePermissionGlobalDefault.ALLOW -> "Allowed"
        WebsitePermissionGlobalDefault.ASK -> "Ask first"
        WebsitePermissionGlobalDefault.BLOCK -> "Not allowed"
    }

    fun overridesLabel(field: (SiteSettings) -> Boolean?): String {
        val count = siteSettings.values.count { field(it) != null }
        return if (count == 0) "Default for all sites"
        else "Customised for $count site${if (count == 1) "" else "s"}"
    }

    fun subtitle(row: SiteSettingsHubRow): String = when (row.kind) {
        SiteSettingsRowKind.UNSUPPORTED -> "Not available in Click"
        SiteSettingsRowKind.PERMISSION -> permissionDefaultLabel(
            when (row.id) {
                "camera" -> cameraDefault
                "location" -> locationDefault
                else -> microphoneDefault
            }
        )
        SiteSettingsRowKind.DATA_STORED -> when (val total = storedTotalBytes) {
            null -> "Website storage on this device"
            0L -> "No data stored"
            else -> "${formatStoredBytes(total)} stored"
        }
        SiteSettingsRowKind.SAVED_ZOOM -> when (val count = zoomHostCount) {
            null -> "Per-site text zoom"
            0 -> "No sites with custom zoom"
            else -> "$count site${if (count == 1) "" else "s"} with custom zoom"
        }
        SiteSettingsRowKind.CONTENT_GLOBAL -> when (row.id) {
            "desktop_site" -> if (desktopSiteDefault) "On" else "Off"
            else -> if (siteDataEnabled) "Sites can save data on your device"
            else "Sites can't save data on your device"
        }
        SiteSettingsRowKind.CONTENT_OVERRIDES -> when (row.id) {
            "javascript" -> if (javaScriptEnabled) "Allowed" else "Not allowed"
            "third_party_cookies" -> overridesLabel { it.thirdPartyCookies }
            "pop_ups" -> overridesLabel { it.popups }
            "sound" -> overridesLabel { it.sound }
            "intrusive_ads" -> overridesLabel { it.adBlock }
            "protected_content" -> overridesLabel { it.protectedContent }
            "automatic_downloads" -> overridesLabel { it.autoDownloads }
            else -> "Default for all sites"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Site settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background,
                    titleContentColor = theme.onSurface
                )
            )
        },
        containerColor = theme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HubRow(
                        theme = theme,
                        icon = Icons.Default.FormatListBulleted,
                        title = "All sites",
                        subtitle = "Review & reset per-site choices for all websites",
                        onClick = onOpenAllSites
                    )
                }
            }
            item { HubSectionHeader(theme, "Permissions") }
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SiteSettingsCatalog.permissionRows.forEachIndexed { index, row ->
                            if (index > 0) {
                                HorizontalDivider(color = theme.onSurface.copy(alpha = 0.08f))
                            }
                            HubRow(
                                theme = theme,
                                icon = siteSettingsRowIcon(row.id),
                                title = row.title,
                                subtitle = subtitle(row),
                                onClick = { onOpenRow(row) }
                            )
                        }
                    }
                }
            }
            item { HubSectionHeader(theme, "Content") }
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SiteSettingsCatalog.contentRows.forEachIndexed { index, row ->
                            if (index > 0) {
                                HorizontalDivider(color = theme.onSurface.copy(alpha = 0.08f))
                            }
                            HubRow(
                                theme = theme,
                                icon = siteSettingsRowIcon(row.id),
                                title = row.title,
                                subtitle = subtitle(row),
                                onClick = { onOpenRow(row) }
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Automatically remove permissions",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = theme.onSurface
                            )
                            Text(
                                "To protect your data, let Click remove saved camera, microphone, " +
                                    "and location choices from sites that you haven't visited in " +
                                    "the last 30 days.",
                                fontSize = 12.sp,
                                color = theme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = autoRemovePermissions,
                            onCheckedChange = onAutoRemovePermissionsChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HubSectionHeader(theme: ModeTheme, title: String) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = theme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun HubRow(
    theme: ModeTheme,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = theme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = theme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f))
        }
    }
}

/** Material icon closest to Chrome's for each hub row (material-icons-extended). */
private fun siteSettingsRowIcon(rowId: String): ImageVector = when (rowId) {
    "location" -> Icons.Default.LocationOn
    "camera" -> Icons.Default.Videocam
    "microphone" -> Icons.Default.Mic
    "notifications" -> Icons.Default.Notifications
    "embedded_content" -> Icons.Default.WebAsset
    "motion_sensors" -> Icons.Default.Sensors
    "nfc_devices" -> Icons.Default.Nfc
    "usb" -> Icons.Default.Usb
    "serial_port" -> Icons.Default.Cable
    "file_editing" -> Icons.Default.EditNote
    "clipboard" -> Icons.Default.ContentPaste
    "virtual_reality" -> Icons.Default.ViewInAr
    "augmented_reality" -> Icons.Default.ViewInAr
    "your_device_use" -> Icons.Default.DevicesOther
    "local_network" -> Icons.Default.Router
    "apps_on_device" -> Icons.Default.Apps
    "third_party_cookies" -> Icons.Default.Cookie
    "javascript" -> Icons.Default.Code
    "pop_ups" -> Icons.Default.OpenInNew
    "sound" -> Icons.Default.VolumeUp
    "intrusive_ads" -> Icons.Default.AdsClick
    "protected_content" -> Icons.Default.SmartDisplay
    "third_party_sign_in" -> Icons.Default.AccountCircle
    "auto_verify" -> Icons.Default.VerifiedUser
    "on_device_site_data" -> Icons.Default.Storage
    "desktop_site" -> Icons.Default.DesktopWindows
    "background_sync" -> Icons.Default.Sync
    "automatic_downloads" -> Icons.Default.Download
    "javascript_optimisation" -> Icons.Default.Security
    "saved_zoom" -> Icons.Default.ZoomIn
    "data_stored" -> Icons.Default.FormatListBulleted
    else -> Icons.Default.Settings
}

// ---------------------------------------------------------------------------
// Permission category screen (Camera / Microphone / Location)
// ---------------------------------------------------------------------------

/**
 * One reusable category screen for the three real device permissions: a
 * profile-wide default (Allowed / Ask first / Not allowed) stored in
 * BrowserRepository, plus every site with a saved choice and a per-site
 * reset. The runtime consent flows read exactly these stores.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionCategoryScreen(
    theme: ModeTheme,
    repository: BrowserRepository,
    kind: DevicePermissionKind,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val title = when (kind) {
        DevicePermissionKind.CAMERA -> "Camera"
        DevicePermissionKind.MICROPHONE -> "Microphone"
        DevicePermissionKind.LOCATION -> "Location"
    }
    val defaultFlow = when (kind) {
        DevicePermissionKind.CAMERA -> repository.websiteCameraDefaultFlow
        DevicePermissionKind.MICROPHONE -> repository.websiteMicrophoneDefaultFlow
        DevicePermissionKind.LOCATION -> repository.websiteLocationDefaultFlow
    }
    val decisionsFlow = when (kind) {
        DevicePermissionKind.CAMERA -> repository.websiteCameraPermissionsFlow
        DevicePermissionKind.MICROPHONE -> repository.websiteMicrophonePermissionsFlow
        DevicePermissionKind.LOCATION -> repository.websiteLocationPermissionsFlow
    }
    val currentDefault by defaultFlow.collectAsState(initial = WebsitePermissionGlobalDefault.ASK)
    val decisions by decisionsFlow.collectAsState(initial = emptyMap())

    fun setDefault(value: WebsitePermissionGlobalDefault) {
        scope.launch {
            when (kind) {
                DevicePermissionKind.CAMERA -> repository.setWebsiteCameraDefault(value)
                DevicePermissionKind.MICROPHONE -> repository.setWebsiteMicrophoneDefault(value)
                DevicePermissionKind.LOCATION -> repository.setWebsiteLocationDefault(value)
            }
        }
    }

    fun resetDecision(origin: String) {
        scope.launch {
            when (kind) {
                DevicePermissionKind.CAMERA -> repository.clearWebsiteCameraPermission(origin)
                DevicePermissionKind.MICROPHONE -> repository.clearWebsiteMicrophonePermission(origin)
                DevicePermissionKind.LOCATION -> repository.clearWebsiteLocationPermission(origin)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background,
                    titleContentColor = theme.onSurface
                )
            )
        },
        containerColor = theme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Default for sites without a saved choice",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = theme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            PermissionDefaultOption(
                theme = theme,
                label = "Allowed",
                description = "Sites can use the ${title.lowercase()} without asking (Android permission is still required).",
                selected = currentDefault == WebsitePermissionGlobalDefault.ALLOW,
                onClick = { setDefault(WebsitePermissionGlobalDefault.ALLOW) }
            )
            PermissionDefaultOption(
                theme = theme,
                label = "Ask first",
                description = "Click asks you the first time a site wants the ${title.lowercase()}.",
                selected = currentDefault == WebsitePermissionGlobalDefault.ASK,
                onClick = { setDefault(WebsitePermissionGlobalDefault.ASK) }
            )
            PermissionDefaultOption(
                theme = theme,
                label = "Not allowed",
                description = "No site can use the ${title.lowercase()}.",
                selected = currentDefault == WebsitePermissionGlobalDefault.BLOCK,
                onClick = { setDefault(WebsitePermissionGlobalDefault.BLOCK) }
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Sites with saved choices",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = theme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            if (decisions.isEmpty()) {
                Text(
                    "No sites have a saved $title choice yet. Choices you make when a site " +
                        "asks — or in the per-site sheet — appear here.",
                    fontSize = 13.sp,
                    color = theme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                decisions.toSortedMap().forEach { (origin, allowed) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(origin, fontSize = 14.sp, color = theme.onSurface)
                            Text(
                                if (allowed) "Allowed" else "Blocked",
                                fontSize = 12.sp,
                                color = theme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        TextButton(onClick = { resetDecision(origin) }) { Text("Reset") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionDefaultOption(
    theme: ModeTheme,
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, fontSize = 15.sp, color = theme.onSurface)
            Text(description, fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f))
        }
    }
}

// ---------------------------------------------------------------------------
// Content category screens
// ---------------------------------------------------------------------------

/** Accessor for one Boolean field of the per-site content overrides. */
private class ContentFieldSpec(
    val get: (SiteSettings) -> Boolean?,
    val clear: (SiteSettings) -> SiteSettings,
    val label: (Boolean) -> String
)

private val contentFieldSpecs: Map<String, ContentFieldSpec> = mapOf(
    "third_party_cookies" to ContentFieldSpec(
        { it.thirdPartyCookies },
        { it.copy(thirdPartyCookies = null) },
        { allowed -> if (allowed) "Allowed" else "Blocked" }
    ),
    "pop_ups" to ContentFieldSpec(
        { it.popups },
        { it.copy(popups = null) },
        { allowed -> if (allowed) "Pop-ups allowed" else "Pop-ups blocked" }
    ),
    "sound" to ContentFieldSpec(
        { it.sound },
        { it.copy(sound = null) },
        { allowed -> if (allowed) "Allowed" else "Blocked" }
    ),
    "intrusive_ads" to ContentFieldSpec(
        { it.adBlock },
        { it.copy(adBlock = null) },
        { blocked -> if (blocked) "Ads blocked" else "Ads allowed" }
    ),
    "protected_content" to ContentFieldSpec(
        { it.protectedContent },
        { it.copy(protectedContent = null) },
        { allowed -> if (allowed) "Allowed" else "Blocked" }
    ),
    "automatic_downloads" to ContentFieldSpec(
        { it.autoDownloads },
        { it.copy(autoDownloads = null) },
        { allowed -> if (allowed) "Allowed" else "Blocked" }
    ),
    "javascript" to ContentFieldSpec(
        { it.javaScript },
        { it.copy(javaScript = null) },
        { allowed -> if (allowed) "Allowed" else "Blocked" }
    )
)

private fun contentRowDescription(rowId: String): String = when (rowId) {
    "third_party_cookies" ->
        "Third-party cookies let a site's content track you across other sites."
    "javascript" ->
        "JavaScript makes sites interactive. Turning it off can break many sites."
    "pop_ups" ->
        "Pop-ups and redirects open new windows or send you to another page."
    "sound" ->
        "Sound controls whether sites may play media with sound without a tap first."
    "intrusive_ads" ->
        "Intrusive ads are the misleading or disruptive ads Click's ad protection blocks."
    "protected_content" ->
        "Protected content is DRM-encrypted media, such as some streaming video. " +
            "It plays only on sites you allow."
    "automatic_downloads" ->
        "Automatic downloads let a site start file downloads on its own. Blocking a " +
            "site here makes Click refuse downloads that start from it."
    "on_device_site_data" ->
        "Sites save data on your device (local storage, IndexedDB) to keep you signed " +
            "in and remember preferences."
    "desktop_site" ->
        "Desktop site shows the wide desktop layout instead of the mobile layout."
    "saved_zoom" ->
        "Text zoom saved for individual sites, set from the per-site sheet or page menu."
    else -> ""
}

/**
 * One reusable content category screen. Each row shows the real control Click
 * actually has — a global toggle where one exists and is applied, and/or the
 * sites-with-overrides list with per-site reset. Where Click has no global
 * switch for a setting, the screen says so instead of inventing one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentCategoryScreen(
    theme: ModeTheme,
    repository: BrowserRepository,
    rowId: String,
    javaScriptEnabled: Boolean,
    onJavaScriptChange: (Boolean) -> Unit,
    desktopSiteDefault: Boolean,
    onDesktopSiteDefaultChange: (Boolean) -> Unit,
    siteDataEnabled: Boolean,
    onSiteDataEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val title = SiteSettingsCatalog.row(rowId)?.title ?: "Site settings"
    val siteSettings by repository.siteSettingsFlow.collectAsState(initial = emptyMap())
    val desktopOverrides by repository.desktopOverridesFlow.collectAsState(initial = emptyMap())

    var zoomGlobal by remember { mutableStateOf(TextScaleStore.DEFAULT) }
    var zoomHosts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    suspend fun loadZoom() {
        try {
            val (global, hosts) = TextScaleStore.snapshot(context)
            zoomGlobal = global
            zoomHosts = hosts
        } catch (_: Exception) {
        }
    }
    LaunchedEffect(rowId) {
        if (rowId == "saved_zoom") loadZoom()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background,
                    titleContentColor = theme.onSurface
                )
            )
        },
        containerColor = theme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val description = contentRowDescription(rowId)
            if (description.isNotBlank()) {
                Text(
                    description,
                    fontSize = 13.sp,
                    color = theme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(16.dp))
            }
            when (rowId) {
                "javascript" -> {
                    ContentSwitchRow(
                        theme = theme,
                        title = "JavaScript",
                        subtitle = "Global default for WebView tabs. Per-site choices below win over it.",
                        checked = javaScriptEnabled,
                        onChange = onJavaScriptChange
                    )
                    Spacer(Modifier.height(16.dp))
                    SiteOverrideList(
                        theme = theme,
                        spec = contentFieldSpecs.getValue("javascript"),
                        siteSettings = siteSettings,
                        onReset = { host, settings ->
                            scope.launch { repository.setSiteSettings(host, settings) }
                        }
                    )
                }
                "desktop_site" -> {
                    ContentSwitchRow(
                        theme = theme,
                        title = "Desktop site by default",
                        subtitle = "New pages open with the wide layout unless a site below has its own choice.",
                        checked = desktopSiteDefault,
                        onChange = onDesktopSiteDefaultChange
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Sites with saved choices",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = theme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    if (desktopOverrides.isEmpty()) {
                        Text(
                            "No sites have a saved layout choice yet.",
                            fontSize = 13.sp,
                            color = theme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        desktopOverrides.toSortedMap().forEach { (host, desktop) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(host, fontSize = 14.sp, color = theme.onSurface)
                                    Text(
                                        if (desktop) "Desktop site" else "Mobile site",
                                        fontSize = 12.sp,
                                        color = theme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                TextButton(onClick = {
                                    scope.launch { repository.clearDesktopHost(host) }
                                }) { Text("Reset") }
                            }
                        }
                    }
                }
                "on_device_site_data" -> {
                    ContentSwitchRow(
                        theme = theme,
                        title = "Allow sites to save data on your device",
                        subtitle = "Applies when pages load. Turning this off stops sites saving " +
                            "data and may sign sites out or reset their preferences.",
                        checked = siteDataEnabled,
                        onChange = onSiteDataEnabledChange
                    )
                }
                "saved_zoom" -> {
                    Text(
                        "Default text zoom: $zoomGlobal%",
                        fontSize = 14.sp,
                        color = theme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Sites with saved zoom",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = theme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    if (zoomHosts.isEmpty()) {
                        Text(
                            "No sites have a saved zoom yet.",
                            fontSize = 13.sp,
                            color = theme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        zoomHosts.toSortedMap().forEach { (host, pct) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(host, fontSize = 14.sp, color = theme.onSurface)
                                    Text(
                                        "$pct%",
                                        fontSize = 12.sp,
                                        color = theme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        TextScaleStore.clearHostScale(context, host)
                                        loadZoom()
                                    }
                                }) { Text("Reset") }
                            }
                        }
                    }
                }
                else -> {
                    val spec = contentFieldSpecs[rowId]
                    Text(
                        "Click has no single global switch for this setting — the per-site " +
                            "choices below are what applies. You can change a choice from the " +
                            "site itself (address-bar shield → Site settings).",
                        fontSize = 13.sp,
                        color = theme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(16.dp))
                    if (spec != null) {
                        SiteOverrideList(
                            theme = theme,
                            spec = spec,
                            siteSettings = siteSettings,
                            onReset = { host, settings ->
                                scope.launch { repository.setSiteSettings(host, settings) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentSwitchRow(
    theme: ModeTheme,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = theme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Sites that have a saved choice for one content field, with per-site reset. */
@Composable
private fun SiteOverrideList(
    theme: ModeTheme,
    spec: ContentFieldSpec,
    siteSettings: Map<String, SiteSettings>,
    onReset: (String, SiteSettings) -> Unit
) {
    val overridden = remember(siteSettings, spec) {
        siteSettings.entries
            .filter { spec.get(it.value) != null }
            .sortedBy { it.key }
    }
    Text(
        "Sites with saved choices",
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = theme.onSurface
    )
    Spacer(Modifier.height(8.dp))
    if (overridden.isEmpty()) {
        Text(
            "No sites have a saved choice yet.",
            fontSize = 13.sp,
            color = theme.onSurface.copy(alpha = 0.6f)
        )
    } else {
        overridden.forEach { (host, settings) ->
            val value = spec.get(settings)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(host, fontSize = 14.sp, color = theme.onSurface)
                    Text(
                        value?.let { spec.label(it) } ?: "",
                        fontSize = 12.sp,
                        color = theme.onSurface.copy(alpha = 0.6f)
                    )
                }
                TextButton(onClick = { onReset(host, spec.clear(settings)) }) { Text("Reset") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Engine-unsupported info screen
// ---------------------------------------------------------------------------

/** The "<X>" in "Websites can't use <X> in Click" for each unsupported row. */
private val unsupportedCapabilityPhrases: Map<String, String> = mapOf(
    "notifications" to "website notifications",
    "embedded_content" to "embedded content",
    "motion_sensors" to "motion sensors",
    "nfc_devices" to "NFC devices",
    "usb" to "USB devices",
    "serial_port" to "serial ports",
    "file_editing" to "file editing",
    "clipboard" to "the clipboard",
    "virtual_reality" to "virtual reality",
    "augmented_reality" to "augmented reality",
    "your_device_use" to "device-use detection",
    "local_network" to "the local network",
    "apps_on_device" to "apps on your device",
    "background_sync" to "background sync",
    "third_party_sign_in" to "third-party sign-in",
    "auto_verify" to "auto-verify",
    "javascript_optimisation" to "JavaScript optimisation controls"
)

/**
 * Truthful status screen for capabilities the browser engine does not
 * provide at all. Deliberately contains no switch or radio: there is no
 * behaviour a control here could change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnsupportedInfoScreen(
    theme: ModeTheme,
    row: SiteSettingsHubRow,
    onBack: () -> Unit
) {
    val phrase = unsupportedCapabilityPhrases[row.id] ?: row.title.lowercase()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(row.title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background,
                    titleContentColor = theme.onSurface
                )
            )
        },
        containerColor = theme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Icon(
                siteSettingsRowIcon(row.id),
                contentDescription = null,
                tint = theme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Websites can't use $phrase in Click. The browser engine doesn't provide " +
                    "this capability, so no site can request it — there is nothing to allow. " +
                    "This is stricter than Chrome's 'Ask first'.",
                fontSize = 14.sp,
                color = theme.onSurface.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )
        }
    }
}
