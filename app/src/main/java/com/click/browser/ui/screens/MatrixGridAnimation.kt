package com.click.browser.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun MatrixGridAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "matrix")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "matrix_progress"
    )

    // Remember coordinates of drops
    val dropsCount = 20
    val drops = remember {
        List(dropsCount) {
            Random.nextFloat() to Random.nextFloat() // x, y speeds
        }
    }

    Canvas(modifier = modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        val width = size.width
        val height = size.height

        // Draw matrix grid / lines
        val lineSpacing = 40.dp.toPx()
        for (x in 0..width.toInt() step lineSpacing.toInt()) {
            drawLine(
                color = Color(0x1F00FF00),
                start = Offset(x.toFloat(), 0f),
                end = Offset(x.toFloat(), height),
                strokeWidth = 1f
            )
        }

        // Draw animated digital rain drops
        drops.forEachIndexed { _, (xRatio, speed) ->
            val x = xRatio * width
            val currentProgress = (progress + speed) % 1f
            val y = currentProgress * height

            // Draw glowing green circles simulating cascading matrix code
            drawCircle(
                color = Color(0xFF00FF00),
                radius = 4f,
                center = Offset(x, y)
            )

            drawCircle(
                color = Color(0xAA00FF00),
                radius = 3f,
                center = Offset(x, y - 20)
            )

            drawCircle(
                color = Color(0x5500FF00),
                radius = 2f,
                center = Offset(x, y - 40)
            )
        }
    }
}
