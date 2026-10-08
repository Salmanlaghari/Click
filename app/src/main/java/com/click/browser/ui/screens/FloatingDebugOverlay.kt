package com.click.browser.ui.screens

import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FloatingDebugOverlay(
    pageLoadTime: Long,
    modifier: Modifier = Modifier
) {
    // Real FPS measured with Choreographer frame callbacks (frames per last second)
    var fps by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val choreographer = Choreographer.getInstance()
        var frames = 0
        var windowStartNanos = System.nanoTime()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frames++
                val elapsedSec = (frameTimeNanos - windowStartNanos) / 1_000_000_000.0
                if (elapsedSec >= 1.0) {
                    fps = (frames / elapsedSec).toInt()
                    frames = 0
                    windowStartNanos = frameTimeNanos
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose { choreographer.removeFrameCallback(callback) }
    }

    // Real memory stats for this process
    val runtime = Runtime.getRuntime()
    val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

    Box(
        modifier = modifier
            .background(Color(0xCC000000), shape = RoundedCornerShape(8.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text("FPS: $fps", color = Color.Green, fontSize = 11.sp)
            Text("Load Time: ${pageLoadTime}ms", color = Color.Yellow, fontSize = 11.sp)
            Text("RAM: ${usedMemory}MB", color = Color.Cyan, fontSize = 11.sp)
        }
    }
}
