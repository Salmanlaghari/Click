package com.click.browser.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.ModeTheme

/**
 * Long-press context menu for WebView content (links + images).
 *
 * Shown when the user long-presses a link or an image on a web page.
 * Every item actually works — no dead buttons:
 * - Link: Open in new tab / Copy link / Share link
 * - Image: Save image (Downloads) / Copy image URL / Share image / Open image in new tab
 * - Linked image (anchor wrapping an <img>): both sections are shown.
 *
 * Works in all browser modes (Simple / Developer / Hack / Advance) because the
 * listener is attached at WebView creation in MainActivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongPressMenuSheet(
    theme: ModeTheme,
    linkUrl: String?,
    imageUrl: String?,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
    onSaveImage: (String) -> Unit,
    onCopyImageUrl: (String) -> Unit,
    onShareImage: (String) -> Unit,
    onAddToPlaylist: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = theme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header: what was long-pressed.
            val headerUrl = imageUrl ?: linkUrl ?: ""
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (imageUrl != null) Icons.Default.Image else Icons.Default.Link,
                    contentDescription = null,
                    tint = theme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = headerUrl,
                    fontSize = 13.sp,
                    color = theme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            LongPressMenuDivider(theme)

            // Image section (shown when an image was long-pressed).
            if (imageUrl != null) {
                Text(
                    text = "Image",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                LongPressMenuRow(
                    theme = theme,
                    icon = Icons.Default.Download,
                    label = "Save image",
                    onClick = { onSaveImage(imageUrl) }
                )
                LongPressMenuRow(
                    theme = theme,
                    icon = Icons.Default.ContentCopy,
                    label = "Copy image URL",
                    onClick = { onCopyImageUrl(imageUrl) }
                )
                LongPressMenuRow(
                    theme = theme,
                    icon = Icons.Default.Share,
                    label = "Share image",
                    onClick = { onShareImage(imageUrl) }
                )
                if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                    LongPressMenuRow(
                        theme = theme,
                        icon = Icons.Default.OpenInNew,
                        label = "Open image in new tab",
                        onClick = { onOpenInNewTab(imageUrl) }
                    )
                }
                if (linkUrl != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LongPressMenuDivider(theme)
                }
            }

            // Link section (shown when a link was long-pressed, or the image is wrapped in a link).
            if (linkUrl != null && linkUrl != imageUrl) {
                if (imageUrl != null) {
                    Text(
                        text = "Link",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
                if (linkUrl.startsWith("http://") || linkUrl.startsWith("https://")) {
                    LongPressMenuRow(
                        theme = theme,
                        icon = Icons.Default.OpenInNew,
                        label = "Open in new tab",
                        onClick = { onOpenInNewTab(linkUrl) }
                    )
                }
                LongPressMenuRow(
                    theme = theme,
                    icon = Icons.Default.ContentCopy,
                    label = "Copy link",
                    onClick = { onCopyLink(linkUrl) }
                )
                LongPressMenuRow(
                    theme = theme,
                    icon = Icons.Default.Share,
                    label = "Share link",
                    onClick = { onShareLink(linkUrl) }
                )
                // Only shown for direct media file links — never for
                // streaming-service pages (Playlist refuses those).
                if (onAddToPlaylist != null) {
                    LongPressMenuRow(
                        theme = theme,
                        icon = Icons.Default.QueueMusic,
                        label = "Add to Playlist",
                        onClick = { onAddToPlaylist(linkUrl) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LongPressMenuRow(
    theme: ModeTheme,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = theme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            color = theme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LongPressMenuDivider(theme: ModeTheme) {
    Divider(
        color = theme.onSurface.copy(alpha = 0.12f),
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = 6.dp, horizontal = 16.dp)
    )
}
