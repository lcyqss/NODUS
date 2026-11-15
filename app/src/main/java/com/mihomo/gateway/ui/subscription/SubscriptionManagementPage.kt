package com.mihomo.gateway.ui.subscription

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mihomo.gateway.model.SubscriptionProfile
import com.mihomo.gateway.ui.components.SubPageHeader
import com.mihomo.gateway.ui.icons.GwIcon
import kotlinx.coroutines.delay

/**
 * 订阅集合页：切换（单选）+ 对象管理（更新/编辑/删除）+ 添加/全部更新。
 * 单个订阅的属性在 [SubscriptionDetailPage] 里编辑。
 */
@Composable
fun SubscriptionManagementPage(
    vm: SubscriptionViewModel,
    onBack: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val subs by vm.subscriptions.collectAsState()
    val busy by vm.busy.collectAsState()
    val switchingId by vm.switchingId.collectAsState()
    val message by vm.message.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }
    var addUrl by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<SubscriptionProfile?>(null) }
    var pendingDelete by remember { mutableStateOf<SubscriptionProfile?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            vm.clearMessage()
        }
    }

    BackHandler(enabled = detail != null) { detail = null }

    val editing = detail
    if (editing != null) {
        val current = subs.firstOrNull { it.id == editing.id } ?: editing
        SubscriptionDetailPage(
            profile = current,
            active = current.id == settings.activeSubId,
            busy = busy,
            switching = switchingId == current.id,
            message = message,
            onBack = { detail = null },
            onSave = { name, url -> vm.save(current, name, url) },
            onSaveAndUpdate = { name, url -> vm.saveAndUpdate(current, name, url) },
            onSetInterval = { hours -> vm.setInterval(current, hours) },
            onDelete = {
                vm.delete(current)
                detail = null
            },
            loadProviders = { vm.providers(current) }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubPageHeader(
            title = "订阅管理",
            onBack = onBack
        ) {
            IconButton(onClick = { vm.updateAll() }, enabled = !busy) {
                GwIcon(
                    GwIcon.Refresh,
                    Modifier.size(20.dp),
                    tint = if (busy) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                addName = ""
                addUrl = ""
                showAdd = true
            }, enabled = !busy) {
                GwIcon(
                    GwIcon.Plus,
                    Modifier.size(22.dp),
                    tint = if (busy) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }

        if (subs.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Text("还没有订阅", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "点右上角 ＋ 添加 Clash 订阅链接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            subs.forEachIndexed { index, item ->
                SubscriptionCard(
                    item = item,
                    active = item.id == settings.activeSubId,
                    busy = busy,
                    switching = switchingId == item.id,
                    onSelect = { vm.switchTo(item) },
                    onUpdate = { vm.update(item) },
                    onEdit = { detail = item },
                    onDelete = { pendingDelete = item }
                )
                if (index != subs.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除订阅？") },
            text = {
                Text(
                    if (target.id == settings.activeSubId) {
                        "「${target.name}」是当前订阅，删除后会自动切换到剩余订阅；没有剩余则保留当前节点配置。"
                    } else {
                        "将删除「${target.name}」及其缓存配置。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    vm.delete(target)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = {
                showAdd = false
                addName = ""
                addUrl = ""
            },
            title = { Text("添加订阅") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = addName,
                        onValueChange = { addName = it },
                        label = { Text("名称（可留空）") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = addUrl,
                        onValueChange = { addUrl = it },
                        label = { Text("订阅链接") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAdd = false
                    vm.add(addName, addUrl)
                    addName = ""
                    addUrl = ""
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAdd = false
                    addName = ""
                    addUrl = ""
                }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SubscriptionCard(
    item: SubscriptionProfile,
    active: Boolean,
    busy: Boolean,
    switching: Boolean,
    onSelect: () -> Unit,
    onUpdate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onEdit() }
            .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
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
                buildString {
                    append(if (item.nodeCount > 0) "${item.nodeCount} 节点" else "节点数未知")
                    append(" · ")
                    append(intervalLabel(item.autoUpdateMinutes))
                    append(" · ")
                    append(if (item.lastUpdateAt <= 0) "未更新" else formatTime(item.lastUpdateAt))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                item.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (switching) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Box {
                IconButton(onClick = { menu = true }, enabled = !busy) {
                    GwIcon(
                        GwIcon.More,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("立即更新") },
                        onClick = {
                            menu = false
                            onUpdate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = {
                            menu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
