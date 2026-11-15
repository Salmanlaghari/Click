package com.click.browser.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.click.browser.engine.ModeTheme

/** One full drift of the ambient gradient; slow on purpose (Gemini-ambient feel). */
internal const val AMBIENT_GRADIENT_PERIOD_MS = 14_000

/** Tint strength of the drifting washes over the mode background. Kept low so text stays crisp. */
internal const val AMBIENT_TINT_ALPHA = 0.12f

/**
 * The drifting wash stops: the mode's primary and secondary at
 * [AMBIENT_TINT_ALPHA], separated by a transparent middle so the base
 * background always dominates and content legibility never changes.
 */
internal fun ambientGradientStops(theme: ModeTheme): List<Color> = listOf(
    theme.primary.copy(alpha = AMBIENT_TINT_ALPHA),
    Color.Transparent,
    theme.secondary.copy(alpha = AMBIENT_TINT_ALPHA)
)

/**
 * Gemini-style ambient background for Click's OWN screens (Prince, 15 Nov
 * 2026: "Full page per halka halka rang badlao hoon"): the mode background
 * with a very soft primary/secondary wash that slowly drifts across the
 * whole surface on a ~14s reverse loop.
 *
 * Draw-phase only: the animated value is read inside [drawBehind], so the
 * drift never recomposes content — in particular it never touches the
 * GeckoView/WebView child composables. Callers gate where it runs (home
 * dashboards always; the web-page backdrop only until content commits), so
 * no animation loop runs invisibly under a fully painted web page.
 * A website's own rendered page is never affected — this paints Click's UI
 * background only.
 */
@Composable
internal fun Modifier.ambientGradientBackground(theme: ModeTheme): Modifier {
    val transition = rememberInfiniteTransition(label = "clickAmbientGradient")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(AMBIENT_GRADIENT_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "clickAmbientDrift"
    )
    val base = theme.background
    val stops = ambientGradientStops(theme)
    return drawBehind {
        drawRect(color = base)
        val width = size.width
        val height = size.height
        drawRect(
            brush = Brush.linearGradient(
                colors = stops,
                start = Offset(
                    x = -0.25f * width + 0.5f * width * drift,
                    y = -0.1f * height
                ),
                end = Offset(
                    x = 0.75f * width + 0.5f * width * drift,
                    y = 1.1f * height
                )
            )
        )
    }
}
