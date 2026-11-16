package com.mihomo.gateway.ui.policy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.mihomo.gateway.data.tags.RegionPresets
import com.mihomo.gateway.data.tags.TagExprParser
import com.mihomo.gateway.model.NodeTag
import com.mihomo.gateway.ui.components.SubPageHeader
import com.mihomo.gateway.ui.icons.GwIcon
import java.util.UUID

/**
 * 节点标签管理：关键词式标签（包含 OR / 排除 NOT）；内置类型与地区标签只读。
 */
@Composable
fun NodeTagManagerScreen(
    onBack: () -> Unit,
    vm: DevicePolicyViewModel
) {
    val tags by vm.tags.collectAsState()
    val nodes by vm.nodes.collectAsState()
    val busy by vm.busy.collectAsState()

    var editing by remember { mutableStateOf<NodeTag?>(null) }
    var confirmDelete by remember { mutableStateOf<NodeTag?>(null) }
    var regionExpanded by remember { mutableStateOf(true) }
    var customExpanded by remember { mutableStateOf(true) }
    var builtinExpanded by remember { mutableStateOf(true) }

    val region = tags.filterNot { it.builtin }
        .filter { it.id.startsWith("__region_") }
        .sortedBy { tag ->
            RegionPresets.all.indexOfFirst { it.id == tag.id }
                .let { if (it < 0) Int.MAX_VALUE else it }
        }
    val custom = tags.filterNot { it.builtin || it.id.startsWith("__region_") }
    val builtin = tags.filter { it.builtin }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubPageHeader(
            title = "节点标签",
            subtitle = "包含关键词为 OR，排除关键词为 NOT",
            onBack = onBack
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = {
                    editing = NodeTag(id = UUID.randomUUID().toString(), name = "")
                }) {
                    GwIcon(GwIcon.Plus, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (nodes.isEmpty()) {
            Text(
                "当前订阅没有可解析的节点；先导入/更新订阅。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        TagSectionHeader(
            title = "地区标签（预设，可编辑）",
            count = region.size,
            expanded = regionExpanded,
            onToggle = { regionExpanded = !regionExpanded }
        ) {
            TextButton(onClick = { vm.restoreRegions() }) {
                Text("恢复默认", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (regionExpanded) {
        if (region.isEmpty()) {
            Text(
                "地区预设已全部删除；可新建自定义标签替代。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            region.forEachIndexed { index, tag ->
                TagRow(
                    tag = tag,
                    count = vm.countTag(tag),
                    subtitle = "包含：" + tag.include.joinToString(" / "),
                    onClick = { editing = tag },
                    onDelete = { confirmDelete = tag }
                )
                if (index != region.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }

        }
        Spacer(Modifier.height(16.dp))
        TagSectionHeader(
            title = "自定义标签",
            count = custom.size,
            expanded = customExpanded,
            onToggle = { customExpanded = !customExpanded }
        )

        if (customExpanded) {
        if (custom.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp)
            ) {
                Text("还没有自定义标签", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "例如：香港 = 包含 HK/香港/hongkong，排除「剩余流量」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            custom.forEachIndexed { index, tag ->
                TagRow(
                    tag = tag,
                    count = vm.countTag(tag),
                    subtitle = tag.expr?.let { "表达式：" + TagExprParser.toText(it, tags) }
                        ?: buildString {
                            append("包含：").append(tag.include.joinToString(" / ").ifBlank { "任意" })
                            if (tag.exclude.isNotEmpty()) {
                                append(" · 排除：").append(tag.exclude.joinToString(" / "))
                            }
                        },
                    onClick = { editing = tag },
                    onDelete = { confirmDelete = tag }
                )
                if (index != custom.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }

        }
        Spacer(Modifier.height(16.dp))
        TagSectionHeader(
            title = "内置类型（当前订阅 ${builtin.size} 种，只读）",
            count = builtin.size,
            expanded = builtinExpanded,
            onToggle = { builtinExpanded = !builtinExpanded }
        )
        if (builtinExpanded) {
            Text(
                "由当前订阅节点自动生成；切换订阅后自动更新",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
        builtin.forEachIndexed { index, tag ->
            TagRow(
                tag = tag,
                count = vm.countTag(tag),
                subtitle = "节点协议",
                onClick = null,
                onDelete = null
            )
            if (index != builtin.lastIndex) Spacer(Modifier.height(6.dp))
        }

        }
        Spacer(Modifier.height(12.dp))
    }

    editing?.let { tag ->
        TagEditorDialog(
            tag = tag,
            tags = tags,
            onDismiss = { editing = null },
            onSave = {
                editing = null
                vm.saveTag(it)
            }
        )
    }

    confirmDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除标签？") },
            text = { Text("删除「${tag.name}」后，引用它的设备策略会提示标签已删除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    vm.deleteTag(tag.id)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TagSectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (expanded) "▾" else "▸",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        trailing?.let {
            Spacer(Modifier.width(4.dp))
            it()
        }
    }
}

@Composable
private fun TagRow(
    tag: NodeTag,
    count: Int,
    subtitle: String,
    onClick: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                tag.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "命中 $count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onDelete != null) {
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onDelete) {
                Text("删除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TagEditorDialog(
    tag: NodeTag,
    tags: List<NodeTag>,
    onDismiss: () -> Unit,
    onSave: (NodeTag) -> Unit
) {
    var name by remember { mutableStateOf(tag.name) }
    var include by remember { mutableStateOf(tag.include.joinToString(", ")) }
    var exclude by remember { mutableStateOf(tag.exclude.joinToString(", ")) }
    var exprText by remember { mutableStateOf(TagExprParser.toText(tag.expr, tags)) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag.name.isBlank()) "新建标签" else "编辑标签") },
        text = {
            Column(
                Modifier
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("标签名，如 香港") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = include,
                    onValueChange = { include = it },
                    label = { Text("包含关键词（逗号分隔，任一命中）") },
                    minLines = 2,
                    enabled = exprText.isBlank()
                )
                OutlinedTextField(
                    value = exclude,
                    onValueChange = { exclude = it },
                    label = { Text("排除关键词（逗号分隔，命中即排除）") },
                    minLines = 2,
                    enabled = exprText.isBlank()
                )
                OutlinedTextField(
                    value = exprText,
                    onValueChange = {
                        exprText = it
                        error = null
                    },
                    label = { Text("组合表达式（可选，留空用关键词）") },
                    placeholder = { Text("例：vless ∨ hysteria2") },
                    minLines = 2,
                    isError = error != null
                )
                Text(
                    error ?: if (exprText.isNotBlank()) {
                        "已填写表达式：关键词将被忽略"
                    } else {
                        "表达式可引用其他标签；含空格的标签名用引号"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val parsed = if (exprText.isBlank()) {
                        null
                    } else {
                        TagExprParser.parse(exprText, tags)
                    }
                    if (parsed != null && parsed.isFailure) {
                        error = parsed.exceptionOrNull()?.message
                    } else {
                        onSave(
                            tag.copy(
                                name = name.trim(),
                                include = splitKeywords(include),
                                exclude = splitKeywords(exclude),
                                expr = parsed?.getOrNull()
                            )
                        )
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun splitKeywords(raw: String): List<String> =
    raw.split(',', '，', ' ', '\n', '、')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
