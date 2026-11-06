package com.click.browser.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.PaymentCard
import com.click.browser.engine.SecureCardStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Payment methods: cards encrypted on-device (EncryptedSharedPreferences).
 * Only the last 4 digits are ever displayed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodsScreen(
    theme: ModeTheme,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cards by remember { mutableStateOf<List<PaymentCard>>(emptyList()) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PaymentCard?>(null) }
    var confirmDelete by remember { mutableStateOf<PaymentCard?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        // Encrypted-prefs reads involve Keystore crypto — never on the UI thread.
        scope.launch {
            loadError = null
            cards = try {
                withContext(Dispatchers.IO) { SecureCardStore.listCards(context) }
            } catch (e: Exception) {
                // Distinguish corruption from "no cards": the store throws
                // IllegalStateException on unreadable/corrupt data.
                loadError = (e.message ?: "Couldn't load saved cards").take(120)
                emptyList()
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Methods", fontWeight = FontWeight.Bold, color = theme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.topBarBg)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = null; showEditor = true },
                containerColor = theme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add card", tint = theme.surface)
            }
        },
        containerColor = theme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(14.dp)
        ) {
            Text(
                "Stored encrypted on this device only — Click never uploads card details. " +
                    "Only the last 4 digits are shown.",
                fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(12.dp))
            if (loadError != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Couldn't load saved cards",
                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                loadError!!,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                        }
                        TextButton(onClick = { refresh() }) { Text("Retry", fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (cards.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CreditCard, contentDescription = null,
                            tint = theme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("No payment methods yet", color = theme.onSurface.copy(alpha = 0.5f), fontSize = 14.sp)
                        Text("Tap + to add one", color = theme.onSurface.copy(alpha = 0.4f), fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(cards, key = { it.id }) { card ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CreditCard, contentDescription = null,
                                    tint = theme.primary, modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(card.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = theme.onSurface)
                                    Text(card.masked, fontSize = 14.sp, color = theme.onSurface.copy(alpha = 0.8f))
                                    Text(
                                        "${card.cardholder} • Exp ${card.expiry}",
                                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.55f)
                                    )
                                }
                                IconButton(onClick = { editing = card; showEditor = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = theme.onSurface.copy(alpha = 0.6f))
                                }
                                IconButton(onClick = { confirmDelete = card }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        CardEditorDialog(
            theme = theme,
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { card ->
                scope.launch {
                    try {
                        if (editing == null) SecureCardStore.addCard(context, card)
                        else SecureCardStore.updateCard(context, card.copy(id = editing!!.id))
                    } catch (_: Exception) { }
                    refresh()
                    showEditor = false
                }
            }
        )
    }

    confirmDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete card?") },
            text = { Text("“${card.label}” (${card.masked}) will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try { SecureCardStore.deleteCard(context, card.id) } catch (_: Exception) { }
                        refresh()
                        confirmDelete = null
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CardEditorDialog(
    theme: ModeTheme,
    initial: PaymentCard?,
    onDismiss: () -> Unit,
    onSave: (PaymentCard) -> Unit
) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var cardholder by remember { mutableStateOf(initial?.cardholder ?: "") }
    var number by remember { mutableStateOf(initial?.number ?: "") }
    var expiry by remember { mutableStateOf(initial?.expiry ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add card" else "Edit card", color = theme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it; error = null },
                    label = { Text("Label (e.g. Personal)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cardholder, onValueChange = { cardholder = it; error = null },
                    label = { Text("Cardholder name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = number, onValueChange = { number = it; error = null },
                    label = { Text("Card number") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = expiry, onValueChange = { expiry = it; error = null },
                    label = { Text("Expiry (MM/YY)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("e.g. 01/25") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Text(
                    "Encrypted with your device's secure key before storage.",
                    fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.5f)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                // Normalize flexible input ("0125" -> "01/25") before validating.
                val expiryNorm = SecureCardStore.normalizeExpiry(expiry.trim())
                val problem = SecureCardStore.validate(label.trim(), cardholder.trim(), number, expiryNorm)
                if (problem != null) {
                    error = problem
                } else {
                    onSave(
                        PaymentCard(
                            label = label.trim(),
                            cardholder = cardholder.trim(),
                            number = number.filter { it.isDigit() },
                            expiry = expiryNorm
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
