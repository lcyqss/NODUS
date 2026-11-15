package com.mihomo.gateway.ui.policy

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
import androidx.compose.material3.Checkbox
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
import com.mihomo.gateway.data.catalog.DeviceTarget
import com.mihomo.gateway.data.policy.PolicyRuntime
import com.mihomo.gateway.model.DevicePolicy
import com.mihomo.gateway.model.DeviceSelector
import com.mihomo.gateway.model.NodeTag
import com.mihomo.gateway.model.TagSelection
import com.mihomo.gateway.ui.components.SubPageHeader
import com.mihomo.gateway.ui.icons.GwIcon
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * 设备策略：按源设备（MAC）选择一组标签条件，并支持一个回落条件。
 * 条件由标签组成，多个标签默认 AND，每个标签可单独取反。
 */
@Composable
fun DevicePolicyScreen(
    onBack: () -> Unit,
    vm: DevicePolicyViewModel
) {
    val policies by vm.policies.collectAsState()
    val runtime by vm.runtime.collectAsState()
    val devices by vm.devices.collectAsState()
    val tags by vm.tags.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()

    var editing by remember { mutableStateOf<DevicePolicy?>(null) }
    var showTags by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            vm.clearMessage()
        }
    }

    BackHandler(enabled = showTags) { showTags = false }

    if (showTags) {
        NodeTagManagerScreen(onBack = { showTags = false }, vm = vm)
        return
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
            subtitle = "按设备选择节点标签，主条件不可用时回落",
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

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { showTags = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("节点标签", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "${tags.count { !it.builtin }} 个自定义 · ${tags.count { it.builtin }} 个内置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
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
                    "点右上角 ＋ 选择一台局域网设备，指定它优先使用哪些节点标签",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            policies.forEachIndexed { index, policy ->
                PolicyCard(
                    policy = policy,
                    runtime = runtimeById[policy.id],
                    tags = tags,
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
            tags = tags,
            busy = busy,
            matchCount = { selections -> vm.matchCount(selections) },
            countTag = { tag -> vm.countTag(tag) },
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
private fun PolicyCard(
    policy: DevicePolicy,
    runtime: PolicyRuntime?,
    tags: List<NodeTag>,
    onClick: () -> Unit
) {
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
            selectorText(policy.primary, tags) + " → " + selectorText(policy.fallback, tags),
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
                if (policy.maxDelayMs > 0) append(" · ≤").append(policy.maxDelayMs).append("ms")
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
    devices: List<DeviceTarget>,
    tags: List<NodeTag>,
    busy: Boolean,
    matchCount: (List<TagSelection>) -> Int,
    countTag: (NodeTag) -> Int,
    onDismiss: () -> Unit,
    onSave: (DevicePolicy) -> Unit,
    onDelete: () -> Unit
) {
    var mac by remember { mutableStateOf(draft.mac) }
    var label by remember { mutableStateOf(draft.label) }
    var primary by remember { mutableStateOf(draft.primary.tags) }
    var fallback by remember { mutableStateOf(draft.fallback.tags) }
    var enabled by remember { mutableStateOf(draft.enabled) }
    var maxDelay by remember { mutableStateOf(if (draft.maxDelayMs > 0) draft.maxDelayMs.toString() else "") }
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

                TagSelectorField(
                    title = "优先条件",
                    selections = primary,
                    tags = tags,
                    countTag = countTag,
                    onChange = { primary = it }
                )
                Text(
                    "命中 ${matchCount(primary)} 个节点",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TagSelectorField(
                    title = "回落条件",
                    selections = fallback,
                    tags = tags,
                    countTag = countTag,
                    onChange = { fallback = it }
                )
                Text(
                    "命中 ${matchCount(fallback)} 个节点",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = maxDelay,
                    onValueChange = { maxDelay = it.filter { ch -> ch.isDigit() } },
                    label = { Text("延迟阈值（毫秒，0=只按存活回落）") },
                    singleLine = true
                )

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
                            primary = DeviceSelector(primary),
                            fallback = DeviceSelector(fallback),
                            maxDelayMs = maxDelay.toIntOrNull() ?: 0,
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
private fun TagSelectorField(
    title: String,
    selections: List<TagSelection>,
    tags: List<NodeTag>,
    countTag: (NodeTag) -> Int,
    onChange: (List<TagSelection>) -> Unit
) {
    var picker by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                selectorText(DeviceSelector(selections), tags),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = { picker = true }) { Text("选择") }
    }
    if (picker) {
        TagPickerDialog(
            title = title,
            selections = selections,
            tags = tags,
            countTag = countTag,
            onDismiss = { picker = false },
            onApply = {
                picker = false
                onChange(it)
            }
        )
    }
}

@Composable
private fun TagPickerDialog(
    title: String,
    selections: List<TagSelection>,
    tags: List<NodeTag>,
    countTag: (NodeTag) -> Int,
    onDismiss: () -> Unit,
    onApply: (List<TagSelection>) -> Unit
) {
    var draft by remember { mutableStateOf(selections) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .height(360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "多个标签为 AND；选中后可点「取反」变成 NOT。留空表示任意节点。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                tags.forEach { tag ->
                    val selection = draft.firstOrNull { it.tagId == tag.id }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selection != null,
                            onCheckedChange = { checked ->
                                draft = if (checked) {
                                    draft + TagSelection(tag.id)
                                } else {
                                    draft.filterNot { it.tagId == tag.id }
                                }
                            }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                tag.name + if (tag.builtin) "（内置）" else "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "命中 ${countTag(tag)} 节点",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selection != null) {
                            TextButton(onClick = {
                                draft = draft.map {
                                    if (it.tagId == tag.id) it.copy(negated = !it.negated) else it
                                }
                            }) {
                                Text(
                                    if (selection.negated) "取反 ✓" else "取反",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selection.negated) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun DevicePicker(
    devices: List<DeviceTarget>,
    selectedMac: String,
    onPick: (DeviceTarget) -> Unit
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

private fun selectorText(selector: DeviceSelector, tags: List<NodeTag>): String {
    if (selector.tags.isEmpty()) return "任意节点"
    return selector.tags.joinToString(" ∧ ") { selection ->
        val name = tags.firstOrNull { it.id == selection.tagId }?.name ?: "已删除标签"
        if (selection.negated) "¬$name" else name
    }
}
