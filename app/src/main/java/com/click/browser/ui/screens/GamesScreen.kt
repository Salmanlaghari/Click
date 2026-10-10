package com.click.browser.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.click.browser.engine.BrowserMode
import com.click.browser.engine.ModeTheme
import org.json.JSONObject

// ---------------------------------------------------------------------------
// Games catalog — 100% original TEAM PK AI mini-games, bundled as offline
// HTML5 assets (app/src/main/assets/games/<id>/index.html).
// ---------------------------------------------------------------------------

data class GameEntry(
    val id: String,
    val name: String,
    val desc: String,
    val emoji: String,
    val tint: Color
)

val GAMES_CATALOG: List<GameEntry> = listOf(
    GameEntry("snake", "Snake", "Classic snake — eat, grow, don't crash", "🐍", Color(0xFF22C55E)),
    GameEntry("breakout", "Breakout", "Smash every brick with the paddle", "🧱", Color(0xFF3B82F6)),
    GameEntry("memory", "Memory Match", "Flip cards and find all 8 pairs", "🃏", Color(0xFF8B5CF6)),
    GameEntry("tictactoe", "Tic-Tac-Toe", "Beat the AI at noughts & crosses", "⭕", Color(0xFFF59E0B)),
    GameEntry("flappy", "Sky Hopper", "Tap to flap through the pipes", "🐤", Color(0xFF0EA5E9)),
)

// ---------------------------------------------------------------------------
// Changelog ("Today Update") — read from bundled assets/changelog.json.
// ---------------------------------------------------------------------------

data class ChangelogItem(val icon: String, val title: String, val desc: String)

/** Loads the bundled changelog; never crashes — returns empty list on any error. */
fun loadChangelog(context: Context): List<ChangelogItem> {
    return try {
        val raw = context.assets.open("changelog.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val arr = root.optJSONArray("items") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    ChangelogItem(
                        icon = o.optString("icon", "✨"),
                        title = o.optString("title", ""),
                        desc = o.optString("desc", "")
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

// ---------------------------------------------------------------------------
// Games hub — the `click://games` internal page.
// ---------------------------------------------------------------------------

@Composable
fun GamesHubScreen(
    theme: ModeTheme,
    onPlayGame: (String) -> Unit
) {
    val context = LocalContext.current
    val changelog = remember { loadChangelog(context) }
    val neon = theme.mode == BrowserMode.HACK
    val cardBg = when {
        neon -> Color(0xFF0A0A12)
        theme.mode == BrowserMode.DEVELOPER -> theme.surface.copy(alpha = 0.72f)
        else -> theme.surface
    }
    val cardBorder = when {
        neon -> theme.glow.copy(alpha = 0.35f)
        theme.mode == BrowserMode.DEVELOPER -> Color.White.copy(alpha = 0.08f)
        else -> theme.onSurface.copy(alpha = 0.08f)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Today Update banner (full-width).
            if (changelog.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    TodayUpdateBanner(theme, changelog, cardBg, cardBorder)
                }
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Text(
                    "Offline mini-games",
                    color = theme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Text(
                    "100% offline — no internet needed, no ads inside games.",
                    color = theme.onBackground.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            items(GAMES_CATALOG) { game ->
                GameCard(game, cardBg, cardBorder, theme.onSurface) { onPlayGame(game.id) }
            }
        }
    }
}

@Composable
private fun GameCard(
    game: GameEntry,
    cardBg: Color,
    cardBorder: Color,
    onSurface: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(game.tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(game.emoji, fontSize = 28.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(game.name, color = onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                game.desc,
                color = onSurface.copy(alpha = 0.55f),
                fontSize = 11.sp,
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun TodayUpdateBanner(
    theme: ModeTheme,
    changelog: List<ChangelogItem>,
    cardBg: Color,
    cardBorder: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "Today Update",
                        color = theme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        "What's new in this build",
                        color = theme.onSurface.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            changelog.take(6).forEach { item ->
                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(item.icon, fontSize = 16.sp, modifier = Modifier.padding(end = 10.dp))
                    Column {
                        Text(
                            item.title,
                            color = theme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            item.desc,
                            color = theme.onSurface.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Home-screen "Today Update" card (above the news feed).
// ---------------------------------------------------------------------------

@Composable
fun TodayUpdateCard(
    theme: ModeTheme,
    cardBg: Color,
    cardBorder: Color,
    onGamesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val changelog = remember { loadChangelog(context) }
    if (changelog.isEmpty()) return
    val first = changelog.first()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onGamesClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFFD946EF)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("✨", fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Today Update",
                    color = theme.onSurface.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${first.icon} ${first.title}",
                    color = theme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    maxLines = 1
                )
                Text(
                    first.desc,
                    color = theme.onSurface.copy(alpha = 0.6f),
                    fontSize = 11.5.sp,
                    maxLines = 1
                )
            }
            Text("▶", color = theme.primary, fontSize = 16.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Game player — full-screen WebView loading the offline bundled game.
// ---------------------------------------------------------------------------

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GamePlayerScreen(
    gameId: String,
    theme: ModeTheme,
    onClose: () -> Unit
) {
    val game = remember(gameId) { GAMES_CATALOG.firstOrNull { it.id == gameId } }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    val gameUrl = remember(gameId) { "file:///android_asset/games/$gameId/index.html" }

    // Restart button: reload the game page exactly once per tap.
    androidx.compose.runtime.LaunchedEffect(reloadTick) {
        if (reloadTick > 0) webViewRef.value?.loadUrl(gameUrl)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.topBarBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = theme.onTopBar)
            }
            Text(
                text = "${game?.emoji ?: "🎮"} ${game?.name ?: "Game"}",
                color = theme.onTopBar,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { reloadTick++ }) {
                Icon(Icons.Default.Refresh, contentDescription = "Restart game", tint = theme.onTopBar)
            }
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = WebViewClient()
                    loadUrl(gameUrl)
                    webViewRef.value = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
