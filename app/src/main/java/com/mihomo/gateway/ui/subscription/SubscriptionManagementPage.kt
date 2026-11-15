package com.mihomo.gateway.ui.subscription

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
    val switchingId by vm.switchingId.collectAsState()
    val message by vm.message.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }
    var addUrl by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<SubscriptionProfile?>(null) }

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
            onUpdate = { vm.update(current) },
            onSetInterval = { hours -> vm.setInterval(current, hours) },
            onSetActive = { vm.switchTo(current) },
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
            subtitle = "添加、编辑与删除订阅",
            onBack = onBack
        )

        Spacer(Modifier.height(14.dp))

        SettingsGroup("订阅") {
            if (subs.isEmpty()) {
                SettingsRow(
                    title = "还没有保存的订阅",
                    subtitle = "导入成功后会自动出现在这里",
                    showChevron = false
                )
            } else {
                subs.forEachIndexed { index, item ->
                    SettingsRow(
                        title = item.name,
                        subtitle = buildString {
                            append(if (item.nodeCount > 0) "${item.nodeCount} 节点" else "节点数未知")
                            append(" · ")
                            append(if (item.autoUpdateHours > 0) intervalLabel(item.autoUpdateHours) else "自动更新关闭")
                            if (item.id == settings.activeSubId) append(" · 当前")
                        },
                        onClick = { detail = item }
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
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("取消") }
            }
        )
    }
}

/** 兼容旧调用：详情页需要的高亮行由 SettingsRow 承担。 */
@Suppress("unused")
private fun profileMeta(item: SubscriptionProfile): String =
    "${item.nodeCount} 节点 · ${if (item.lastUpdateAt <= 0) "未更新" else formatTime(item.lastUpdateAt)}"
