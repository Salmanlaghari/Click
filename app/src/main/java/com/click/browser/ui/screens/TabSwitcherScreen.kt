package com.click.browser.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.TabItem
import com.click.browser.engine.ModeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Visual tab switcher — Mises/Chrome-style card grid.
 *
 * - 2-column grid of cards with scaled page thumbnails (captured once per
 *   page finish into [TabThumbnailStore], max 360px wide — never live).
 * - Top: tab count badge + "Search tabs" filter (title/URL).
 * - Bottom: + button for a new blank tab.
 * - Incognito tabs never have thumbnails: lock placeholder instead.
 * - Proper item keys + LRU-cached bitmaps keep scrolling smooth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherScreen(
    tabs: List<TabItem>,
    activeTabIndex: Int,
    theme: ModeTheme,
    thumbnails: Map<String, Bitmap>,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onNewTab: () -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(tabs, query) {
        if (query.isBlank()) tabs
        else tabs.filter {
            it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
        }
    }
    // Premium UI v2: staggered card entry + animated close.
    val animScope = rememberCoroutineScope()
    var cardsVisible by remember { mutableStateOf(false) }
    var closingTabId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { cardsVisible = true }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Tab count badge (Mises-style)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = theme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = tabs.size.toString(),
                                color = theme.primary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Tabs",
                            color = theme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.topBarBg)
            )
        },
        bottomBar = {
            Surface(
                color = theme.topBarBg,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = onNewTab,
                        modifier = Modifier
                            .size(52.dp)
                            .background(theme.primary.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "New tab",
                            tint = theme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            // Search tabs
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Search your tabs", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.primary.copy(alpha = 0.6f),
                    unfocusedBorderColor = theme.onSurface.copy(alpha = 0.2f),
                    focusedContainerColor = theme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = theme.surfaceVariant.copy(alpha = 0.5f),
                    cursorColor = theme.primary
                )
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 8.dp)
            ) {
                items(filtered, key = { it.id }) { tab ->
                    val index = filtered.indexOf(tab)
                    val idx = tabs.indexOfFirst { it.id == tab.id }
                    val isActive = idx == activeTabIndex
                    val thumb = thumbnails[tab.id]
                    // Premium UI v2: staggered fade+slide entry (~70ms), and
                    // animated close (scale-down + slide-out, ~300ms).
                    val stagger = (index % 10) * 70
                    AnimatedVisibility(
                        visible = cardsVisible && closingTabId != tab.id,
                        enter = fadeIn(tween(380, delayMillis = stagger)),
                        exit = fadeOut(tween(180)) + scaleOut(tween(300), 0.7f),
                    ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = idx >= 0) { onSelectTab(idx) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (tab.isIncognito) theme.surface else theme.surfaceVariant
                        ),
                        border = if (isActive) androidx.compose.foundation.BorderStroke(
                            2.dp,
                            theme.primary
                        ) else null,
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 6.dp else 2.dp)
                    ) {
                        Column {
                            // Thumbnail
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.15f)
                                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                                    .background(theme.background)
                            ) {
                                if (thumb != null && !tab.isIncognito) {
                                    Image(
                                        bitmap = thumb.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    // Placeholder: shimmer-ish tinted box with an icon
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (tab.isIncognito) Icons.Default.Lock else Icons.Default.Web,
                                            contentDescription = null,
                                            tint = theme.onSurface.copy(alpha = 0.25f),
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                                // Close X (top-right, like Mises) — Premium UI v2:
                                // plays the slide/scale-out animation, then removes.
                                IconButton(
                                    onClick = {
                                        if (idx >= 0 && closingTabId == null) {
                                            closingTabId = tab.id
                                            animScope.launch {
                                                delay(300)
                                                onCloseTab(idx)
                                                closingTabId = null
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(28.dp)
                                        .background(
                                            theme.background.copy(alpha = 0.75f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close tab",
                                        tint = theme.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                if (tab.isIncognito) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = theme.primary.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            "Private",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.background,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            // Title + URL
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = tab.title.ifBlank { "New Tab" },
                                    fontSize = 12.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isActive) theme.primary else theme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab.url.ifBlank { "about:blank" },
                                    fontSize = 10.sp,
                                    color = theme.onSurface.copy(alpha = 0.55f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}
