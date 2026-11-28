package com.click.browser.extensions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class InstalledAddonPanelAction(
    val label: String,
    val pageAction: Boolean
)

internal data class InstalledAddonPanelItem(
    val id: String,
    val name: String,
    val version: String,
    val enabled: Boolean,
    val policyBlockers: List<String>,
    val description: String? = null,
    val optionsPageUrl: String? = null,
    val optionsPageBaseUrl: String? = null,
    val hasDeclaredOptionsPage: Boolean = optionsPageUrl != null,
    val toolbarAction: InstalledAddonPanelAction? = null,
    /** True for the extensions Click ships pre-installed in the APK. */
    val builtIn: Boolean = false,
    /** The add-on's own icon inside the APK assets (pre-installed add-ons only). */
    val iconAssetPath: String? = null
) {
    val statusLabel: String
        get() = when {
            policyBlockers.isNotEmpty() -> "Blocked by Click safety policy"
            enabled -> "Enabled"
            else -> "Disabled"
        }

    val canOpenAction: Boolean
        get() = enabled && policyBlockers.isEmpty() && toolbarAction != null

    val canOpenOptions: Boolean
        get() = enabled && policyBlockers.isEmpty() && optionsPageUrl != null && optionsPageBaseUrl != null

    val hasNoSeparateWindow: Boolean
        get() = enabled && policyBlockers.isEmpty() && toolbarAction == null && optionsPageUrl == null
}

internal fun safeBuildInstalledAddonPanelItems(
    buildItems: () -> List<InstalledAddonPanelItem>
): Result<List<InstalledAddonPanelItem>> = try {
    Result.success(buildItems())
} catch (exception: Exception) {
    Result.failure(exception)
}

/** One row of the panel list: a section header or an add-on card. */
internal sealed interface InstalledAddonPanelRow {
    data class Header(val title: String) : InstalledAddonPanelRow
    data class Entry(val item: InstalledAddonPanelItem) : InstalledAddonPanelRow
}

/**
 * Panel order: the pre-installed extensions first, under an "Already
 * installed" header so the user sees they came with Click and are ready to
 * use; the user's own add-ons follow under their own header. When nothing
 * is pre-installed the list renders exactly as before (no headers).
 */
