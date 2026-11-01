package com.click.browser.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.ModeTheme

/**
 * A brief, light processing layer shown until the first visible page content
 * commits. The progress value comes directly from WebChromeClient; no synthetic
 * completion percentage is shown. The animation is decorative and can be
 * disabled for reduced-motion settings.
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
    val rotation = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "websiteProcessing")
        val value by transition.animateFloat(
            initialValue = -12f,
            targetValue = 12f,
            animationSpec = infiniteRepeatable(
                animation = tween(1700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "processingTilt"
        )
        value
    } else {
        0f
    }

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
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(104.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = 9.dp, y = 8.dp)
                            .size(66.dp)
                            .graphicsLayer {
                                rotationX = -24f
                                rotationY = rotation * 0.65f
                                cameraDistance = 18.dp.toPx()
                            }
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(theme.secondary.copy(alpha = 0.34f), theme.primary.copy(alpha = 0.12f))
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = (-8).dp, y = (-5).dp)
                            .size(66.dp)
                            .graphicsLayer {
                                rotationX = 20f
                                rotationY = -rotation
                                cameraDistance = 18.dp.toPx()
                            }
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(listOf(theme.primary, theme.secondary))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(theme.surfaceVariant)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Loading website",
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
                if (progress > 0) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0, 100) / 100f },
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
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page resources",
                        color = theme.onSurface.copy(alpha = 0.58f),
                        fontSize = 11.sp
                    )
                    if (progress > 0) {
                        Text(
                            text = "${progress.coerceIn(0, 100)}%",
                            color = theme.onSurface.copy(alpha = 0.68f),
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
