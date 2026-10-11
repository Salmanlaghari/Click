package com.click.browser.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.CrashReporter
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.ReportMailer
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Reports: crash reports (auto-captured), OOM reports, in-app feedback and
 * bug-report forms. Everything is sent as an email to the admin team —
 * the user reviews it in their mail app before sending. Nothing uploads
 * silently.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    theme: ModeTheme,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var reports by remember { mutableStateOf<List<CrashReporter.Report>>(emptyList()) }
    var tab by remember { mutableStateOf(0) } // 0 Reports, 1 Feedback, 2 Bug

    var feedbackText by remember { mutableStateOf("") }
    var feedbackSent by remember { mutableStateOf(false) }
    var bugSummary by remember { mutableStateOf("") }
    var bugSteps by remember { mutableStateOf("") }
    var bugSent by remember { mutableStateOf(false) }

    fun refresh() {
        reports = try { CrashReporter.listReports(context) } catch (_: Exception) { emptyList() }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports", fontWeight = FontWeight.Bold, color = theme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.topBarBg)
            )
        },
        containerColor = theme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            TabRow(selectedTabIndex = tab, containerColor = theme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Crash & OOM") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Feedback") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Bug Report") })
            }
            when (tab) {
                0 -> ReportsTab(theme, reports,
                    onSend = { report ->
                        if (report.kind == "crash") ReportMailer.sendCrashReport(context, report)
                        else ReportMailer.sendOomReport(context, report)
                    },
                    onDiscard = { report ->
                        scope.launch {
                            try { CrashReporter.deleteReport(context, report.fileName) } catch (_: Exception) { }
                            refresh()
                        }
                    },
                    onTestCrash = {
                        scope.launch {
                            try { CrashReporter.writeTestCrash(context) } catch (_: Exception) { }
                            refresh()
                        }
                    }
                )
                1 -> FeedbackTab(theme, feedbackText, { feedbackText = it }, feedbackSent,
                    onSend = {
                        if (feedbackText.isNotBlank()) {
                            ReportMailer.sendFeedback(context, feedbackText.trim())
                            feedbackSent = true
                            feedbackText = ""
                        }
                    })
                2 -> BugTab(theme, bugSummary, { bugSummary = it }, bugSteps, { bugSteps = it }, bugSent,
                    onSend = {
                        if (bugSummary.isNotBlank()) {
                            ReportMailer.sendBugReport(context, bugSummary.trim(), bugSteps.trim())
                            bugSent = true
                            bugSummary = ""
                            bugSteps = ""
                        }
                    })
            }
        }
    }
}

@Composable
private fun ReportsTab(
    theme: ModeTheme,
    reports: List<CrashReporter.Report>,
    onSend: (CrashReporter.Report) -> Unit,
    onDiscard: (CrashReporter.Report) -> Unit,
    onTestCrash: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Crashes and low-memory warnings are captured on this device. " +
                "Tap Send to email one to the team — nothing is sent automatically.",
            fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            "Admins: ${ReportMailer.ADMIN_EMAILS.joinToString(", ")}",
            fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.45f)
        )
        if (reports.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle, contentDescription = null,
                        tint = theme.primary.copy(alpha = 0.6f), modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No crash or OOM reports", color = theme.onSurface.copy(alpha = 0.55f), fontSize = 14.sp)
                }
            }
        } else {
            reports.forEach { report ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (report.kind == "crash") Icons.Default.BugReport else Icons.Default.Memory,
                                contentDescription = null, tint = theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(report.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = theme.onSurface)
                                Text(
                                    DateFormat.getDateTimeInstance().format(Date(report.timestamp)),
                                    fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onSend(report) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Send", fontSize = 13.sp)
                            }
                            OutlinedButton(onClick = { onDiscard(report) }, modifier = Modifier.weight(1f)) {
                                Text("Discard", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onTestCrash, modifier = Modifier.fillMaxWidth()) {
            Text("Generate test crash report", fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeedbackTab(
    theme: ModeTheme,
    text: String,
    onText: (String) -> Unit,
    sent: Boolean,
    onSend: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Ideas, suggestions, or praise — goes straight to the Click team.",
            fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f)
        )
        OutlinedTextField(
            value = text, onValueChange = onText,
            label = { Text("Your feedback") },
            placeholder = { Text("I love… / I wish Click had…") },
            minLines = 5, maxLines = 10,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onSend,
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Send feedback")
        }
        if (sent) {
            Text(
                "Opening your mail app — review and hit send ✓",
                fontSize = 12.sp, color = theme.primary
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BugTab(
    theme: ModeTheme,
    summary: String, onSummary: (String) -> Unit,
    steps: String, onSteps: (String) -> Unit,
    sent: Boolean,
    onSend: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Found a bug? Describe it — device info is attached automatically.",
            fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f)
        )
        OutlinedTextField(
            value = summary, onValueChange = onSummary,
            label = { Text("Bug summary") },
            placeholder = { Text("What went wrong?") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = steps, onValueChange = onSteps,
            label = { Text("Steps to reproduce (optional)") },
            placeholder = { Text("1. Open…\n2. Tap…\n3. See…") },
            minLines = 4, maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onSend,
            enabled = summary.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Send bug report")
        }
        if (sent) {
            Text(
                "Opening your mail app — review and hit send ✓",
                fontSize = 12.sp, color = theme.primary
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
