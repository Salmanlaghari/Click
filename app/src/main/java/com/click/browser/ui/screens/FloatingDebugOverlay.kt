package com.click.browser.ui.screens

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
import kotlinx.coroutines.delay

@Composable
fun FloatingDebugOverlay(
    pageLoadTime: Long,
    modifier: Modifier = Modifier
) {
    // Basic reactive simulated FPS counter
    var fps by remember { mutableStateOf(60) }

    LaunchedEffect(Unit) {
        while (true) {
            fps = (55..60).random()
            delay(500)
        }
    }

    // Basic memory stats
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
