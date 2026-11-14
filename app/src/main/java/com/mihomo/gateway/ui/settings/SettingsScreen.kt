package com.mihomo.gateway.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.IpMode
import com.mihomo.gateway.ui.components.NavRow
import com.mihomo.gateway.ui.components.ScreenHeader
import com.mihomo.gateway.ui.components.SectionCard
import com.mihomo.gateway.ui.diagnostics.DiagnosticsScreen
import com.mihomo.gateway.ui.logs.LogsScreen
import com.mihomo.gateway.ui.providers.ProvidersScreen

private enum class SettingsPage { MAIN, SUBSCRIPTION, PROVIDERS, DIAGNOSTICS, LOGS }

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    when (page) {
        SettingsPage.MAIN -> SettingsMain(
            vm = vm,
            onSubscription = { page = SettingsPage.SUBSCRIPTION },
            onProviders = { page = SettingsPage.PROVIDERS },
            onDiagnostics = { page = SettingsPage.DIAGNOSTICS },
        onLogs = { page = SettingsPage.LOGS }
        )

        SettingsPage.SUBSCRIPTION -> SubscriptionPage(vm, onBack = { page = SettingsPage.MAIN })
        SettingsPage.PROVIDERS -> ProvidersScreen(onBack = { page = SettingsPage.MAIN })
        SettingsPage.DIAGNOSTICS -> DiagnosticsScreen(onBack = { page = SettingsPage.MAIN })
        SettingsPage.LOGS -> LogsScreen()
    }
}

