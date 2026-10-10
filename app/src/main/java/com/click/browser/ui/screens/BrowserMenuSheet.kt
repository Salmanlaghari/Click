package com.click.browser.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.ClosedTab
import com.click.browser.engine.ModeTheme

/**
 * Chrome/Mises-style browser menu (bottom sheet).
 *
 * Every item actually works — no dead buttons:
 * New tab / New private tab / Tabs (visual switcher) / History /
 * Delete browsing data / Downloads / Bookmarks / Games / Recent tabs /
 * Extensions (userscripts) / Share / Find in page / Translate /
 * Desktop site toggle / Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenuSheet(
    theme: ModeTheme,
    isDesktopForSite: Boolean,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onHistory: () -> Unit,
    onDeleteBrowsingData: () -> Unit,
    onDownloads: () -> Unit,
    onPlaylist: () -> Unit,
    onBookmarks: () -> Unit,
    onGames: () -> Unit,
    onRecentTabs: () -> Unit,
    onExtensions: () -> Unit,
    onShare: () -> Unit,
    onFindInPage: () -> Unit,
    onTranslate: () -> Unit,
    onToggleDesktopSite: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = theme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.Add,
                    label = "New tab",
                    onClick = onNewTab
                )
            }
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.Security,
                    label = "New Private tab",
                    onClick = onNewPrivateTab
                )
            }
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.FilterNone,
                    label = "Tabs",
                    onClick = onOpenTabSwitcher
                )
            }
            item { MenuDivider(theme) }
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.History,
                    label = "History",
                    onClick = onHistory
                )
            }
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.Delete,
                    label = "Delete browsing data",
                    onClick = onDeleteBrowsingData
                )
            }
            item { MenuDivider(theme) }
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.Download,
                    label = "Downloads",
                    onClick = onDownloads
                )
            }
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.QueueMusic,
                    label = "Playlist",
                    onClick = onPlaylist
                )
            }
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.Bookmark,
                    label = "Bookmarks",
                    onClick = onBookmarks
                )
            }
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.SportsEsports,
                    label = "Games",
                    onClick = onGames
                )
            }
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.Restore,
                    label = "Recent tabs",
                    onClick = onRecentTabs
                )
            }
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.Extension,
                    label = "Extensions",
                    onClick = onExtensions
                )
            }
            item { MenuDivider(theme) }
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.Share,
                    label = "Share…",
                    onClick = onShare
                )
            }
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.Search,
                    label = "Find in page",
                    onClick = onFindInPage
                )
            }
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.Translate,
                    label = "Translate",
                    onClick = onTranslate
                )
            }
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.DesktopWindows,
                    label = "Desktop site",
                    trailing = {
                        Switch(
                            checked = isDesktopForSite,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(checkedThumbColor = theme.primary)
                        )
                    },
                    onClick = onToggleDesktopSite
                )
            }
            item {
                MenuRow(
                    theme = theme,
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    onClick = onSettings
                )
            }
        }
    }
}

@Composable
private fun MenuRow(
    theme: ModeTheme,
    icon: ImageVector,
    label: String,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = theme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            color = theme.onSurface,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
private fun MenuDivider(theme: ModeTheme) {
    Divider(
        color = theme.onSurface.copy(alpha = 0.12f),
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = 6.dp, horizontal = 16.dp)
    )
}

/**
 * Recently closed tabs — tap to re-open, swipe-free simple list with
 * per-item remove and a Clear-all action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentTabsScreen(
    closedTabs: List<ClosedTab>,
    theme: ModeTheme,
    onReopen: (ClosedTab) -> Unit,
    onRemove: (ClosedTab) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = theme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Recent tabs",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.onSurface
                )
                if (closedTabs.isNotEmpty()) {
                    Text(
                        "Clear all",
                        fontSize = 13.sp,
                        color = theme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onClearAll)
                    )
                }
            }
            if (closedTabs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No recently closed tabs",
                        fontSize = 14.sp,
                        color = theme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn {
                    items(closedTabs, key = { it.url }) { tab ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onReopen(tab) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    tab.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    tab.url,
                                    fontSize = 12.sp,
                                    color = theme.onSurface.copy(alpha = 0.55f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.material3.IconButton(
                                onClick = { onRemove(tab) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = theme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Divider(
                            color = theme.onSurface.copy(alpha = 0.08f),
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }
        }
    }
}
