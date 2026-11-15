package com.mihomo.gateway.ui.policy

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
import androidx.compose.material3.Switch
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
import com.mihomo.gateway.data.catalog.NodeRegions
import com.mihomo.gateway.data.policy.PolicyRuntime
import com.mihomo.gateway.model.DevicePolicy
import com.mihomo.gateway.model.DeviceSelector
import com.mihomo.gateway.ui.components.SubPageHeader
import com.mihomo.gateway.ui.icons.GwIcon
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * 设备策略：按源设备（MAC）选择「地区 + 协议」出口，并支持一个回落条件。
 * v1 的回落语义：主条件节点全部不可用时走回落条件。
 */
@Composable
fun DevicePolicyScreen(
    onBack: () -> Unit,
    vm: DevicePolicyViewModel
) {
    val policies by vm.policies.collectAsState()
    val runtime by vm.runtime.collectAsState()
    val devices by vm.devices.collectAsState()
    val nodes by vm.nodes.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()

    var editing by remember { mutableStateOf<DevicePolicy?>(null) }

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            vm.clearMessage()
        }
    }

    val runtimeById = runtime.associateBy { it.policy.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubPageHeader(
            title = "设备策略",
            subtitle = "按设备选择地区与协议，主条件不可用时回落",
            onBack = onBack
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = { vm.reapply() }) {
                    GwIcon(GwIcon.Refresh, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = {
                    editing = DevicePolicy(
                        id = UUID.randomUUID().toString(),
                        mac = "",
                        label = ""
                    )
                }) {
                    GwIcon(GwIcon.Plus, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }

        if (policies.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Text("还没有设备策略", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "点右上角 ＋ 选择一台局域网设备，指定它优先使用哪个地区/协议的节点",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            policies.forEachIndexed { index, policy ->
                PolicyCard(
                    policy = policy,
                    runtime = runtimeById[policy.id],
                    onClick = { editing = policy }
                )
                if (index != policies.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    editing?.let { draft ->
        PolicyEditorDialog(
            draft = draft,
            devices = devices,
            nodeRegions = nodes.map { it.region }.filter { it.isNotBlank() }.distinct(),
            nodeProtocols = nodes.map { it.type }.filter { it.isNotBlank() }.distinct().sorted(),
            busy = busy,
            onDismiss = { editing = null },
            onSave = {
                editing = null
                vm.save(it)
            },
            onDelete = {
                editing = null
                vm.delete(draft.id)
            }
        )
    }
}

@Composable
private fun PolicyCard(policy: DevicePolicy, runtime: PolicyRuntime?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                policy.label.ifBlank { policy.mac.ifBlank { "未选择设备" } },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!policy.enabled) {
                Spacer(Modifier.width(6.dp))
                Text("已停用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.weight(1f))
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
        }
        Text(
            selectorText(policy.primary) + " → " + selectorText(policy.fallback),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            buildString {
                append(if (policy.mac.isBlank()) "未绑定 MAC" else policy.mac)
                runtime?.ip?.let { append(" · ").append(it) }
                runtime?.let { rt ->
                    append(" · 主 ").append(rt.primaryNodes.size).append(" / 回落 ").append(rt.fallbackNodes.size).append(" 节点")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        runtime?.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PolicyEditorDialog(
    draft: DevicePolicy,
    devices: List<com.mihomo.gateway.data.catalog.DeviceTarget>,
    nodeRegions: List<String>,
    nodeProtocols: List<String>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (DevicePolicy) -> Unit,
    onDelete: () -> Unit
) {
    var mac by remember { mutableStateOf(draft.mac) }
    var label by remember { mutableStateOf(draft.label) }
    var primaryRegion by remember { mutableStateOf(draft.primary.region) }
    var primaryProtocol by remember { mutableStateOf(draft.primary.protocol) }
    var fallbackRegion by remember { mutableStateOf(draft.fallback.region) }
    var fallbackProtocol by remember { mutableStateOf(draft.fallback.protocol) }
    var enabled by remember { mutableStateOf(draft.enabled) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.mac.isBlank()) "添加设备策略" else "编辑设备策略") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DevicePicker(
                    devices = devices,
                    selectedMac = mac,
                    onPick = { target ->
                        mac = target.mac
                        if (label.isBlank()) label = target.hostname ?: target.mac
                    }
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("备注名称（可留空）") },
                    singleLine = true
                )

                Text("优先条件", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionPicker(
                        title = NodeRegions.label(primaryRegion),
                        options = listOf("" to "任意地区") +
                            nodeRegions.map { it to NodeRegions.label(it) },
                        onSelect = { primaryRegion = it }
                    )
                    OptionPicker(
                        title = if (primaryProtocol.isBlank()) "任意协议" else primaryProtocol,
                        options = listOf("" to "任意协议") + nodeProtocols.map { it to it },
                        onSelect = { primaryProtocol = it }
                    )
                }

                Text("回落条件", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionPicker(
                        title = NodeRegions.label(fallbackRegion),
                        options = listOf("" to "任意地区") +
                            nodeRegions.map { it to NodeRegions.label(it) },
                        onSelect = { fallbackRegion = it }
                    )
                    OptionPicker(
                        title = if (fallbackProtocol.isBlank()) "任意协议" else fallbackProtocol,
                        options = listOf("" to "任意协议") + nodeProtocols.map { it to it },
                        onSelect = { fallbackProtocol = it }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && mac.isNotBlank(),
                onClick = {
                    onSave(
                        draft.copy(
                            mac = mac.lowercase(),
                            label = label.trim(),
                            primary = DeviceSelector(primaryRegion, primaryProtocol),
                            fallback = DeviceSelector(fallbackRegion, fallbackProtocol),
                            enabled = enabled
                        )
                    )
                }
            ) { Text("保存并应用") }
        },
        dismissButton = {
            Row {
                if (draft.mac.isNotBlank()) {
                    TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除策略？") },
            text = { Text("将移除该设备的出口策略，之后它按普通规则走。") },
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
}

@Composable
private fun DevicePicker(
    devices: List<com.mihomo.gateway.data.catalog.DeviceTarget>,
    selectedMac: String,
    onPick: (com.mihomo.gateway.data.catalog.DeviceTarget) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val selected = devices.firstOrNull { it.mac == selectedMac }
    Box {
        OutlinedTextField(
            value = selected?.display ?: selectedMac.ifBlank { "选择设备" },
            onValueChange = {},
            readOnly = true,
            label = { Text("设备") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable { menu = true }
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (devices.isEmpty()) {
                DropdownMenuItem(text = { Text("未发现设备（先连接热点/局域网）") }, onClick = { menu = false })
            } else {
                devices.forEach { target ->
                    DropdownMenuItem(
                        text = { Text(target.display) },
                        onClick = {
                            menu = false
                            onPick(target)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionPicker(
    title: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { menu = true }) {
            Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(" ▾", style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        menu = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

private fun selectorText(selector: DeviceSelector): String {
    val region = NodeRegions.label(selector.region)
    val protocol = selector.protocol.ifBlank { "任意协议" }
    return "$region · $protocol"
}
