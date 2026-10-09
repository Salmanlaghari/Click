package com.click.browser.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hack Mode signature moment: full-screen 5-second dramatic intro shown when
 * the user switches to Hack Mode (a new V9 engine boot).
 *
 * "Revenge type" aggressive feel: blood-red pulse, glitch slices, rising
 * embers, flared markhor horns, and the TEAM PK AI ERA mark.
 *
 * Pure vector/Compose — no video asset, no licensing issues, tiny footprint.
 * Tap anywhere to skip; auto-dismisses after [durationMs].
 */
@Composable
fun HackIntroOverlay(
    onDone: () -> Unit,
    durationMs: Long = 5000L,
) {
    LaunchedEffect(Unit) {
        delay(durationMs)
        onDone()
    }

    val infinite = rememberInfiniteTransition(label = "hackIntro")
    val pulse by infinite.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val glitchT by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(240), repeatMode = RepeatMode.Restart),
        label = "glitch"
    )
    // Continuous 6s cycle for rising embers (independent of the glitch timer).
    val emberT by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(6000), repeatMode = RepeatMode.Restart),
        label = "embers"
    )
    // Deterministic ember particles.
    val embers = remember {
        List(26) { i ->
            val r = Random(i * 7919)
            Ember(r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat() * 1.6f, 3f + r.nextFloat() * 7f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDone() }
    ) {
        // Blood-red radial pulse behind everything.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.42f
            val radius = size.width * (0.55f + 0.25f * pulse)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFB91C1C).copy(alpha = 0.55f * pulse),
                        Color(0xFF7F1D1D).copy(alpha = 0.28f * pulse),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = radius
                ),
                radius = radius,
                center = Offset(cx, cy)
            )
            // Vignette.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                    center = Offset(cx, cy),
                    radius = size.width * 0.75f
                ),
                size = size
            )
            // Glitch slice bars.
            val g = (glitchT * 997).toInt()
            if (g % 7 < 2) {
                val y = (g * 37 % size.height.toInt()).toFloat()
                drawRect(
                    color = Color(0xFFEF4444).copy(alpha = 0.16f),
                    topLeft = Offset(0f, y),
                    size = androidx.compose.ui.geometry.Size(size.width, 14f)
                )
                drawRect(
                    color = Color.Cyan.copy(alpha = 0.10f),
                    topLeft = Offset(0f, y + 22f),
                    size = androidx.compose.ui.geometry.Size(size.width, 6f)
                )
            }
            // Rising embers (continuous rise, wraps around).
            for (e in embers) {
                val rise = ((emberT * e.speed + e.yOff) % 1.2f) * size.height
                val x = e.xOff * size.width + sin((emberT * 40f + e.xOff * 9f).toDouble()).toFloat() * 24f
                val y = size.height - rise + size.height * 0.1f
                drawCircle(
                    color = Color(0xFFF97316).copy(alpha = 0.75f),
                    radius = e.r,
                    center = Offset(x, y)
                )
            }
        }

        // Flared markhor horns (stylized, symmetric).
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .align(Alignment.Center)
        ) {
            val cx = size.width / 2f
            val baseY = size.height * 0.92f
            fun horn(mirror: Float) {
                val path = Path().apply {
                    moveTo(cx + mirror * 40f, baseY)
                    // Flare outward then curl — markhor-like sweep.
                    cubicTo(
                        cx + mirror * 150f, baseY - 60f,
                        cx + mirror * 190f, baseY - 220f,
                        cx + mirror * 120f, baseY - 330f
                    )
                }
                drawPath(
                    path, color = Color(0xFF1F1F23),
                    style = Stroke(width = 26f, cap = StrokeCap.Round)
                )
                drawPath(
                    path, color = Color(0xFFEF4444).copy(alpha = 0.9f),
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
                // Ridges along the horn.
                for (i in 1..4) {
                    val f = i / 5f
                    val px = cx + mirror * (40f + f * 130f * (1 - f * 0.35f))
                    val py = baseY - f * 300f
                    drawLine(
                        color = Color(0xFFEF4444).copy(alpha = 0.55f),
                        start = Offset(px - mirror * 22f, py),
                        end = Offset(px + mirror * 22f, py - 12f),
                        strokeWidth = 4f
                    )
                }
            }
            horn(1f); horn(-1f)
        }

        // Glitching HACK MODE headline.
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val g = (glitchT * 997).toInt()
            val xOff = if (g % 11 < 2) ((g % 5) - 2) * 6f else 0f
            Text(
                text = "HACK MODE",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                modifier = Modifier.graphicsLayer { translationX = xOff }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "V9 ENGINE ENGAGED",
                color = Color(0xFFEF4444),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
        }

        // TEAM PK AI ERA mark at the bottom.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TEAM PK AI ERA",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 5.sp,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = Shadow(
                        color = Color(0xFFEF4444),
                        offset = Offset(0f, 0f),
                        blurRadius = 18f
                    )
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "tap to skip",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp
            )
        }
    }
}

private data class Ember(val xOff: Float, val yOff: Float, val speed: Float, val r: Float)
