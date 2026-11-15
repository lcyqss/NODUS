package com.mihomo.gateway.ui.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mihomo.gateway.model.ProviderMeta
import com.mihomo.gateway.model.SubscriptionProfile
import com.mihomo.gateway.ui.components.SettingsDivider
import com.mihomo.gateway.ui.components.SettingsGroup
import com.mihomo.gateway.ui.components.SettingsRow
import com.mihomo.gateway.ui.components.SubPageHeader

/**
 * 单个订阅的详情：名称/链接、自动更新、Provider（只读）、立即更新、设为当前、删除。
 * Provider 元数据来自该订阅的缓存配置；健康检查仅当前订阅可用。
 */
@Composable
fun SubscriptionDetailPage(
    profile: SubscriptionProfile,
    active: Boolean,
    busy: Boolean,
    switching: Boolean,
    message: String?,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
    onSaveAndUpdate: (String, String) -> Unit,
    onSetInterval: (Int) -> Unit,
    onDelete: () -> Unit,
    loadProviders: suspend () -> List<ProviderMeta>
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var url by remember(profile.id) { mutableStateOf(profile.url) }
    var intervalMenu by remember { mutableStateOf(false) }
    var customInterval by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var providers by remember(profile.id) { mutableStateOf<List<ProviderMeta>>(emptyList()) }
    var providersLoaded by remember(profile.id) { mutableStateOf(false) }

    LaunchedEffect(profile.id, profile.lastUpdateAt, profile.url) {
        providers = runCatching { loadProviders() }.getOrDefault(emptyList())
        providersLoaded = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubPageHeader(
            title = profile.name,
            subtitle = if (active) "当前订阅" else "未启用",
            onBack = onBack
        )

        Spacer(Modifier.height(12.dp))

        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("名称") },
            singleLine = true,
            readOnly = busy,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("订阅链接") },
            singleLine = true,
            readOnly = busy,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onSave(name, url) },
                enabled = !busy && (name != profile.name || url != profile.url)
            ) { Text("保存") }
            OutlinedButton(
                onClick = { onSaveAndUpdate(name, url) },
                enabled = !busy && !switching
            ) { Text("保存并更新") }
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup("更新") {
            SettingsRow(
                title = "自动更新",
                subtitle = "仅对这个订阅生效",
                value = intervalLabel(profile.autoUpdateMinutes),
                enabled = !busy,
                onClick = { intervalMenu = true }
            )
            Box {
                DropdownMenu(expanded = intervalMenu, onDismissRequest = { intervalMenu = false }) {
                    AutoUpdateOptions.forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text(intervalLabel(minutes)) },
                            onClick = {
                                intervalMenu = false
                                onSetInterval(minutes)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("自定义…") },
                        onClick = {
                            intervalMenu = false
                            customMinutes = profile.autoUpdateMinutes
                                .takeIf { it > 0 }?.toString().orEmpty()
                            customInterval = true
                        }
                    )
                }
            }
            SettingsDivider()
            SettingsRow(
                title = "上次更新",
                value = if (profile.lastUpdateAt <= 0) "从未" else formatTime(profile.lastUpdateAt),
                showChevron = false
            )
            SettingsDivider()
            SettingsRow(
                title = "配置内节点数",
                value = if (profile.nodeCount > 0) "${profile.nodeCount}" else "未知",
                showChevron = false
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup("节点供应商（Provider）") {
            when {
                !providersLoaded -> SettingsRow(title = "加载中…", showChevron = false)
                providers.isEmpty() -> SettingsRow(
                    title = "该订阅未使用 proxy-providers",
                    subtitle = "节点直接写在订阅配置中，属于正常情况",
                    showChevron = false
                )
                else -> providers.forEachIndexed { index, provider ->
                    SettingsRow(
                        title = provider.name,
                        subtitle = buildString {
                            append(provider.type)
                            if (provider.url.isNotBlank()) {
                                append(" · ")
                                append(provider.url.take(48))
                            }
                        },
                        showChevron = false
                    )
                    if (index != providers.lastIndex) SettingsDivider()
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Provider 健康检查仅适用于 proxy-providers 型订阅",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Spacer(Modifier.height(18.dp))

        TextButton(
            onClick = { confirmDelete = true },
            enabled = !busy,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("删除订阅", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(12.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除订阅？") },
            text = {
                Text(
                    if (active) {
                        "「${profile.name}」是当前订阅，删除后会自动切换到剩余订阅；没有剩余订阅则保留当前节点配置。"
                    } else {
                        "将删除「${profile.name}」及其缓存配置。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }

    if (customInterval) {
        val minutes = customMinutes.toIntOrNull()
        val valid = minutes != null && minutes >= 10 && minutes % 10 == 0
        AlertDialog(
            onDismissRequest = { customInterval = false },
            title = { Text("自定义自动更新间隔") },
            text = {
                Column {
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { customMinutes = it.filter { ch -> ch.isDigit() } },
                        label = { Text("分钟") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "最小 10 分钟，且为 10 的倍数",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        minutes?.let { onSetInterval(it) }
                        customInterval = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { customInterval = false }) { Text("取消") }
            }
        )
    }
}
