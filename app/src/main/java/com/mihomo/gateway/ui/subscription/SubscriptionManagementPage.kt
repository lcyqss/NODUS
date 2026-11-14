package com.mihomo.gateway.ui.subscription

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mihomo.gateway.model.SubscriptionProfile
import com.mihomo.gateway.ui.components.SettingsDivider
import com.mihomo.gateway.ui.components.SettingsGroup
import com.mihomo.gateway.ui.components.SettingsRow
import com.mihomo.gateway.ui.components.SubPageHeader
import kotlinx.coroutines.delay

@Composable
fun SubscriptionManagementPage(
    vm: SubscriptionViewModel,
    onBack: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val subs by vm.subscriptions.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }
    var addUrl by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<SubscriptionProfile?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            vm.clearMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubPageHeader(
            title = "订阅管理",
            subtitle = "导入 Clash 订阅、切换与删除",
            onBack = onBack
        )

        Spacer(Modifier.height(14.dp))

        SettingsGroup("当前") {
            SettingsRow(
                title = "订阅名称",
                value = settings.subName.ifBlank { "未设置" },
                showChevron = false
            )
            SettingsDivider()
            SettingsRow(
                title = "节点数",
                value = if (settings.nodeCount > 0) "${settings.nodeCount}" else "-",
                showChevron = false
            )
            SettingsDivider()
            SettingsRow(
                title = "上次更新",
                value = if (settings.lastUpdateAt <= 0) "从未" else formatTime(settings.lastUpdateAt),
                showChevron = false
            )
        }

        Spacer(Modifier.height(24.dp))

        SettingsGroup("已保存订阅") {
            if (subs.isEmpty()) {
                SettingsRow(
                    title = "还没有保存的订阅",
                    subtitle = "导入成功后会自动出现在这里",
                    showChevron = false
                )
            } else {
                subs.forEachIndexed { index, item ->
                    SubscriptionRow(
                        name = item.name,
                        url = item.url,
                        meta = "${item.nodeCount} 节点 · " +
                            if (item.lastUpdateAt <= 0) "未更新" else formatTime(item.lastUpdateAt),
                        active = settings.activeSubId == item.id,
                        busy = busy,
                        onActivate = { vm.activate(item) },
                        onDelete = { pendingDelete = item }
                    )
                    if (index != subs.lastIndex) SettingsDivider()
                }
            }
            SettingsDivider()
            SettingsRow(
                title = "添加订阅",
                subtitle = "粘贴 Clash 订阅链接，导入后自动切换",
                enabled = !busy,
                onClick = { showAdd = true }
            )
        }

        Spacer(Modifier.height(24.dp))

        SettingsGroup("导入流程") {
            listOf(
                "下载并校验是否为 Clash 配置",
                "写入 /data/adb/mihomo/config.yaml",
                "自动补齐 external-ui / secret / redir-port 等字段",
                "热重载内核（保留原地址族与自定义规则）"
            ).forEachIndexed { index, step ->
                SettingsRow(
                    title = "${index + 1}. $step",
                    showChevron = false
                )
                if (index != 3) SettingsDivider()
            }
        }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(12.dp))
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
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
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("取消") }
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除订阅？") },
            text = {
                Text(
                    if (target.id == settings.activeSubId) {
                        "「${target.name}」是当前订阅。删除后节点仍是旧配置，需要切换或更新其他订阅才会改变。"
                    } else {
                        "将删除「${target.name}」，不影响当前生效的配置。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    vm.delete(target)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SubscriptionRow(
    name: String,
    url: String,
    meta: String,
    active: Boolean,
    busy: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onActivate)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = active, onClick = onActivate)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
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
                url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = onDelete,
            enabled = !busy,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
        ) { Text("删除", style = MaterialTheme.typography.bodySmall) }
    }
}

private fun formatTime(millis: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))
