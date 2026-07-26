package com.click.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveMusicPlayerDialog(onClose: () -> Unit) {
    var isPlaying by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0.4f) }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(1000)
            progress = (progress + 0.02f).coerceAtMost(1f)
            if (progress >= 1f) progress = 0f
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Premium Audio Player", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Album Art
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Priscilla", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Cyberpunk Neon Track", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))

                // Seek bar
                Slider(value = progress, onValueChange = { progress = it })

                // Track stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1:45", fontSize = 10.sp, color = Color.Gray)
                    Text("4:20", fontSize = 10.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Playback controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(0.2f), CircleShape)
                            .clickable { isPlaying = !isPlaying },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClose) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveVideoPlayerDialog(onClose: () -> Unit) {
    var isPlaying by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1f) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Premium Video Player", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(Color.Black, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        Text("Streaming 1080p @ ${speed}x Speed...", color = Color.Green, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    } else {
                        IconButton(onClick = { isPlaying = true }) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gestures: Swipe left/right to seek", fontSize = 11.sp, color = Color.Gray)
                    Button(
                        onClick = { speed = if (speed == 1f) 1.5f else if (speed == 1.5f) 2f else 1f },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.DarkGray)
                    ) {
                        Text("${speed}x", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClose) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractivePdfReaderDialog(onClose: () -> Unit) {
    var pageNum by remember { mutableStateOf(1) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Built-In PDF Reader", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Prince_Laghari_Portfolio.pdf", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Page $pageNum of 12", color = Color.DarkGray, fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = { if (pageNum > 1) pageNum-- }) {
                        Text("Prev")
                    }
                    Button(onClick = { if (pageNum < 12) pageNum++ }) {
                        Text("Next")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClose) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveImageGalleryDialog(onClose: () -> Unit) {
    var rotation by remember { mutableStateOf(0f) }
    var zoomScale by remember { mutableStateOf(1f) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Image Gallery Viewer", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray)
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            rotationZ = rotation
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Brush.linearGradient(listOf(Color.Red, Color.Magenta)))
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { rotation += 90f }) {
                        Text("Rotate")
                    }
                    Button(onClick = { zoomScale = if (zoomScale == 1f) 1.5f else 1f }) {
                        Text("Zoom")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClose) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveExtensionsDialog(
    adblock: Boolean,
    onToggleAdblock: (Boolean) -> Unit,
    night: Boolean,
    onToggleNight: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Extensions Manager", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🧩 AdBlocker extension", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Switch(checked = adblock, onCheckedChange = onToggleAdblock)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🌙 Dark Mode for websites", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Switch(checked = night, onCheckedChange = onToggleNight)
                }
            }
        },
        confirmButton = {
            Button(onClick = onClose) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveAiHubDialog(onClose: () -> Unit) {
    var textQuery by remember { mutableStateOf("") }
    var chatResponse by remember { mutableStateOf("Ask PK AI any smart questions!") }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Open Source AI Hub", fontWeight = FontWeight.Bold, color = Color(0xFF4FC3FF)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1320))
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Text(chatResponse, color = Color.White, fontSize = 12.sp)
                    }
                }
                OutlinedTextField(
                    value = textQuery,
                    onValueChange = { textQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("How can I help you today?") },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (textQuery.isNotBlank()) {
                                chatResponse = "PK AI response: Click Browser is the world's first 5D Glassmorphism ecosystem developed by Prince Laghari."
                                textQuery = ""
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = onClose) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Standard Click Browser Privacy Policy Agreement.\n\nWe strictly respect and protect user browsing histories, bookmarks, and local data settings. We enforce HTTPS only secure TLS links, DNS level overrides, and offer full incognito modes where zero history caching or tracking occurs.\n\nThis platform complies with international data safety regulations.",
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = onClose) {
                Text("Accept")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAppDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Click Browser Pro v1.0.0", fontWeight = FontWeight.Bold, color = Color.Green) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(" Ecosystem built by: Team PK AI", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(" UI Design Built By: Prince Laghari", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4FC3FF))
                Spacer(modifier = Modifier.height(12.dp))
                Text("The first luxury 5D Glassmorphism Android browser featuring multi-mode engines, video grabs, interactive widgets and Adblock.", fontSize = 11.sp, color = Color.LightGray)
            }
        },
        confirmButton = {
            Button(onClick = onClose) {
                Text("Close")
            }
        }
    )
}
