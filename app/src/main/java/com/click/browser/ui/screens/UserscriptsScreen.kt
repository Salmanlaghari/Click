package com.click.browser.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.InstalledUserscript
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.UserscriptEngine
import com.click.browser.engine.UserscriptMetadata
import com.click.browser.engine.UserscriptMetadataParser
import com.click.browser.engine.UserscriptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A fetched/picked/pasted script awaiting the user's install decision. */
private data class PendingUserscript(
    val meta: UserscriptMetadata,
    val code: String,
    val sourceUrl: String?
)

/** Shared download client for the install-from-URL fetch. */
private val userscriptFetchClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

/**
 * Reads a response body with a hard byte cap (the parser's char cap × 4,
 * the UTF-8 worst case) so a hostile or broken server cannot make the
 * installer load an unbounded response into memory. Returns null when
 * the cap is exceeded.
 */
private fun readBodyWithCap(response: okhttp3.Response): String? {
    val source = response.body?.source() ?: return ""
    val maxBytes = UserscriptMetadataParser.MAX_SCRIPT_CHARS * 4L + 1024L
    val buffer = okio.Buffer()
    var total = 0L
    while (true) {
        val read = source.read(buffer, 8192)
        if (read == -1L) break
        total += read
        if (total > maxBytes) return null
    }
    return buffer.readString(Charsets.UTF_8)
}

