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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.click.browser.engine.vpn.VpnConfigBuilder
import com.click.browser.engine.vpn.VpnController
import com.click.browser.engine.vpn.VlessLink
import com.click.browser.engine.vpn.VpnServer
import com.click.browser.engine.vpn.VpnState
import kotlinx.coroutines.launch

/**
 * Click VPN — server list, connect toggle, status, DNS leak test.
 *
 * HONEST LIMITATIONS (shown in UI, not hidden):
 * - The built-in server sits in one country; you cannot pick a country
 *   with it. Country selection needs your own server (paste a
 *   vless://, vmess://, trojan:// or ss:// link, import it from the
 *   clipboard, or fill the manual VLESS form field by field).
 * - Free public servers from the internet are slow and unreliable.
 * - Fast, stable USA/UK exits need a paid VPN or a self-hosted server.
 *
 * Play policy: prominent disclosure + explicit acknowledge before first use.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
    var editingServer by remember { mutableStateOf<VpnServer?>(null) }
    var starting by remember { mutableStateOf(false) }

    // The service owns the truth: once it reports any state change, the
    // local "starting" spinner must stop. (Previously an exception between
    // start() returning true and the service coming up left the spinner
    // spinning on "Disconnected" forever.)
    LaunchedEffect(vpnState) {
        if (vpnState != VpnState.DISCONNECTED) starting = false
    }

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
                // start() never throws, but guard anyway — the spinner
                // must never get stuck.
                if (!runCatching { VpnController.start(context) }.getOrDefault(false)) {
                    starting = false
                }
            }
        }
    }

    fun setVpn(on: Boolean) {
        if (on) {
            if (acked != true) return // disclosure dialog blocks
            if (VpnController.isBlockedByV9Shield()) {
                VpnController.reportV9ShieldConflict()
                return
            }
            starting = true
            scope.launch {
                try {
                    val intent = VpnController.prepareIntent(context)
                    if (intent == null) {
                        if (!VpnController.start(context)) starting = false
                    } else {
                        // Permission needed — launcher resets `starting` on result.
                        vpnPermissionLauncher.launch(intent)
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("ClickVpn", "setVpn failed", t)
                    starting = false
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
                                VpnState.CONNECTED -> "Tunnel active"
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
                PrivacyDisclosurePolicy.VPN_DNS_LEAK_TEST_GUIDANCE,
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
                    onEdit = if (server.kind == VpnServer.ServerKind.CUSTOM) {
                        { editingServer = server }
                    } else null,
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

            // Server location note
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Note: the built-in server is in Canada — country " +
                    "selection is not available with it. For a fixed " +
                    "country, add your own server below " +
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
        ServerEditorDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, url, onResult ->
                scope.launch {
                    val saved = runCatching {
                        VpnController.addCustomServer(context, name, url)
                    }.getOrDefault(false)
                    if (saved) {
                        showAddDialog = false
                        refresh()
                        onResult(null)
                    } else {
                        onResult("Couldn't save this server. Check the link and try again.")
                    }
                }
            }
        )
    }

    editingServer?.let { server ->
        ServerEditorDialog(
            initial = server,
            onDismiss = { editingServer = null },
            onSave = { name, url, onResult ->
                scope.launch {
                    val saved = runCatching {
                        VpnController.updateCustomServer(context, server.id, name, url)
                    }.getOrDefault(false)
                    if (saved) {
                        editingServer = null
                        refresh()
                        onResult(null)
                    } else {
                        onResult("Couldn't save this server. Check the link and try again.")
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
    onEdit: (() -> Unit)?,
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
                        VpnServer.ServerKind.BUILT_IN_VLESS -> "Fixed location • Canada"
                        VpnServer.ServerKind.CUSTOM ->
                            VlessLink.parse(server.config)?.summary() ?: "Custom server"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary)
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
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
private fun ServerTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder) }) else null,
        modifier = Modifier.fillMaxWidth(), singleLine = true
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ServerFieldDropdown(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(value); expanded = false }
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * Add/edit dialog for custom servers (B4). Two ways in, HA Tunnel style:
 * paste a share link (with clipboard import), or fill the manual VLESS
 * form field by field — both end up as the same validated share link.
 * The manual form only covers VLESS; other schemes edit as raw links.
 */
