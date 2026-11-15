package com.mihomo.gateway.ui.policy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.mihomo.gateway.data.catalog.DeviceTarget
import com.mihomo.gateway.data.policy.PolicyRuntime
import com.mihomo.gateway.data.tags.TagExprParser
import com.mihomo.gateway.model.DevicePolicy
import com.mihomo.gateway.model.DeviceSelector
import com.mihomo.gateway.model.NodeTag
import com.mihomo.gateway.model.TagExpr
import com.mihomo.gateway.ui.components.SubPageHeader
import com.mihomo.gateway.ui.icons.GwIcon
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * 设备策略：按源设备（MAC）选择标签表达式条件，并支持一个回落条件。
 * 表达式支持 ∧/∨/¬ 与括号；留空表示任意节点。
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
    val intervalSec by vm.intervalSec.collectAsState()
    val ipIntervalSec by vm.ipIntervalSec.collectAsState()
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
            subtitle = "按设备选择标签表达式，主条件不满足时回落",
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

        Spacer(Modifier.height(8.dp))
        IntervalRow(
            title = "延迟调度间隔",
            subtitle = "仅对设置了延迟阈值的策略生效",
            seconds = intervalSec,
            options = listOf(30 to "30 秒", 60 to "1 分钟", 300 to "5 分钟"),
            onChange = { vm.setInterval(it) }
        )
        Spacer(Modifier.height(8.dp))
        IntervalRow(
            title = "IP 跟随检查",
            subtitle = "设备 IP 变化后自动重建规则",
            seconds = ipIntervalSec,
            options = listOf(30 to "30 秒", 60 to "1 分钟", 120 to "2 分钟", 300 to "5 分钟"),
            onChange = { vm.setIpInterval(it) }
        )
        Spacer(Modifier.height(8.dp))

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
            matchCount = { expr -> vm.matchCount(expr) },
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
    matchCount: (TagExpr?) -> Int,
    onDismiss: () -> Unit,
    onSave: (DevicePolicy) -> Unit,
    onDelete: () -> Unit
) {
    var mac by remember { mutableStateOf(draft.mac) }
    var label by remember { mutableStateOf(draft.label) }
    var primaryText by remember { mutableStateOf(TagExprParser.toText(draft.primary.expr, tags)) }
    var fallbackText by remember { mutableStateOf(TagExprParser.toText(draft.fallback.expr, tags)) }
    var enabled by remember { mutableStateOf(draft.enabled) }
    var maxDelay by remember { mutableStateOf(if (draft.maxDelayMs > 0) draft.maxDelayMs.toString() else "") }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.mac.isBlank()) "添加设备策略" else "编辑设备策略") },
        text = {
            Column(
                Modifier
                    .height(460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

                ExprField(
                    title = "优先条件",
                    text = primaryText,
                    onTextChange = { primaryText = it },
                    tags = tags,
                    matchCount = matchCount
                )

                ExprField(
                    title = "回落条件",
                    text = fallbackText,
                    onTextChange = { fallbackText = it },
                    tags = tags,
                    matchCount = matchCount
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
                    val parsedPrimary = TagExprParser.parse(primaryText, tags)
                    val parsedFallback = TagExprParser.parse(fallbackText, tags)
                    if (parsedPrimary.isSuccess && parsedFallback.isSuccess) {
                        onSave(
                            draft.copy(
                                mac = mac.lowercase(),
                                label = label.trim(),
                                primary = DeviceSelector(parsedPrimary.getOrNull()),
                                fallback = DeviceSelector(parsedFallback.getOrNull()),
                                maxDelayMs = maxDelay.toIntOrNull() ?: 0,
                                enabled = enabled
                            )
                        )
                    }
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
private fun ExprField(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    tags: List<NodeTag>,
    matchCount: (TagExpr?) -> Int
) {
    Column {
        val parsed = TagExprParser.parse(text, tags)
        val error = parsed.exceptionOrNull()?.message
        val count = parsed.getOrNull()?.let { matchCount(it) } ?: 0
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("例如 香港 ∧ vless，留空=任意") },
            minLines = 2,
            isError = error != null,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tags.take(10).forEach { tag ->
                Text(
                    tag.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            onTextChange(if (text.isBlank()) tag.name else "$text ∧ ${tag.name}")
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
        Text(
            error ?: "命中 $count 个节点 · 支持 ∧ ∨ ¬ 与括号",
            style = MaterialTheme.typography.labelSmall,
            color = if (error != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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

@Composable
private fun IntervalRow(
    title: String,
    subtitle: String,
    seconds: Int,
    options: List<Pair<Int, String>>,
    onChange: (Int) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { menu = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                options.firstOrNull { it.first == seconds }?.second ?: "$seconds 秒",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(" ▾", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        menu = false
                        onChange(value)
                    }
                )
            }
        }
    }
}

private fun parseOrNull(text: String, tags: List<NodeTag>): TagExpr? =
    TagExprParser.parse(text, tags).getOrNull()

private fun selectorText(selector: DeviceSelector, tags: List<NodeTag>): String {
    val text = TagExprParser.toText(selector.expr, tags)
    return text.ifBlank { "任意节点" }
}
