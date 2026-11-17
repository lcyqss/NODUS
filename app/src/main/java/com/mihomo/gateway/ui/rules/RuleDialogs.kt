package com.mihomo.gateway.ui.rules

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mihomo.gateway.model.CustomRule
import com.mihomo.gateway.model.RuleKind
import com.mihomo.gateway.ui.components.SearchBar
@Composable
internal fun AddSheet(onManual: () -> Unit, onTemplate: (String) -> Unit) {
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
internal fun AddRuleDialog(
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
