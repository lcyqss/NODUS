package com.mihomo.gateway.ui.subscription

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mihomo.gateway.model.SubscriptionProfile

/** 自动更新间隔选项（小时，0=关闭）。 */
internal val AutoUpdateOptions = listOf(0, 1, 6, 12, 24)

internal fun intervalLabel(hours: Int): String = when (hours) {
    0 -> "关闭"
    1 -> "每 1 小时"
    6 -> "每 6 小时"
    12 -> "每 12 小时"
    24 -> "每天"
    else -> "每 $hours 小时"
}

/**
 * 节点页的订阅切换弹层：切换、单独更新、设置每个订阅的自动更新间隔。
 * 添加/删除/改名改链接在「订阅管理」里，避免同一动作有两个入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionSheet(
    subs: List<SubscriptionProfile>,
    activeId: String,
    busy: Boolean,
    switchingId: String?,
    message: String?,
    onSelect: (SubscriptionProfile) -> Unit,
    onUpdate: (SubscriptionProfile) -> Unit,
    onInterval: (SubscriptionProfile, Int) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text("订阅", style = MaterialTheme.typography.titleLarge)
            Text(
                "切换订阅，或单独设置每个订阅的自动更新",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(8.dp))

            if (subs.isEmpty()) {
                Text(
                    "还没有保存的订阅，到「管理订阅」添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(subs, key = { it.id }) { item ->
                        SubscriptionRowInSheet(
                            item = item,
                            active = item.id == activeId,
                            busy = busy,
                            updating = switchingId == item.id,
                            onSelect = { onSelect(item) },
                            onUpdate = { onUpdate(item) },
                            onInterval = { hours -> onInterval(item, hours) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "添加、删除、改名在管理页",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onManage, enabled = !busy) { Text("管理订阅") }
            }
        }
    }
}

@Composable
private fun SubscriptionRowInSheet(
    item: SubscriptionProfile,
    active: Boolean,
    busy: Boolean,
    updating: Boolean,
    onSelect: () -> Unit,
    onUpdate: () -> Unit,
    onInterval: (Int) -> Unit
) {
    var intervalMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !busy) { onSelect() }
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = active,
                onClick = onSelect,
                enabled = !busy
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (active) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "当前",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    "${if (item.nodeCount > 0) "${item.nodeCount} 节点" else "节点数未知"} · " +
                        if (item.lastUpdateAt <= 0) "未更新" else formatTime(item.lastUpdateAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (updating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                TextButton(onClick = onUpdate, enabled = !busy) {
                    Text("更新", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Box(Modifier.padding(start = 40.dp, bottom = 6.dp)) {
            TextButton(
                onClick = { intervalMenu = true },
                enabled = !busy,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(
                    "自动更新 ${intervalLabel(item.autoUpdateHours)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.autoUpdateHours > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    " ▾",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = intervalMenu, onDismissRequest = { intervalMenu = false }) {
                AutoUpdateOptions.forEach { hours ->
                    DropdownMenuItem(
                        text = { Text(intervalLabel(hours)) },
                        onClick = {
                            intervalMenu = false
                            onInterval(hours)
                        }
                    )
                }
            }
        }
    }
}

internal fun formatTime(millis: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))
