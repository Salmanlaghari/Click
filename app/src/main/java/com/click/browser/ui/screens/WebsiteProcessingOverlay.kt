package com.click.browser.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.ModeTheme

/** Return only a real, positive WebChromeClient percentage; zero remains indeterminate. */
internal fun visibleWebsiteProgressPercent(progress: Int): Int? =
    if (progress <= 0) null else progress.coerceIn(0, 100)

/**
 * A brief, light processing layer shown until the first visible page content
 * commits. Progress comes directly from WebChromeClient; no synthetic
 * completion percentage is shown. Decorative motion is disabled when the
 * browser's reduced-motion setting is enabled.
 */
@Composable
fun WebsiteProcessingOverlay(
    theme: ModeTheme,
    site: String,
    progress: Int,
    animationsEnabled: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "websiteProcessing")
    val rotation = if (animationsEnabled) {
        val value by transition.animateFloat(
            initialValue = -14f,
            targetValue = 14f,
            animationSpec = infiniteRepeatable(
                animation = tween(1900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "processingTilt"
        )
        value
    } else {
        0f
    }
    val pulse = if (animationsEnabled) {
        val value by transition.animateFloat(
            initialValue = 0.97f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "processingDepth"
        )
        value
    } else {
        1f
    }
    val progressPercent = visibleWebsiteProgressPercent(progress)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background.copy(alpha = 0.97f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(28.dp),
            color = theme.surface,
            border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.16f)),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(116.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Thin orbital ring adds depth without a shader, bitmap, or extra dependency.
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .graphicsLayer { rotationZ = rotation * 2f }
                            .border(
                                width = 1.dp,
                                brush = Brush.sweepGradient(
                                    listOf(
                                        theme.primary.copy(alpha = 0.05f),
                                        theme.secondary.copy(alpha = 0.58f),
                                        theme.primary.copy(alpha = 0.08f)
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = 10.dp, y = 9.dp)
                            .size(66.dp)
                            .graphicsLayer {
                                rotationX = -25f
                                rotationY = rotation * 0.7f
                                cameraDistance = 22.dp.toPx()
                            }
                            .shadow(10.dp, RoundedCornerShape(20.dp), spotColor = theme.primary)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        theme.secondary.copy(alpha = 0.62f),
                                        theme.primary.copy(alpha = 0.20f)
                                    )
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = (-8).dp, y = (-5).dp)
                            .size(66.dp)
                            .graphicsLayer {
                                rotationX = 18f
                                rotationY = -rotation
                                rotationZ = rotation * 0.12f
                                scaleX = pulse
                                scaleY = pulse
                                cameraDistance = 22.dp.toPx()
                            }
                            .shadow(14.dp, RoundedCornerShape(20.dp), spotColor = theme.primary)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(theme.primary, theme.secondary)))
                            .border(1.dp, Color.White.copy(alpha = 0.58f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.78f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 7.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(theme.surfaceVariant)
                            .border(2.dp, theme.surface, CircleShape)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Loading website",
                    modifier = Modifier.semantics { heading() },
                    color = theme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = site.ifBlank { "Preparing the page" },
                    color = theme.onSurface.copy(alpha = 0.68f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(18.dp))
                if (progressPercent != null) {
                    LinearProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = theme.primary,
                        trackColor = theme.surfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = theme.primary,
                        trackColor = theme.surfaceVariant
                    )
                }
                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page load progress",
                        color = theme.onSurface.copy(alpha = 0.58f),
                        fontSize = 11.sp
                    )
                    progressPercent?.let {
                        Text(
                            text = "$it%",
                            color = theme.onSurface.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onCancel) {
                    Text("Cancel loading", color = theme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
