package com.click.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.DevToolsComputedStyles
import com.click.browser.engine.DevToolsDomNode
import com.click.browser.engine.DevToolsDomSnapshot
import com.click.browser.engine.DevToolsInspectionPolicy
import com.click.browser.engine.LogEntry
import com.click.browser.engine.NetworkRequest
import com.click.browser.engine.NetworkRequestOutcome

internal object DevToolsPalette {
    val canvas = Color(0xFFFFF8EC)
    val surface = Color(0xFFFFFCF6)
    val card = Color(0xFFF4EBDD)
    val selected = Color(0xFFE8DFF4)
    val border = Color(0xFFE3D6C2)
    val ink = Color(0xFF2D2924)
    val muted = Color(0xFF71685D)
    val accent = Color(0xFF6551A3)
    val teal = Color(0xFF287F83)
    val info = Color(0xFF316A97)
    val success = Color(0xFF287548)
    val warning = Color(0xFF965D00)
    val error = Color(0xFFB3261E)
    val white = Color(0xFFFFFFFF)
}

/** Viewport / device facts gathered from the live WebView via JS. */
data class DeviceInfo(
    val viewport: String = "",
    val devicePixelRatio: String = "",
    val userAgent: String = "",
    val screenSize: String = "",
    val platform: String = "",
    val language: String = "",
    val touchSupport: String = "",
    val cookiesEnabled: String = ""
)

private data class VisibleDomRow(val node: DevToolsDomNode, val depth: Int)

@Composable
internal fun DevToolsPanel(
    modifier: Modifier = Modifier,
    logs: List<LogEntry>,
    networkRequests: List<NetworkRequest>,
    domSnapshot: DevToolsDomSnapshot?,
    selectedNodePath: List<Int>?,
    selectedStyles: DevToolsComputedStyles?,
    sourcesList: List<String>,
    pageSessionKey: String,
    pageMessagesCaptureConsented: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onPageMessagesCaptureConsentChanged: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onClearNetwork: () -> Unit,
    onRefreshPageSnapshot: () -> Unit,
    onSelectNode: (List<Int>) -> Unit,
    onRunConsoleCommand: (String, (String) -> Unit) -> Unit,
    deviceInfo: DeviceInfo?,
    onRefreshDeviceInfo: () -> Unit,
    onRefreshSources: () -> Unit,
    renderedDomMarkup: String?,
    renderedDomLoading: Boolean,
    onInspectRenderedDom: () -> Unit,
    onClearRenderedDom: () -> Unit
) {
    val tabs = listOf("Elements", "Console", "Network", "Sources", "Device")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DevToolsPalette.canvas)
    ) {
        Text(
            text = "Developer Tools · WebView",
            color = DevToolsPalette.ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
        )
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DevToolsPalette.surface,
            contentColor = DevToolsPalette.ink
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = {
                        Text(
                            title,
                            color = if (selectedTab == index) DevToolsPalette.accent else DevToolsPalette.muted,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 560.dp)
                .background(DevToolsPalette.canvas)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            when (selectedTab) {
                0 -> ElementsTab(
                    snapshot = domSnapshot,
                    selectedNodePath = selectedNodePath,
                    selectedStyles = selectedStyles,
                    onRefresh = onRefreshPageSnapshot,
                    onSelectNode = onSelectNode
                )
                1 -> key(pageSessionKey) {
                    ConsoleTab(
                        logs = logs,
                        pageMessagesCaptureConsented = pageMessagesCaptureConsented,
                        onPageMessagesCaptureConsentChanged = onPageMessagesCaptureConsentChanged,
                        onClearLogs = onClearLogs,
                        onRunConsoleCommand = onRunConsoleCommand
                    )
                }
                2 -> NetworkTab(networkRequests, onClearNetwork)
                3 -> key(pageSessionKey) {
                    SourcesTab(
                        sources = sourcesList,
                        renderedDomMarkup = renderedDomMarkup,
                        renderedDomLoading = renderedDomLoading,
                        onRefresh = onRefreshSources,
                        onInspectRenderedDom = onInspectRenderedDom,
                        onClearRenderedDom = onClearRenderedDom
                    )
                }
                4 -> DeviceTab(deviceInfo, onRefreshDeviceInfo)
            }
        }
    }
}

