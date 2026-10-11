package com.click.browser.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import com.click.browser.engine.AppSettings
import com.click.browser.engine.ClickAccount
import com.click.browser.engine.ClickAccountManager
import com.click.browser.engine.CredentialVault
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.SafetyCheck
import com.click.browser.engine.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Privacy & Security hub: Click account (Google Sign-In), Google Password
 * Manager (Credential Manager), Safety Check, Autofill, Payment methods.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySecurityScreen(
    theme: ModeTheme,
    onOpenPaymentMethods: () -> Unit,
    onOpenPasswords: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var account by remember { mutableStateOf<ClickAccount?>(null) }
    var accountLoading by remember { mutableStateOf(true) }
    var accountError by remember { mutableStateOf<String?>(null) }

    var autofillEnabled by remember { mutableStateOf(true) }

    var checks by remember { mutableStateOf<List<SafetyCheck.Check>?>(null) }
    var checking by remember { mutableStateOf(false) }

    var vaultMessage by remember { mutableStateOf<String?>(null) }
    var showSavePasswordDialog by remember { mutableStateOf(false) }

    fun refreshAccount() {
        scope.launch {
            accountLoading = true
            accountError = null
            account = try {
                ClickAccountManager.cachedAccount(context)
            } catch (_: Exception) { null }
            accountLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshAccount()
        autofillEnabled = try {
            context.dataStore.data.map { prefs -> prefs[AppSettings.AUTOFILL_ENABLED] }.first() ?: true
        } catch (_: Exception) { true }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            accountLoading = true
            accountError = null
            try {
                val signed = ClickAccountManager.handleSignInResult(context, result.data)
                if (signed == null) {
                    accountError = "Sign-in cancelled"
                } else {
                    account = signed
                }
            } catch (e: Exception) {
                accountError = "Sign-in failed: ${(e.message ?: "unknown error").take(100)}"
            }
            accountLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Security", fontWeight = FontWeight.Bold, color = theme.onSurface) },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ============ CLICK ACCOUNT ============
            PrivCard(theme) {
                PrivCardTitle("Click Account", Icons.Default.AccountCircle, theme)
                Text(
                    "Sign in with your Google account to personalize Click. " +
                        "Sign-in happens on your device via Google — Click has no servers.",
                    fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(10.dp))
                when {
                    accountLoading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Checking account…", fontSize = 13.sp, color = theme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    account != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    account!!.name ?: "Google user",
                                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = theme.onSurface
                                )
                                Text(
                                    account!!.email ?: "",
                                    fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    try { ClickAccountManager.signOut(context) } catch (_: Exception) { }
                                    account = null
                                }
                            }) { Text("Sign out") }
                        }
                    }
                    else -> {
                        Button(
                            onClick = {
                                accountError = null
                                if (activity != null) {
                                    signInLauncher.launch(ClickAccountManager.signInIntent(activity))
                                } else {
                                    accountError = "Sign-in unavailable right now"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign in with Google")
                        }
                    }
                }
                if (accountError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(accountError!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            // ============ PASSWORDS ============
            PrivCard(theme) {
                PrivCardTitle("Passwords", Icons.Default.Key, theme)
                Text(
                    "Google Password Manager (synced with your Google account) via " +
                        "Android Credential Manager — plus Click's own on-device encrypted vault.",
                    fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showSavePasswordDialog = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save password", fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                vaultMessage = "Opening credential picker…"
                                when (val r = activity?.let { CredentialVault.getPassword(it) }
                                    ?: CredentialVault.VaultResult.Error("Unavailable")) {
                                    is CredentialVault.VaultResult.Ok ->
                                        vaultMessage = "Found login for “${r.value.username}” — use it on the site's sign-in form."
                                    CredentialVault.VaultResult.Cancelled ->
                                        vaultMessage = null
                                    is CredentialVault.VaultResult.Error ->
                                        vaultMessage = r.message
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Use saved", fontSize = 13.sp) }
                }
                if (vaultMessage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(vaultMessage!!, fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(4.dp))
                PrivActionRow(
                    "Click's on-device vault",
                    "Encrypted passwords stored only on this device",
                    Icons.Default.Lock, theme, onOpenPasswords
                )
            }

            // ============ SAFETY CHECK ============
            PrivCard(theme) {
                PrivCardTitle("Safety Check", Icons.Default.VerifiedUser, theme)
                Text(
                    "Scans Click's protections on this device — Safe Browsing, HTTPS, " +
                        "password manager, fingerprint protection, tracker stripping.",
                    fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        scope.launch {
                            checking = true
                            checks = try { SafetyCheck.run(context) } catch (_: Exception) { null }
                            checking = false
                        }
                    },
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (checking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (checking) "Checking…" else "Run Safety Check")
                }
                checks?.let { list ->
                    Spacer(Modifier.height(8.dp))
                    val passed = list.count { it.passed }
                    Text(
                        "$passed of ${list.size} checks passed",
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = if (passed == list.size) theme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    list.forEach { check ->
                        SafetyCheckRow(check, theme, onFix = {
                            scope.launch {
                                if (SafetyCheck.applyFix(context, check.id)) {
                                    checks = try { SafetyCheck.run(context) } catch (_: Exception) { checks }
                                }
                            }
                        }, onAction = {
                            if (check.id == "updates") SafetyCheck.openPlayListing(context)
                        })
                    }
                }
            }

            // ============ AUTOFILL ============
            PrivCard(theme) {
                PrivCardTitle("Autofill", Icons.Default.AutoAwesome, theme)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Autofill in Click", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = theme.onSurface)
                        Text(
                            "Offer to fill logins, addresses and payment methods on web forms.",
                            fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(checked = autofillEnabled, onCheckedChange = { on ->
                        autofillEnabled = on
                        scope.launch {
                            try {
                                context.dataStore.edit { prefs ->
                                    prefs[AppSettings.AUTOFILL_ENABLED] = on
                                }
                            } catch (_: Exception) { }
                        }
                    })
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            } catch (_: Exception) { }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choose system autofill service", fontSize = 13.sp) }
            }

            // ============ PAYMENT METHODS ============
            PrivCard(theme) {
                PrivActionRow(
                    "Payment methods",
                    "Cards stored encrypted on this device only — never uploaded",
                    Icons.Default.CreditCard, theme, onOpenPaymentMethods
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSavePasswordDialog && activity != null) {
        SavePasswordDialog(
            theme = theme,
            onDismiss = { showSavePasswordDialog = false },
            onSave = { username, password ->
                scope.launch {
                    when (val r = CredentialVault.savePassword(activity, username, password)) {
                        is CredentialVault.VaultResult.Ok ->
                            vaultMessage = "Saved to Google Password Manager ✓"
                        CredentialVault.VaultResult.Cancelled ->
                            vaultMessage = null
                        is CredentialVault.VaultResult.Error ->
                            vaultMessage = r.message
                    }
                    showSavePasswordDialog = false
                }
            }
        )
    }
}

@Composable
private fun PrivCard(theme: ModeTheme, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun PrivCardTitle(title: String, icon: ImageVector, theme: ModeTheme) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = theme.onSurface)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun PrivActionRow(
    title: String,
    desc: String,
    icon: ImageVector,
    theme: ModeTheme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(desc, fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f))
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
            tint = theme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SafetyCheckRow(
    check: SafetyCheck.Check,
    theme: ModeTheme,
    onFix: () -> Unit,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (check.passed) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (check.passed) theme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(check.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = theme.onSurface)
            Text(check.description, fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f))
        }
        when {
            check.fixLabel != null && !check.passed -> {
                TextButton(onClick = onFix) { Text(check.fixLabel, fontSize = 12.sp) }
            }
            check.actionLabel != null -> {
                TextButton(onClick = onAction) { Text(check.actionLabel, fontSize = 12.sp) }
            }
        }
    }
    HorizontalDivider(color = theme.onSurface.copy(alpha = 0.08f))
}

@Composable
private fun SavePasswordDialog(
    theme: ModeTheme,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to Google Password Manager", color = theme.onSurface, fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    "Saves into YOUR Google Password Manager (synced with your Google account).",
                    fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Username / email") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(username.trim(), password) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
