package com.click.browser.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.ModeTheme

/**
 * Premium UI v2 (Prince-approved design).
 *
 * - [ChromeTopBar]: single-row Chrome-like top bar — back, forward, rounded
 *   address bar, tab-count badge, ⋮ browser menu. Replaces the old 2-row
 *   BrowserTopBar + separate TabStrip.
 * - [FeatureMenuFabOverlay]: always-visible bottom-left circular menu button.
 *   Expands an animated 32-feature grid panel (scale + fade, spring feel).
 *   Mode chips (Simple / Developer / Hack) sit above the grid.
 */

// ---------------------------------------------------------------------------
// 32-feature catalogue
// ---------------------------------------------------------------------------

enum class FeatureId {
    HISTORY, SETTINGS, STORAGE, PASSWORDS, TOOLS, DEVTOOLS,
    DOWNLOADS, BOOKMARKS, AI_CHAT, TRANSLATE, DESKTOP, NEW_TAB,
    PRIVATE_TAB, TABS, RECENT_TABS, SHARE, FIND_IN_PAGE, EXTENSIONS,
    ADBLOCK, READER, SCREENSHOT, SAVE_PDF, ADD_HOME, SITE_INFO,
    PRIVACY_GUARDS, UA_SPOOFER, UA_SWITCHER, FULLSCREEN, TEXT_SIZE,
    NIGHT_MODE, CLEAR_DATA, ABOUT, V9_SHIELD, FLAGS, VERSION
}

data class FeatureDef(
    val id: FeatureId,
    val label: String,
    val icon: ImageVector
)

val ALL_FEATURES: List<FeatureDef> = listOf(
    FeatureDef(FeatureId.HISTORY, "History", Icons.Default.History),
    FeatureDef(FeatureId.SETTINGS, "Settings", Icons.Default.Settings),
    FeatureDef(FeatureId.STORAGE, "Storage", Icons.Default.Save),
    FeatureDef(FeatureId.PASSWORDS, "Passwords", Icons.Default.Lock),
    FeatureDef(FeatureId.TOOLS, "Tools", Icons.Default.Build),
    FeatureDef(FeatureId.DEVTOOLS, "DevTools", Icons.Default.Code),
    FeatureDef(FeatureId.DOWNLOADS, "Downloads", Icons.Default.Download),
    FeatureDef(FeatureId.BOOKMARKS, "Bookmarks", Icons.Default.Bookmark),
    FeatureDef(FeatureId.AI_CHAT, "AI Chat", Icons.Default.AutoAwesome),
    FeatureDef(FeatureId.TRANSLATE, "Translate", Icons.Default.Translate),
    FeatureDef(FeatureId.DESKTOP, "Desktop", Icons.Default.Computer),
    FeatureDef(FeatureId.NEW_TAB, "New Tab", Icons.Default.Add),
    FeatureDef(FeatureId.PRIVATE_TAB, "Private", Icons.Default.VisibilityOff),
    FeatureDef(FeatureId.TABS, "Tabs", Icons.Default.Tab),
    FeatureDef(FeatureId.RECENT_TABS, "Recent", Icons.Default.Restore),
    FeatureDef(FeatureId.SHARE, "Share", Icons.Default.Share),
    FeatureDef(FeatureId.FIND_IN_PAGE, "Find", Icons.Default.Search),
    FeatureDef(FeatureId.EXTENSIONS, "Extensions", Icons.Default.Extension),
    FeatureDef(FeatureId.ADBLOCK, "AdBlock", Icons.Default.Block),
    FeatureDef(FeatureId.READER, "Reader", Icons.Default.MenuBook),
    FeatureDef(FeatureId.SCREENSHOT, "Shot", Icons.Default.PhotoCamera),
    FeatureDef(FeatureId.SAVE_PDF, "PDF", Icons.Default.PictureAsPdf),
    FeatureDef(FeatureId.ADD_HOME, "Add Home", Icons.Default.Home),
    FeatureDef(FeatureId.SITE_INFO, "Site Info", Icons.Default.Info),
    FeatureDef(FeatureId.PRIVACY_GUARDS, "Guards", Icons.Default.Shield),
    FeatureDef(FeatureId.UA_SPOOFER, "Spoofer", Icons.Default.Fingerprint),
    FeatureDef(FeatureId.UA_SWITCHER, "UA Switch", Icons.Default.SwapHoriz),
    FeatureDef(FeatureId.FULLSCREEN, "Fullscreen", Icons.Default.Fullscreen),
    FeatureDef(FeatureId.TEXT_SIZE, "Text Size", Icons.Default.TextFields),
    FeatureDef(FeatureId.NIGHT_MODE, "Night", Icons.Default.DarkMode),
    FeatureDef(FeatureId.CLEAR_DATA, "Clear Data", Icons.Default.DeleteSweep),
    FeatureDef(FeatureId.ABOUT, "About", Icons.Default.Help),
    FeatureDef(FeatureId.V9_SHIELD, "V9 Shield", Icons.Default.VpnKey),
    FeatureDef(FeatureId.FLAGS, "Flags", Icons.Default.Science),
    FeatureDef(FeatureId.VERSION, "Version", Icons.Default.Info),
)

