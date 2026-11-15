package com.mihomo.gateway.ui.policy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mihomo.gateway.data.catalog.NodeInfo
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.data.tags.TagExprParser
import com.mihomo.gateway.model.ConditionTemplate
import com.mihomo.gateway.model.NodeTag
import com.mihomo.gateway.model.TagExpr

/** 可视化条件树：组统一 AND/OR，组/标签可单独取反，子组表达括号。 */
enum class CondOp { AND, OR }

sealed interface CondNode {
    data class Tag(val tagId: String, val negated: Boolean = false) : CondNode
    data class Group(
        val op: CondOp = CondOp.AND,
        val negated: Boolean = false,
        val children: List<CondNode> = emptyList()
    ) : CondNode
}

object CondCodec {
    fun toRoot(expr: TagExpr?): CondNode.Group {
        val node = fromExpr(expr)
        return node as? CondNode.Group ?: CondNode.Group(children = listOf(node))
    }

    fun toExpr(node: CondNode): TagExpr? = when (node) {
        is CondNode.Tag -> TagExpr.Ref(node.tagId, node.negated)
        is CondNode.Group -> {
            val items = node.children.mapNotNull { toExpr(it) }
            if (items.isEmpty()) {
                null
            } else {
                val base = if (items.size == 1) {
                    items.first()
                } else if (node.op == CondOp.AND) {
                    TagExpr.And(items)
                } else {
                    TagExpr.Or(items)
                }
                if (node.negated) TagExpr.Not(base) else base
            }
        }
    }

    private fun fromExpr(expr: TagExpr?): CondNode = when (expr) {
        null -> CondNode.Group()
        is TagExpr.Ref -> CondNode.Tag(expr.tagId, expr.negated)
        is TagExpr.And -> CondNode.Group(CondOp.AND, children = expr.items.map { fromExpr(it) })
        is TagExpr.Or -> CondNode.Group(CondOp.OR, children = expr.items.map { fromExpr(it) })
        is TagExpr.Not -> when (val inner = expr.item) {
            is TagExpr.Ref -> CondNode.Tag(inner.tagId, !inner.negated)
            is TagExpr.And -> CondNode.Group(
                CondOp.AND, true, inner.items.map { fromExpr(it) }
            )
            is TagExpr.Or -> CondNode.Group(
                CondOp.OR, true, inner.items.map { fromExpr(it) }
            )
            is TagExpr.Not -> fromExpr(inner.item)
        }
    }
}

/**
 * 条件编辑区：可视化组块（默认）+ 高级表达式。
 * 可视化模式下所有操作都是结构化更新，不会产生语法错误。
 */
