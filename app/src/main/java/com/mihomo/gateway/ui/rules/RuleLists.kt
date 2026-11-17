package com.mihomo.gateway.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mihomo.gateway.model.CustomRule
import com.mihomo.gateway.model.RuleInfo
import com.mihomo.gateway.model.RuleKind
import com.mihomo.gateway.ui.components.EmptyState
import com.mihomo.gateway.ui.components.SearchBar
import com.mihomo.gateway.ui.icons.GwIcon
@Composable
internal fun CustomRulesList(
    rules: List<CustomRule>,
    conflicts: Set<String>,
    onToggle: (CustomRule, Boolean) -> Unit,
    onMove: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (CustomRule) -> Unit,
    onAdd: () -> Unit
) {
    if (rules.isEmpty()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EmptyState("还没有自定义规则")
            OutlinedButton(onClick = onAdd) { Text("添加规则") }
        }
        return
    }

    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowStepPx = with(LocalDensity.current) { 76.dp.toPx() }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        items(rules, key = { it.id }) { rule ->
            val conflict = conflicts.contains(rule.key().uppercase())
            val dragging = draggingId == rule.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                    .pointerInput(rule.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = rule.id
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                            },
                            onDragEnd = {
                                val steps = kotlin.math.round(dragOffset / rowStepPx).toInt()
                                repeat(kotlin.math.abs(steps)) {
                                    onMove(rule.id, steps < 0)
                                }
                                draggingId = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffset = 0f
                            }
                        )
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (dragging) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surface
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GwIcon(
                    GwIcon.Logs,
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RuleTag(rule.kind.wire, kindColor(rule.kind))
                        Spacer(Modifier.width(4.dp))
                        RuleTag(
                            rule.target,
                            MaterialTheme.colorScheme.secondary,
                            Modifier.weight(1f, fill = false)
                        )
                        if (conflict) {
                            Spacer(Modifier.width(4.dp))
                            RuleTag("重复", WarnColor)
                        }
                        Spacer(Modifier.weight(1f))
                        CompactSwitch(checked = rule.enabled) { onToggle(rule, it) }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            rule.value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (rule.enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        RuleAction("✎", MaterialTheme.colorScheme.primary) { onEdit(rule) }
                        RuleAction("↑") { onMove(rule.id, true) }
                        RuleAction("↓") { onMove(rule.id, false) }
                        RuleAction("✕", MaterialTheme.colorScheme.error) { onDelete(rule.id) }
                    }
                }
            }
        }
    }
}

/** 紧凑开关，避免默认 Switch 的 48dp 触摸区把行高撑大。 */
@Composable
private fun CompactSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val track = if (checked) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(
        Modifier
            .width(38.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(track)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun RuleTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RuleAction(
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun kindColor(kind: RuleKind): Color = when (kind) {
    RuleKind.DOMAIN, RuleKind.DOMAIN_SUFFIX, RuleKind.DOMAIN_KEYWORD -> MaterialTheme.colorScheme.primary
    RuleKind.IP_CIDR, RuleKind.IP_CIDR6 -> MaterialTheme.colorScheme.secondary
    RuleKind.SRC_IP_CIDR -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun ruleTypeColor(type: String): Color = when {
    type.contains("SRC", true) -> MaterialTheme.colorScheme.tertiary
    type.contains("IP-CIDR", true) -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
internal fun BuiltInRulesList(rules: List<RuleInfo>) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    val types = remember(rules) { rules.map { it.type }.distinct() }
    val filtered = remember(rules, query, typeFilter) {
        rules.filter { rule ->
            (typeFilter == null || rule.type == typeFilter) &&
                (query.isBlank() ||
                    rule.payload.contains(query, true) ||
                    rule.proxy.contains(query, true))
        }
    }

    SearchBar(
        value = query,
        onValueChange = { query = it },
        placeholder = "搜索域名或策略"
    )

    Spacer(Modifier.height(6.dp))

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        selectedLabelColor = MaterialTheme.colorScheme.primary
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            FilterChip(
                selected = typeFilter == null,
                onClick = { typeFilter = null },
                label = { Text("全部") },
                colors = chipColors
            )
        }
        items(types) { type ->
            FilterChip(
                selected = typeFilter == type,
                onClick = { typeFilter = if (typeFilter == type) null else type },
                label = { Text(type) },
                colors = chipColors
            )
        }
    }

    Spacer(Modifier.height(4.dp))
    Text(
        "${filtered.size} / ${rules.size} 条",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))

    if (rules.isEmpty()) {
        EmptyState("订阅未提供分流规则")
        return
    }
    if (filtered.isEmpty()) {
        EmptyState("没有匹配的规则")
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        items(filtered) { rule ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RuleTag(rule.type, ruleTypeColor(rule.type))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        rule.payload,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "→ ${rule.proxy}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
