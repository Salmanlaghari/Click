package com.click.browser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.ModeTheme

/**
 * Shown when a navigation targets a .onion address (1.8.9 B10, stage 1).
 * Onion services only resolve inside the Tor network, so Click holds the
 * load and offers the Tor route installed on the device — Tor Browser,
 * Orbot — or the way to get one. Mirrors [DirectMediaOfferSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnionOfferSheet(
    theme: ModeTheme,
    url: String,
    torBrowserInstalled: Boolean,
    orbotInstalled: Boolean,
    onOpenTorBrowser: () -> Unit,
    onOpenOrbot: () -> Unit,
    onGetTorApp: () -> Unit,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = theme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    "Onion site (.onion)",
                    color = theme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                url,
                color = theme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                ".onion addresses only exist inside the Tor network, so Click " +
                    "can't open them directly. Open this link in a Tor app instead — " +
                    "your traffic to it stays inside Tor either way.",
                color = theme.onSurface.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(4.dp))
            if (torBrowserInstalled) {
                Button(
                    onClick = onOpenTorBrowser,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Open in Tor Browser")
                }
            }
            if (orbotInstalled) {
                OutlinedButton(
                    onClick = onOpenOrbot,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Security, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Open Orbot (link copied — paste it there)")
                }
            }
            if (!torBrowserInstalled && !orbotInstalled) {
                Button(
                    onClick = onGetTorApp,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Get Orbot — free Tor app")
                }
                Text(
                    "Tor Browser for Android works too — install either app, then tap this link again.",
                    color = theme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            OutlinedButton(
                onClick = onCopyLink,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Copy link")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
