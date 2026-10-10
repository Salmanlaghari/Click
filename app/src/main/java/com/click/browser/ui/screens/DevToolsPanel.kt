package com.click.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.LogEntry
import com.click.browser.engine.NetworkRequest

/**
 * Viewport / device facts gathered from the live page via JS.
 * Every field is real data from the WebView — no placeholders.
 */
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

@Composable
fun DevToolsPanel(
    modifier: Modifier = Modifier,
    logs: List<LogEntry>,
    networkRequests: List<NetworkRequest>,
    domHtml: String,
    sourcesList: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onClearLogs: () -> Unit,
    onClearNetwork: () -> Unit,
    onEvalJs: (String) -> Unit,
    inspectorEnabled: Boolean,
    onToggleInspector: () -> Unit,
    deviceInfo: DeviceInfo?,
    onRefreshDeviceInfo: () -> Unit
) {
    val tabs = listOf("Elements", "Console", "Network", "Sources", "Device")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // Tab Headers
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 480.dp)
                .background(Color(0xFF1E1E1E))
                .padding(8.dp)
        ) {
            when (selectedTab) {
                0 -> ElementsTab(domHtml, inspectorEnabled, onToggleInspector)
                1 -> ConsoleTab(logs, onClearLogs, onEvalJs)
                2 -> NetworkTab(networkRequests, onClearNetwork)
                3 -> SourcesTab(sourcesList)
                4 -> DeviceTab(deviceInfo, onRefreshDeviceInfo)
            }
        }
    }
}

@Composable
fun ElementsTab(
    domHtml: String,
    inspectorEnabled: Boolean,
    onToggleInspector: () -> Unit
) {
    var displayModeHtml by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("DOM Tree / Selected Element HTML", color = Color.Gray, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Real element inspector toggle — tap page elements to inspect them.
                Button(
                    onClick = onToggleInspector,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (inspectorEnabled) Color(0xFF7B1FA2) else Color.DarkGray
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (inspectorEnabled) "Inspect: ON" else "Inspect: OFF",
                        color = Color.White, fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = { displayModeHtml = !displayModeHtml },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.DarkGray)
                ) {
                    Text(if (displayModeHtml) "Formatted View" else "HTML Code", color = Color.White, fontSize = 10.sp)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (displayModeHtml) {
                Text(
                    text = domHtml.ifEmpty { "No DOM element inspected/loaded yet." },
                    color = Color(0xFF80CBC4),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            } else {
                // Easy parsed tags view
                val tags = remember(domHtml) {
                    val list = mutableListOf<String>()
                    val regex = "<([a-zA-Z0-9]+)([^>]*)>".toRegex()
                    regex.findAll(domHtml).forEach { match ->
                        list.add("<" + match.groupValues[1] + ">")
                    }
                    list
                }
                if (tags.isEmpty()) {
                    Text("No tags parsed.", color = Color.White)
                } else {
                    Column {
                        tags.forEach { tag ->
                            Text(tag, color = Color(0xFFECEFF1), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConsoleTab(
    logs: List<LogEntry>,
    onClearLogs: () -> Unit,
    onEvalJs: (String) -> Unit
) {
    var jsInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("JavaScript Console", color = Color.Gray, fontSize = 12.sp)
            TextButton(onClick = onClearLogs) {
                Text("Clear", color = Color.Red, fontSize = 12.sp)
            }
        }

        // Logs Display list
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Cache the date formatter — creating one per log entry per
            // composition is wasteful (SimpleDateFormat init is expensive).
            val timeFormat = remember {
                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true
            ) {
                items(logs.reversed()) { log ->
                    val color = when (log.type) {
                        "info" -> Color(0xFF29B6F6) // info blue
                        "success" -> Color(0xFF66BB6A) // success green
                        "warning" -> Color(0xFFFFA726) // warning orange
                        "error" -> Color(0xFFEF5350) // error red
                        else -> Color.White
                    }
                    val time = timeFormat.format(java.util.Date(log.timestamp))
                    Text(
                        text = "[$time] [${log.type.uppercase()}] ${log.message}",
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // JS Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = jsInput,
                onValueChange = { jsInput = it },
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Magenta,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                placeholder = { Text("eval(code...)", color = Color.DarkGray, fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (jsInput.isNotBlank()) {
                        onEvalJs(jsInput)
                        jsInput = ""
                    }
                })
            )
            Spacer(modifier = Modifier.width(4.dp))
            Button(
                onClick = {
                    if (jsInput.isNotBlank()) {
                        onEvalJs(jsInput)
                        jsInput = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2))
            ) {
                Text("Run", fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun NetworkTab(requests: List<NetworkRequest>, onClearNetwork: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Network Interceptor (${requests.size})",
                color = Color.Gray, fontSize = 12.sp
            )
            TextButton(onClick = onClearNetwork) {
                Text("Clear", color = Color.Red, fontSize = 12.sp)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(requests) { req ->
                val statusColor = when {
                    req.status in 200..299 -> Color(0xFF66BB6A) // green
                    req.status in 300..399 -> Color(0xFFFFA726) // yellow
                    else -> Color(0xFFEF5350) // red
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color(0xFF2B2B2B))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row {
                            Text(req.method, color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(req.url, color = Color.White, fontSize = 11.sp, maxLines = 1)
                        }
                        Text("Time: ${req.time} ms | Size: ${req.size}", color = Color.LightGray, fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = req.status.toString(),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SourcesTab(sources: List<String>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Page Sources & Resources", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))

        if (sources.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No sources detected.", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(sources) { src ->
                    val typeColor = when {
                        src.endsWith(".js") -> Color(0xFFFFF176)
                        src.endsWith(".css") -> Color(0xFF81D4FA)
                        src.endsWith(".png") || src.endsWith(".jpg") || src.endsWith(".jpeg") || src.endsWith(".gif") || src.endsWith(".webp") -> Color(0xFFC5E1A5)
                        else -> Color.White
                    }

                    Text(
                        text = src,
                        color = typeColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(Color(0xFF262626))
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceTab(deviceInfo: DeviceInfo?, onRefreshDeviceInfo: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Viewport & Device Info", color = Color.Gray, fontSize = 12.sp)
            Button(
                onClick = onRefreshDeviceInfo,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Refresh", color = Color.White, fontSize = 10.sp)
            }
        }

        if (deviceInfo == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Tap Refresh to read live viewport info from the page.",
                    color = Color.Gray, fontSize = 12.sp
                )
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(Color(0xFF262626))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "$label: ",
                            color = Color(0xFF81D4FA),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = value.ifEmpty { "—" },
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
                item {
                    Text(
                        text = "User Agent:",
                        color = Color(0xFF81D4FA),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                    Text(
                        text = deviceInfo.userAgent.ifEmpty { "—" },
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF262626))
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}
