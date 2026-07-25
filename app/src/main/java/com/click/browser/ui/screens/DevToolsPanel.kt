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

@Composable
fun DevToolsPanel(
    modifier: Modifier = Modifier,
    logs: List<LogEntry>,
    networkRequests: List<NetworkRequest>,
    domHtml: String,
    sourcesList: List<String>,
    onClearLogs: () -> Unit,
    onEvalJs: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Elements", "Console", "Network", "Sources")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(350.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // Tab Headers
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1E1E1E))
                .padding(8.dp)
        ) {
            when (selectedTab) {
                0 -> ElementsTab(domHtml)
                1 -> ConsoleTab(logs, onClearLogs, onEvalJs)
                2 -> NetworkTab(networkRequests)
                3 -> SourcesTab(sourcesList)
            }
        }
    }
}

@Composable
fun ElementsTab(domHtml: String) {
    var displayModeHtml by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("DOM Tree / Selected Element HTML", color = Color.Gray, fontSize = 12.sp)
            Button(
                onClick = { displayModeHtml = !displayModeHtml },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.DarkGray)
            ) {
                Text(if (displayModeHtml) "Formatted View" else "HTML Code", color = Color.White, fontSize = 10.sp)
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
                    Text(
                        text = "[${log.type.uppercase()}] ${log.message}",
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
fun NetworkTab(requests: List<NetworkRequest>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Network Interceptor", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))

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
