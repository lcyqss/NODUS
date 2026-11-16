package com.mihomo.gateway.ui.rules

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.CustomRule
import com.mihomo.gateway.model.RuleInfo
import com.mihomo.gateway.model.RuleKind
import com.mihomo.gateway.ui.components.EmptyState
import com.mihomo.gateway.ui.components.SegmentedControl
import com.mihomo.gateway.ui.components.SearchBar
import com.mihomo.gateway.ui.components.SectionCard
import com.mihomo.gateway.ui.icons.GwIcon
import com.mihomo.gateway.ui.policy.DevicePolicyScreen
import com.mihomo.gateway.ui.policy.DevicePolicyViewModel
import kotlinx.coroutines.delay

private val WarnColor = Color(0xFFEA580C)
private val OkColor = Color(0xFF16A34A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(vm: RulesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var section by remember { mutableIntStateOf(1) }
    var tab by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<CustomRule?>(null) }
    var showPolicy by remember { mutableStateOf(false) }
    val policyVm: DevicePolicyViewModel = viewModel()
    val policies by policyVm.policies.collectAsState()

    BackHandler(enabled = showPolicy) { showPolicy = false }

    if (showPolicy) {
        DevicePolicyScreen(onBack = { showPolicy = false }, vm = policyVm)
        return
    }

    // 操作提示几秒后自动消失，避免“记得应用”之类文案一直挂着
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(4000)
            vm.clearMessage()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SegmentedControl(
            options = listOf("设备策略", "规则"),
            selectedIndex = section,
            onSelect = { section = it }
        )

        Spacer(Modifier.height(12.dp))

        if (section == 0) {
            val enabled = policies.count { it.enabled }
            SectionCard {
                Text("设备策略", style = MaterialTheme.typography.titleLarge)
                Text(
                    "按设备 MAC 选择节点标签，条件不满足时回落。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "$enabled / ${policies.size} 条已启用",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "设备策略与分流规则相互独立",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = { showPolicy = true }) { Text("管理") }
                }
                Text(
                    "设备策略决定某台设备的出口节点；规则决定流量如何匹配。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            SummaryLine(state)
            if (state.mode != "rule") ModeWarning(state.mode)

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SegmentedControl(
                    options = listOf("自定义", "订阅规则"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.weight(1f)
                )
                if (tab == 0) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { showSheet = true }, enabled = !state.applying) {
                        GwIcon(GwIcon.Plus, tint = MaterialTheme.colorScheme.primary)
                    }
                    if (state.dirty || state.applying) {
                        Spacer(Modifier.width(2.dp))
                        Button(onClick = vm::apply, enabled = !state.applying) {
                            Text(if (state.applying) "应用中…" else "应用", maxLines = 1)
                        }
                    }
                }
            }

            if (tab == 0) {
                state.message?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                state.error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (tab == 0) {
                CustomRulesList(
                    rules = state.custom,
                    conflicts = state.conflicts,
                    onToggle = vm::setEnabled,
                    onMove = vm::move,
                    onDelete = vm::delete,
                    onEdit = { editingRule = it },
                    onAdd = { showSheet = true }
                )
            } else {
                BuiltInRulesList(state.builtIn)
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            AddSheet(
                onManual = {
                    showSheet = false
                    showAdd = true
                },
                onTemplate = { name ->
                    vm.addTemplate(name)
                    showSheet = false
                }
            )
        }
    }

    if (showAdd) {
        AddRuleDialog(
            targets = state.targets,
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { kind, value, target, noResolve ->
                vm.add(kind, value, target, noResolve)
            }
        )
    }

    editingRule?.let { rule ->
        AddRuleDialog(
            targets = state.targets,
            initial = rule,
            onDismiss = { editingRule = null },
            onSave = { kind, value, target, noResolve ->
                vm.update(
                    rule.copy(
                        kind = kind,
                        value = value,
                        target = target,
                        noResolve = noResolve
                    )
                )
            }
        )
    }
}

/** 汇总收敛成一行：条数 · 启用 · 重复 · 生效状态。 */
@Composable
private fun SummaryLine(state: RulesUiState) {
    val enabled = state.custom.count { it.enabled }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatColumn("自定义", state.custom.size.toString())
        Spacer(Modifier.width(26.dp))
        StatColumn("启用", enabled.toString())
        Spacer(Modifier.width(26.dp))
        StatColumn(
            "重复",
            state.conflicts.size.toString(),
            if (state.conflicts.isEmpty()) MaterialTheme.colorScheme.onSurface else WarnColor
        )
        Spacer(Modifier.weight(1f))
        Text(
            when {
                state.applying -> "应用中…"
                state.dirty -> "有未应用的改动"
                else -> "已生效"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
                state.applying -> MaterialTheme.colorScheme.primary
                state.dirty -> WarnColor
                else -> OkColor
            }
        )
    }
}

