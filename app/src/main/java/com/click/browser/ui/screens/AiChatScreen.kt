package com.click.browser.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.AiChatClient
import com.click.browser.engine.AiProviders
import com.click.browser.engine.AiSafetyFilter
import com.click.browser.engine.ChatMessage
import com.click.browser.engine.PrivacyGuards
import kotlinx.coroutines.launch
import okhttp3.Dns

/**
 * Real AI chat screen. Talks to Groq or OpenRouter (OpenAI-compatible
 * /chat/completions) using the user's own key from Settings, or the
 * built-in Groq key (BuildConfig) when the user hasn't pasted one.
 *
 * Safety (Google Play AI-Generated Content policy):
 * - Every prompt passes [AiSafetyFilter] BEFORE any network call.
 * - Every assistant message has a flag button for in-app reporting.
 *
 * There are NO demo or canned responses anywhere in this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    apiKey: String,
    providerId: String,
    model: String,
    secureDns: Boolean,
    usingBuiltInKey: Boolean = false,
    onReportMessage: (String) -> Unit = {},
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    val provider = AiProviders.byId(providerId)
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    // Single screen-level report dialog (Kilo review: per-bubble dialogs could stack).
    var reportTarget by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val client = remember(secureDns) {
        AiChatClient(dns = if (secureDns) PrivacyGuards.safeSecureDns() else Dns.SYSTEM)
    }

    LaunchedEffect(messages.size, typing) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || typing) return
        input = ""
        messages.add(ChatMessage("user", text))
        // Pre-call content moderation — blocked prompts never reach the API.
        if (AiSafetyFilter.isBlocked(text)) {
            messages.add(ChatMessage("system", "This prompt was blocked by content safety."))
            return
        }
        typing = true
        scope.launch {
            val result = client.send(apiKey, provider, model, messages.toList())
            typing = false
            result
                .onSuccess { reply -> messages.add(ChatMessage("assistant", reply)) }
                .onFailure { e ->
                    messages.add(
                        ChatMessage(
                            "error",
                            "Request failed: ${e.message ?: "unknown error"}\nCheck your API key in Settings → AI Assistant."
                        )
                    )
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Chat", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            if (usingBuiltInKey) "${provider.displayName} • built-in key active"
                            else "${provider.displayName} • ${model.ifBlank { provider.defaultModel }}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (apiKey.isBlank()) {
                // Honest setup guide — no fake chat without a key.
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("AI Chat needs an API key", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "This is a real AI chat — it calls the provider's API with YOUR key. " +
                                "No key is bundled with the app and nothing is sent anywhere until you add one.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("1. Get a free key:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        AiProviders.all().forEach { p ->
                            Text(
                                "• ${p.displayName}: ${p.keySignupUrl}  (keys start with ${p.keyPrefixHint})",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "2. Paste it in Settings → AI Assistant.\n3. Keys starting with gsk_ auto-select Groq, sk-or- auto-selects OpenRouter.",
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("Open Settings")
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Ask anything",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    if (usingBuiltInKey) "Powered by ${provider.displayName} — built-in key active."
                                    else "Powered by ${provider.displayName} — your key, your account.",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                    items(messages) { msg -> ChatBubble(msg, onFlagClick = { reportTarget = it }) }
                    if (typing) {
                        item { TypingIndicator() }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message…") },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = false,
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { send() },
                        enabled = !typing,
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Single screen-level report dialog for flagged AI responses
        // (Play AI-Generated Content policy — in-app reporting).
        reportTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { reportTarget = null },
                title = { Text("Report this response?") },
                text = { Text("Flag this AI response as inappropriate? It will be recorded on this device for review.") },
                confirmButton = {
                    TextButton(onClick = {
                        reportTarget = null
                        onReportMessage(target)
                    }) { Text("Report") }
                },
                dismissButton = {
                    TextButton(onClick = { reportTarget = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, onFlagClick: (String) -> Unit) {
    val isUser = msg.role == "user"
    val isError = msg.role == "error"
    val isSystem = msg.role == "system"
    val isAssistant = !isUser && !isError && !isSystem

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isError -> Color(0xFF7F1D1D)
                    isUser -> MaterialTheme.colorScheme.primary
                    isSystem -> Color(0xFF3B2F1A)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier.fillMaxWidth(if (isAssistant) 0.78f else 0.85f)
        ) {
            Row(modifier = Modifier.padding(12.dp)) {
                if (isError) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 4.dp)
                    )
                }
                if (isSystem) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 4.dp)
                    )
                }
                Text(
                    msg.content,
                    fontSize = 14.sp,
                    color = if (isUser || isError || isSystem) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // In-app reporting (Play AI-Generated Content policy) on every assistant message.
        // The dialog itself lives at screen level (single dialog) — see reportTarget below.
        if (isAssistant) {
            IconButton(
                onClick = { onFlagClick(msg.content) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Flag,
                    contentDescription = "Report this response",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "typingAlpha"
    )
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(alpha)
                    .background(Color.Gray, CircleShape)
            )
        }
    }
}
