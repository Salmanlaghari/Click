package com.click.browser.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.data.BrowserRepository
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.SitePermissionPref
import com.click.browser.engine.SiteSettings
import kotlinx.coroutines.launch

/**
 * All-sites settings list (Settings -> Site settings). The per-site sheet
 * reached from the address-bar shield edits ONE site (the one open); this
 * screen is the management view Chrome users expect: every site that has
 * saved per-site choices — content overrides from [SiteSettings] plus the
 * desktop-site hosts set — with a per-site reset and a reset-all.
 *
 * Editing happens on the site itself (open it, tap the shield); this list
 * is view + reset only, so nothing here can silently change a live page.
 *
 * Data lives in the per-profile DataStore, so this list shows the CURRENT
 * engine mode's choices only (Simple / Developer / Power / Advance).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllSiteSettingsScreen(
    theme: ModeTheme,
    repository: BrowserRepository,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val settingsMap by repository.siteSettingsFlow.collectAsState(initial = emptyMap())
    val desktopHosts by repository.desktopHostsFlow.collectAsState(initial = emptySet())
    var query by remember { mutableStateOf("") }
    var showResetAll by remember { mutableStateOf(false) }
    var siteToReset by remember { mutableStateOf<String?>(null) }

    // Union of both per-site stores: a host appears when it has content
    // overrides, a desktop-site flag, or both.
    val hosts = remember(settingsMap, desktopHosts) {
        (settingsMap.keys + desktopHosts).sorted()
    }
    val visible = remember(hosts, query) {
        if (query.isBlank()) hosts
        else hosts.filter { it.contains(query.trim(), ignoreCase = true) }
    }

    fun resetHost(host: String) {
        scope.launch {
            repository.clearSiteSettings(host)
            repository.setDesktopHost(host, false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Site settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hosts.isNotEmpty()) {
                        TextButton(onClick = { showResetAll = true }) {
                            Text("Reset all", color = MaterialTheme.colorScheme.error)
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
                "🛡️ ${hosts.size} site${if (hosts.size == 1) "" else "s"} with custom settings · current engine mode only",
                color = theme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            when {
                visible.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Text("🛡️", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (query.isBlank()) "No per-site settings yet."
                                else "No sites match \"$query\".",
                                color = theme.onSurface.copy(alpha = 0.7f),
                                fontSize = 15.sp
                            )
                            if (query.isBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Open a website and tap the shield icon in the address bar " +
                                        "to set rules for that site — they will appear here.",
                                    color = theme.onSurface.copy(alpha = 0.5f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(visible, key = { it }) { host ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = theme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            host,
                                            color = theme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            siteSettingsSummary(
                                                settingsMap[host],
                                                desktop = host in desktopHosts
                                            ),
                                            color = theme.onSurface.copy(alpha = 0.6f),
                                            fontSize = 12.sp
                                        )
                                    }
                                    IconButton(onClick = { siteToReset = host }) {
                                        Icon(
                                            Icons.Default.DeleteSweep,
                                            contentDescription = "Reset site settings",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showResetAll) {
            AlertDialog(
                onDismissRequest = { showResetAll = false },
                title = { Text("Reset all site settings?") },
                text = {
                    Text("This removes the saved per-site choices for all ${hosts.size} sites " +
                        "in the current engine mode. Every site goes back to the global defaults. " +
                        "This cannot be undone.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showResetAll = false
                        scope.launch {
                            hosts.forEach { host ->
                                repository.clearSiteSettings(host)
                                repository.setDesktopHost(host, false)
                            }
                        }
                    }) {
                        Text("Reset all", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetAll = false }) { Text("Cancel") }
                }
            )
        }

        siteToReset?.let { host ->
            AlertDialog(
                onDismissRequest = { siteToReset = null },
                title = { Text("Reset settings for $host?") },
                text = {
                    Text("This site goes back to the global defaults — content rules, " +
                        "permissions and desktop-site choice are all removed.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        siteToReset = null
                        resetHost(host)
                    }) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { siteToReset = null }) { Text("Cancel") }
                }
            )
        }
    }
}

/** Plain-words summary of one site's overrides, e.g. "JavaScript off · Camera: Block". */
private fun siteSettingsSummary(settings: SiteSettings?, desktop: Boolean): String {
    val parts = mutableListOf<String>()
    settings?.let { s ->
        s.javaScript?.let { parts += if (it) "JavaScript on" else "JavaScript off" }
        s.images?.let { parts += if (it) "Images on" else "Images off" }
        s.popups?.let { parts += if (it) "Pop-ups allowed" else "Pop-ups blocked" }
        s.sound?.let { parts += if (it) "Sound allowed" else "Sound blocked" }
        s.adBlock?.let { parts += if (it) "Ad block on" else "Ad block off" }
        s.thirdPartyCookies?.let {
            parts += if (it) "Third-party cookies allowed" else "Third-party cookies blocked"
        }
        s.textZoom?.let { parts += "Text zoom $it%" }
        s.camera?.let { parts += "Camera: ${prefLabel(it)}" }
        s.microphone?.let { parts += "Microphone: ${prefLabel(it)}" }
        s.notifications?.let { parts += "Notifications: ${prefLabel(it)}" }
        s.backgroundSync?.let { parts += if (it) "Background sync on" else "Background sync off" }
    }
    if (desktop) parts += "Desktop site"
    return if (parts.isEmpty()) "Custom settings" else parts.joinToString(" · ")
}

private fun prefLabel(pref: SitePermissionPref): String = when (pref) {
    SitePermissionPref.ASK -> "Ask"
    SitePermissionPref.ALLOW -> "Allow"
    SitePermissionPref.BLOCK -> "Block"
}
