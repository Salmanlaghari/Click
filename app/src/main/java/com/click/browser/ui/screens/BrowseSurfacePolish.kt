package com.click.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.BrowserMode

/**
 * Browse-surface polish (Prince's "Same Website. 3 Beautiful Modes." reference).
 *
 * Official per-mode display names + taglines, used by every mode switcher:
 * - LIGHT MODE — Clean · Fresh · Easy on Eyes
 * - DARK MODE — Modern · Stylish · Premium
 * - OLED BLACK / FUTURE MODE — Pure Black · Neon Glow · Next Level
 */
data class ModeDisplay(val title: String, val tagline: String)

fun BrowserMode.display(): ModeDisplay = when (this) {
    BrowserMode.SIMPLE -> ModeDisplay(
        "LIGHT MODE",
        "Clean · Fresh · Easy on Eyes"
    )
    BrowserMode.DEVELOPER -> ModeDisplay(
        "DARK MODE",
        "Modern · Stylish · Premium"
    )
    BrowserMode.HACK -> ModeDisplay(
        "OLED BLACK / FUTURE MODE",
        "Pure Black · Neon Glow · Next Level"
    )
}

/** Small gradient "C" Click logo mark, reused in the browsing top bar. */
@Composable
fun ClickLogoMark(size: Dp = 30.dp, fontSize: Int = 17) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "C",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = fontSize.sp
        )
    }
}
