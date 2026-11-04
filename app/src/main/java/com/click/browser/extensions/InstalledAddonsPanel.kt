package com.click.browser.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val toolbarAction: InstalledAddonPanelAction? = null
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InstalledAddonsPanel(
    addOns: List<InstalledAddonPanelItem>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onOpenAction: (String) -> Unit,
    onOpenOptions: (String) -> Unit,
    onOpenStore: () -> Unit,
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
                "Use Open action for an add-on toolbar button, or Options for its declared settings page. Private tabs remain on WebView and do not run Gecko add-ons.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

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
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(addOns, key = InstalledAddonPanelItem::id) { addOn ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
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
                                            if (addOn.toolbarAction.pageAction) {
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
                                            "No toolbar action is available on this tab, and no options page is available here. This add-on may work automatically only on pages it supports; no separate window is expected here.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (addOn.canOpenAction || addOn.canOpenOptions) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (addOn.canOpenAction) {
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