@Composable
private fun ElementsTab(
    snapshot: DevToolsDomSnapshot?,
    selectedNodePath: List<Int>?,
    selectedStyles: DevToolsComputedStyles?,
    onRefresh: () -> Unit,
    onSelectNode: (List<Int>) -> Unit
) {
    var expandedPaths by remember(snapshot) {
        mutableStateOf(setOf(snapshot?.root?.pathKey.orEmpty()))
    }
    val rows = remember(snapshot, expandedPaths) {
        buildList {
            fun append(node: DevToolsDomNode, depth: Int) {
                add(VisibleDomRow(node, depth))
                if (node.pathKey in expandedPaths) node.children.forEach { append(it, depth + 1) }
            }
            snapshot?.root?.let { append(it, 0) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Elements · read-only", color = DevToolsPalette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onRefresh) {
                Text("Refresh", color = DevToolsPalette.accent, fontSize = 12.sp)
            }
        }
        Text(
            "Structure and computed styles only. Click does not read page text, form values, cookies, or storage.",
            color = DevToolsPalette.muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 5.dp)
        )
        if (snapshot?.truncated == true) {
            Text("Tree limited to ${DevToolsInspectionPolicy.MAX_DOM_NODES} elements for responsiveness.", color = DevToolsPalette.warning, fontSize = 10.sp)
        }

        if (snapshot?.root == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Tap Refresh to inspect the current WebView page.",
                    color = DevToolsPalette.muted,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(rows, key = { it.node.pathKey }) { row ->
                    val node = row.node
                    val isSelected = selectedNodePath == node.path
                    val isExpanded = node.pathKey in expandedPaths
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) DevToolsPalette.selected else DevToolsPalette.surface)
                            .clickable { onSelectNode(node.path) }
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.width((row.depth * 12).coerceAtMost(144).dp))
                        if (node.childCount > 0) {
                            TextButton(
                                onClick = {
                                    expandedPaths = if (isExpanded) expandedPaths - node.pathKey else expandedPaths + node.pathKey
                                },
                                contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)
                            ) {
                                Text(if (isExpanded) "▾" else "▸", color = DevToolsPalette.accent, fontSize = 13.sp)
                            }
                        } else {
                            Spacer(Modifier.width(28.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "<${node.tagName}>",
                                color = DevToolsPalette.teal,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val details = buildList {
                                node.id.takeIf { it.isNotBlank() }?.let { add("#$it") }
                                node.classes.take(3).takeIf { it.isNotEmpty() }?.let { add(".${it.joinToString(".")}") }
                                node.elementType?.takeIf { it.isNotBlank() }?.let { add("type=$it") }
                            }.joinToString(" ")
                            if (details.isNotBlank()) {
                                Text(
                                    details,
                                    color = DevToolsPalette.muted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        Divider(color = DevToolsPalette.border)
        Column(modifier = Modifier.fillMaxWidth().heightIn(min = 86.dp, max = 146.dp).padding(top = 5.dp)) {
            Text(
                text = selectedStyles?.let { "Computed styles · <${it.tagName}>" } ?: "Computed styles",
                color = DevToolsPalette.ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 3.dp)
            )
            if (selectedStyles == null || selectedStyles.properties.isEmpty()) {
                Text("Select an element to inspect its safe, read-only style properties.", color = DevToolsPalette.muted, fontSize = 10.sp)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(selectedStyles.properties.entries.toList(), key = { it.key }) { (name, value) ->
                        Text(
                            "$name: $value",
                            color = DevToolsPalette.ink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleTab(
    logs: List<LogEntry>,
    pageMessagesCaptureConsented: Boolean,
    onPageMessagesCaptureConsentChanged: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onRunConsoleCommand: (String, (String) -> Unit) -> Unit
) {
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var commandWasTruncated by remember { mutableStateOf(false) }
    var consentGranted by remember { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var showPageMessagesConsentDialog by remember { mutableStateOf(false) }
    var pendingCommand by remember { mutableStateOf("") }
    val timeFormatter = remember {
        java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss", java.util.Locale.getDefault())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Page console", color = DevToolsPalette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { onClearLogs(); output = "" }) {
                Text("Clear", color = DevToolsPalette.error, fontSize = 12.sp)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (pageMessagesCaptureConsented) {
                    "Page messages are on only while Console is selected."
                } else {
                    "Page message capture is off; sites may log private data."
                },
                color = DevToolsPalette.muted,
                fontSize = 9.sp,
                modifier = Modifier.weight(1f)
            )
            if (pageMessagesCaptureConsented) {
                TextButton(onClick = { onPageMessagesCaptureConsentChanged(false); onClearLogs() }) {
                    Text("Stop", color = DevToolsPalette.error, fontSize = 11.sp)
                }
            } else {
                TextButton(onClick = { showPageMessagesConsentDialog = true }) {
                    Text("Enable", color = DevToolsPalette.accent, fontSize = 11.sp)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            reverseLayout = true
        ) {
            if (!pageMessagesCaptureConsented) {
                item {
                    Text(
                        "Page messages are not collected until you enable them.",
                        color = DevToolsPalette.muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            } else {
                items(logs.reversed()) { log ->
                    val color = when (log.type) {
                        "success" -> DevToolsPalette.success
                        "warning" -> DevToolsPalette.warning
                        "error" -> DevToolsPalette.error
                        else -> DevToolsPalette.info
                    }
                    val time = timeFormatter.format(
                        java.time.Instant.ofEpochMilli(log.timestamp)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalTime()
                    )
                    Text(
                        text = "[$time] [${log.type.uppercase()}] ${log.message}",
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    )
                }
            }
        }
        if (output.isNotBlank()) {
            Text(
                "Expression result",
                color = DevToolsPalette.ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
            Text(
                output,
                color = DevToolsPalette.ink,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(DevToolsPalette.card).padding(6.dp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = { input ->
                    commandWasTruncated = input.length > DevToolsInspectionPolicy.MAX_COMMAND_LENGTH
                    command = input.take(DevToolsInspectionPolicy.MAX_COMMAND_LENGTH)
                },
                modifier = Modifier.weight(1f),
                label = { Text("JavaScript expression", color = DevToolsPalette.muted) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = DevToolsPalette.ink,
                    fontFamily = FontFamily.Monospace
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DevToolsPalette.ink,
                    unfocusedTextColor = DevToolsPalette.ink,
                    focusedBorderColor = DevToolsPalette.accent,
                    unfocusedBorderColor = DevToolsPalette.border,
                    focusedLabelColor = DevToolsPalette.accent,
                    unfocusedLabelColor = DevToolsPalette.muted,
                    cursorColor = DevToolsPalette.accent
                )
            )
            Spacer(Modifier.width(6.dp))
            Button(
                enabled = command.trim().isNotBlank(),
                onClick = {
                    pendingCommand = command.trim()
                    if (consentGranted) {
                        val toRun = pendingCommand
                        pendingCommand = ""
                        command = ""
                        commandWasTruncated = false
                        output = "Running…"
                        onRunConsoleCommand(toRun) { output = it }
                    } else {
                        showConsentDialog = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = DevToolsPalette.accent,
                    contentColor = DevToolsPalette.white,
                    disabledContainerColor = DevToolsPalette.border,
                    disabledContentColor = DevToolsPalette.muted
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("Run", fontSize = 11.sp)
            }
        }
        if (commandWasTruncated) {
            Text(
                "Input exceeded ${DevToolsInspectionPolicy.MAX_COMMAND_LENGTH} characters; only the first ${DevToolsInspectionPolicy.MAX_COMMAND_LENGTH} were kept. Review before running.",
                color = DevToolsPalette.warning,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            "One JavaScript expression per run. After page-session consent, each run still requires tapping Run; input clears when this panel closes.",
            color = DevToolsPalette.muted,
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }

    if (showPageMessagesConsentDialog) {
        AlertDialog(
            onDismissRequest = { showPageMessagesConsentDialog = false },
            title = { Text("Capture page console messages?", color = DevToolsPalette.ink) },
            text = {
                Text(
                    "A website can put private text, form data, cookies, or tokens in its own console messages. Click will collect none until you enable this. If enabled, messages are kept only in this panel while Console is selected and are cleared when you leave it or close DevTools. Continue only on pages you trust.",
                    color = DevToolsPalette.ink
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onPageMessagesCaptureConsentChanged(true)
                    showPageMessagesConsentDialog = false
                }) {
                    Text("Enable page messages", color = DevToolsPalette.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPageMessagesConsentDialog = false }) {
                    Text("Cancel", color = DevToolsPalette.muted)
                }
            },
            containerColor = DevToolsPalette.surface,
            titleContentColor = DevToolsPalette.ink,
            textContentColor = DevToolsPalette.ink
        )
    }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false; pendingCommand = "" },
            title = { Text("Run an expression on this page?", color = DevToolsPalette.ink) },
            text = {
                Text(
                    "One JavaScript expression runs in the active website context and can read or change data that site can access, including form values and cookies. Click does not read these automatically. Run only trusted expressions; results stay in this panel and are cleared when it closes.",
                    color = DevToolsPalette.ink
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    consentGranted = true
                    showConsentDialog = false
                    val toRun = pendingCommand
                    pendingCommand = ""
                    command = ""
                    commandWasTruncated = false
                    output = "Running…"
                    if (toRun.isNotBlank()) onRunConsoleCommand(toRun) { output = it }
                }) {
                    Text("Run this expression", color = DevToolsPalette.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog = false; pendingCommand = "" }) {
                    Text("Cancel", color = DevToolsPalette.muted)
                }
            },
            containerColor = DevToolsPalette.surface,
            titleContentColor = DevToolsPalette.ink,
            textContentColor = DevToolsPalette.ink
        )
    }
}

@Composable
private fun NetworkTab(requests: List<NetworkRequest>, onClearNetwork: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Observed WebView requests (${requests.size})", color = DevToolsPalette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onClearNetwork) {
                Text("Clear", color = DevToolsPalette.error, fontSize = 12.sp)
            }
        }
        Text(
            "Captured only while Network is selected. Credentials, query strings, fragments, and token-like path parts are hidden.",
            color = DevToolsPalette.muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 5.dp)
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(requests.asReversed(), key = { it.id }) { request ->
                val statusColor = when (request.outcome) {
                    NetworkRequestOutcome.HTTP_ERROR, NetworkRequestOutcome.LOAD_ERROR -> DevToolsPalette.error
                    NetworkRequestOutcome.BLOCKED_BY_CLICK -> DevToolsPalette.warning
                    NetworkRequestOutcome.OBSERVED -> DevToolsPalette.muted
                }
                val statusLabel = when (request.outcome) {
                    NetworkRequestOutcome.OBSERVED -> "Observed · status n/a"
                    NetworkRequestOutcome.BLOCKED_BY_CLICK -> "Blocked by Click"
                    NetworkRequestOutcome.HTTP_ERROR -> "HTTP error · ${request.statusCode ?: "?"}"
                    NetworkRequestOutcome.LOAD_ERROR -> "Load error"
                }
                val entryType = when (request.outcome) {
                    NetworkRequestOutcome.OBSERVED -> if (request.isMainFrame) "Document" else "Resource"
                    NetworkRequestOutcome.BLOCKED_BY_CLICK -> "Request"
                    NetworkRequestOutcome.HTTP_ERROR, NetworkRequestOutcome.LOAD_ERROR -> "Error event"
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp)).background(DevToolsPalette.surface)
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(request.method, color = DevToolsPalette.accent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(entryType, color = DevToolsPalette.muted, fontSize = 10.sp)
                        Spacer(Modifier.weight(1f))
                        Text(statusLabel, color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 9.sp)
                    }
                    Text(request.url, color = DevToolsPalette.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    request.detail?.let { Text(it, color = DevToolsPalette.muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun SourcesTab(
    sources: List<String>,
    renderedDomMarkup: String?,
    renderedDomLoading: Boolean,
    onRefresh: () -> Unit,
    onInspectRenderedDom: () -> Unit,
    onClearRenderedDom: () -> Unit
) {
    var showRenderedDomConsent by remember { mutableStateOf(false) }
    if (showRenderedDomConsent) {
        AlertDialog(
            onDismissRequest = { showRenderedDomConsent = false },
            title = { Text("View rendered DOM?") },
            text = {
                Text(
                    "This shows rendered DOM markup capped at ${DevToolsInspectionPolicy.MAX_RENDERED_DOM_SOURCE_LENGTH} characters; a short truncation note may follow. It can include hidden or off-screen text and selected element attributes such as IDs and classes, which may contain URLs, tokens, or other sensitive information. Form values and editable content, scripts, styles, cookies, and storage are omitted. Link and resource attributes expose only HTTP(S) origins; other URLs are omitted. Linked resources are not fetched; this inspection itself sends nothing to AI or the network. This is rendered markup, not the original response source."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRenderedDomConsent = false
                        onInspectRenderedDom()
                    }
                ) { Text("Show markup", color = DevToolsPalette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showRenderedDomConsent = false }) {
                    Text("Cancel", color = DevToolsPalette.muted)
                }
            },
            containerColor = DevToolsPalette.surface,
            titleContentColor = DevToolsPalette.ink,
            textContentColor = DevToolsPalette.muted
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sources · WebView", color = DevToolsPalette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onRefresh) {
                Text("Refresh references", color = DevToolsPalette.accent, fontSize = 11.sp, maxLines = 1)
            }
        }
        Text(
            "Reference URLs are sanitized; query and fragment data are hidden. Viewing rendered markup requires confirmation.",
            color = DevToolsPalette.muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 5.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { showRenderedDomConsent = true },
                enabled = !renderedDomLoading
            ) {
                Text(
                    if (renderedDomMarkup == null) "View rendered DOM" else "Refresh rendered DOM",
                    color = DevToolsPalette.accent,
                    fontSize = 11.sp
                )
            }
            if (renderedDomMarkup != null) {
                TextButton(onClick = onClearRenderedDom, enabled = !renderedDomLoading) {
                    Text("Clear preview", color = DevToolsPalette.muted, fontSize = 11.sp)
                }
            }
        }
        if (renderedDomLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = DevToolsPalette.accent,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Reading the current rendered DOM…", color = DevToolsPalette.muted, fontSize = 10.sp)
            }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (renderedDomMarkup != null) {
                item(key = "rendered-dom-title") {
                    Text(
                        "Rendered DOM (not original response source)",
                        color = DevToolsPalette.ink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }
                item(key = "rendered-dom-markup") {
                    SelectionContainer {
                        Text(
                            renderedDomMarkup,
                            color = DevToolsPalette.ink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(DevToolsPalette.surface).padding(8.dp)
                        )
                    }
                }
            }
            item(key = "source-references-title") {
                Text(
                    "Markup resource references",
                    color = DevToolsPalette.ink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            if (sources.isEmpty()) {
                item(key = "source-references-empty") {
                    Text("Tap Refresh references to list page URLs.", color = DevToolsPalette.muted, fontSize = 12.sp)
                }
            } else {
                items(sources) { source ->
                    Text(
                        text = source,
                        color = DevToolsPalette.teal,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(6.dp)).background(DevToolsPalette.surface).padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceTab(deviceInfo: DeviceInfo?, onRefreshDeviceInfo: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Viewport & device facts", color = DevToolsPalette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = onRefreshDeviceInfo,
                colors = ButtonDefaults.buttonColors(containerColor = DevToolsPalette.accent, contentColor = DevToolsPalette.white),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Refresh", fontSize = 10.sp)
            }
        }

        if (deviceInfo == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tap Refresh to read live viewport facts from the WebView.", color = DevToolsPalette.muted, fontSize = 12.sp)
            }
        } else {
            val rows = listOf(
                "Viewport" to deviceInfo.viewport,
                "Device Pixel Ratio" to deviceInfo.devicePixelRatio,
                "Screen" to deviceInfo.screenSize,
                "Platform" to deviceInfo.platform,
                "Language" to deviceInfo.language,
                "Touch support" to deviceInfo.touchSupport,
                "Cookies enabled" to deviceInfo.cookiesEnabled
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(rows) { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(6.dp)).background(DevToolsPalette.surface).padding(8.dp)
                    ) {
                        Text("$label: ", color = DevToolsPalette.accent, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(value.ifEmpty { "—" }, color = DevToolsPalette.ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
                item {
                    Text(
                        "User Agent:",
                        color = DevToolsPalette.accent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                    Text(
                        deviceInfo.userAgent.ifEmpty { "—" },
                        color = DevToolsPalette.ink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(DevToolsPalette.surface).padding(8.dp)
                    )
                }
            }
        }
    }
}
