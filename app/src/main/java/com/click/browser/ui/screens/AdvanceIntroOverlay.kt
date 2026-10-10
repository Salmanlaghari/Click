package com.click.browser.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Click Advance signature moment: full-screen 5-second intro shown when the
 * user enters Advance mode (a new V9 engine boot into the isolated space).
 *
 * Deep-space BLUE-LIGHT identity — deliberately distinct from Hack Mode's
 * blood-red ember look: twinkling starfield, glowing blue grid, rising
 * "CLICK" letters with a blue halo, "ADVANCE" condensing in, tagline
 * "ISOLATED · FAST · FRESH SPACE", and a shield checkmark drawing itself.
 *
 * Pure vector/Compose — no video asset, tiny APK footprint, crisp on every
 * screen. Tap anywhere to skip; auto-dismisses after [durationMs].
 *
 * Mirrors the approved HTML mockup
 * (~/workspace/click-program/advance-intro-mockup.html).
 */
@Composable
fun AdvanceIntroOverlay(
    onDone: () -> Unit,
    durationMs: Long = 5000L,
) {
    // Guard: onDone exactly once (tap + timer can race).
    var done by remember { mutableStateOf(false) }
    fun finish() { if (!done) { done = true; onDone() } }

    // Master timeline 0 -> 1 over the full duration.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMs.toInt().coerceAtLeast(1), easing = LinearEasing))
        finish()
    }
    val t = progress.value

    // Star twinkle driver.
    val infinite = rememberInfiniteTransition(label = "advanceIntro")
    val twinkle by infinite.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "twinkle"
    )

    // Deterministic starfield (same stars every run).
    val stars = remember {
        List(90) { i ->
            val r = Random(i * 4051 + 17)
            Star(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 1.8f, r.nextFloat() * 6.28f)
        }
    }

    val density = LocalDensity.current
    val blue = Color(0xFF38BDF8)      // blue-light primary
    val blueDeep = Color(0xFF0EA5E9)  // deeper blue
    val iceWhite = Color(0xFFE8F4FF)

    // ---- Timeline keyframes (fractions of 5s, from the mockup) ----
    fun seg(start: Float, end: Float): Float =
        ((t - start) / (end - start)).coerceIn(0f, 1f)

    val starsA = seg(0f, 0.06f)
    val gridA = seg(0.06f, 0.30f)
    val advSpacing = 0.62f - 0.47f * seg(0.34f, 0.56f) // 0.62em -> 0.15em
    val advA = seg(0.34f, 0.52f)
    val tagA = seg(0.58f, 0.74f)
    val tagDy = (1f - seg(0.58f, 0.74f)) * 14f
    val shieldP = seg(0.70f, 0.92f)
    val skipA = seg(0.24f, 0.36f)
    val fadeOut = seg(0.90f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF02040A))
            .alpha(1f - fadeOut)
            .clickable { finish() }
    ) {
        // ---- Starfield + blue nebula glow + grid ----
        Canvas(modifier = Modifier.fillMaxSize().alpha(starsA)) {
            val w = size.width
            val h = size.height
            // Nebula glow, upper-middle.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        blueDeep.copy(alpha = 0.35f),
                        blueDeep.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(w / 2f, h * 0.40f),
                    radius = w * 0.75f
                ),
                radius = w * 0.75f,
                center = Offset(w / 2f, h * 0.40f)
            )
            // Stars with twinkle.
            for (s in stars) {
                val a = (0.35f + 0.65f * (0.5f + 0.5f * sin(twinkle + s.phase))).coerceIn(0f, 1f)
                drawCircle(
                    color = iceWhite.copy(alpha = a * 0.9f),
                    radius = s.size,
                    center = Offset(s.x * w, s.y * h)
                )
            }
            // Faint blue grid, masked toward the center by the nebula.
            if (gridA > 0f) {
                val step = 44.dp.toPx()
                val gridColor = blue.copy(alpha = 0.13f * gridA)
                var x = 0f
                while (x <= w) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                    x += step
                }
                var y = 0f
                while (y <= h) {
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                    y += step
                }
            }
            // Vignette.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                    center = Offset(w / 2f, h * 0.45f),
                    radius = w * 0.8f
                ),
                size = size
            )
        }

        // ---- Center stack: CLICK letters / ADVANCE / tagline / shield ----
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // "CLICK" — letters rise in staggered (mockup 0.7s–1.9s).
            Row {
                "CLICK".forEachIndexed { i, ch ->
                    val lp = seg(0.14f + i * 0.024f, 0.26f + i * 0.024f)
                    val dy = with(density) { ((1f - lp) * 34).dp }
                    Text(
                        text = ch.toString(),
                        color = Color.White.copy(alpha = lp),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.ExtraBold,
                        style = TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = blue.copy(alpha = 0.9f * lp),
                                offset = Offset(0f, 0f),
                                blurRadius = 24f * lp
                            )
                        ),
                        modifier = Modifier.offset(y = dy)
                    )
                }
            }
            // "ADVANCE" — letter-spacing condenses (mockup 1.7s–2.8s).
            Text(
                text = "ADVANCE",
                color = blue.copy(alpha = advA),
                fontSize = 26.sp,
                fontWeight = FontWeight.Light,
                style = TextStyle(letterSpacing = advSpacing.em),
                textAlign = TextAlign.Center
            )
            // Tagline (mockup 2.9s–3.7s).
            Text(
                text = "ISOLATED  ·  FAST  ·  FRESH SPACE",
                color = Color(0xFF9FB3C8).copy(alpha = tagA),
                fontSize = 11.sp,
                letterSpacing = 0.25.em,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(y = with(density) { tagDy.dp })
                    .alpha(tagA)
            )
            // Shield checkmark drawing itself (mockup 3.5s–4.7s).
            Canvas(
                modifier = Modifier
                    .size(72.dp)
                    .alpha(if (shieldP > 0f) 1f else 0f)
            ) {
                val s = 72.dp.toPx()
                val scale = s / 72f
                fun X(v: Float) = v * scale
                // Shield outline path.
                val shield = Path().apply {
                    moveTo(X(36f), X(6f))
                    lineTo(X(60f), X(16f))
                    lineTo(X(60f), X(34f))
                    cubicTo(X(60f), X(50f), X(48f), X(60f), X(36f), X(66f))
                    cubicTo(X(24f), X(60f), X(12f), X(50f), X(12f), X(34f))
                    lineTo(X(12f), X(16f))
                    close()
                }
                val check = Path().apply {
                    moveTo(X(28f), X(35f))
                    lineTo(X(34f), X(41f))
                    lineTo(X(45f), X(29f))
                }
                val pm = PathMeasure()
                val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                pm.setPath(shield, false)
                val segPath = Path()
                pm.getSegment(0f, pm.length * shieldP, segPath, true)
                drawPath(segPath, blueDeep, style = stroke)
                if (shieldP > 0.55f) {
                    val cp = ((shieldP - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    pm.setPath(check, false)
                    val cseg = Path()
                    pm.getSegment(0f, pm.length * cp, cseg, true)
                    drawPath(cseg, Color(0xFF7DD3FC), style = stroke)
                }
            }
        }

        // ---- Tap-to-skip hint ----
        Text(
            text = "TAP TO SKIP",
            color = Color(0xFF5B6B7A).copy(alpha = skipA),
            fontSize = 11.sp,
            letterSpacing = 0.2.em,
            modifier = Modifier.align(Alignment.BottomCenter)
                .offset(y = (-26).dp)
        )

        // ---- Progress bar ----
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width * t
            drawRect(
                brush = Brush.horizontalGradient(listOf(blueDeep, blue)),
                topLeft = Offset(0f, size.height - 3.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(w, 3.dp.toPx())
            )
        }
    }
}

private data class Star(val x: Float, val y: Float, val size: Float, val phase: Float)
