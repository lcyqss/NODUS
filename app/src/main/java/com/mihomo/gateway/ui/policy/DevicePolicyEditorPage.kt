package com.mihomo.gateway.ui.policy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mihomo.gateway.data.catalog.DeviceTarget
import com.mihomo.gateway.data.catalog.NodeInfo
import com.mihomo.gateway.model.ConditionTemplate
import com.mihomo.gateway.model.DevicePolicy
import com.mihomo.gateway.model.DeviceRoutingMode
import com.mihomo.gateway.model.DeviceSelector
import com.mihomo.gateway.model.NodeTag
import com.mihomo.gateway.model.TagExpr
import com.mihomo.gateway.ui.components.SubPageHeader

/**
 * 设备策略编辑页：全屏表单，避免弹窗内滚动和拥挤。
 * 设备 / 优先条件 / 回落条件 / 其他设置 四个区块，底部固定操作栏。
 */
@Composable
fun DevicePolicyEditorPage(
    draft: DevicePolicy,
    devices: List<DeviceTarget>,
    tags: List<NodeTag>,
    templates: List<ConditionTemplate>,
    busy: Boolean,
    matchCount: (TagExpr?) -> Int,
    matchNodes: (TagExpr?) -> List<NodeInfo>,
    onSaveTemplate: (String, TagExpr) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onBack: () -> Unit,
    onSave: (DevicePolicy) -> Unit,
    onDelete: () -> Unit
) {
    var mac by remember { mutableStateOf(draft.mac) }
    var mode by remember { mutableStateOf(draft.mode) }
    var label by remember { mutableStateOf(draft.label) }
    var primaryExpr by remember { mutableStateOf(draft.primary.expr) }
    var fallbackExpr by remember { mutableStateOf(draft.fallback.expr) }
    var primaryInvalid by remember { mutableStateOf(false) }
    var fallbackInvalid by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(draft.enabled) }
    var maxDelay by remember { mutableStateOf(if (draft.maxDelayMs > 0) draft.maxDelayMs.toString() else "") }
    var scheduleInterval by remember {
        mutableStateOf(if (draft.scheduleIntervalSec > 0) draft.scheduleIntervalSec.toString() else "")
    }
    var showDevices by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().imePadding()) {
        SubPageHeader(
            title = if (draft.mac.isBlank()) "添加设备策略" else "编辑设备策略",
            subtitle = "按设备 MAC 生效，条件不满足时回落",
            onBack = onBack
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EditorCard {
                SectionTitle("设备")
                DeviceRow(
                    mac = mac,
                    devices = devices,
                    onClick = { showDevices = true }
                )
                Spacer(Modifier.height(10.dp))
                CompactField(
                    label = "备注名称",
                    value = label,
                    placeholder = "可留空",
                    onValueChange = { label = it }
                )
            }

            EditorCard {
                SectionTitle("连接模式")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeChip("全局", mode == DeviceRoutingMode.GLOBAL, Modifier.weight(1f)) {
                        mode = DeviceRoutingMode.GLOBAL
                    }
                    ModeChip("规则", mode == DeviceRoutingMode.RULE, Modifier.weight(1f)) {
                        mode = DeviceRoutingMode.RULE
                    }
                    ModeChip("直连", mode == DeviceRoutingMode.DIRECT, Modifier.weight(1f)) {
                        mode = DeviceRoutingMode.DIRECT
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when (mode) {
                        DeviceRoutingMode.GLOBAL -> "该设备全部流量都走设备策略组"
                        DeviceRoutingMode.RULE -> "遵循首页规则；未命中规则时走设备策略组"
                        DeviceRoutingMode.DIRECT -> "该设备全部流量直连，不使用代理"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (mode != DeviceRoutingMode.DIRECT) {
            ConditionSection(
                title = "优先条件",
                initial = draft.primary.expr,
                tags = tags,
                matchCount = matchCount,
                matchNodes = matchNodes,
                templates = templates,
                onSaveTemplate = onSaveTemplate,
                onDeleteTemplate = onDeleteTemplate,
                onExprChange = { primaryExpr = it },
                onInvalidChange = { primaryInvalid = it }
            )
            }

            if (mode != DeviceRoutingMode.DIRECT) {
            ConditionSection(
                title = "回落条件",
                initial = draft.fallback.expr,
                tags = tags,
                matchCount = matchCount,
                matchNodes = matchNodes,
                templates = templates,
                onSaveTemplate = onSaveTemplate,
                onDeleteTemplate = onDeleteTemplate,
                onExprChange = { fallbackExpr = it },
                onInvalidChange = { fallbackInvalid = it }
            )
            }

            EditorCard {
                SectionTitle("其他设置")
                NumericRow(
                    label = "延迟阈值",
                    value = maxDelay,
                    unit = "ms",
                    hint = "0=只按存活",
                    onValueChange = { maxDelay = it }
                )
                NumericRow(
                    label = "调度间隔",
                    value = scheduleInterval,
                    unit = "s",
                    hint = "0=跟随全局",
                    onValueChange = { scheduleInterval = it }
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (draft.mac.isNotBlank()) {
                TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("取消") }
            Spacer(Modifier.width(6.dp))
            Button(
                enabled = !busy && mac.isNotBlank() &&
                    (mode == DeviceRoutingMode.DIRECT || (!primaryInvalid && !fallbackInvalid)),
                onClick = {
                    onSave(
                        draft.copy(
                            mac = mac.lowercase(),
                            label = label.trim(),
                            mode = mode,
                            primary = DeviceSelector(primaryExpr),
                            fallback = DeviceSelector(fallbackExpr),
                            maxDelayMs = maxDelay.toIntOrNull() ?: 0,
                            scheduleIntervalSec = scheduleInterval.toIntOrNull() ?: 0,
                            enabled = enabled
                        )
                    )
                }
            ) { Text("保存并应用") }
        }
    }

    if (showDevices) {
        DevicePickerDialog(
            devices = devices,
            selectedMac = mac,
            onDismiss = { showDevices = false },
            onPick = { target ->
                mac = target.mac
                if (label.isBlank()) label = target.hostname ?: target.mac
                showDevices = false
            }
        )
    }

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
private fun EditorCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun DeviceRow(mac: String, devices: List<DeviceTarget>, onClick: () -> Unit) {
    val target = devices.firstOrNull { it.mac == mac }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                target?.display ?: mac.ifBlank { "选择局域网设备" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun CompactField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    RoundedCornerShape(9.dp)
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NumericRow(
    label: String,
    value: String,
    unit: String,
    hint: String,
    onValueChange: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            Modifier
                .width(96.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    RoundedCornerShape(9.dp)
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = value,
                    onValueChange = { input -> onValueChange(input.filter { it.isDigit() }) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DevicePickerDialog(
    devices: List<DeviceTarget>,
    selectedMac: String,
    onDismiss: () -> Unit,
    onPick: (DeviceTarget) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择设备") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (devices.isEmpty()) {
                    Text(
                        "未发现设备（先连接热点/局域网）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                devices.forEach { target ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (target.mac == selectedMac) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { onPick(target) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                target.hostname?.takeIf { it.isNotBlank() } ?: target.mac,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${target.mac} · ${target.ip}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (target.mac == selectedMac) {
                            Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}