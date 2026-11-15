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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.mihomo.gateway.data.tags.TagExprParser
import com.mihomo.gateway.data.catalog.DeviceTarget
import com.mihomo.gateway.model.ConditionTemplate
import com.mihomo.gateway.model.DevicePolicy
import com.mihomo.gateway.model.DeviceSelector
import com.mihomo.gateway.ui.components.SubPageHeader
import java.util.UUID

/** 条件模板管理：重命名、排序、删除；新建仍在条件编辑区完成。 */
@Composable
fun ConditionTemplateScreen(
    onBack: () -> Unit,
    vm: DevicePolicyViewModel
) {
    val templates by vm.templates.collectAsState()
    val tags by vm.tags.collectAsState()
    val devices by vm.devices.collectAsState()
    val policies by vm.policies.collectAsState()

    var renaming by remember { mutableStateOf<ConditionTemplate?>(null) }
    var deleting by remember { mutableStateOf<ConditionTemplate?>(null) }
    var applying by remember { mutableStateOf<ConditionTemplate?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubPageHeader(
            title = "条件模板",
            subtitle = "在条件编辑区保存，供其他设备套用",
            onBack = onBack
        )

        Spacer(Modifier.height(10.dp))

        if (templates.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Text("还没有条件模板", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "在设备策略编辑页的条件区点「模板」→「保存当前条件为模板」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            templates.forEachIndexed { index, template ->
                TemplateRow(
                    template = template,
                    expression = TagExprParser.toText(template.expr, tags),
                    canUp = index > 0,
                    canDown = index < templates.lastIndex,
                    onUp = { vm.moveTemplate(template.id, true) },
                    onDown = { vm.moveTemplate(template.id, false) },
                    onApply = { applying = template },
                    onRename = { renaming = template },
                    onDelete = { deleting = template }
                )
                if (index != templates.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    renaming?.let { template ->
        var name by remember(template.id) { mutableStateOf(template.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重命名模板") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("模板名") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.renameTemplate(template.id, name)
                        renaming = null
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("取消") }
            }
        )
    }

    deleting?.let { template ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除模板？") },
            text = { Text("删除「${template.name}」后，已套用它的设备策略不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTemplate(template.id)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }

    applying?.let { template ->
        AlertDialog(
            onDismissRequest = { applying = null },
            title = { Text("从模板新建设备策略") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "模板：${template.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    if (devices.isEmpty()) {
                        Text(
                            "未发现设备，请先连接热点/局域网",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    devices.forEach { target ->
                        val existing = policies.firstOrNull {
                            it.mac.equals(target.mac, true)
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val policy = existing?.copy(
                                        primary = DeviceSelector(template.expr)
                                    ) ?: DevicePolicy(
                                        id = UUID.randomUUID().toString(),
                                        mac = target.mac,
                                        label = target.hostname ?: target.mac,
                                        primary = DeviceSelector(template.expr),
                                        fallback = DeviceSelector()
                                    )
                                    vm.save(policy)
                                    applying = null
                                    onBack()
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    target.hostname?.takeIf { it.isNotBlank() } ?: target.mac,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${target.mac} · ${target.ip}" +
                                        if (existing != null) " · 将替换现有策略" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (existing != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { applying = null }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun TemplateRow(
    template: ConditionTemplate,
    expression: String,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onApply: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                template.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            SmallAction("↑", canUp, onUp)
            SmallAction("↓", canDown, onDown)
            SmallAction("新建", true, onApply, MaterialTheme.colorScheme.primary)
            SmallAction("重命名", true, onRename)
            SmallAction("删除", true, onDelete, MaterialTheme.colorScheme.error)
        }
        Text(
            expression.ifBlank { "任意节点" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SmallAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (enabled) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        modifier = (if (enabled) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        })
            .clip(RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}