/**
 * Userscripts manager (overhaul B1): install `.user.js` scripts from a
 * URL, a file, or pasted code; review exactly what will run (name,
 * version, site patterns, requested GM APIs) before installing; toggle
 * or delete installed scripts. The support notes at the top are honest:
 * they list what the runner implements and what it does not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserscriptsScreen(
    theme: ModeTheme,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var scripts by remember { mutableStateOf<List<InstalledUserscript>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var urlInput by remember { mutableStateOf("") }
    var fetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var showPaste by remember { mutableStateOf(false) }
    var pasteInput by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<PendingUserscript?>(null) }
    var scriptToDelete by remember { mutableStateOf<InstalledUserscript?>(null) }

    suspend fun load() {
        loading = true
        scripts = runCatching { UserscriptStore.list(context) }.getOrDefault(emptyList())
        loading = false
    }

    fun afterChange() {
        scope.launch {
            load()
            UserscriptEngine.sync(context)
        }
    }

    fun review(code: String, sourceUrl: String?) {
        val meta = UserscriptMetadataParser.parse(code)
        if (meta == null) {
            fetchError = if (code.length > UserscriptMetadataParser.MAX_SCRIPT_CHARS) {
                "That script is too large (over ${UserscriptMetadataParser.MAX_SCRIPT_CHARS / 1_000_000} MB)."
            } else {
                "No valid ==UserScript== block with a @name was found."
            }
            return
        }
        fetchError = null
        pending = PendingUserscript(meta, code, sourceUrl)
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()
                        ?.readText()
                }.getOrNull()
            }
            if (text == null) {
                fetchError = "Could not read that file."
            } else {
                review(text, null)
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Userscripts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background,
                    titleContentColor = theme.onSurface
                )
            )
        },
        containerColor = theme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "notes") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Scripts run on pages they match, at page load. " +
                                "Reload open tabs after installing or toggling. " +
                                "Userscripts never run in private tabs.",
                            color = theme.onSurface.copy(alpha = 0.75f), fontSize = 13.sp
                        )
                        Text(
                            "Supported: @match / @include / @exclude, @run-at, " +
                                "GM_addStyle, GM_setValue / GM_getValue / GM_deleteValue / GM_listValues, " +
                                "GM_xmlhttpRequest, GM_info, unsafeWindow.",
                            color = theme.onSurface.copy(alpha = 0.75f), fontSize = 13.sp
                        )
                        Text(
                            "Not supported: @require / @resource, GM_openInTab, " +
                                "GM_registerMenuCommand, GM_notification, GM_setClipboard, " +
                                "and automatic update checks. GM_xmlhttpRequest does not " +
                                "send the page's cookies.",
                            color = theme.onSurface.copy(alpha = 0.75f), fontSize = 13.sp
                        )
                    }
                }
            }

            item(key = "add_header") {
                Text("Add a script", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    color = theme.onSurface)
            }
            item(key = "add_url") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://…/script.user.js") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !fetching && urlInput.isNotBlank(),
                            onClick = {
                                val url = urlInput.trim()
                                fetching = true
                                fetchError = null
                                scope.launch {
                                    val text = withContext(Dispatchers.IO) {
                                        runCatching {
                                            userscriptFetchClient.newCall(Request.Builder().url(url).build())
                                                .execute().use { resp ->
                                                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                                                    readBodyWithCap(resp) ?: error("Response too large")
                                                }
                                        }.getOrNull()
                                    }
                                    fetching = false
                                    if (text == null) {
                                        fetchError = "Could not download that URL (or it was too large)."
                                    } else {
                                        review(text, url)
                                    }
                                }
                            }
                        ) { Text(if (fetching) "Fetching…" else "Fetch") }
                        OutlinedButton(onClick = { fileLauncher.launch(arrayOf("*/*")) }) {
                            Text("From file")
                        }
                        OutlinedButton(onClick = { showPaste = !showPaste }) {
                            Text("Paste code")
                        }
                    }
                    if (showPaste) {
                        OutlinedTextField(
                            value = pasteInput,
                            onValueChange = { pasteInput = it },
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            placeholder = { Text("// ==UserScript==\n// @name …") },
                            shape = RoundedCornerShape(12.dp)
                        )
                        Button(
                            enabled = pasteInput.isNotBlank(),
                            onClick = { review(pasteInput, null) }
                        ) { Text("Review pasted script") }
                    }
                    fetchError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }

            pending?.let { p ->
                item(key = "pending") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = theme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Install this script?", fontWeight = FontWeight.Bold,
                                fontSize = 15.sp, color = theme.onSurface)
                            Text("${p.meta.name}  •  v${p.meta.version.ifBlank { "?" }}",
                                color = theme.onSurface, fontSize = 14.sp)
                            if (p.meta.description.isNotBlank()) {
                                Text(p.meta.description, color = theme.onSurface.copy(alpha = 0.75f),
                                    fontSize = 13.sp)
                            }
                            val sites = p.meta.matchPatterns + p.meta.includes
                            Text(
                                "Runs on: " + if (sites.isEmpty()) "(no @match/@include — it will not run anywhere)"
                                else sites.joinToString(", "),
                                color = theme.onSurface.copy(alpha = 0.75f), fontSize = 13.sp
                            )
                            Text(
                                "Runs at: " + if (p.meta.runAt.name == "DOCUMENT_START") "document start" else "document end",
                                color = theme.onSurface.copy(alpha = 0.75f), fontSize = 13.sp
                            )
                            Text(
                                "Requests: " + if (p.meta.grants.isEmpty()) "no GM APIs declared"
                                else p.meta.grants.joinToString(", "),
                                color = theme.onSurface.copy(alpha = 0.75f), fontSize = 13.sp
                            )
                            p.sourceUrl?.let {
                                Text("Source: $it", color = theme.onSurface.copy(alpha = 0.6f),
                                    fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val toInstall = InstalledUserscript.fromMetadata(p.meta, p.code, p.sourceUrl)
                                    pending = null
                                    scope.launch {
                                        UserscriptStore.install(context, toInstall)
                                        Toast.makeText(context, "Userscript installed.", Toast.LENGTH_SHORT).show()
                                        afterChange()
                                    }
                                }) { Text("Install") }
                                OutlinedButton(onClick = { pending = null }) { Text("Cancel") }
                            }
                        }
                    }
                }
            }

            item(key = "list_header") {
                Text("Installed (${scripts.size})", fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, color = theme.onSurface)
            }
            if (loading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (scripts.isEmpty()) {
                item(key = "empty") {
                    Text("No userscripts installed yet.",
                        color = theme.onSurface.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            } else {
                items(scripts, key = { it.id }) { script ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(script.name, fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp, color = theme.onSurface,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("v${script.version.ifBlank { "?" }}" +
                                        if (script.namespace.isNotBlank()) "  •  ${script.namespace}" else "",
                                        color = theme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Switch(
                                    checked = script.enabled,
                                    onCheckedChange = { on ->
                                        scope.launch {
                                            UserscriptStore.setEnabled(context, script.id, on)
                                            afterChange()
                                        }
                                    }
                                )
                                IconButton(onClick = { scriptToDelete = script }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete script",
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (script.description.isNotBlank()) {
                                Text(script.description, color = theme.onSurface.copy(alpha = 0.75f),
                                    fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            val sites = script.matchPatterns + script.includes
                            Text(
                                "Runs on: " + if (sites.isEmpty()) "nowhere (no @match/@include)"
                                else sites.take(3).joinToString(", ") +
                                    if (sites.size > 3) " +${sites.size - 3} more" else "",
                                color = theme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp
                            )
                            script.lastError?.let {
                                Text("Last error: $it", color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    scriptToDelete?.let { script ->
        AlertDialog(
            onDismissRequest = { scriptToDelete = null },
            title = { Text("Delete userscript?") },
            text = { Text("\"${script.name}\" and its stored values will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    scriptToDelete = null
                    scope.launch {
                        UserscriptStore.delete(context, script.id)
                        afterChange()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { scriptToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
