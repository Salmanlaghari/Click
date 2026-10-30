package com.click.browser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.ModeTheme

/** Explains Click's deny-only geolocation behavior without offering a false Allow action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSettingsSheet(
    theme: ModeTheme,
    onClose: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = theme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Website location", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.onSurface)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.onSurface)
                }
            }
            Icon(Icons.Default.LocationOff, contentDescription = null, tint = theme.primary)
            Text(
                "Click does not request Android device-location permission. Website geolocation requests are blocked, and Click never sends substitute coordinates.",
                fontSize = 13.sp,
                color = theme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
