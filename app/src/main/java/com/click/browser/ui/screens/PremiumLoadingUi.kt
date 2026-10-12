package com.click.browser.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.click.browser.engine.ModeTheme

/**
 * Premium page-load progress line, shown under the address bar while a
 * page loads. Per-mode themed: the gradient runs from the mode's primary
 * to secondary color (Simple = blue, Developer = purple, Hack = neon,
 * Advance = teal), with an animated shimmer sweep and a soft glow.
 *
 * Driven by WebChromeClient.onProgressChanged; fades out smoothly when
 * the load completes (progress reaches 100).
 */
@Composable
fun PremiumLoadingBar(
    theme: ModeTheme,
    progress: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = progress in 1..99,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val infinite = rememberInfiniteTransition(label = "loadShimmer")
        val sweep by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sweep"
        )
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
        ) {
            // Real bar width in pixels — available during composition via
            // constraints (never 0 like graphicsLayer's size on 1st frame).
            val barWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                maxWidth.toPx()
            }
            // Faint track in the mode's primary color.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.primary.copy(alpha = 0.15f))
            )
            // Actual progress fill: honest proportional gradient, no
            // artificial minimum — 1% progress shows a 1% line.
            val fillFraction = (progress / 100f).coerceIn(0f, 1f)
            if (fillFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fillFraction)
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(theme.primary, theme.secondary)
                            )
                        )
                )
            }
            // Animated shimmer highlight sweeping across the bar, sized
            // relative to the real bar width.
            if (barWidthPx > 0f) {
                val highlightW = barWidthPx * 0.35f
                val x = (sweep * (barWidthPx + highlightW * 2f)) - highlightW
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.55f),
                                    Color.Transparent
                                ),
                                start = androidx.compose.ui.geometry.Offset(x, 0f),
                                end = androidx.compose.ui.geometry.Offset(x + highlightW, 0f)
                            )
                        )
                )
            }
        }
    }
}

/**
 * Skeleton shimmer placeholders shown over the content area while the
 * first paint hasn't happened yet (no blank white screen). Per-mode
 * themed surfaces with an animated diagonal shimmer.
 *
 * Show only briefly: [visible] should be true while progress is 1..99
 * AND the page hasn't drawn content yet. Callers tie it to the tab's
 * load state.
 */
@Composable
fun LoadingSkeleton(
    theme: ModeTheme,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val infinite = rememberInfiniteTransition(label = "skelShimmer")
        val shimmer by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerPos"
        )
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            // Shimmer band sized relative to the real content width —
            // no hardcoded pixel coordinates.
            val contentW = with(androidx.compose.ui.platform.LocalDensity.current) {
                maxWidth.toPx().coerceAtLeast(1f)
            }
            val bandW = contentW * 0.6f
            val x = shimmer * (contentW + bandW) - bandW
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(
                    theme.surfaceVariant,
                    theme.surfaceVariant.copy(alpha = 0.55f),
                    theme.surfaceVariant
                ),
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x + bandW, 0f)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.background)
                    .padding(16.dp)
            ) {
            // Hero block.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(shimmerBrush)
            )
            Spacer(Modifier.height(14.dp))
            // Text lines.
            SkeletonLine(shimmerBrush, 0.72f)
            Spacer(Modifier.height(10.dp))
            SkeletonLine(shimmerBrush, 0.92f)
            Spacer(Modifier.height(10.dp))
            SkeletonLine(shimmerBrush, 0.58f)
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(shimmerBrush)
            )
            }
        }
    }
}

@Composable
private fun SkeletonLine(brush: Brush, fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
    )
}
