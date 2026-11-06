package com.click.browser.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.click.browser.R
import com.click.browser.engine.ModeTheme

private data class ThirdPartyNotice(
    val name: String,
    val version: String,
    val description: String,
    val referenceUrl: String
)

private val THIRD_PARTY_NOTICES = listOf(
    ThirdPartyNotice(
        name = "AndroidX / Jetpack",
        version = "Multiple versions",
        description = "Core, Compose, DataStore, WebKit, Biometric, WorkManager, Credentials, Browser, DocumentFile and Security Crypto.",
        referenceUrl = "https://github.com/androidx/androidx"
    ),
    ThirdPartyNotice(
        name = "AndroidX Media3",
        version = "1.4.1",
        description = "Media playback components.",
        referenceUrl = "https://github.com/androidx/media"
    ),
    ThirdPartyNotice(
        name = "Mozilla GeckoView",
        version = "158.0.20261021001945",
        description = "Gecko rendering engine for the optional extension browser. Mozilla Public License 2.0.",
        referenceUrl = "https://www.mozilla.org/en-US/MPL/2.0/"
    ),
    ThirdPartyNotice(
        name = "Kotlin",
        version = "1.9.22",
        description = "Language and runtime.",
        referenceUrl = "https://github.com/JetBrains/kotlin"
    ),
    ThirdPartyNotice(
        name = "OkHttp",
        version = "4.12.0",
        description = "HTTP client used for selected app network features.",
        referenceUrl = "https://github.com/square/okhttp"
    ),
    ThirdPartyNotice(
        name = "Google ML Kit Translate",
        version = "17.0.3",
        description = "On-device translation SDK; language models download when requested.",
        referenceUrl = "https://developers.google.com/ml-kit/language/translation/android"
    ),
    ThirdPartyNotice(
        name = "Google Play Services Auth",
        version = "21.2.0",
        description = "Google account sign-in integration.",
        referenceUrl = "https://developers.google.com/identity/sign-in/android/start-integrating"
    ),
    ThirdPartyNotice(
        name = "sing-box libbox",
        version = "v1.14.3",
        description = "VPN core, built from the official source release.",
        referenceUrl = "https://github.com/SagerNet/sing-box/tree/v1.14.3"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(
    theme: ModeTheme,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val clickLicense = remember(context) {
        context.resources.openRawResource(R.raw.click_license)
            .bufferedReader()
            .use { it.readText() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Licenses", fontWeight = FontWeight.Bold, color = theme.onSurface) },
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
            LicenseCard(theme) {
                Text("Click Browser source code · MIT License", color = theme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(clickLicense, color = theme.onSurface.copy(alpha = 0.78f), fontSize = 11.sp, lineHeight = 16.sp)
            }

            LicenseCard(theme) {
                Text("Third-party components and services", color = theme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Their licenses or service terms are separate from Click's MIT license. These links point to upstream project or product information; consult the notices for the exact version used.",
                    color = theme.onSurface.copy(alpha = 0.68f), fontSize = 12.sp, lineHeight = 18.sp
                )
            }

            THIRD_PARTY_NOTICES.forEach { notice ->
                LicenseCard(theme) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(notice.name, color = theme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(notice.version, color = theme.onSurface.copy(alpha = 0.55f), fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(notice.description, color = theme.onSurface.copy(alpha = 0.68f), fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        TextButton(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(notice.referenceUrl)))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Couldn't open the upstream page", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Open upstream page", modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseCard(theme: ModeTheme, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}