@Composable
private fun SettingsMain(
    vm: SettingsViewModel,
    onSubscription: () -> Unit,
    onProviders: () -> Unit,
    onDiagnostics: () -> Unit,
    onLogs: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    val context = LocalContext.current
    var showCleanup by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    var restoreJson by remember { mutableStateOf("") }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "-"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader(title = "设置", subtitle = "网关参数与订阅")

        SectionCard(title = "节点地址族") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton("自动", IpMode.AUTO, settings.ipMode, busy, vm::setMode)
                ModeButton("IPv6", IpMode.V6, settings.ipMode, busy, vm::setMode)
                ModeButton("IPv4", IpMode.V4, settings.ipMode, busy, vm::setMode)
                ModeButton("双栈", IpMode.DUAL, settings.ipMode, busy, vm::setMode)
            }
            Text(
                "自动：探测本机 IPv6 可用性后选择节点出站地址族",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(title = "运行模式") {
            val runtimeMode by vm.runtimeMode.collectAsState()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("rule" to "规则", "global" to "全局", "direct" to "直连").forEach { (mode, label) ->
                    if (runtimeMode == mode) {
                        Button(onClick = { vm.setRuntimeMode(mode) }, enabled = !busy) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { vm.setRuntimeMode(mode) }, enabled = !busy) { Text(label) }
                    }
                }
            }
            Text(
                "规则=按分流；全局=全部走代理；直连=全部不走代理",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(title = "开关") {
            SwitchRow(
                title = "IPv6 拦截",
                subtitle = "关闭后客户端可用 IPv6 直连",
                checked = settings.blockIpv6,
                enabled = !busy,
                onChange = vm::setBlockIpv6
            )
            SwitchRow(
                title = "开机自启",
                subtitle = "重启后自动启动网关",
                checked = settings.autoStart,
                enabled = !busy,
                onChange = vm::setAutoStart
            )
        }

        SectionCard(title = "管理") {
            NavRow(
                title = "订阅管理",
                subtitle = settings.subscriptionUrl.ifBlank { "未设置订阅" },
                onClick = onSubscription
            )
            NavRow(
                title = "Provider",
                subtitle = "节点来源 / 节点数 / 健康检查",
                onClick = onProviders
            )
            NavRow(
                title = "日志",
                subtitle = "实时日志 / 搜索 / 导出",
                onClick = onLogs
            )
            NavRow(
                title = "诊断",
                subtitle = "内核状态 / root 命令 / 规则快照",
                onClick = onDiagnostics
            )
            NavRow(
                title = "备份配置",
                subtitle = "导出规则与设置（分享文本）",
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, vm.buildBackup())
                    }
                    context.startActivity(Intent.createChooser(send, "备份配置"))
                }
            )
            NavRow(
                title = "恢复配置",
                subtitle = "粘贴备份内容并导入",
                onClick = { showRestore = true }
            )
            NavRow(
                title = "清理运行数据（卸载前）",
                subtitle = "停止网关、清除规则、删除 /data/adb/mihomo",
                onClick = { showCleanup = true }
            )
            NavRow(
                title = "安装 / 更新核心",
                subtitle = "从 APK 资源重新解压内核与脚本",
                onClick = { vm.installCore() }
            )
        }

        if (showRestore) {
            AlertDialog(
                onDismissRequest = { showRestore = false },
                title = { Text("恢复配置") },
                text = {
                    OutlinedTextField(
                        value = restoreJson,
                        onValueChange = { restoreJson = it },
                        label = { Text("粘贴备份 JSON") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showRestore = false
                        vm.restoreBackup(restoreJson)
                    }) { Text("恢复") }
                },
                dismissButton = {
                    TextButton(onClick = { showRestore = false }) { Text("取消") }
                }
            )
        }

        if (showCleanup) {
            AlertDialog(
                onDismissRequest = { showCleanup = false },
                title = { Text("确认清理？") },
                text = { Text("将停止网关、清除 iptables 规则并删除 /data/adb/mihomo。清理后需要重新“安装/更新核心”才能使用。") },
                confirmButton = {
                    TextButton(onClick = {
                        showCleanup = false
                        vm.cleanup()
                    }) { Text("清理") }
                },
                dismissButton = {
                    TextButton(onClick = { showCleanup = false }) { Text("取消") }
                }
            )
        }

        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Spacer(Modifier.height(2.dp))
        Text(
            "Mihomo Gateway v$versionName · 内核 mihomo",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SubscriptionPage(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    var subUrl by remember(settings.subscriptionUrl) { mutableStateOf(settings.subscriptionUrl) }
    var subName by remember(settings.subName) { mutableStateOf(settings.subName) }
    val subs by vm.subscriptions.collectAsState()
    var showAddSub by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }
    var addUrl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader(title = "订阅管理", subtitle = "导入 Clash 订阅并自动补全网关字段") {
            OutlinedButton(onClick = onBack) { Text("返回") }
        }

        SectionCard(title = "订阅配置") {
            Text(
                "在下方「已保存订阅」里添加链接，导入成功后会自动切换",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "当前订阅：${settings.subscriptionUrl.ifBlank { "未设置" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "节点数：${if (settings.nodeCount > 0) settings.nodeCount else "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(title = "已保存订阅") {
            if (subs.isEmpty()) {
                Text(
                    "还没有保存的订阅，导入成功后会出现在这里",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            subs.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.activateSubscription(item) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.activeSubId == item.id,
                        onClick = { vm.activateSubscription(item) }
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (settings.activeSubId == item.id) FontWeight.SemiBold
                            else FontWeight.Normal
                        )
                        Text(
                            item.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Text(
                            "${item.nodeCount} 节点 · " +
                                if (item.lastUpdateAt <= 0) "未更新" else formatTime(item.lastUpdateAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { vm.deleteSubscription(item) }) { Text("删除") }
                }
            }
            OutlinedButton(
                onClick = { showAddSub = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("添加订阅") }
        }

        SectionCard(title = "自动更新") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "关闭", 6 to "6h", 24 to "24h", 72 to "72h").forEach { (hours, label) ->
                    val selected = settings.updateIntervalHours == hours
                    if (selected) Button(onClick = { vm.setUpdateInterval(hours) }, enabled = !busy) { Text(label) }
                    else OutlinedButton(onClick = { vm.setUpdateInterval(hours) }, enabled = !busy) { Text(label) }
                }
            }
            Text(
                "上次更新：" + if (settings.lastUpdateAt <= 0) "从未" else formatTime(settings.lastUpdateAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { vm.updateNow() }, enabled = !busy) { Text("立即更新") }
        }
        SectionCard(title = "导入流程") {
            listOf(
                "下载并校验是否为 Clash 配置",
                "写入 /data/adb/mihomo/config.yaml",
                "自动补齐 external-ui / secret / redir-port 等字段",
                "重启内核并等待 API 就绪"
            ).forEachIndexed { index, step ->
                Text(
                    "${index + 1}. $step",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        message?.let { Text(it) }

        if (showAddSub) {
            AlertDialog(
                onDismissRequest = { showAddSub = false },
                title = { Text("添加订阅") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = addName,
                            onValueChange = { addName = it },
                            label = { Text("名称") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = addUrl,
                            onValueChange = { addUrl = it },
                            label = { Text("订阅链接") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showAddSub = false
                        vm.addSubscription(addName, addUrl)
                        addName = ""
                        addUrl = ""
                    }) { Text("添加") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddSub = false }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
private fun formatTime(millis: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ModeButton(
    label: String,
    mode: IpMode,
    current: IpMode,
    busy: Boolean,
    onClick: (IpMode) -> Unit
) {
    if (mode == current) {
        Button(onClick = { onClick(mode) }, enabled = !busy) { Text(label) }
    } else {
        OutlinedButton(onClick = { onClick(mode) }, enabled = !busy) { Text(label) }
    }
}
