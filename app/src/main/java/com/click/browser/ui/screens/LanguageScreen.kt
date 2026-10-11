package com.click.browser.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import com.click.browser.engine.AppLanguage
import com.click.browser.engine.AppSettings
import com.click.browser.engine.MlKitTranslator
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.UiStrings
import com.click.browser.engine.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Language: app language (English / اردو / Roman Urdu) + on-device page
 * translation (ML Kit — models download on demand, never bundled).
 *
 * Changing the app language recreates the activity so the new locale
 * applies everywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(
    theme: ModeTheme,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()

    var appLang by remember { mutableStateOf(AppLanguage.ENGLISH) }
    var translateTarget by remember { mutableStateOf(MlKitTranslator.defaultTargetLanguage()) }
    var translateAuto by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val romanUrdu = appLang == AppLanguage.ROMAN

    LaunchedEffect(Unit) {
        try {
            val prefs = context.dataStore.data.first()
            appLang = AppLanguage.normalize(prefs[AppSettings.APP_LANGUAGE])
            translateTarget = prefs[AppSettings.TRANSLATE_TARGET]
                ?: MlKitTranslator.defaultTargetLanguage()
            translateAuto = prefs[AppSettings.TRANSLATE_AUTO] ?: false
        } catch (_: Exception) { }
        loaded = true
    }

    fun applyAppLanguage(code: String) {
        if (code == appLang || saving) return
        saving = true
        scope.launch {
            AppLanguage.set(context, code)
            // Recreate so attachBaseContext wraps the new locale.
            try { activity?.recreate() } catch (_: Exception) { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiStrings.s("Language", romanUrdu), fontWeight = FontWeight.Bold, color = theme.onSurface) },
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
            LangCard(theme) {
                Text("App language", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = theme.onSurface)
                Text(
                    "Applies everywhere after the app restarts its screen.",
                    fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                listOf(AppLanguage.ENGLISH, AppLanguage.URDU, AppLanguage.ROMAN).forEach { code ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = loaded && !saving) { applyAppLanguage(code) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = appLang == code, onClick = { applyAppLanguage(code) }, enabled = loaded && !saving)
                        Spacer(Modifier.width(8.dp))
                        Text(AppLanguage.displayName(code), fontSize = 14.sp, color = theme.onSurface)
                    }
                }
                if (saving) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Applying…", fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }

            LangCard(theme) {
                Text("Page translate", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = theme.onSurface)
                Text(
                    "On-device translation (ML Kit). Language models download on first use — nothing is bundled in the app.",
                    fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-translate pages", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = theme.onSurface)
                        Text(
                            "Offer translation when a page isn't in your language.",
                            fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = translateAuto,
                        enabled = loaded,
                        onCheckedChange = { on ->
                            translateAuto = on
                            scope.launch {
                                try { context.dataStore.edit { it[AppSettings.TRANSLATE_AUTO] = on } }
                                catch (_: Exception) { }
                            }
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                var expanded by remember { mutableStateOf(false) }
                Text("Translate to", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.onSurface)
                Spacer(Modifier.height(4.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = MlKitTranslator.languageName(translateTarget),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        MlKitTranslator.offeredLanguages.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    translateTarget = code
                                    expanded = false
                                    scope.launch {
                                        try { context.dataStore.edit { it[AppSettings.TRANSLATE_TARGET] = code } }
                                        catch (_: Exception) { }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LangCard(theme: ModeTheme, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}
