package com.click.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.ClickInternalPages
import com.click.browser.engine.ExperimentalFlags
import com.click.browser.engine.ModeTheme

/**
 * Native host for `click://` internal pages (chrome://-style).
 * Rendered as a full-screen overlay with premium per-mode styling.
 */
@Composable
fun ClickPageHost(
    pageKey: String,
    theme: ModeTheme,
    flags: ExperimentalFlags,
    versionName: String,
    versionCode: Int,
    onFlagToggle: (ExperimentalFlags.Meta, Boolean) -> Unit,
    onStringFlag: (androidx.datastore.preferences.core.Preferences.Key<String>, String) -> Unit,
    onOpenPage: (String) -> Unit,
    onClose: () -> Unit,
    onPlayGame: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        // Top bar with the click:// URL shown (like Chrome's omnibox).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.topBarBg)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = theme.onTopBar)
            }
            Text(
                text = "click://$pageKey",
                color = theme.onTopBar,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
        }
        when (pageKey) {
            "flags" -> FlagsScreen(theme, flags, onFlagToggle, onStringFlag)
            "version" -> VersionScreen(theme, versionName, versionCode, onOpenPage)
            "games" -> GamesHubScreen(theme, onPlayGame = onPlayGame)
            "unknown" -> UnknownClickPage(theme, onOpenPage)
            else -> UnknownClickPage(theme, onOpenPage)
        }
    }
}

@Composable
private fun FlagsScreen(
    theme: ModeTheme,
    flags: ExperimentalFlags,
    onFlagToggle: (ExperimentalFlags.Meta, Boolean) -> Unit,
    onStringFlag: (androidx.datastore.preferences.core.Preferences.Key<String>, String) -> Unit,
) {
    var uaText by remember(flags.customUserAgent) { mutableStateOf(flags.customUserAgent) }
    var homeText by remember(flags.customHomepage) { mutableStateOf(flags.customHomepage) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "⚗ Experiments",
            color = theme.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            "These features are experimental. Every toggle works for real — changes apply to new and existing pages.",
            color = theme.onBackground.copy(alpha = 0.65f),
            fontSize = 13.sp
        )
        ExperimentalFlags.Meta.values().forEach { meta ->
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFlagToggle(meta, !meta.get(flags)) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(meta.title, color = theme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(meta.desc, color = theme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = meta.get(flags),
                        onCheckedChange = { onFlagToggle(meta, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = theme.primary)
                    )
                }
            }
        }
        // String flags: custom UA + custom homepage.
        Card(
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Custom User-Agent", color = theme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Empty = mode default. Applied to every page load.",
                    color = theme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                OutlinedTextField(
                    value = uaText,
                    onValueChange = {
                        uaText = it
                        onStringFlag(ExperimentalFlags.K_CUSTOM_UA, it)
                    },
                    placeholder = { Text("e.g. Mozilla/5.0 …") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Custom homepage", color = theme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("New tabs open this URL instead of the premium home.",
                    color = theme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                OutlinedTextField(
                    value = homeText,
                    onValueChange = {
                        homeText = it
                        onStringFlag(ExperimentalFlags.K_CUSTOM_HOMEPAGE, it)
                    },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VersionScreen(
    theme: ModeTheme,
    versionName: String,
    versionCode: Int,
    onOpenPage: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("⚡ Click Browser", color = theme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("V9 System — 1 Browser, 4 Engines", color = theme.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        VersionRow(theme, "Version", "$versionName ($versionCode)")
        VersionRow(theme, "Engines", "Simple · Developer · Hack (isolated)")
        VersionRow(theme, "Features", "${ClickInternalPages.FEATURE_COUNT} built-in")
        VersionRow(theme, "AI", "Premium Assist (Groq)")
        Spacer(Modifier.height(8.dp))
        Text("Internal pages", color = theme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        ClickInternalPages.PAGES.forEach { page ->
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenPage(page.key) }
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("click://${page.key}", color = theme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(page.desc, color = theme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VersionRow(theme: ModeTheme, label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = theme.onSurface.copy(alpha = 0.7f), fontSize = 14.sp)
            Text(value, color = theme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun UnknownClickPage(theme: ModeTheme, onOpenPage: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(Icons.Default.Warning, contentDescription = null, tint = theme.primary,
            modifier = Modifier.padding(8.dp))
        Text("Unknown click:// page", color = theme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Try one of these:", color = theme.onBackground.copy(alpha = 0.65f), fontSize = 14.sp)
        ClickInternalPages.PAGES.take(4).forEach { page ->
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenPage(page.key) }
            ) {
                Text("click://${page.key} — ${page.title}",
                    color = theme.primary, fontSize = 14.sp,
                    modifier = Modifier.padding(14.dp))
            }
        }
    }
}