@Composable
private fun StatColumn(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}

@Composable
private fun ModeWarning(mode: String) {
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            "当前为${if (mode == "global") "全局" else "直连"}模式，规则不会生效；请到「设置 → 网关 → 运行模式」切回规则",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun CustomRulesList(
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
private fun BuiltInRulesList(rules: List<RuleInfo>) {
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

@Composable
private fun AddSheet(onManual: () -> Unit, onTemplate: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
    ) {
        Text("添加到自定义规则", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        SheetRow(
            title = "手动添加",
            subtitle = "选择类型、填写域名或网段、指定目标策略",
            onClick = onManual
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "模板",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RulesViewModel.TEMPLATE_NAMES.forEach { name ->
            SheetRow(
                title = name,
                subtitle = "${RulesViewModel.templateDescription(name)} · ${RulesViewModel.templateCount(name)} 条",
                onClick = { onTemplate(name) }
            )
        }
    }
}

@Composable
private fun SheetRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
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
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun AddRuleDialog(
    targets: List<String>,
    initial: CustomRule?,
    onDismiss: () -> Unit,
    onSave: (RuleKind, String, String, Boolean) -> Result<Unit>
) {
    var kind by remember { mutableStateOf(initial?.kind ?: RuleKind.DOMAIN) }
    var value by remember { mutableStateOf(initial?.value.orEmpty()) }
    var target by remember {
        mutableStateOf(initial?.target ?: targets.firstOrNull() ?: "DIRECT")
    }
    var noResolve by remember { mutableStateOf(initial?.noResolve ?: kind.needsNoResolve) }
    var kindMenu by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加自定义规则" else "编辑自定义规则") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box {
                    OutlinedButton(
                        onClick = { kindMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(kind.wire, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                kind.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                        RuleKind.entries.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.wire} · ${item.label}") },
                                onClick = {
                                    kind = item
                                    noResolve = item.needsNoResolve
                                    kindMenu = false
                                },
                                trailingIcon = {
                                    if (item == kind) {
                                        Text("✓", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = {
                        Text(if (kind.needsNoResolve) "网段，如 10.0.0.0/8" else "域名，如 example.com")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { showTargetPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "目标：$target",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "更换",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("no-resolve（IP 规则建议开启）", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = noResolve, onCheckedChange = { noResolve = it })
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (value.isBlank()) {
                        error = "值不能为空"
                        return@TextButton
                    }
                    val result = onSave(kind, value.trim(), target, noResolve)
                    if (result.isSuccess) {
                        onDismiss()
                    } else {
                        error = result.exceptionOrNull()?.message ?: "保存失败"
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showTargetPicker) {
        TargetPickerDialog(
            targets = targets,
            current = target,
            onPick = {
                target = it
                showTargetPicker = false
            },
            onDismiss = { showTargetPicker = false }
        )
    }
}

@Composable
private fun TargetPickerDialog(
    targets: List<String>,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(targets, query, current) {
        val others = targets.filter { it != current }
        if (query.isBlank()) {
            listOf(current) + others
        } else {
            listOf(current).filter { it.contains(query, true) } +
                others.filter { it.contains(query, true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择目标策略") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchBar(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索策略或节点名"
                )
                LazyColumn(
                    Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filtered) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (item == current) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                    else Color.Transparent
                                )
                                .clickable { onPick(item) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                item,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (item == current) {
                                Text(
                                    "当前",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
