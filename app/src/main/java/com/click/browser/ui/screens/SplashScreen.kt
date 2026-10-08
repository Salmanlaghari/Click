package com.click.browser.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.coroutines.delay

/**
 * Premium cold-start splash — pure Jetpack Compose vector animation.
 *
 * No video files, no heavy image assets: a dark gradient + a softly pulsing
 * glow, the Click/TEAM PK AI brand mark, and "Welcome to The Team PK AI Era"
 * revealed word-by-word with a staggered fade-up.
 *
 * Behavior:
 * - Auto-dismisses after ~2.5 s (never blocks first interaction past ~3 s).
 * - Tapping anywhere skips it immediately.
 * - [onFinished] is invoked exactly once (tap and timer race is guarded).
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var finished by remember { mutableStateOf(false) }
    fun finishOnce() {
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    // Auto-dismiss: 2.5 s animation, hard cap well under 3 s.
    LaunchedEffect(Unit) {
        delay(2500)
        finishOnce()
    }

    // Softly pulsing ambient glow behind the logo.
    val glowTransition = rememberInfiniteTransition(label = "splashGlow")
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val glowScale by glowTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF05070F), Color(0xFF0B1030), Color(0xFF05070F))
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { finishOnce() }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ambient glow.
        Box(
            modifier = Modifier
                .size(320.dp)
                .scale(glowScale)
                .alpha(glowAlpha)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF3B82F6).copy(alpha = 0.55f), Color.Transparent)
                    ),
                    RoundedCornerShape(160.dp)
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // Brand mark.
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))),
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FlashOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                "CLICK BROWSER",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 6.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            // TEAM PK AI pill.
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    "TEAM PK AI",
                    color = Color(0xFF93C5FD),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
            }
            Spacer(modifier = Modifier.height(36.dp))
            // Staggered headline: word-by-word fade-up.
            StaggeredHeadline(
                text = "Welcome to The Team PK AI Era",
                wordDelayMs = 130L
            )
        }
    }
}

/**
 * Reveals [text] one word at a time — each word fades in while rising
 * slightly, staggered by [wordDelayMs].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaggeredHeadline(text: String, wordDelayMs: Long) {
    val words = remember(text) { text.split(" ") }
    // One visibility flag per word; flipped on with a staggered delay.
    var visibleCount by remember { mutableStateOf(0) }
    LaunchedEffect(text) {
        words.indices.forEach { i ->
            delay(if (i == 0) 350 else wordDelayMs)
            visibleCount = i + 1
        }
    }
    // Wrapping row of animated words, centered.
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        words.forEachIndexed { index, word ->
            val shown = index < visibleCount
            // Animate alpha + rise manually (cheap, no AnimatedVisibility per word).
            val alphaTarget = if (shown) 1f else 0f
            val riseTarget = if (shown) 0f else 14f
            val animAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = alphaTarget,
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                label = "wordAlpha$index"
            )
            val animRise by androidx.compose.animation.core.animateFloatAsState(
                targetValue = riseTarget,
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                label = "wordRise$index"
            )
            Text(
                word,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .alpha(animAlpha)
                    .offset(y = animRise.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}
