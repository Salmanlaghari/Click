package com.click.browser.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.BuildConfig
import com.click.browser.engine.ModeTheme

/**
 * Browser Help (FAQ/guide) + Feedback (email to Prince).
 *
 * Help answers common questions about the 3 V9 engines, privacy features,
 * and AI. Feedback composes an email to lovefaillaghari@gmail.com with
 * device/app info pre-filled.
 */
private const val FEEDBACK_EMAIL = "lovefaillaghari@gmail.com"

private data class Faq(val q: String, val a: String)

private val FAQS = listOf(
    Faq(
        "What are the 3 modes (Simple / Developer / Hack)?",
        "Click is 1 browser with 3 isolated engines. Simple is the everyday " +
            "light mode (Google search). Developer is the dark-glass mode for " +
            "coders (DuckDuckGo search, dev tools). Hack is the OLED-neon " +
            "desktop-class mode (Brave search, desktop sites). Each engine has " +
            "separate cookies, cache, and presents a different device identity " +
            "to websites."
    ),
    Faq(
        "How do I switch modes?",
        "Open the menu (☰) and tap Simple, Developer, or Hack. The app " +
            "restarts into the new engine — your logins stay separate per mode."
    ),
    Faq(
        "What is V9 Shield?",
        "V9 Shield is DNS-layer protection: it encrypts your DNS queries " +
            "(DNS-over-HTTPS) and blocks known ad, tracker, and malware " +
            "domains. Tap the V9 Shield card on the home screen to toggle it. " +
            "Note: it does not change your IP address — that needs a full VPN."
    ),
    Faq(
        "How does Click AI work?",
        "Tap the AI button and ask anything: summarize pages, explain " +
            "articles, translate, or search. AI has a built-in safety filter " +
            "and every AI message has a report (🚩) button."
    ),
    Faq(
        "How do I save passwords?",
        "When you log into a site, Click offers to save the password. " +
            "It's encrypted with your device's secure hardware key and " +
            "auto-fills next time. Manage saved logins in Settings → Passwords."
    ),
    Faq(
        "What is click://flags?",
        "Type click://flags in the address bar to open the experimental " +
            "features lab — toggles for desktop mode, aggressive ad-blocking, " +
            "bottom address bar, and more. Also try click://version."
    ),
    Faq(
        "Is my browsing private?",
        "Each mode isolates cookies/cache/storage. Fingerprint protection " +
            "poisons canvas and audio fingerprinting. Secure DNS encrypts " +
            "lookups. Nothing is uploaded — AI prompts go to the AI provider " +
            "only when you use AI chat."
    ),
)

fun sendFeedbackEmail(context: Context) {
    val deviceInfo = """
        
        ----
        App: Click Browser ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
        Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})
    """.trimIndent()
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$FEEDBACK_EMAIL")
        putExtra(Intent.EXTRA_SUBJECT, "Click Browser Feedback")
        putExtra(Intent.EXTRA_TEXT, "Hi Prince,\n\n[Your feedback/suggestion here]$deviceInfo")
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Send feedback"))
    } catch (_: Exception) { /* no email app */ }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpFeedbackScreen(
    theme: ModeTheme,
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableStateOf(0) } // 0 = Help, 1 = Feedback

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Feedback", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = theme.background,
                contentColor = theme.primary
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Help") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Feedback") })
            }
            Spacer(Modifier.height(12.dp))

            if (tab == 0) {
                // ---- HELP / FAQ ----
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FAQS.forEach { faq ->
                        var expanded by remember { mutableStateOf(false) }
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surface),
                            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    faq.q,
                                    color = theme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                if (expanded) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        faq.a,
                                        color = theme.onSurface.copy(alpha = 0.75f),
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} · TEAM PK AI",
                        color = theme.onSurface.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            } else {
                // ---- FEEDBACK ----
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(Modifier.height(24.dp))
                    Text("💬", fontSize = 56.sp)
                    Text(
                        "Send Feedback",
                        color = theme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        "Found a bug or have an idea? Your message goes " +
                            "straight to Prince (the developer) with your " +
                            "device info attached.",
                        color = theme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { sendFeedbackEmail(context) },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text("Email $FEEDBACK_EMAIL")
                    }
                    Text(
                        "No account needed — opens your email app.",
                        color = theme.onSurface.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