@Composable
private fun ServerEditorDialog(
    initial: VpnServer?,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, onResult: (String?) -> Unit) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val initialVless = remember { initial?.let { VlessLink.parse(it.config) } }
    val manualAvailable = initial == null || initialVless != null
    var manualMode by remember { mutableStateOf(initial != null && initialVless != null) }
    var name by remember { mutableStateOf(initial?.name ?: initialVless?.name ?: "") }
    var url by remember { mutableStateOf(initial?.config ?: "") }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    // Manual VLESS form fields.
    var fUuid by remember { mutableStateOf(initialVless?.uuid ?: "") }
    var fHost by remember { mutableStateOf(initialVless?.host ?: "") }
    var fPort by remember { mutableStateOf((initialVless?.port ?: 443).toString()) }
    var fFlow by remember { mutableStateOf(initialVless?.flow ?: "") }
    var fNetwork by remember { mutableStateOf(initialVless?.network ?: "tcp") }
    var fSecurity by remember { mutableStateOf(initialVless?.security ?: "reality") }
    var fSni by remember { mutableStateOf(initialVless?.sni ?: "") }
    var fFingerprint by remember { mutableStateOf(initialVless?.fingerprint ?: "") }
    var fPublicKey by remember { mutableStateOf(initialVless?.publicKey ?: "") }
    var fShortId by remember { mutableStateOf(initialVless?.shortId ?: "") }
    var fPath by remember { mutableStateOf(initialVless?.path ?: "") }
    var fHostHeader by remember { mutableStateOf(initialVless?.hostHeader ?: "") }
    var fServiceName by remember { mutableStateOf(initialVless?.serviceName ?: "") }

    fun submit() {
        val finalUrl: String
        if (manualMode) {
            val port = fPort.trim().toIntOrNull()
            if (fUuid.isBlank() || fHost.isBlank()) {
                error = "UUID and server address are required."
                return
            }
            if (port == null || port !in 1..65535) {
                error = "Port must be a number between 1 and 65535."
                return
            }
            if (fSecurity == "reality" && fPublicKey.isBlank()) {
                error = "Reality servers need the public key (pbk)."
                return
            }
            finalUrl = VlessLink(
                uuid = fUuid.trim(),
                host = fHost.trim(),
                port = port,
                name = name.trim(),
                flow = fFlow,
                network = fNetwork,
                security = fSecurity,
                sni = fSni.trim(),
                fingerprint = fFingerprint.trim(),
                publicKey = fPublicKey.trim(),
                shortId = fShortId.trim(),
                path = fPath.trim(),
                hostHeader = fHostHeader.trim(),
                serviceName = fServiceName.trim()
            ).toShareLink()
        } else {
            finalUrl = url.trim()
            if (finalUrl.isBlank()) {
                error = "Paste a proxy URL first."
                return
            }
        }
        if (runCatching { VpnConfigBuilder.buildCustomConfig(finalUrl) }.getOrNull() == null) {
            error = "This server isn't valid or supported. Use vless://, vmess://, trojan://, or ss://."
            return
        }
        saving = true
        error = ""
        onSave(name.trim(), finalUrl) { failure ->
            saving = false
            if (failure != null) error = failure
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                Modifier.padding(20.dp)
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (initial == null) "Add custom server" else "Edit server",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                if (manualAvailable) {
                    Row(Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = !manualMode,
                            onClick = { manualMode = false; error = "" },
                            label = { Text("Paste link") }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = manualMode,
                            onClick = { manualMode = true; error = "" },
                            label = { Text("Manual form (VLESS)") }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                ServerTextField("Name (e.g. My USA server)", name, { name = it })
                if (manualMode) {
                    ServerTextField("Server address", fHost, { fHost = it }, "node.example.com or 203.0.113.10")
                    ServerTextField("Port", fPort, { fPort = it.filter { c -> c.isDigit() } }, "443")
                    ServerTextField("UUID", fUuid, { fUuid = it })
                    ServerFieldDropdown(
                        "Transport",
                        listOf("tcp" to "TCP", "ws" to "WebSocket", "grpc" to "gRPC"),
                        fNetwork
                    ) { fNetwork = it }
                    ServerFieldDropdown(
                        "Security",
                        listOf("reality" to "Reality", "tls" to "TLS", "none" to "None"),
                        fSecurity
                    ) { fSecurity = it }
                    ServerFieldDropdown(
                        "Flow",
                        listOf("" to "None", "xtls-rprx-vision" to "xtls-rprx-vision"),
                        fFlow
                    ) { fFlow = it }
                    if (fSecurity != "none") {
                        ServerTextField("SNI", fSni, { fSni = it }, "www.example.com")
                        ServerTextField("Fingerprint", fFingerprint, { fFingerprint = it }, "chrome")
                    }
                    if (fSecurity == "reality") {
                        ServerTextField("Reality public key (pbk)", fPublicKey, { fPublicKey = it })
                        ServerTextField("Reality short ID (sid)", fShortId, { fShortId = it })
                    }
                    if (fNetwork == "ws") {
                        ServerTextField("WebSocket path", fPath, { fPath = it }, "/")
                        ServerTextField("WebSocket Host header", fHostHeader, { fHostHeader = it })
                    }
                    if (fNetwork == "grpc") {
                        ServerTextField("gRPC service name", fServiceName, { fServiceName = it })
                    }
                } else {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it; error = "" },
                        label = { Text("Proxy URL") },
                        placeholder = { Text("vless://… / vmess://… / trojan://… / ss://…") },
                        modifier = Modifier.fillMaxWidth(), minLines = 3
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let {
                                url = it.trim()
                                error = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import from clipboard")
                    }
                }
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = !saving, onClick = { submit() }) {
                        Text(if (saving) "Saving…" else if (initial == null) "Add" else "Save")
                    }
                }
            }
        }
    }
}
