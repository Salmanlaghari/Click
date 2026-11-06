package com.click.browser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.browser.engine.MenuCustomization
import com.click.browser.engine.ModeTheme

/**
 * "Customize menu" sheet: reorder browser-menu items with up/down arrows
 * and toggle each item's visibility. Changes apply immediately and are
 * persisted by the caller via [onSave].
 *
 * No dead controls: every arrow reorders, every eye hides/shows, Reset
 * restores the default order + visibility, Done saves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuCustomizeSheet(
    theme: ModeTheme,
    initialOrder: List<MenuCustomization.MenuItemId>,
    initialHidden: Set<MenuCustomization.MenuItemId>,
    onSave: (order: List<MenuCustomization.MenuItemId>, hidden: Set<MenuCustomization.MenuItemId>) -> Unit,
    onDismiss: () -> Unit
) {
    var order by remember { mutableStateOf(MenuCustomization.effectiveFullOrder(initialOrder)) }
    var hidden by remember { mutableStateOf(initialHidden - MenuCustomization.LOCKED_VISIBLE) }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in order.indices) return
        val next = order.toMutableList()
        val item = next.removeAt(index)
        next.add(target, item)
        order = next
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = theme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Customize menu",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.onSurface
                )
                TextButton(onClick = {
                    order = MenuCustomization.DEFAULT_ORDER
                    hidden = emptySet()
                }) {
                    Text("Reset", color = theme.primary)
                }
            }
            Text(
                "Reorder with the arrows, hide items you never use. Settings always stays visible.",
                fontSize = 12.sp,
                color = theme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.background.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .padding(vertical = 4.dp)
                ) {
                    itemsIndexed(order, key = { _, id -> id.name }) { index, id ->
                        val isHidden = id in hidden
                        val locked = id in MenuCustomization.LOCKED_VISIBLE
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                IconButton(
                                    onClick = { move(index, -1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Move up",
                                        tint = if (index > 0) theme.onSurface.copy(alpha = 0.7f)
                                        else theme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { move(index, 1) },
                                    enabled = index < order.lastIndex,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Move down",
                                        tint = if (index < order.lastIndex) theme.onSurface.copy(alpha = 0.7f)
                                        else theme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                id.title,
                                fontSize = 15.sp,
                                color = if (isHidden) theme.onSurface.copy(alpha = 0.4f) else theme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (!locked) {
                                IconButton(
                                    onClick = {
                                        hidden = if (isHidden) hidden - id else hidden + id
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isHidden) "Show" else "Hide",
                                        tint = if (isHidden) theme.onSurface.copy(alpha = 0.4f) else theme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                Text(
                                    "Always on",
                                    fontSize = 11.sp,
                                    color = theme.onSurface.copy(alpha = 0.45f),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                        if (index < order.lastIndex) {
                            Divider(
                                color = theme.onSurface.copy(alpha = 0.08f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { onSave(order, hidden); onDismiss() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}
