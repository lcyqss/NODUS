package com.mihomo.gateway.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.CustomRule
import com.mihomo.gateway.ui.components.SegmentedControl
import com.mihomo.gateway.ui.icons.GwIcon
import com.mihomo.gateway.ui.policy.DevicePolicyScreen
import com.mihomo.gateway.ui.policy.DevicePolicyViewModel
import kotlinx.coroutines.delay

internal val WarnColor = Color(0xFFEA580C)
internal val OkColor = Color(0xFF16A34A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(vm: RulesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var section by remember { mutableIntStateOf(1) }
    var tab by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<CustomRule?>(null) }
    val policyVm: DevicePolicyViewModel = viewModel()

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
            Box(Modifier.weight(1f)) {
                DevicePolicyScreen(vm = policyVm)
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