// ---------------------------------------------------------------------------
// Chrome-like single-row top bar (approved design)
// ---------------------------------------------------------------------------

@Composable
fun ChromeTopBar(
    theme: ModeTheme,
    currentUrl: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNavigate: (String) -> Unit,
    onTabCounterClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    var textInput by remember(currentUrl) { mutableStateOf(currentUrl) }
    val accent = theme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.topBarBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(38.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = theme.onTopBar.copy(alpha = if (canGoBack) 1f else 0.35f)
            )
        }
        IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(38.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Forward",
                tint = theme.onTopBar.copy(alpha = if (canGoForward) 1f else 0.35f)
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
                // Lock for real web pages (http/https); search icon for
                // anything else (about:blank, file://, typed queries…).
                val isWebPage = currentUrl.startsWith("http://") ||
                    currentUrl.startsWith("https://")
                if (isWebPage) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Secure",
                        tint = androidx.compose.ui.graphics.Color(0xFF22C55E),
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
        Spacer(modifier = Modifier.width(6.dp))
        // Tab counter badge → opens the visual tab switcher (Mises-style)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.15f))
                .clickable(onClick = onTabCounterClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tabCount.toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
        }
        IconButton(onClick = onMenuClick, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Browser menu", tint = theme.onTopBar)
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom-left FAB menu overlay (always visible)
// ---------------------------------------------------------------------------

@Composable
fun FeatureMenuFabOverlay(
    theme: ModeTheme,
    expanded: Boolean,
    activeMode: BrowserMode,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onModeChange: (BrowserMode) -> Unit,
    onFeature: (FeatureId) -> Unit,
) {
    val accent = theme.primary
    // Tap-outside-to-dismiss scrim (transparent, only when open). Consumes ALL
    // pointer input so taps can never leak through to the WebView, top bar or
    // bottom nav underneath; a tap dismisses the menu.
    if (expanded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onDismiss) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            up.consume()
                            onDismiss()
                        }
                    }
                }
        )
    }
    // Expanding feature panel
    AnimatedVisibility(
        visible = expanded,
        enter = scaleIn(
            transformOrigin = TransformOrigin(0f, 1f),
            animationSpec = tween(280)
        ) + fadeIn(animationSpec = tween(200)),
        exit = scaleOut(
            transformOrigin = TransformOrigin(0f, 1f),
            animationSpec = tween(200)
        ) + fadeOut(animationSpec = tween(150)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 84.dp, end = 12.dp)
                    .shadow(16.dp, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = theme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Mode chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModeChip("Simple", activeMode == BrowserMode.SIMPLE, accent, theme) {
                            onModeChange(BrowserMode.SIMPLE)
                        }
                        ModeChip("Developer", activeMode == BrowserMode.DEVELOPER, accent, theme) {
                            onModeChange(BrowserMode.DEVELOPER)
                        }
                        ModeChip("Hack", activeMode == BrowserMode.HACK, accent, theme) {
                            onModeChange(BrowserMode.HACK)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    // 32-feature grid (4 columns, scrollable)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.height(320.dp)
                    ) {
                        items(ALL_FEATURES, key = { it.id }) { feature ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onFeature(feature.id) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(theme.surfaceVariant.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        feature.icon,
                                        contentDescription = feature.label,
                                        tint = accent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    feature.label,
                                    fontSize = 9.sp,
                                    color = theme.onSurface.copy(alpha = 0.85f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    // The FAB itself (rotates 45° when open)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(250),
        label = "fabRotate"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Surface(
            onClick = onToggle,
            shape = CircleShape,
            color = accent,
            shadowElevation = 10.dp,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Feature menu",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    theme: ModeTheme,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.2f),
            selectedLabelColor = accent
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = if (selected) accent else theme.onSurface.copy(alpha = 0.25f),
            selectedBorderColor = accent,
            enabled = true,
            selected = selected
        )
    )
}
