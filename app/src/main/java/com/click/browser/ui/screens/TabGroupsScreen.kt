package com.click.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.ModeTheme
import com.click.browser.engine.TabGroup
import com.click.browser.engine.TabGroupStore
import kotlinx.coroutines.launch

/**
 * Tab Groups: named, color-coded collections of tabs. Groups expand/collapse
 * and tabs can be added/removed per group. Tab IDs are supplied by the
 * caller (the live browser tab list).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabGroupsScreen(
    theme: ModeTheme,
    /** Live tab IDs with titles for assignment, e.g. from the tab switcher. */
    liveTabs: List<Pair<String, String>> = emptyList(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var groups by remember { mutableStateOf<List<TabGroup>>(emptyList()) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TabGroup?>(null) }
    var confirmDelete by remember { mutableStateOf<TabGroup?>(null) }
    var assignTarget by remember { mutableStateOf<TabGroup?>(null) }

    fun refresh() {
        scope.launch { groups = TabGroupStore.list(context) }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tab Groups", fontWeight = FontWeight.Bold, color = theme.onSurface) },
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
                Icon(Icons.Default.Add, contentDescription = "New group", tint = theme.surface)
            }
        },
        containerColor = theme.background
    ) { padding ->
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(
                        Icons.Default.FolderCopy, contentDescription = null,
                        tint = theme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("No tab groups yet", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = theme.onSurface)
                    Text(
                        "Group related tabs with a name and color — expand a group to see its tabs.",
                        fontSize = 13.sp, color = theme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(groups, key = { it.id }) { group ->
                    val isExpanded = group.id in expanded
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expanded = if (isExpanded) expanded - group.id else expanded + group.id
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(group.color))
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(group.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = theme.onSurface)
                                    Text(
                                        "${group.tabIds.size} tab(s)",
                                        fontSize = 11.sp, color = theme.onSurface.copy(alpha = 0.55f)
                                    )
                                }
                                IconButton(onClick = { assignTarget = group }) {
                                    Icon(Icons.Default.PlaylistAdd, contentDescription = "Add tabs", tint = theme.primary)
                                }
                                IconButton(onClick = { editing = group; showEditor = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = theme.onSurface.copy(alpha = 0.6f))
                                }
                                IconButton(onClick = { confirmDelete = group }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                                Icon(
                                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null, tint = theme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            if (isExpanded) {
                                HorizontalDivider(color = theme.onSurface.copy(alpha = 0.08f))
                                if (group.tabIds.isEmpty()) {
                                    Text(
                                        "Empty — tap + to add open tabs.",
                                        fontSize = 12.sp, color = theme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                } else {
                                    group.tabIds.forEach { tabId ->
                                        val title = liveTabs.firstOrNull { it.first == tabId }?.second ?: tabId.take(24)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Tab, contentDescription = null,
                                                tint = theme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                title, fontSize = 13.sp, color = theme.onSurface,
                                                modifier = Modifier.weight(1f), maxLines = 1
                                            )
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        TabGroupStore.removeTab(context, group.id, tabId)
                                                        refresh()
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close, contentDescription = "Remove",
                                                    tint = theme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showEditor) {
        GroupEditorDialog(
            theme = theme,
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { name, color ->
                scope.launch {
                    if (editing == null) TabGroupStore.create(context, name, color)
                    else {
                        TabGroupStore.rename(context, editing!!.id, name)
                        TabGroupStore.recolor(context, editing!!.id, color)
                    }
                    refresh()
                    showEditor = false
                }
            }
        )
    }

    assignTarget?.let { group ->
        AssignTabsDialog(
            theme = theme,
            group = group,
            liveTabs = liveTabs,
            onDismiss = { assignTarget = null },
            onToggle = { tabId, add ->
                scope.launch {
                    if (add) TabGroupStore.addTab(context, group.id, tabId)
                    else TabGroupStore.removeTab(context, group.id, tabId)
                    refresh()
                }
            }
        )
    }

    confirmDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete group?") },
            text = { Text("“${group.name}” will be removed. Its tabs stay open.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        TabGroupStore.delete(context, group.id)
                        refresh()
                        confirmDelete = null
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GroupEditorDialog(
    theme: ModeTheme,
    initial: TabGroup?,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: TabGroupStore.PALETTE[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New tab group" else "Edit group", color = theme.onSurface) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Group name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Color", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.onSurface)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TabGroupStore.PALETTE.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable { color = c },
                            contentAlignment = Alignment.Center
                        ) {
                            if (c == color) {
                                Icon(
                                    Icons.Default.Check, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim().ifBlank { "Group" }, color) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AssignTabsDialog(
    theme: ModeTheme,
    group: TabGroup,
    liveTabs: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onToggle: (String, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tabs in “${group.name}”", color = theme.onSurface, fontSize = 16.sp) },
        text = {
            if (liveTabs.isEmpty()) {
                Text(
                    "No open tabs to assign right now.",
                    fontSize = 13.sp, color = theme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(liveTabs, key = { it.first }) { (id, title) ->
                        val inGroup = id in group.tabIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(id, !inGroup) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = inGroup, onCheckedChange = { onToggle(id, it) })
                            Spacer(Modifier.width(8.dp))
                            Text(title.ifBlank { id.take(20) }, fontSize = 13.sp, color = theme.onSurface, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
