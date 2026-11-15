package com.mihomo.gateway.ui.policy

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mihomo.gateway.data.catalog.DeviceTarget
import com.mihomo.gateway.data.catalog.NodeInfo
import com.mihomo.gateway.data.policy.PolicyRuntime
import com.mihomo.gateway.data.tags.TagExprParser
import com.mihomo.gateway.model.ConditionTemplate
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
    val templates by vm.templates.collectAsState()
    val intervalSec by vm.intervalSec.collectAsState()
    val ipIntervalSec by vm.ipIntervalSec.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()

    var editing by remember { mutableStateOf<DevicePolicy?>(null) }
    var showTags by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            vm.clearMessage()
        }
    }

    BackHandler(enabled = showTags) { showTags = false }
    BackHandler(enabled = showTemplates) { showTemplates = false }

    if (showTags) {
        NodeTagManagerScreen(onBack = { showTags = false }, vm = vm)
        return
    }

    if (showTemplates) {
        ConditionTemplateScreen(onBack = { showTemplates = false }, vm = vm)
        return
    }

    editing?.let { draft ->
        DevicePolicyEditorPage(
            draft = draft,
            devices = devices,
            tags = tags,
            templates = templates,
            busy = busy,
            matchCount = { expr -> vm.matchCount(expr) },
            matchNodes = { expr -> vm.matchNodes(expr) },
            onSaveTemplate = { name, expr -> vm.saveTemplate(name, expr) },
            onDeleteTemplate = { vm.deleteTemplate(it) },
            onBack = { editing = null },
            onSave = {
                editing = null
                vm.save(it)
            },
            onDelete = {
                editing = null
                vm.delete(draft.id)
            }
        )
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

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { showTemplates = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("条件模板", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "${templates.size} 个模板 · 可复用条件树",
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
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable {
                    showBackup = true
                    vm.exportBackup()
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("备份与恢复", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "导出/导入策略、标签、条件模板",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
        }
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
            val macCounts = policies.groupingBy { it.mac.lowercase() }.eachCount()
            policies.forEachIndexed { index, policy ->
                PolicyCard(
                    policy = policy,
                    runtime = runtimeById[policy.id],
                    tags = tags,
                    duplicate = (macCounts[policy.mac.lowercase()] ?: 0) > 1,
                    onToggle = { enabled -> vm.save(policy.copy(enabled = enabled)) },
                    onClick = { editing = policy }
                )
                if (index != policies.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    if (showBackup) {
        BackupDialog(vm = vm, onDismiss = { showBackup = false })
    }
}

@Composable
private fun BackupDialog(vm: DevicePolicyViewModel, onDismiss: () -> Unit) {
    val backupJson by vm.backupJson.collectAsState()
    val message by vm.backupMessage.collectAsState()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    var importText by remember { mutableStateOf("") }
    var confirmReplace by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var fileMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(backupJson.toByteArray())
                }
            }.onSuccess {
                fileMessage = "已导出到文件"
            }.onFailure {
                fileMessage = "导出文件失败: ${it.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.onSuccess { text ->
                if (text != null) {
                    importText = text
                    tab = 1
                    fileMessage = "已读取文件，请确认合并或覆盖"
                }
            }.onFailure {
                fileMessage = "读取文件失败: ${it.message}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("备份与恢复") },
        text = {
            Column(
                Modifier.height(380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabChip("导出", tab == 0) { tab = 0 }
                    TabChip("导入", tab == 1) { tab = 1 }
                }
                Spacer(Modifier.height(4.dp))

                if (tab == 0) {
                    OutlinedTextField(
                        value = backupJson,
                        onValueChange = {},
                        readOnly = true,
                        minLines = 8,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row {
                        TextButton(onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(backupJson))
                        }) { Text("复制", style = MaterialTheme.typography.labelSmall) }
                        TextButton(onClick = {
                            importText = backupJson
                            tab = 1
                        }) { Text("粘贴到导入", style = MaterialTheme.typography.labelSmall) }
                        TextButton(
                            enabled = backupJson.isNotBlank(),
                            onClick = { exportLauncher.launch("mihomo-gateway-backup.json") }
                        ) { Text("导出文件", style = MaterialTheme.typography.labelSmall) }
                    }
                } else {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        placeholder = { Text("粘贴备份 JSON") },
                        minLines = 8,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row {
                        TextButton(
                            enabled = importText.isNotBlank(),
                            onClick = { vm.importBackup(importText, merge = true) }
                        ) { Text("合并导入", style = MaterialTheme.typography.labelSmall) }
                        TextButton(
                            enabled = importText.isNotBlank(),
                            onClick = { confirmReplace = true }
                        ) {
                            Text(
                                "覆盖导入",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        TextButton(onClick = {
                            importText = clipboard.getText()?.text.orEmpty()
                        }) { Text("从剪贴板", style = MaterialTheme.typography.labelSmall) }
                        TextButton(onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }) { Text("从文件导入", style = MaterialTheme.typography.labelSmall) }
                    }
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                fileMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("覆盖导入？") },
            text = { Text("会替换当前设备策略、自定义标签和条件模板。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    vm.importBackup(importText, merge = false)
                }) { Text("覆盖", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun TinySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .width(38.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White)
        )
    }
}

@Composable
private fun PolicyCard(
    policy: DevicePolicy,
    runtime: PolicyRuntime?,
    tags: List<NodeTag>,
    duplicate: Boolean,
    onToggle: (Boolean) -> Unit,
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
            if (duplicate) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "重复 MAC",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            TinySwitch(checked = policy.enabled, onCheckedChange = onToggle)
            Spacer(Modifier.width(6.dp))
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
                if (policy.scheduleIntervalSec > 0) append(" · 间隔 ").append(policy.scheduleIntervalSec).append("s")
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

private fun selectorText(selector: DeviceSelector, tags: List<NodeTag>): String {
    val text = TagExprParser.toText(selector.expr, tags)
    return text.ifBlank { "任意节点" }
}