internal fun installedAddonPanelRows(
    addOns: List<InstalledAddonPanelItem>
): List<InstalledAddonPanelRow> {
    val builtIns = addOns.filter { it.builtIn }
    val userAddOns = addOns.filterNot { it.builtIn }
    if (builtIns.isEmpty()) {
        return userAddOns.map { InstalledAddonPanelRow.Entry(it) }
    }
    return buildList {
        add(InstalledAddonPanelRow.Header("Already installed — ready to use"))
        builtIns.mapTo(this) { InstalledAddonPanelRow.Entry(it) }
        if (userAddOns.isNotEmpty()) {
            add(InstalledAddonPanelRow.Header("Installed by you"))
            userAddOns.mapTo(this) { InstalledAddonPanelRow.Entry(it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InstalledAddonsPanel(
    addOns: List<InstalledAddonPanelItem>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onOpenAction: (String) -> Unit,
    onOpenOptions: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onOpenStore: () -> Unit,
    currentTabUsesGecko: Boolean,
    currentPageSupportsActions: Boolean,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Installed add-ons", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Click GeckoView runtime · regular tabs only",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh installed add-ons")
                }
            }

            Text(
                ExtensionUiGuidance.enabledDoesNotMeanPopup,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            if (!currentTabUsesGecko) {
                Text(
                    ExtensionUiGuidance.webViewTabNotice,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            when {
                isLoading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                }
                errorMessage != null -> Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
                addOns.isEmpty() -> Text(
                    "No add-ons are installed yet.",
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> {
                    // Small-icon grid access (Prince, 2026-11-28: extensions
                    // in a grid of small icons). Tapping a tile selects the
                    // add-on; its full controls (enable switch, action,
                    // options, guidance) appear once in the detail card
                    // under the grid — the same controls the old full-width
                    // cards repeated for every add-on.
                    //
                    // The grid is plain Rows inside the sheet's own scroll
                    // (no nested LazyVerticalGrid) and the column count
                    // adapts to the sheet width, so narrow screens,
                    // split-screen and foldables get usable tiles
                    // (Kilo WARNINGs on PR #271, 2026-11-28).
                    var selectedId by remember { mutableStateOf<String?>(null) }
                    val selected = addOns.firstOrNull { it.id == selectedId }
                        ?: addOns.firstOrNull()
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val columns = (maxWidth / 88.dp).toInt().coerceIn(3, 5)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            var pendingEntries = emptyList<InstalledAddonPanelItem>()
                            fun flushEntries() {
                                pendingEntries.chunked(columns).forEach { chunk ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        chunk.forEach { item ->
                                            AddonGridTile(
                                                addOn = item,
                                                selected = selected?.id == item.id,
                                                onClick = { selectedId = item.id },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(columns - chunk.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                                pendingEntries = emptyList()
                            }
                            installedAddonPanelRows(addOns).forEach { row ->
                                when (row) {
                                    is InstalledAddonPanelRow.Header -> {
                                        flushEntries()
                                        Text(
                                            row.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                    is InstalledAddonPanelRow.Entry -> {
                                        pendingEntries = pendingEntries + row.item
                                    }
                                }
                            }
                            flushEntries()
                        }
                    }
                    selected?.let { addOn ->
                        AddonDetailCard(
                            addOn = addOn,
                            currentPageSupportsActions = currentPageSupportsActions,
                            onOpenAction = onOpenAction,
                            onOpenOptions = onOpenOptions,
                            onToggleEnabled = onToggleEnabled
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Button(onClick = onOpenStore, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Extension, contentDescription = null)
                Text("Open Mozilla Add-ons Store", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}


/**
 * One add-on in the small-icon access grid: its icon, a status dot
 * (green enabled, grey disabled, red policy-blocked) and its name.
 */
@Composable
private fun AddonGridTile(
    addOn: InstalledAddonPanelItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor = when {
        addOn.policyBlockers.isNotEmpty() -> MaterialTheme.colorScheme.error
        addOn.enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (selected) "Selected" else "Not selected"
                contentDescription = addOn.name
            }
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box {
            AddonPanelIcon(addOn, size = 36.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        Text(
            addOn.name,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * The selected add-on's full controls — the exact content the old
 * full-width list cards carried (status, description, guidance,
 * enable switch, action/options buttons), shown once under the grid.
 */
@Composable
private fun AddonDetailCard(
    addOn: InstalledAddonPanelItem,
    currentPageSupportsActions: Boolean,
    onOpenAction: (String) -> Unit,
    onOpenOptions: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AddonPanelIcon(addOn)
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        addOn.name,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Version ${addOn.version} · ${addOn.statusLabel}",
                        color = if (addOn.policyBlockers.isNotEmpty()) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                if (addOn.policyBlockers.isEmpty()) {
                    Switch(
                        checked = addOn.enabled,
                        onCheckedChange = { enabled -> onToggleEnabled(addOn.id, enabled) }
                    )
                }
            }
            addOn.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (addOn.policyBlockers.isNotEmpty()) {
                Text(
                    addOn.policyBlockers.joinToString(),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            } else if (!addOn.enabled) {
                Text(
                    "Enable this add-on before opening its action or settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            } else {
                if (addOn.toolbarAction != null) {
                    Text(
                        if (!currentPageSupportsActions) {
                            "Add-on actions are available on ordinary HTTP(S) website tabs, not on add-on settings pages."
                        } else if (addOn.toolbarAction.pageAction) {
                            "A page action is available on this tab."
                        } else {
                            "A toolbar action is available. If it declares a popup, it opens here; otherwise it may run in the background."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                } else if (addOn.optionsPageUrl != null) {
                    Text(
                        "No toolbar action is available on this tab; you can still open the add-on's options.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                } else if (addOn.hasDeclaredOptionsPage) {
                    Text(
                        "Options were not opened because the page could not be verified as belonging to this add-on.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                } else if (addOn.hasNoSeparateWindow) {
                    Text(
                        ExtensionUiGuidance.noActionOrOptions,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                if ((addOn.canOpenAction && currentPageSupportsActions) || addOn.canOpenOptions) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (addOn.canOpenAction && currentPageSupportsActions) {
                            OutlinedButton(
                                onClick = { onOpenAction(addOn.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Open action", maxLines = 1)
                            }
                        }
                        if (addOn.canOpenOptions) {
                            OutlinedButton(
                                onClick = { onOpenOptions(addOn.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Options", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The add-on's own icon. Pre-installed add-ons bundle their manifest icon
 * PNG inside the APK assets, so it renders with no network and no store
 * round-trip; anything without a bundled icon gets the generic glyph.
 */
@Composable
private fun AddonPanelIcon(addOn: InstalledAddonPanelItem, size: Dp = 30.dp) {
    val context = LocalContext.current
    val iconPath = addOn.iconAssetPath
    val bitmap by produceState<Bitmap?>(initialValue = null, iconPath) {
        value = if (iconPath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open(iconPath).use { stream -> BitmapFactory.decodeStream(stream) }
                }.getOrNull()
            }
        }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = addOn.name,
            modifier = Modifier.size(size)
        )
    } else {
        Icon(
            Icons.Default.Extension,
            contentDescription = addOn.name,
            modifier = Modifier.size(size),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
