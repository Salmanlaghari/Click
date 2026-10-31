package com.click.browser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.webkit.WebViewFeature
import com.click.browser.BuildConfig
import com.click.browser.engine.ExperimentalFlags
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.SafeBrowsingManager
import com.click.browser.engine.SafetyCheck
import com.click.browser.engine.TamperCheck
import com.click.browser.engine.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSecurityScreen(
    theme: ModeTheme,
    onOpenSiteSettings: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val supported = remember {
        WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)
    }
    val threatsBlocked by SafeBrowsingManager.threatsBlocked.collectAsState(initial = 0)
    val pendingHit by SafeBrowsingManager.pendingHit.collectAsState(initial = null)

    var checks by remember { mutableStateOf<List<SafetyCheck.Check>?>(null) }
    var checking by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }
    var safeBrowsingEnabled by remember { mutableStateOf<Boolean?>(null) }
    var signatureValid by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        safeBrowsingEnabled = try {
            ExperimentalFlags.load(context.dataStore.data.first()).safeBrowsing
        } catch (_: Exception) {
            null
        }
        signatureValid = TamperCheck.isReleaseSignatureValid(context)
    }

    fun runSafetyCheck() {
        scope.launch {
            checking = true
            checkError = null
            checks = try {
                SafetyCheck.run(context)
            } catch (e: Exception) {
                checkError = "Safety Check couldn't run: ${(e.message ?: "unknown error").take(100)}"
                null
            }
            checking = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Security", fontWeight = FontWeight.Bold, color = theme.onSurface) },
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
            SecurityCard(theme) {
                SecurityTitle("Safety Check", Icons.Default.VerifiedUser, theme)
                Text(
                    "Run the browser's on-device checks for Safe Browsing, HTTPS, password storage, tracker-link stripping and updates.",
                    fontSize = 12.sp,
                    color = theme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = ::runSafetyCheck, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
                    if (checking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(if (checking) "Checking…" else if (checks == null && checkError != null) "Retry Safety Check" else "Run Safety Check")
                }
                checkError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                checks?.let { results ->
                    Spacer(Modifier.height(8.dp))
                    val passed = results.count { it.passed }
                    Text(
                        "$passed of ${results.size} checks passed",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (passed == results.size) theme.primary else MaterialTheme.colorScheme.error
                    )
                    results.forEach { check ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = if (check.passed) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = if (check.passed) "Passed" else "Needs attention",
                                tint = if (check.passed) theme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(check.title, color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(check.description, color = theme.onSurface.copy(alpha = 0.65f), fontSize = 11.sp)
                                check.actionLabel?.let { label ->
                                    OutlinedButton(onClick = { SafetyCheck.openPlayListing(context) }) {
                                        Text(label)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SecurityCard(theme) {
                SecurityTitle("Safe Browsing", Icons.Default.Shield, theme)
                val status = when {
                    !supported -> "Not supported by this WebView"
                    safeBrowsingEnabled == null -> "Status unavailable"
                    safeBrowsingEnabled == false -> "Disabled in browser configuration"
                    else -> "Enabled on this supported WebView"
                }
                Text(status, color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    when {
                        !supported -> "This WebView does not expose the Safe Browsing setting."
                        safeBrowsingEnabled == false -> "The browser's saved configuration has Safe Browsing turned off."
                        safeBrowsingEnabled == null -> "The saved browser configuration could not be read."
                        else -> "WebView Safe Browsing is configured on. Click handles any warning through its safety interstitial."
                    },
                    color = theme.onSurface.copy(alpha = 0.65f), fontSize = 12.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (pendingHit != null) "A Safe Browsing warning is waiting for your choice."
                    else "$threatsBlocked warning${if (threatsBlocked == 1) "" else "s"} reported this app session.",
                    color = theme.onSurface.copy(alpha = 0.75f), fontSize = 12.sp
                )
            }

            SecurityCard(theme) {
                SecurityTitle("Per-site settings", Icons.Default.Shield, theme)
                Text(
                    "Review saved site choices for the current browser mode.",
                    color = theme.onSurface.copy(alpha = 0.65f), fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenSiteSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open site settings")
                }
            }

            SecurityCard(theme) {
                SecurityTitle("App integrity", Icons.Default.VerifiedUser, theme)
                val signatureStatus = when {
                    signatureValid == null -> "Checking installation signature…"
                    BuildConfig.DEBUG -> "Debug build — the release signature check is bypassed by design."
                    signatureValid == true -> "Recognized application signing certificate."
                    else -> "The application signature failed verification or could not be checked."
                }
                Text(signatureStatus, color = theme.onSurface, fontSize = 13.sp)
                Text(
                    "This is the app's existing signing-certificate check; it is not a guarantee against every form of tampering.",
                    color = theme.onSurface.copy(alpha = 0.65f), fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SecurityCard(theme: ModeTheme, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
private fun SecurityTitle(title: String, icon: ImageVector, theme: ModeTheme) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
        Text(title, modifier = Modifier.padding(start = 8.dp), color = theme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
