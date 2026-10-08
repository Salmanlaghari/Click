package com.click.browser.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
 *
 * Premium upgrades: bouncing-dot typing indicator, voice input (mic button),
 * suggestion chips, and animated message arrivals. The safety contract is
 * untouched: AiSafetyFilter pre-call moderation, the per-message report
 * button, and key precedence all behave exactly as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    apiKey: String,
    providerId: String,
    model: String,
    secureDns: Boolean,
    usingBuiltInKey: Boolean = false,
    /** Current browser page — used to build the "Summarize this page" prompt. */
    pageTitle: String = "",
    pageUrl: String = "",
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

    /** Sends [prompt] as the user message. Runs the unchanged safety pipeline. */
    fun sendMessage(prompt: String) {
        val text = prompt.trim()
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

    fun send() = sendMessage(input)

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
                    // Append-only list: index keys are stable, and each new
                    // message slides/fades in on arrival.
                    items(messages.size, key = { it }) { index ->
                        val msg = messages[index]
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(280)) +
                                slideInVertically(animationSpec = tween(280)) { it / 4 }
                        ) {
                            ChatBubble(msg, onFlagClick = { reportTarget = it })
                        }
                    }
                    if (typing) {
                        item { TypingIndicator() }
                    }
                }

                // Suggestion chips — shown before the conversation starts.
                if (messages.isEmpty() && !typing) {
                    SuggestionChipsRow(
                        pageTitle = pageTitle,
                        pageUrl = pageUrl,
                        onChipClick = { prompt -> sendMessage(prompt) }
                    )
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
                    MicButton(
                        enabled = !typing,
                        onHeard = { heard ->
                            input = (input.trim() + " " + heard).trim()
                        }
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

/**
 * Premium typing indicator: three dots bouncing in a wave while the
 * provider response is on its way.
 */
@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typingDots")
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val bounce by transition.animateFloat(
                initialValue = 0f,
                targetValue = -9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(380, delayMillis = i * 140),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotBounce$i"
            )
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .offset(y = bounce.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

/** One suggestion chip: visible label + how to build the prompt it sends. */
private data class SuggestionChip(
    val label: String,
    val buildPrompt: (pageTitle: String, pageUrl: String) -> String
)

private val SUGGESTION_CHIPS = listOf(
    SuggestionChip("Summarize this page") { title, url ->
        buildString {
            append("Please summarize this page for me.")
            if (title.isNotBlank() && title != "New Tab") append("\n\nPage title: ").append(title)
            if (url.isNotBlank() && url != "about:blank") append("\nPage URL: ").append(url)
        }
    },
    SuggestionChip("Explain simply") { _, _ -> "Explain simply:" },
    SuggestionChip("Translate to Urdu") { _, _ -> "Translate to Urdu:" },
    SuggestionChip("Key points") { _, _ -> "List the key points:" }
)

/** Horizontal scroll of suggestion chips above the input bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionChipsRow(
    pageTitle: String,
    pageUrl: String,
    onChipClick: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SUGGESTION_CHIPS.size, key = { it }) { index ->
            val chip = SUGGESTION_CHIPS[index]
            FilterChip(
                selected = false,
                onClick = { onChipClick(chip.buildPrompt(pageTitle, pageUrl)) },
                label = { Text(chip.label, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
        }
    }
}

/**
 * Mic button for voice input. Uses Android's [SpeechRecognizer] to transcribe
 * speech into the chat text field. RECORD_AUDIO is requested at runtime;
 * when the system recommends it, a rationale dialog explains the usage
 * first (mic is only used for transcription — nothing is recorded or
 * uploaded by the browser itself).
 */
@Composable
private fun MicButton(
    enabled: Boolean,
    onHeard: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var listening by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun stopListening() {
        listening = false
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(
                context,
                "Speech recognition isn't available on this device.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(
            VoiceInputListener(
                onResult = { heard -> onHeard(heard) },
                onEnd = { stopListening() }
            )
        )
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            r.startListening(intent)
            recognizer = r
            listening = true
        } catch (_: Exception) {
            stopListening()
            Toast.makeText(context, "Couldn't start voice input.", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            Toast.makeText(
                context,
                "Microphone permission denied — voice input is off.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun onMicClick() {
        if (listening) {
            // Tap again to stop: the listener's onResults/onError fires → onEnd.
            try {
                recognizer?.stopListening()
            } catch (_: Exception) {
                stopListening()
            }
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startListening()
        } else if (activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.RECORD_AUDIO
            )
        ) {
            showRationale = true
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopListening() }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Microphone access") },
            text = {
                Text(
                    "Voice input uses your microphone only to turn speech into text " +
                        "for the chat box. Nothing is recorded or uploaded by Click Browser " +
                        "itself — transcription is done by your device's speech service."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Not now") }
            }
        )
    }

    // Gentle pulse while listening so the state is obvious.
    val pulse = rememberInfiniteTransition(label = "micPulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 0.45f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micAlpha"
    )

    IconButton(
        onClick = { onMicClick() },
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .alpha(if (listening) pulseAlpha else 1f)
            .background(
                if (listening) Color(0xFFDC2626)
                else MaterialTheme.colorScheme.surfaceVariant,
                CircleShape
            )
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = if (listening) "Stop listening" else "Voice input",
            tint = if (listening) Color.White else MaterialTheme.colorScheme.primary
        )
    }
}
