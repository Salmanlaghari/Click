package com.click.browser.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.click.browser.engine.AiChatClient
import com.click.browser.engine.AiPageContextPolicy
import com.click.browser.engine.AiProviders
import com.click.browser.engine.AiRolePresets
import com.click.browser.engine.AiSafetyFilter
import com.click.browser.engine.ChatMessage
import com.click.browser.engine.PrivacyGuards
import kotlinx.coroutines.launch
import okhttp3.Dns
import java.util.Locale

/**
 * Real AI chat screen supporting Google Gemini (with optional Google Search
 * Grounding citations), Groq, and OpenRouter, plus Role Presets, active page
 * context summarization, voice input, and TTS read-aloud.
 *
 * Safety (Google Play AI-Generated Content policy):
 * - Every prompt passes [AiSafetyFilter] BEFORE any network call.
 * - Every assistant message has a flag button for in-app reporting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    apiKey: String,
    providerId: String,
    model: String,
    secureDns: Boolean,
    usingBuiltInKey: Boolean = false,
    pageTitle: String = "",
    pageUrl: String = "",
    pageContextIdentity: String = "",
    canSharePageContext: Boolean = false,
    onRequestPageContext: (onCaptured: (String) -> Unit) -> Unit = { onCaptured -> onCaptured("") },
    onOpenUrl: (String) -> Unit = {},
    onReportMessage: (String) -> Unit = {},
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val effectiveProviderId = if (apiKey.startsWith("AIza")) "gemini" else providerId
    val provider = AiProviders.byId(effectiveProviderId)
    val externalProviderName = AiPageContextPolicy.externalProviderName(provider.id)
    val isGemini = provider.id == "gemini"
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf("browser") }
    var searchGroundingEnabled by remember { mutableStateOf(true) }
    var reportTarget by remember { mutableStateOf<String?>(null) }
    var speakingText by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val pageContextKey = "${provider.id}|$pageContextIdentity|$pageTitle|$pageUrl|$canSharePageContext|$searchGroundingEnabled"
    val latestPageContextKey = rememberUpdatedState(pageContextKey)
    var pageContextApproved by remember(pageContextKey) { mutableStateOf(false) }
    var pageContextLoading by remember(pageContextKey) { mutableStateOf(false) }
    var pageExcerpt by remember(pageContextKey) { mutableStateOf("") }
    var showPageContextConsent by remember(pageContextKey) { mutableStateOf(false) }
    var pendingPagePrompt by remember(pageContextKey) { mutableStateOf<String?>(null) }
    var screenActive by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { screenActive = false }
    }

    val client = remember(secureDns) {
        AiChatClient(dns = if (secureDns) PrivacyGuards.safeSecureDns() else Dns.SYSTEM)
    }

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engineRef: TextToSpeech? = null
        engineRef = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engineRef?.language = Locale.getDefault()
            }
        }
        ttsEngine = engineRef
        onDispose {
            try {
                engineRef?.stop()
                engineRef?.shutdown()
            } catch (_: Exception) {
            }
            ttsEngine = null
        }
    }

    fun toggleSpeak(text: String) {
        val engine = ttsEngine ?: return
        if (speakingText == text) {
            engine.stop()
            speakingText = null
        } else {
            speakingText = text
            engine.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "click_ai_tts")
        }
    }

    fun copyReply(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("Click AI Reply", text))
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(messages.size, typing) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun sendMessage(prompt: String) {
        val text = prompt.trim()
        if (text.isEmpty() || typing || pageContextLoading) return
        input = ""
        messages.add(ChatMessage("user", text))
        if (AiSafetyFilter.isBlocked(text)) {
            messages.add(ChatMessage("system", "This prompt was blocked by content safety."))
            return
        }
        typing = true
        val sysInstruction = AiPageContextPolicy.systemInstruction(
            roleId = selectedRole,
            consentGranted = pageContextApproved && canSharePageContext,
            pageTitle = pageTitle,
            pageUrl = pageUrl,
            pageExcerpt = pageExcerpt
        )
        scope.launch {
            val result = client.sendRich(
                apiKey = apiKey,
                provider = provider,
                model = model,
                history = messages.toList(),
                systemInstruction = sysInstruction,
                enableGoogleSearch = isGemini && searchGroundingEnabled
            )
            typing = false
            result
                .onSuccess { reply ->
                    messages.add(
                        ChatMessage(
                            role = "assistant",
                            content = reply.text,
                            sources = reply.sources
                        )
                    )
                }
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

    fun requestPageAwarePrompt(prompt: String) {
        if (!canSharePageContext || apiKey.isBlank()) return
        if (pageContextApproved) {
            sendMessage(prompt)
        } else {
            pendingPagePrompt = prompt
            showPageContextConsent = true
        }
    }

    fun capturePageAfterConsent() {
        val requestKey = pageContextKey
        pageContextApproved = true
        pageContextLoading = true
        onRequestPageContext { captured ->
            if (!screenActive || latestPageContextKey.value != requestKey) return@onRequestPageContext
            pageExcerpt = captured.take(AiPageContextPolicy.MAX_EXCERPT_CHARS)
            pageContextLoading = false
            pendingPagePrompt?.let { input = it }
            pendingPagePrompt = null
        }
    }

    fun send() = sendMessage(input)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Click AI Assistant", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            buildString {
                                append(provider.displayName)
                                append(" • ")
                                if (usingBuiltInKey) append("built-in key active")
                                else append(model.ifBlank { provider.defaultModel })
                            },
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = {
                            ttsEngine?.stop()
                            speakingText = null
                            messages.clear()
                        }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear conversation")
                        }
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
                            "This is a real AI assistant — it calls Google Gemini, Groq, or OpenRouter with YOUR key. " +
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
                            "2. Paste it in Settings → AI Assistant.\n3. Keys starting with AIza auto-select Google Gemini, gsk_ auto-selects Groq, and sk-or- auto-selects OpenRouter.",
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("Open Settings")
                        }
                    }
                }
            } else {
                // Role Presets + Google Search Grounding toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AiRolePresets.ALL.forEach { preset ->
                        FilterChip(
                            selected = selectedRole == preset.id,
                            onClick = { selectedRole = preset.id },
                            label = { Text(preset.label, fontSize = 11.sp) }
                        )
                    }
                    if (isGemini) {
                        FilterChip(
                            selected = searchGroundingEnabled,
                            onClick = { searchGroundingEnabled = !searchGroundingEnabled },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            label = {
                                Text(
                                    if (searchGroundingEnabled) "Google Search ON" else "Google Search OFF",
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }
                }

                if (pageUrl.isNotBlank() && pageUrl != "about:blank") {
                    if (canSharePageContext && apiKey.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (pageContextApproved) {
                                        if (pageContextLoading) "Reading page for $externalProviderName…" else "Page sharing is ON with $externalProviderName."
                                    } else {
                                        "Page context is OFF. Nothing from this page is sent."
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (pageContextApproved) {
                                        "Title, URL and up to 3,000 visible characters accompany AI requests until this chat closes."
                                    } else {
                                        "Click will not read page text unless you allow sharing."
                                    },
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                            if (!pageContextApproved && !pageContextLoading) {
                                TextButton(onClick = { showPageContextConsent = true }) {
                                    Text("Share page")
                                }
                            }
                        }
                    } else {
                        Text(
                            text = when {
                                !canSharePageContext -> "Page context sharing is unavailable for this page or tab; AI requests will not include its details."
                                apiKey.isBlank() -> "Add an AI provider key before enabling optional page context. No page text is read or sent."
                                else -> "Page context sharing is off."
                            },
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                        )
                    }
                }

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
                                    .padding(top = 36.dp),
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
                    items(messages.size, key = { it }) { index ->
                        val msg = messages[index]
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(280)) +
                                slideInVertically(animationSpec = tween(280)) { it / 4 }
                        ) {
                            ChatBubble(
                                msg = msg,
                                isSpeaking = speakingText == msg.content,
                                onSpeakClick = { toggleSpeak(msg.content) },
                                onCopyClick = { copyReply(msg.content) },
                                onSourceClick = { uri -> onOpenUrl(uri) },
                                onFlagClick = { reportTarget = it }
                            )
                        }
                    }
                    if (typing) {
                        item { TypingIndicator() }
                    }
                }

                if (messages.isEmpty() && !typing && canSharePageContext && apiKey.isNotBlank()) {
                    SuggestionChipsRow(
                        onChipClick = { prompt -> requestPageAwarePrompt(prompt) }
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
                        placeholder = { Text("Ask about this page or the web…") },
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

        if (showPageContextConsent && canSharePageContext && apiKey.isNotBlank()) {
            AlertDialog(
                onDismissRequest = {
                    showPageContextConsent = false
                    pendingPagePrompt = null
                },
                title = { Text("Share this page with $externalProviderName?") },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            AiPageContextPolicy.consentDisclosure(
                                providerName = externalProviderName,
                                includeGoogleSearchGrounding = isGemini && searchGroundingEnabled
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Page title", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(pageTitle.take(AiPageContextPolicy.MAX_TITLE_CHARS).ifBlank { "(untitled)" }, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Page URL", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(pageUrl.take(AiPageContextPolicy.MAX_URL_CHARS), fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showPageContextConsent = false
                        capturePageAfterConsent()
                    }) { Text("Allow for this chat") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showPageContextConsent = false
                        pendingPagePrompt = null
                    }) { Text("Not now") }
                }
            )
        }

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
private fun ChatBubble(
    msg: ChatMessage,
    isSpeaking: Boolean,
    onSpeakClick: () -> Unit,
    onCopyClick: () -> Unit,
    onSourceClick: (String) -> Unit,
    onFlagClick: (String) -> Unit
) {
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
            modifier = Modifier.fillMaxWidth(if (isAssistant) 0.84f else 0.85f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row {
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

                if (isAssistant && msg.sources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Google Search Sources",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                    msg.sources.forEach { src ->
                        Text(
                            text = "↗ ${src.title}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSourceClick(src.uri) }
                                .padding(vertical = 2.dp)
                        )
                    }
                }

                if (isAssistant) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onSpeakClick, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isSpeaking) "Stop reading" else "Read aloud",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onCopyClick, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy response",
                                tint = Color.Gray,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        IconButton(onClick = { onFlagClick(msg.content) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = "Report this response",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

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

private data class SuggestionChip(
    val label: String,
    val prompt: String
)

private val SUGGESTION_CHIPS = listOf(
    SuggestionChip("Summarize this page", "Please summarize this page in 4 concise bullet points."),
    SuggestionChip("Translate summary to Urdu", "Summarize this page in easy Urdu (اردو) and Roman Urdu."),
    SuggestionChip("Explain simply", "Explain the main topic on this page in simple everyday words."),
    SuggestionChip("Key takeaways", "List the top key takeaways and verify important facts.")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionChipsRow(
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
                onClick = { onChipClick(chip.prompt) },
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
