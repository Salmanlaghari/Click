package com.click.browser.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class FirstRunPage(
    val eyebrow: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accent: Color
)

private val firstRunPages = listOf(
    FirstRunPage(
        eyebrow = "FOUR WAYS TO BROWSE",
        title = "One browser. Your way.",
        description = "Simple — clean, everyday browsing.\nDeveloper — dev shortcuts and DevTools.\nPower — desktop-style browsing.\nAdvance — starts fresh; bookmarks, history, downloads and saved passwords stay separate from the other three. Website cookies/cache are already separate in every mode.",
        icon = Icons.Default.Apps,
        accent = Color(0xFF7C6B54)
    ),
    FirstRunPage(
        eyebrow = "SEARCH, YOUR WAY",
        title = "Know who's powering search.",
        description = "Choose a search provider for each profile. Its brand mark stays beside the address bar, so the active engine is always clear.",
        icon = Icons.Default.Search,
        accent = Color(0xFF4C7C8D)
    ),
    FirstRunPage(
        eyebrow = "PRIVACY BUILT IN",
        title = "Browse with more control.",
        description = "Use ad and tracker blocking, HTTPS controls, private tabs, and per-site protections from one place.",
        icon = Icons.Default.Shield,
        accent = Color(0xFF577B63)
    ),
    FirstRunPage(
        eyebrow = "TOOLS THAT GO FURTHER",
        title = "More than a place to open links.",
        description = "Translate pages, use reader mode, organize tabs, manage downloads, and reach useful browser tools without leaving Click.",
        icon = Icons.Default.AutoAwesome,
        accent = Color(0xFF9A7652)
    )
)

/** First-run feature tour: horizontal swipe, visible progress, and skip at any time. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FirstRunOnboarding(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { firstRunPages.size })
    val scope = rememberCoroutineScope()
    val cream = Color(0xFFFBF8F1)
    val ink = Color(0xFF302A22)
    val muted = Color(0xFF756C5F)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = cream
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0xFFEFE5D5)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("C", color = Color(0xFF66533F), fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Click", color = ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("BROWSER", color = muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                }
                TextButton(onClick = onFinished) {
                    Text("Skip", color = muted, fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                pageSpacing = 12.dp,
                contentPadding = PaddingValues(horizontal = 1.dp)
            ) { pageIndex ->
                FirstRunFeatureCard(
                    page = firstRunPages[pageIndex],
                    ink = ink,
                    muted = muted,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                firstRunPages.indices.forEach { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (selected) 22.dp else 7.dp, height = 7.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color(0xFF806B51) else Color(0xFFD8CFC0))
                    )
                }
            }
            Text(
                "Swipe to explore",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = muted,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Back", color = Color(0xFF66533F))
                    }
                }
                Button(
                    onClick = {
                        if (pagerState.currentPage == firstRunPages.lastIndex) onFinished()
                        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier.weight(if (pagerState.currentPage > 0) 1.6f else 1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66533F), contentColor = Color.White)
                ) {
                    Text(
                        if (pagerState.currentPage == firstRunPages.lastIndex) "Start browsing" else "Next",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun FirstRunFeatureCard(
    page: FirstRunPage,
    ink: Color,
    muted: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 25.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(page.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(page.icon, contentDescription = null, tint = page.accent, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(page.eyebrow, color = page.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                page.title,
                color = ink,
                fontSize = 29.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))
            Text(
                page.description,
                color = muted,
                fontSize = 15.sp,
                lineHeight = 23.sp
            )
        }
    }
}
