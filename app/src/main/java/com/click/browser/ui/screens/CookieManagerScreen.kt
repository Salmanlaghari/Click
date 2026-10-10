package com.click.browser.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.data.BrowserRepository
import com.click.browser.engine.CookieStore
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.SiteCookies
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Cookie Manager: lists cookies grouped by site (from history/bookmark hosts),
 * with per-cookie delete, per-site clear, and clear-all. Values are shown
 * as-is — they are the user's own cookies.
 *
 * Cookies live in the per-engine WebView data directory, so this screen shows
 * the CURRENT engine mode's cookies only (Simple / Developer / Hack).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookieManagerScreen(
    theme: ModeTheme,
    repository: BrowserRepository,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sites by remember { mutableStateOf<List<SiteCookies>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showClearAll by remember { mutableStateOf(false) }
    var siteToClear by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        try {
            val urls = repository.historyFlow.first().map { it.url } +
                repository.bookmarksFlow.first().map { it.url }
            // Bound the IPC burst: distinct hosts (not history items) are capped;
            // 500 covers real-world usage while keeping load time sane.
            val samples = CookieStore.sampleUrlsByHost(urls).entries.take(500)
            sites = coroutineScope {
                samples.map { (host, sampleUrls) ->
                    async { SiteCookies(host, CookieStore.cookiesForHost(host, sampleUrls)) }
                }.awaitAll()
            }.filter { it.cookies.isNotEmpty() }
        } catch (_: Exception) {
            sites = emptyList()
        }
        loading = false
    }

    fun refresh() {
        scope.launch { load() }
    }

    LaunchedEffect(Unit) { load() }

    val visible = remember(sites, query) {
        if (query.isBlank()) sites
        else sites.filter { it.host.contains(query.trim(), ignoreCase = true) }
    }
    val totalCookies = remember(sites) { sites.sumOf { it.cookies.size } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cookie Manager", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (sites.isNotEmpty()) {
                        TextButton(onClick = { showClearAll = true }) {
                            Text("Clear all", color = MaterialTheme.colorScheme.error)
                        }
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
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Search sites…") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null,
                        tint = theme.onSurface.copy(alpha = 0.5f))
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Text(
                "🍪 $totalCookies cookies · current engine mode only",
                color = theme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                visible.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🍪", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (query.isBlank()) "No cookies found."
                                else "No sites match \"$query\".",
                                color = theme.onSurface.copy(alpha = 0.7f),
                                fontSize = 15.sp
                            )
                            Text(
                                "Browse some sites and their cookies will appear here.",
                                color = theme.onSurface.copy(alpha = 0.5f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(visible, key = { it.host }) { site ->
                            val isExpanded = site.host in expanded
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = theme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                site.host,
                                                color = theme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                "${site.cookies.size} cookie" +
                                                    if (site.cookies.size == 1) "" else "s",
                                                color = theme.onSurface.copy(alpha = 0.6f),
                                                fontSize = 12.sp
                                            )
                                        }
                                        IconButton(onClick = { siteToClear = site.host }) {
                                            Icon(
                                                Icons.Default.DeleteSweep,
                                                contentDescription = "Clear site cookies",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                            )
                                        }
                                        IconButton(onClick = {
                                            expanded = if (isExpanded) expanded - site.host
                                            else expanded + site.host
                                        }) {
                                            Icon(
                                                if (isExpanded) Icons.Default.ExpandLess
                                                else Icons.Default.ExpandMore,
                                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                tint = theme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                    if (isExpanded) {
                                        Spacer(Modifier.height(8.dp))
                                        site.cookies.forEach { cookie ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        cookie.name,
                                                        color = theme.onSurface,
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        cookie.value.ifBlank { "(empty)" },
                                                        color = theme.onSurface.copy(alpha = 0.55f),
                                                        fontSize = 12.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                IconButton(onClick = {
                                                    // deleteCookie must run on the main thread;
                                                    // scope.launch defaults to Main.
                                                    CookieStore.deleteCookie(site.host, cookie.name) { ok ->
                                                        if (!ok) {
                                                            Toast.makeText(
                                                                context,
                                                                "Couldn't delete cookie \"${cookie.name}\"",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                        refresh()
                                                    }
                                                }) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete cookie",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                            HorizontalDivider(
                                                color = theme.onSurface.copy(alpha = 0.08f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showClearAll) {
            AlertDialog(
                onDismissRequest = { showClearAll = false },
                title = { Text("Clear all cookies?") },
                text = {
                    Text("This removes all $totalCookies cookies for the current engine mode. " +
                        "You will be signed out of websites. This cannot be undone.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        // clearAll must run on the main thread; scope.launch defaults to Main.
                        CookieStore.clearAll { refresh() }
                        showClearAll = false
                    }) {
                        Text("Clear all", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAll = false }) { Text("Cancel") }
                }
            )
        }

        siteToClear?.let { host ->
            val count = sites.find { it.host == host }?.cookies?.size ?: 0
            AlertDialog(
                onDismissRequest = { siteToClear = null },
                title = { Text("Clear cookies for $host?") },
                text = { Text("This removes $count cookie${if (count == 1) "" else "s"} " +
                    "for this site. You may be signed out there. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        siteToClear = null
                        scope.launch {
                            val failed = CookieStore.clearSite(host)
                            if (failed.isNotEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Couldn't clear ${failed.size} cookie${if (failed.size == 1) "" else "s"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            load()
                        }
                    }) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { siteToClear = null }) { Text("Cancel") }
                }
            )
        }
    }
}
