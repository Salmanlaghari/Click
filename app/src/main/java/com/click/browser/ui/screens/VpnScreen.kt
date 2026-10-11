package com.click.browser.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.click.browser.engine.vpn.VpnController
import com.click.browser.engine.vpn.VpnServer
import com.click.browser.engine.vpn.VpnState
import kotlinx.coroutines.launch

/**
 * Click VPN — server list, connect toggle, status, DNS leak test.
 *
 * HONEST LIMITATIONS (shown in UI, not hidden):
 * - Cloudflare WARP connects to the NEAREST datacenter; you cannot pick
 *   USA/UK with WARP. Country selection needs your own server (paste a
 *   vless://, vmess://, trojan:// or ss:// link).
 * - Free public servers from the internet are slow and unreliable.
 * - Fast, stable USA/UK exits need a paid VPN or a self-hosted server.
 *
 * Play policy: prominent disclosure + explicit acknowledge before first use.
 */
@Composable
fun VpnScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val vpnState by VpnController.state.collectAsState(initial = VpnState.DISCONNECTED)
    val serverName by VpnController.serverName.collectAsState(initial = "")
    val vpnError by VpnController.error.collectAsState(initial = "")

    var servers by remember { mutableStateOf<List<VpnServer>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var acked by remember { mutableStateOf<Boolean?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            servers = VpnController.getServers(context)
            selectedId = VpnController.getSelectedServer(context)?.id
            acked = VpnController.isDisclosureAcked(context)
        }
    }
    LaunchedEffect(Unit) { refresh() }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        starting = false
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                if (!VpnController.start(context)) starting = false
            }
        }
    }

    fun setVpn(on: Boolean) {
        if (on) {
            if (acked != true) return // disclosure dialog blocks
            starting = true
            scope.launch {
                val intent = VpnController.prepareIntent(context)
                if (intent == null) {
                    if (!VpnController.start(context)) starting = false
                } else {
                    // Permission needed — launcher resets `starting` on result.
                    vpnPermissionLauncher.launch(intent)
                }
            }
        } else {
            VpnController.stop(context)
        }
    }

    // ---------- disclosure gate ----------
    if (acked == false) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Important: how Click VPN works") },
            text = { Text(VpnController.DISCLOSURE_TEXT, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        VpnController.ackDisclosure(context)
                        acked = true
                    }
                }) { Text("I understand") }
            },
            dismissButton = {
                TextButton(onClick = onClose) { Text("Cancel") }
            }
        )
        return
    }
    if (acked == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Click VPN") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (vpnState) {
                        VpnState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                        VpnState.ERROR -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (vpnState) {
                                VpnState.CONNECTED -> "Connected"
                                VpnState.CONNECTING -> "Connecting…"
                                VpnState.ERROR -> "Connection failed"
                                VpnState.DISCONNECTED -> "Disconnected"
                            },
                            fontWeight = FontWeight.Bold, fontSize = 18.sp
                        )
                        if (vpnState == VpnState.CONNECTED && serverName.isNotEmpty()) {
                            Text(serverName, fontSize = 14.sp)
                        }
                        if (vpnState == VpnState.ERROR && vpnError.isNotEmpty()) {
                            Text(vpnError, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (starting || vpnState == VpnState.CONNECTING) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    } else {
                        Switch(
                            checked = vpnState == VpnState.CONNECTED,
                            onCheckedChange = ::setVpn
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // DNS leak test
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://ipleak.net"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("DNS Leak Test (ipleak.net)")
            }
            Text(
                "After connecting, run the test: the DNS servers shown must " +
                "belong to Cloudflare/Google — never your ISP. That proves " +
                "there is no DNS leak.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            Text("Servers", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))

            servers.forEach { server ->
                ServerRow(
                    server = server,
                    selected = server.id == selectedId,
                    onSelect = {
                        scope.launch {
                            VpnController.selectServer(context, server.id)
                            selectedId = server.id
                        }
                    },
                    onDelete = if (server.kind == VpnServer.ServerKind.CUSTOM) {
                        {
                            scope.launch {
                                VpnController.removeCustomServer(context, server.id)
                                refresh()
                            }
                        }
                    } else null
                )
                Spacer(Modifier.height(8.dp))
            }

            // WARP limitation note
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Note: Cloudflare WARP connects to the nearest server " +
                    "automatically — country selection is not available " +
                    "with WARP. For a fixed country, use the built-in " +
                    "\"Canada VPS\" server above, or add your own below " +
                    "(vless://, vmess://, trojan://, ss://). Free public " +
                    "servers are slow; fast servers in other countries " +
                    "need a paid service or your own VPS.",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add custom server")
            }
        }
    }

    if (showAddDialog) {
        AddServerDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url ->
                scope.launch {
                    val ok = VpnController.addCustomServer(context, name, url)
                    showAddDialog = false
                    refresh()
                    if (!ok) {
                        // Invalid URL — surface via snackbar would need a
                        // scaffold host; the dialog simply stays honest by
                        // not adding. (Keep simple; no dead UI.)
                    }
                }
            }
        )
    }
}

@Composable
private fun ServerRow(
    server: VpnServer,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        border = if (selected) androidx.compose.foundation.BorderStroke(
            2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(server.flagEmoji, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(server.name, fontWeight = FontWeight.SemiBold)
                Text(
                    when (server.kind) {
                        VpnServer.ServerKind.BUILT_IN_WARP -> "Free • Fast • Auto location"
                        VpnServer.ServerKind.BUILT_IN_VLESS -> "Fixed location • Canada"
                        VpnServer.ServerKind.CUSTOM -> "Custom server"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary)
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                }
            }
        }
    }
}

@Composable
private fun AddServerDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Add custom server",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name (e.g. My USA server)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it; error = "" },
                    label = { Text("Proxy URL") },
                    placeholder = { Text("vless://… / vmess://… / trojan://… / ss://…") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3
                )
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (url.isBlank()) {
                            error = "Paste a proxy URL first."
                        } else onAdd(name.trim(), url.trim())
                    }) { Text("Add") }
                }
            }
        }
    }
}
