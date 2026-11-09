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
import com.click.browser.engine.ConnectionSecurityInfo
import com.click.browser.engine.ConnectionSecurityStatus
import com.click.browser.engine.CredentialVault
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.SafetyCheck
import com.click.browser.engine.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Privacy & Security overview plus Click account, password manager, Safety Check,
 * autofill and payment methods.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySecurityScreen(
    theme: ModeTheme,
    currentUrl: String,
    connectionSecurity: ConnectionSecurityInfo,
    adBlockStatusLabel: String,
    adBlockStatusActive: Boolean,
    dnsProtectionActive: Boolean,
    isPrivateTab: Boolean,
    currentSiteControlsAvailable: Boolean,
    onOpenPaymentMethods: () -> Unit,
    onOpenPasswords: () -> Unit,
    onOpenCurrentSiteSettings: () -> Unit,
    onOpenAllSiteSettings: () -> Unit,
    onOpenCookieManager: () -> Unit,
    onOpenV9Shield: () -> Unit,
    onOpenPrivacyGuards: () -> Unit,
    onOpenAdvancedSecurity: () -> Unit,
    onClearAllCurrentModeData: () -> Unit,
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
    var checkError by remember { mutableStateOf<String?>(null) }

    var vaultMessage by remember { mutableStateOf<String?>(null) }
    var showSavePasswordDialog by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }
    var showPrivateStorageDetails by remember { mutableStateOf(false) }

    val strictDns by context.dataStore.data
        .map { prefs -> AppSettings.strictDnsEnabled(prefs) }
        .collectAsState(initial = false)
    val currentPageOverview = remember(currentUrl, connectionSecurity) {
        PrivacyDashboardPolicy.currentPage(currentUrl, connectionSecurity)
    }

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
        // Product default when unset: ON. But on READ ERROR we fail CLOSED
        // (OFF) — a privacy-sensitive feature must not default on when we
        // can't verify the stored preference.
        autofillEnabled = try {
            context.dataStore.data.map { prefs -> prefs[AppSettings.AUTOFILL_ENABLED] }.first() ?: true
        } catch (_: Exception) { false }
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
            // ============ PROTECTION OVERVIEW ==========
            PrivCard(theme) {
                PrivCardTitle("Protection overview", Icons.Default.Shield, theme)
                if (currentPageOverview == null) {
                    Text(
                        "Open an HTTP or HTTPS website to see page-specific connection and ad/tracker status.",
                        fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.68f)
                    )
                } else {
                    Text(
                        "Current site · ${currentPageOverview.host}",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = theme.onSurface
                    )
                    Text(
                        currentPageOverview.connectionTitle,
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = when (currentPageOverview.connectionStatus) {
                            ConnectionSecurityStatus.SECURE_HTTPS -> theme.primary
                            ConnectionSecurityStatus.CERTIFICATE_WARNING -> MaterialTheme.colorScheme.error
                            else -> theme.onSurface.copy(alpha = 0.78f)
                        }
                    )
                    Text(
                        currentPageOverview.connectionDescription,
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.62f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Ad & tracker blocking", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = theme.onSurface)
                    Text(
                        adBlockStatusLabel,
                        fontSize = 12.sp,
                        color = if (adBlockStatusActive) theme.primary else theme.onSurface.copy(alpha = 0.68f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = theme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(8.dp))
                Text(
                    if (dnsProtectionActive) "V9 Shield · ON · DNS only, not a VPN" else "V9 Shield · OFF",
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = if (dnsProtectionActive) theme.primary else theme.onSurface.copy(alpha = 0.72f)
                )
                Text(
                    PrivacyDisclosurePolicy.v9ShieldDnsDisclosure(dnsProtectionActive, strictDns),
                    fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.62f)
                )
                PrivActionRow(
                    "DNS protection settings",
                    "Provider, strict-DNS behavior and live status",
                    Icons.Default.Shield, theme, onOpenV9Shield
                )
                PrivActionRow(
                    "Safe Browsing & security checks",
                    "View the supported WebView status and run on-device checks",
                    Icons.Default.VerifiedUser, theme, onOpenAdvancedSecurity
                )
            }

            // ============ SITE PERMISSIONS & DATA =======
            PrivCard(theme) {
                PrivCardTitle("Site permissions & data", Icons.Default.Security, theme)
                Text(
                    PrivacyDashboardPolicy.WEBSITE_PERMISSION_SCOPE_DESCRIPTION,
                    fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.68f), lineHeight = 17.sp
                )
                if (currentPageOverview != null && currentSiteControlsAvailable) {
                    PrivActionRow(
                        "Current site details",
                        "Connection, supported permissions and available site controls",
                        Icons.Default.Lock, theme, onOpenCurrentSiteSettings
                    )
                } else if (currentPageOverview == null) {
                    Text(
                        "Open a supported website to view its site-specific controls.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.58f)
                    )
                } else if (isPrivateTab) {
                    Text(
                        "Profile-wide site controls are not opened from a private WebView tab. Private-tab website choices are temporary where supported.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.58f)
                    )
                }
                if (!isPrivateTab) {
                    PrivActionRow(
                        "All site settings",
                        "Review or reset saved choices for this browser mode",
                        Icons.Default.Shield, theme, onOpenAllSiteSettings
                    )
                    PrivActionRow(
                        "Cookie manager",
                        "Inspect or delete WebView cookies for the current profile",
                        Icons.Default.Cookie, theme, onOpenCookieManager
                    )
                    OutlinedButton(
                        onClick = { showClearDataConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear supported data for this mode")
                    }
                } else {
                    Text(
                        "Cookie and profile-data controls are available from a regular tab. Private tabs use the profile WebView store, so they are not a separate data-wipe boundary.",
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.58f)
                    )
                }
                PrivActionRow(
                    "Privacy signals & WebRTC test",
                    "Manage optional DNT/GPC signals and run the best-effort test",
                    Icons.Default.Security, theme, onOpenPrivacyGuards
                )
            }

            // ============ PRIVATE BROWSING DISCLOSURE ===
            PrivCard(theme) {
                PrivCardTitle("Private browsing", Icons.Default.VisibilityOff, theme)
                Text(
                    PrivateBrowsingDisclosurePolicy.aboutPrivateTabStorageDescription + " Private tabs are not anonymous.",
                    fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.68f), lineHeight = 17.sp
                )
                TextButton(onClick = { showPrivateStorageDetails = !showPrivateStorageDetails }) {
                    Text(if (showPrivateStorageDetails) "Hide storage details" else "Show storage details")
                }
                if (showPrivateStorageDetails) {
                    Text(
                        PrivateBrowsingDisclosurePolicy.privateTabStorageDescription,
                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.62f), lineHeight = 16.sp
                    )
                }
            }

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
                    "Google Password Manager is synced with your Google account via Android Credential Manager. " +
                        "Its entries are system-managed and separate from Click's on-device, site-scoped vault.",
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
                    "Checks Safe Browsing, HTTPS, the password manager and tracker-link stripping.",
                    fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        scope.launch {
                            checking = true
                            checkError = null
                            checks = try {
                                SafetyCheck.run(context)
                            } catch (e: Exception) {
                                // Never fail silently: show the error with a
                                // retry affordance instead of a blank card.
                                checkError = "Safety Check couldn't run: ${(e.message ?: "unknown error").take(100)}"
                                null
                            }
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
                    Text(if (checking) "Checking…" else if (checkError != null) "Retry Safety Check" else "Run Safety Check")
                }
                if (checkError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(checkError!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                checks?.let { list ->
                    Spacer(Modifier.height(8.dp))
                    val protectionChecks = SafetyCheck.protectionChecks(list)
                    val passed = protectionChecks.count { it.passed }
                    val informationalCount = list.count { it.informational }
                    Text(
                        "$passed of ${protectionChecks.size} protection checks passed" +
                            if (informationalCount > 0) " · $informationalCount informational" else "",
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = if (passed == protectionChecks.size) theme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    list.forEach { check ->
                        SafetyCheckRow(check, theme, onFix = {
                            scope.launch {
                                if (SafetyCheck.applyFix(context, check.id)) {
                                    checks = try {
                                        SafetyCheck.run(context)
                                    } catch (e: Exception) {
                                        checkError = "Re-check failed: ${(e.message ?: "unknown error").take(100)}"
                                        checks
                                    }
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

    if (showClearDataConfirm && !isPrivateTab) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text("Clear supported browsing data?") },
            text = {
                Text(
                    "This clears cookies and the active WebView cache, requests Gecko profile-data cleanup, and clears history and recently closed tabs for the current mode's history scope. Other profiles or data outside these supported stores may remain. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDataConfirm = false
                    onClearAllCurrentModeData()
                }) { Text("Clear data", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirm = false }) { Text("Cancel") }
            }
        )
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
    val icon = when {
        check.informational -> Icons.Default.Info
        check.passed -> Icons.Default.CheckCircle
        else -> Icons.Default.Error
    }
    val iconTint = when {
        check.informational -> theme.onSurface.copy(alpha = 0.55f)
        check.passed -> theme.primary
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = if (check.informational) "Information" else if (check.passed) "Passed" else "Needs attention",
            tint = iconTint,
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
    // Client-side validation: don't even offer Save while a field is empty —
    // avoids a pointless round-trip into the system credential UI.
    val canSave = username.isNotBlank() && password.isNotBlank()
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
            Button(
                onClick = { onSave(username.trim(), password) },
                enabled = canSave
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
