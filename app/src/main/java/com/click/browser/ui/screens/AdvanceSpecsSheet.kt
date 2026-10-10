package com.click.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "About Advance Mode" - polished specifications sheet showing what makes
 * Click Advance special. Opened from the info button next to the Advance
 * entry in the mode switcher (and from Settings - Modes).
 *
 * Honest copy: Advance is a fully-isolated, performance-tuned profile - not a
 * separate engine. The renderer is still the system WebView (Chromium).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvanceSpecsSheet(
    onClose: () -> Unit,
    onEnterAdvance: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val blue = Color(0xFF38BDF8)
    val blueDeep = Color(0xFF0EA5E9)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = Color(0xFF0A0F1A),
        contentColor = Color(0xFFE8F4FF),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0C1B2E), Color(0xFF123A5C), Color(0xFF0C1B2E))
                        )
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(blueDeep, blue))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.RocketLaunch,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Click Advance",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        Text(
                            "Isolated - Fast - Fresh Space",
                            color = blue.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF9FB3C8))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "What makes Advance special",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(10.dp))

            AdvanceSpecRow(
                icon = Icons.Filled.Lock,
                title = "Fully isolated space",
                body = "Own WebView data directory - cookies, cache and localStorage are completely separate from Simple, Developer and Hack.",
                accent = blue
            )
            AdvanceSpecRow(
                icon = Icons.Filled.FolderSpecial,
                title = "Own data profile",
                body = "Bookmarks, history, saved passwords and userscripts start empty. Nothing carries over from your other modes.",
                accent = blue
            )
            AdvanceSpecRow(
                icon = Icons.Filled.Bolt,
                title = "Performance-tuned",
                body = "Desktop-class profile settings for speed: aggressive caching, full rendering pipeline, smooth scrolling.",
                accent = blue
            )
            AdvanceSpecRow(
                icon = Icons.Filled.Language,
                title = "Full web support",
                body = "Complete JavaScript and HTML5 - every modern site works, with desktop-mode rendering available.",
                accent = blue
            )
            AdvanceSpecRow(
                icon = Icons.Filled.CheckCircle,
                title = "Fresh start, every time",
                body = "Switching to Advance never brings tabs or logins with it. Your private space stays private.",
                accent = blue
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "Honest note: Advance uses the same system WebView (Chromium) renderer as the other modes - what changes is the isolated, performance-tuned profile around it.",
                color = Color(0xFF7D8B99),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onEnterAdvance,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = blueDeep,
                    contentColor = Color.White
                )
            ) {
                Text("Enter Advance Mode", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AdvanceSpecRow(
    icon: ImageVector,
    title: String,
    body: String,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(body, color = Color(0xFF9FB3C8), fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}