@Composable
fun ConditionSection(
    title: String,
    initial: TagExpr?,
    tags: List<NodeTag>,
    matchCount: (TagExpr?) -> Int,
    matchNodes: (TagExpr?) -> List<NodeInfo>,
    templates: List<ConditionTemplate>,
    onSaveTemplate: (String, TagExpr) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onExprChange: (TagExpr?) -> Unit,
    onInvalidChange: (Boolean) -> Unit
) {
    var root by remember { mutableStateOf(CondCodec.toRoot(initial)) }
    var advanced by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(TagExprParser.toText(initial, tags)) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showSaveTemplate by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }

    val parsed = if (advanced) TagExprParser.parse(text, tags) else Result.success(CondCodec.toExpr(root))
    val current = parsed.getOrNull()
    val count = matchCount(current)

    LaunchedEffect(root, text, advanced) {
        onInvalidChange(parsed.isFailure)
        error = parsed.exceptionOrNull()?.message
        if (parsed.isSuccess) onExprChange(parsed.getOrNull())
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showTemplates = true }) {
                Text("模板", style = MaterialTheme.typography.labelSmall)
            }
            Text(
                if (parsed.isFailure) "表达式有误" else "命中 $count 个节点 ▾",
                style = MaterialTheme.typography.labelSmall,
                color = if (parsed.isFailure) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                modifier = if (parsed.isFailure) {
                    Modifier
                } else {
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                }
            )
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = {
                if (advanced) {
                    val result = TagExprParser.parse(text, tags)
                    if (result.isSuccess) {
                        root = CondCodec.toRoot(result.getOrNull())
                        advanced = false
                        error = null
                    } else {
                        error = result.exceptionOrNull()?.message
                    }
                } else {
                    text = TagExprParser.toText(CondCodec.toExpr(root), tags)
                    advanced = true
                }
            }) {
                Text(if (advanced) "可视化" else "高级", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (advanced) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("例如 (香港 ∨ 台湾) ∧ ¬vless") },
                minLines = 2,
                isError = error != null,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                error ?: "支持 ∧ ∨ ¬ 与括号；标签名含空格时用引号",
                style = MaterialTheme.typography.labelSmall,
                color = if (error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (expanded && parsed.isSuccess) {
                val matched = matchNodes(parsed.getOrNull())
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    if (matched.isEmpty()) {
                        Text("没有命中的节点", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    } else {
                        matched.take(12).forEach { node ->
                            Text(
                                "• ${node.name}  (${node.type})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { AppContainer.navigator.openProxyNode(node.name) }
                                    .padding(vertical = 1.dp)
                            )
                        }
                        if (matched.size > 12) {
                            Text("还有 ${matched.size - 12} 个…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
            GroupBlock(
                group = root,
                tags = tags,
                depth = 0,
                onChange = { root = it },
                onDelete = null
            )
        }

        if (showTemplates) {
            AlertDialog(
                onDismissRequest = { showTemplates = false },
                title = { Text("条件模板") },
                text = {
                    Column(
                        Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (templates.isEmpty()) {
                            Text(
                                "还没有模板，可把当前条件保存为模板复用。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        templates.forEach { template ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(template.name, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        TagExprParser.toText(template.expr, tags),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = {
                                    root = CondCodec.toRoot(template.expr)
                                    expanded = false
                                    showTemplates = false
                                }) { Text("套用", style = MaterialTheme.typography.labelSmall) }
                                TextButton(onClick = { onDeleteTemplate(template.id) }) {
                                    Text(
                                        "删除",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        TextButton(
                            enabled = parsed.isSuccess,
                            onClick = {
                                templateName = ""
                                showSaveTemplate = true
                            }
                        ) { Text("保存当前条件为模板", style = MaterialTheme.typography.labelSmall) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTemplates = false }) { Text("关闭") }
                }
            )
        }

        if (showSaveTemplate) {
            AlertDialog(
                onDismissRequest = { showSaveTemplate = false },
                title = { Text("保存为模板") },
                text = {
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text("模板名") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = templateName.isNotBlank() && parsed.isSuccess,
                        onClick = {
                            parsed.getOrNull()?.let { expr ->
                                onSaveTemplate(templateName, expr)
                                showSaveTemplate = false
                            }
                        }
                    ) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveTemplate = false }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
private fun GroupBlock(
    group: CondNode.Group,
    tags: List<NodeTag>,
    depth: Int,
    onChange: (CondNode.Group) -> Unit,
    onDelete: (() -> Unit)?,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    fun move(from: Int, to: Int) {
        if (to !in group.children.indices) return
        val list = group.children.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        onChange(group.copy(children = list))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (depth == 0) "条件组" else "子组",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(6.dp))
            OpChip("且", group.op == CondOp.AND) { onChange(group.copy(op = CondOp.AND)) }
            OpChip("或", group.op == CondOp.OR) { onChange(group.copy(op = CondOp.OR)) }
            Spacer(Modifier.width(4.dp))
            OpChip("¬ 取反", group.negated) { onChange(group.copy(negated = !group.negated)) }
            Spacer(Modifier.weight(1f))
            ArrowButton("↑", onMoveUp)
            ArrowButton("↓", onMoveDown)
            if (onDelete != null) {
                Text(
                    "×",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 6.dp)
                )
            }
        }

        if (group.children.isEmpty()) {
            Text(
                "空组（不会生效），添加标签或子组",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        group.children.forEachIndexed { index, child ->
            when (child) {
                is CondNode.Tag -> TagRow(
                    tagId = child.tagId,
                    negated = child.negated,
                    tags = tags,
                    onMoveUp = if (index > 0) ({ move(index, index - 1) }) else null,
                    onMoveDown = if (index < group.children.lastIndex) ({ move(index, index + 1) }) else null,
                    onToggleNegated = {
                        onChange(group.copy(
                            children = group.children.toMutableList().also {
                                it[index] = child.copy(negated = !child.negated)
                            }
                        ))
                    },
                    onDelete = {
                        onChange(group.copy(children = group.children.filterIndexed { i, _ -> i != index }))
                    }
                )

                is CondNode.Group -> GroupBlock(
                    group = child,
                    tags = tags,
                    depth = depth + 1,
                    onChange = { updated ->
                        val list = group.children.toMutableList()
                        list[index] = updated
                        onChange(group.copy(children = list))
                    },
                    onMoveUp = if (index > 0) ({ move(index, index - 1) }) else null,
                    onMoveDown = if (index < group.children.lastIndex) ({ move(index, index + 1) }) else null,
                    onDelete = {
                        onChange(group.copy(children = group.children.filterIndexed { i, _ -> i != index }))
                    }
                )
            }
        }

        Row {
            AddTagButton(tags = tags) { tag ->
                onChange(group.copy(children = group.children + CondNode.Tag(tag.id)))
            }
            if (depth < 3) {
                TextButton(onClick = {
                    onChange(group.copy(children = group.children + CondNode.Group()))
                }) {
                    Text("＋ 子组", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun TagRow(
    tagId: String,
    negated: Boolean,
    tags: List<NodeTag>,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onToggleNegated: () -> Unit,
    onDelete: () -> Unit
) {
    val name = tags.firstOrNull { it.id == tagId }?.name ?: "已删除标签"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (negated) {
            Text("¬", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(4.dp))
        }
        Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        ArrowButton("↑", onMoveUp)
        ArrowButton("↓", onMoveDown)
        Text(
            "取反",
            style = MaterialTheme.typography.labelSmall,
            color = if (negated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onToggleNegated)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Text(
            "×",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onDelete)
                .padding(horizontal = 6.dp)
        )
    }
}

@Composable
private fun ArrowButton(text: String, onClick: (() -> Unit)?) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (onClick == null) MaterialTheme.colorScheme.outline
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = (if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}

@Composable
private fun OpChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun AddTagButton(tags: List<NodeTag>, onPick: (NodeTag) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { menu = true }) {
            Text("＋ 标签", style = MaterialTheme.typography.labelSmall)
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            modifier = Modifier.height(320.dp)
        ) {
            tags.forEach { tag ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (tag.builtin) "${tag.name}（内置）" else tag.name,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {
                        menu = false
                        onPick(tag)
                    }
                )
            }
        }
    }
}
