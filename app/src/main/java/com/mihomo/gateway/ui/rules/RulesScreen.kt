package com.mihomo.gateway.ui.rules

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.CustomRule
import com.mihomo.gateway.model.RuleInfo
import com.mihomo.gateway.model.RuleKind
import com.mihomo.gateway.ui.components.EmptyState
import com.mihomo.gateway.ui.components.Tag

@Composable
fun RulesScreen(vm: RulesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // 顶部汇总
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val enabled = state.custom.count { it.enabled }
            Text(
                "自定义规则 ${state.custom.size} 条（启用 $enabled）",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "与订阅规则重复 ${state.conflicts.size} 条",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.conflicts.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else Color(0xFFEA580C)
            )
            Text(
                "当前模式：${state.mode}",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.mode == "rule") MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error
            )
            if (state.mode != "rule") {
                Text(
                    "全局/直连模式下规则不会生效，请到设置里切回“规则”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == 0) Button(onClick = { tab = 0 }) { Text("自定义") }
            else OutlinedButton(onClick = { tab = 0 }) { Text("自定义") }
            if (tab == 1) Button(onClick = { tab = 1 }) { Text("订阅规则") }
            else OutlinedButton(onClick = { tab = 1 }) { Text("订阅规则") }
        }

        if (tab == 0) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                OutlinedButton(onClick = { showAdd = true }) { Text("添加规则") }
                Button(onClick = { vm.apply() }, enabled = !state.applying) {
                    Text(if (state.applying) "应用中…" else "应用", maxLines = 1)
                }
            }
        }

        state.message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        state.error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(8.dp))

        if (tab == 0) {
            CustomRulesList(
                rules = state.custom,
                conflicts = state.conflicts,
                onToggle = vm::setEnabled,
                onMove = vm::move,
                onDelete = vm::delete
            )
        } else {
            BuiltInRulesList(state.builtIn)
        }
    }

    if (showAdd) {
        AddRuleDialog(
            targets = state.targets,
            onDismiss = { showAdd = false },
            onSave = { kind, value, target, noResolve ->
                vm.add(kind, value, target, noResolve)
                showAdd = false
            }
        )
    }
}

@Composable
private fun CustomRulesList(
    rules: List<CustomRule>,
    conflicts: Set<String>,
    onToggle: (CustomRule, Boolean) -> Unit,
    onMove: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    if (rules.isEmpty()) {
        EmptyState("还没有自定义规则，点右上角“添加”")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(rules, key = { it.id }) { rule ->
            val conflict = conflicts.contains(rule.key().uppercase())
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggle(rule, it) }
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tag(rule.kind.wire)
                        Tag(rule.target, MaterialTheme.colorScheme.secondary)
                        if (conflict) Tag("重复", Color(0xFFEA580C))
                    }
                    Text(
                        rule.value,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text("↑", modifier = Modifier.clickable { onMove(rule.id, true) }.padding(6.dp))
                Text("↓", modifier = Modifier.clickable { onMove(rule.id, false) }.padding(6.dp))
                Text(
                    "✕",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable { onDelete(rule.id) }.padding(6.dp)
                )
            }
        }
    }
}

@Composable
private fun BuiltInRulesList(rules: List<RuleInfo>) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    val types = remember(rules) { rules.map { it.type }.distinct().take(10) }
    val filtered = remember(rules, query, typeFilter) {
        rules.filter { rule ->
            (typeFilter == null || rule.type == typeFilter) &&
                (query.isBlank() ||
                    rule.payload.contains(query, true) ||
                    rule.proxy.contains(query, true))
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("搜索订阅规则") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(6.dp))

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            if (typeFilter == null) Button(onClick = { typeFilter = null }) { Text("全部") }
            else OutlinedButton(onClick = { typeFilter = null }) { Text("全部") }
        }
        items(types) { type ->
            if (typeFilter == type) Button(onClick = { typeFilter = type }) { Text(type) }
            else OutlinedButton(onClick = { typeFilter = type }) { Text(type) }
        }
    }

    Spacer(Modifier.height(6.dp))
    Text(
        "${filtered.size} / ${rules.size} 条",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))

    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(filtered) { rule ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Tag(rule.type)
                Text(rule.payload, style = MaterialTheme.typography.bodySmall)
                Text(
                    "→ ${rule.proxy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun AddRuleDialog(
    targets: List<String>,
    onDismiss: () -> Unit,
    onSave: (RuleKind, String, String, Boolean) -> Unit
) {
    var kind by remember { mutableStateOf(RuleKind.DOMAIN) }
    var value by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(targets.firstOrNull() ?: "DIRECT") }
    var noResolve by remember { mutableStateOf(kind.needsNoResolve) }
    var targetMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义规则") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(RuleKind.entries) { item ->
                        FilterChip(
                            selected = kind == item,
                            onClick = {
                                kind = item
                                noResolve = item.needsNoResolve
                            },
                            label = { Text(item.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(if (kind.needsNoResolve) "网段，如 10.0.0.0/8" else "域名，如 example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box {
                    OutlinedButton(onClick = { targetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("目标：$target")
                    }
                    DropdownMenu(
                        expanded = targetMenu,
                        onDismissRequest = { targetMenu = false },
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        targets.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    target = item
                                    targetMenu = false
                                }
                            )
                        }
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
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(kind, value, target, noResolve) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
